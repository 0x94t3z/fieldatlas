package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
) {
    suspend fun install(onProgress: (Long) -> Unit): InstalledAsset = withContext(Dispatchers.IO) {
        val spec = RecommendedModel
        require(registry.list().none { it.id == spec.id && it.version == spec.version }) {
            "This model is already installed"
        }
        val destination = File(storageRoot, "packs/${spec.id}/${spec.version}")
        require(!destination.exists()) { "Model files already exist; import or remove them before retrying" }
        // Leave room for Android and the app to operate after the transfer completes.
        check(StorageBudget.evaluate(registry.totalInstalledBytes(), spec.bytes,
            storageRoot.usableSpace - 512_000_000L) ==
            BudgetDecision.Allowed) { "Not enough free space for this model" }
        val staging = File(storageRoot, "model-downloads").apply { mkdirs() }
        val partial = File(staging, "${spec.id}-${spec.version}.part")
        var createdDestination = false
        var registered = false
        try {
            // A previous interrupted download is not trusted. Start over rather than append to
            // bytes whose server identity or range semantics cannot be established.
            if (partial.exists()) check(partial.delete()) { "Could not clear interrupted download" }
            val connection = openPinnedHttps(spec.url)
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Download failed (HTTP ${connection.responseCode})"
                }
                val declared = connection.contentLengthLong
                check(declared < 0 || declared == spec.bytes) { "Unexpected model download size" }
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            copied += read
                            check(copied <= spec.bytes) { "Model download exceeded expected size" }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            onProgress(copied)
                        }
                        output.fd.sync()
                    }
                }
                check(copied == spec.bytes) { "Model download was incomplete" }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(actualHash == spec.sha256) { "Model checksum did not match; nothing was installed" }
            } finally {
                connection.disconnect()
            }
            check(destination.mkdirs()) { "Could not create model directory" }
            createdDestination = true
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
            try {
                registry.add(installed)
                registered = true
            } catch (error: Exception) {
                throw error
            }
            installed
        } finally {
            partial.delete()
            if (createdDestination && !registered) destination.deleteRecursively()
        }
    }

    private fun openPinnedHttps(address: String): HttpURLConnection {
        var current = address
        repeat(6) {
            val url = URL(current)
            check(url.protocol == "https") { "Model downloads must use HTTPS" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept-Encoding", "identity")
            }
            val status = connection.responseCode
            if (status !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            check(!location.isNullOrBlank()) { "Model download redirected without a location" }
            current = URL(url, location).toString()
        }
        error("Too many model download redirects")
    }
}
