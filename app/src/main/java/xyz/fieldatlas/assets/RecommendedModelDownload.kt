package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The same pinned artifact as models/compact-qwen3.5-2b.example.json. */
object RecommendedModel {
    const val id = "qwen3.5-2b-q4-k-m"
    const val version = "1.0.0"
    const val title = "Qwen3.5 2B — Q4_K_M Compact"
    const val license = "Apache-2.0"
    const val bytes = 1_396_198_496L
    const val sha256 = "57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8"
    const val url = "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/7d26695454df6de5fbcce2e58681e62dae06ce43/Qwen_Qwen3.5-2B-Q4_K_M.gguf"
}

class RecommendedModelDownload(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val footprint: StorageFootprint = StorageFootprint { listOf(storageRoot) },
) {
    fun resumableBytes(): Long = File(storageRoot,
        "model-downloads/${RecommendedModel.id}-${RecommendedModel.version}.part")
        .length().coerceIn(0, RecommendedModel.bytes)

    suspend fun install(onProgress: (Long) -> Unit): InstalledAsset = withContext(Dispatchers.IO) {
        val spec = RecommendedModel
        registry.list().firstOrNull { it.id == spec.id && it.version == spec.version }
            ?.let { return@withContext it }
        val destination = File(storageRoot, "packs/${spec.id}/${spec.version}")
        val staging = File(storageRoot, "model-downloads").apply { mkdirs() }
        val partial = File(staging, "${spec.id}-${spec.version}.part")
        // A process can die after the verified model was moved but before registry.add().
        // Return that orphan to staging and verify it rather than fetching 1.4 GB again.
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
        VerifiedResumableDownload().download(spec.url, spec.bytes, spec.sha256, partial, onProgress)
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
                sourceUrls = listOf(spec.url),
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
