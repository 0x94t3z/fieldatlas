package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Downloads a pinned [ModelOffer], verifies it and installs it as a model pack. */
class ModelDownload internal constructor(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val footprint: StorageFootprint = StorageFootprint { listOf(storageRoot) },
    private val downloader: VerifiedResumableDownload = VerifiedResumableDownload(),
) {
    fun resumableBytes(spec: ModelOffer): Long = partialFile(spec).length().coerceIn(0, spec.bytes)

    /** Frees a paused download's saved part the user no longer wants. */
    fun discardResumable(spec: ModelOffer): Boolean = partialFile(spec).delete()

    private fun partialFile(spec: ModelOffer) = File(storageRoot, "model-downloads/${spec.id}-${spec.version}.part")

    suspend fun install(spec: ModelOffer, downloadUrl: String = spec.url, onProgress: (Long) -> Unit): InstalledAsset = withContext(Dispatchers.IO) {
        validatedDownloadUrl(downloadUrl, ".gguf")
        registry.list().firstOrNull { it.id == spec.id && it.version == spec.version }
            ?.let { return@withContext it }
        val destination = File(storageRoot, "packs/${spec.id}/${spec.version}")
        val partial = partialFile(spec).apply { parentFile?.mkdirs() }
        // A process can die after the verified model was moved but before registry.add().
        // Return that orphan to staging and verify it rather than fetching gigabytes again.
        if (destination.exists()) {
            val orphan = File(destination, "model.gguf")
            if (orphan.isFile && orphan.length() == spec.bytes) {
                Files.move(orphan.toPath(), partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            check(destination.deleteRecursively()) { "Could not clear interrupted model install" }
        }
        // Leave room for Android and the app to operate after the transfer completes.
        val remaining = spec.bytes - partial.length().coerceAtMost(spec.bytes)
        admitWithinLimit(remaining)
        check(storageRoot.usableSpace - 512_000_000L >= remaining + StorageBudget.METADATA_RESERVE_BYTES) {
            "Not enough free space for this model"
        }
        downloader.download(downloadUrl, spec.bytes, spec.sha256, partial, onProgress)
        // History, attachments and caches can grow while a long transfer is running.
        // Count the completed staging file again before committing the installation.
        // On rejection it stays resumable; do not delete verified user downloads.
        check(StorageBudget.evaluate(footprint.bytes(), StorageBudget.METADATA_RESERVE_BYTES,
            (storageRoot.usableSpace - 512_000_000L).coerceAtLeast(0)) == BudgetDecision.Allowed) {
            "Storage changed during download. Free some space and retry to finish installing."
        }
        commit(destination, partial, PackManifest(1, spec.id, spec.version, PackType.MODEL, spec.title, spec.license,
            listOf(spec.url, downloadUrl).distinct(), listOf(PackArtifact("model.gguf", spec.bytes, spec.sha256))))
    }

    /**
     * Installs a model file the user chose, copied in from [input] ([bytes] long) and hashed in the same pass. A
     * [ModelFileKind.Tested] file must have that model's SHA-256 and becomes that model, exactly as a download does; a
     * [ModelFileKind.Own] one is installed under an id from its own hash, titled [title] (its header's name).
     */
    suspend fun installFromFile(kind: ModelFileKind, input: InputStream, bytes: Long, fileName: String?, title: String,
        license: String?, onProgress: (Long) -> Unit): InstalledAsset = withContext(Dispatchers.IO) {
        val tested = (kind as? ModelFileKind.Tested)?.offer
        require(tested != null || kind is ModelFileKind.Own) { "Only a tested or own model file can be installed" }
        if (tested != null) registry.list().firstOrNull { it.id == tested.id && it.version == tested.version }
            ?.let { return@withContext it }
        // The source stays where the user keeps it, so only the installed copy counts against the budget.
        admitWithinLimit(bytes)
        check(storageRoot.usableSpace - 512_000_000L >= bytes + StorageBudget.METADATA_RESERVE_BYTES) {
            "Not enough free space for this model"
        }
        // An import the process died in left its copy behind; one import runs at a time, so any earlier copy is stale.
        File(storageRoot, "model-downloads").listFiles { file -> file.name.startsWith("import-") && file.name.endsWith(".part") }
            ?.forEach { it.delete() }
        val staging = File(storageRoot, "model-downloads/import-${System.nanoTime()}.part").apply { parentFile?.mkdirs() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var reported = 0L
            FileOutputStream(staging).use { output ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    copied += read
                    check(copied <= bytes) { "The model file changed while it was being copied" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    // Progress every 64 MB: often enough to move the bar, rarely enough not to flood the screen.
                    if (copied - reported >= 64L shl 20) { reported = copied; onProgress(copied); currentCoroutineContext().ensureActive() }
                }
                output.fd.sync()
            }
            onProgress(copied)
            check(copied == bytes) { "The model file is shorter than it said: ${copied} of ${bytes} bytes" }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val manifest = if (tested != null) {
                if (sha != tested.sha256) throw ModelFileImport.mismatch(tested)
                PackManifest(1, tested.id, tested.version, PackType.MODEL, tested.title, tested.license,
                    listOf(tested.url), listOf(PackArtifact("model.gguf", tested.bytes, tested.sha256)))
            } else {
                val id = ModelFileImport.ownId(sha)
                registry.list().firstOrNull { it.id == id }?.let { return@withContext it }
                PackManifest(1, id, "1", PackType.MODEL, title, license?.trim()?.takeIf { it.isNotEmpty() } ?: "Not stated in the file",
                    listOf("file:" + (fileName ?: "model.gguf")), listOf(PackArtifact("model.gguf", bytes, sha)))
            }
            val destination = File(storageRoot, "packs/${manifest.id}/${manifest.version}")
            if (destination.exists()) check(destination.deleteRecursively()) { "Could not clear an interrupted model install" }
            commit(destination, staging, manifest)
        } finally {
            staging.delete()
        }
    }

    /**
     * Refuses a model that would take everything Field Atlas holds past its 50 GB limit, saying so: all three models,
     * every collection and the voice come to about 47.6 GB, so only something extra, such as a user's own model, gets here.
     */
    private fun admitWithinLimit(incoming: Long) {
        val decision = StorageBudget.evaluate(footprint.bytes(), incoming + StorageBudget.METADATA_RESERVE_BYTES, Long.MAX_VALUE)
        if (decision != BudgetDecision.Allowed) throw AssetImportException("This model would take Field Atlas past its 50 GB limit",
            budgetDecision = decision)
    }

    /** Moves a verified model file into [destination], writes its manifest and registers it. */
    private suspend fun commit(destination: File, verified: File, manifest: PackManifest): InstalledAsset {
        check(destination.mkdirs()) { "Could not create model directory" }
        val modelFile = File(destination, "model.gguf")
        Files.move(verified.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        val manifestBytes = Json.encodeToString(manifest).encodeToByteArray()
        PackManifestParser.parse(manifestBytes)
        FileOutputStream(File(destination, "manifest.json")).use { output ->
            output.write(manifestBytes)
            output.fd.sync()
        }
        val installed = InstalledAsset(
            id = manifest.id,
            version = manifest.version,
            type = PackType.MODEL,
            title = manifest.title,
            license = manifest.license,
            installedBytes = manifest.artifacts.single().bytes,
            manifestSha256 = Sha256.digest(manifestBytes),
            rootPath = destination.absolutePath,
        )
        registry.add(installed)
        return installed
    }
}
