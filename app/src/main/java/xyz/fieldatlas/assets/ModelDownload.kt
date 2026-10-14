package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
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
        check(StorageBudget.evaluate(footprint.bytes(), remaining + StorageBudget.METADATA_RESERVE_BYTES, Long.MAX_VALUE) ==
            BudgetDecision.Allowed) { "Not enough free space for this model" }
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
        check(destination.mkdirs()) { "Could not create model directory" }
        val modelFile = File(destination, "model.gguf")
        Files.move(partial.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        val manifestBytes = Json.encodeToString(
            PackManifest(
                schemaVersion = 1,
                id = spec.id,
                version = spec.version,
                type = PackType.MODEL,
                title = spec.title,
                license = spec.license,
                sourceUrls = listOf(spec.url, downloadUrl).distinct(),
                artifacts = listOf(PackArtifact("model.gguf", spec.bytes, spec.sha256)),
            ),
        ).encodeToByteArray()
        PackManifestParser.parse(manifestBytes)
        FileOutputStream(File(destination, "manifest.json")).use { output ->
            output.write(manifestBytes)
            output.fd.sync()
        }
        val installed = InstalledAsset(
            id = spec.id,
            version = spec.version,
            type = PackType.MODEL,
            title = spec.title,
            license = spec.license,
            installedBytes = spec.bytes,
            manifestSha256 = Sha256.digest(manifestBytes),
            rootPath = destination.absolutePath,
        )
        registry.add(installed)
        installed
    }
}
