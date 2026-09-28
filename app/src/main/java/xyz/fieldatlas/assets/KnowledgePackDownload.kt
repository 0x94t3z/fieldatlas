package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Network access is confined to this explicit, user-started provisioning action. */
class KnowledgePackDownload(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val importer: AssetImporter,
) {
    suspend fun install(pack: KnowledgeCatalogEntry, onProgress: (Long) -> Unit): InstalledAsset =
        withContext(Dispatchers.IO) {
            require(registry.list().none { it.id == pack.id && it.version == pack.version }) {
                "This collection is already installed"
            }
            val freeBytes = storageRoot.usableSpace
            check(freeBytes - 512_000_000L >= pack.bytes * 2) {
                "Not enough free space to download and install this collection"
            }
            val downloadDir = File(storageRoot, "knowledge-downloads").apply { mkdirs() }
            val partial = File(downloadDir, "${pack.id}-${pack.version}.part")
            try {
                if (partial.exists()) check(partial.delete()) { "Could not clear interrupted download" }
                val connection = openHttps(pack.url)
                try {
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "Download failed (HTTP ${connection.responseCode})"
                    }
                    val declared = connection.contentLengthLong
                    check(declared < 0 || declared == pack.bytes) { "Unexpected collection download size" }
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
                                check(copied <= pack.bytes) { "Collection exceeded expected size" }
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                onProgress(copied)
                            }
                            output.fd.sync()
                        }
                    }
                    check(copied == pack.bytes) { "Collection download was incomplete" }
                    val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    check(hash == pack.sha256) { "Collection checksum did not match; nothing was installed" }
                } finally {
                    connection.disconnect()
                }
                importer.importArchive(partial, pack.id, pack.version, PackType.KNOWLEDGE)
            } finally {
                partial.delete()
            }
        }

    private fun openHttps(address: String): HttpURLConnection {
        var current = address
        repeat(6) {
            val url = URL(current)
            check(url.protocol == "https") { "Collection downloads must use HTTPS" }
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
            check(!location.isNullOrBlank()) { "Download redirected without a location" }
            current = URL(url, location).toString()
        }
        error("Too many collection download redirects")
    }
}
