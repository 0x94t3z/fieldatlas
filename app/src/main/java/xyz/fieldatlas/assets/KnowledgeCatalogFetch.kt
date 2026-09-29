package xyz.fieldatlas.assets

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Provisioning only: callers must invoke this from an explicit user refresh action. */
class KnowledgeCatalogFetch(
    private val url: String = CATALOG_URL,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun fetch(): ByteArray = withContext(Dispatchers.IO) {
        var target = URL(url)
        repeat(6) { hop ->
            currentCoroutineContext().ensureActive()
            require(target.protocol == "https" && target.userInfo == null) { "Unsafe catalog URL" }
            val connection = openConnection(target)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                val code = connection.responseCode
                if (code in 300..399) {
                    check(hop < 5) { "Too many catalog redirects" }
                    val location = connection.getHeaderField("Location")
                    check(!location.isNullOrBlank()) { "Missing redirect location" }
                    target = URL(target, location)
                } else {
                    check(code == 200) { "Catalog request failed" }
                    check(connection.contentLengthLong <= KnowledgeCatalogRepository.MAX_BYTES) { "Catalog too large" }
                    return@withContext connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= KnowledgeCatalogRepository.MAX_BYTES) { "Catalog too large" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("Too many catalog redirects")
    }

    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/0x94t3z/fieldatlas/main/app/src/main/assets/knowledge/catalog.json"
    }
}
