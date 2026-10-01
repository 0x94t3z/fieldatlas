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

/** Keeps incomplete bytes across process death; only a full pinned checksum can complete a download. */
internal class VerifiedResumableDownload(
    private val open: (String, Long) -> HttpURLConnection = ::openHttps,
) {
    suspend fun download(
        url: String,
        expectedBytes: Long,
        expectedSha256: String,
        partial: File,
        onProgress: (Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(expectedBytes > 0 && expectedSha256.matches(Regex("[0-9a-f]{64}")))
        check(partial.parentFile?.isDirectory == true || partial.parentFile?.mkdirs() == true) {
            "Could not create download directory"
        }
        if (partial.length() > expectedBytes) check(partial.delete()) { "Could not clear oversized download" }
        var offset = partial.length()
        if (offset == expectedBytes) {
            if (matchesChecksum(partial, expectedSha256)) {
                onProgress(offset)
                return@withContext partial
            }
            check(partial.delete()) { "Could not clear corrupt download" }
            offset = 0
        }
        onProgress(offset)

        var connection = open(url, offset)
        // A changed or nonconforming server may reject the old offset. Keep the saved bytes
        // until a fresh full response is ready, then replace them rather than append blindly.
        if (offset > 0 && (connection.responseCode == 416 ||
                (connection.responseCode == HttpURLConnection.HTTP_PARTIAL &&
                    !validContentRange(connection.getHeaderField("Content-Range"), offset,
                        expectedBytes, connection.contentLengthLong)))) {
            connection.disconnect()
            connection = open(url, 0)
            offset = 0
        }
        try {
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    check(connection.contentLengthLong < 0 || connection.contentLengthLong == expectedBytes) {
                        "Unexpected download size"
                    }
                    false // The server ignored Range (or If-Range); start a fresh file.
                }
                HttpURLConnection.HTTP_PARTIAL -> {
                    check(offset > 0 && validContentRange(
                        connection.getHeaderField("Content-Range"), offset, expectedBytes,
                        connection.contentLengthLong,
                    )) { "The server returned an invalid download range" }
                    true
                }
                else -> error("Download failed (HTTP ${connection.responseCode})")
            }
            check(connection.getHeaderField("Content-Encoding").isNullOrBlank() ||
                connection.getHeaderField("Content-Encoding").equals("identity", ignoreCase = true)) {
                "Compressed downloads cannot be resumed safely"
            }
            var copied = if (append) offset else 0L
            onProgress(copied)
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        check(copied <= expectedBytes) { "Download exceeded expected size" }
                        output.write(buffer, 0, read)
                        onProgress(copied)
                    }
                    output.fd.sync()
                }
            }
            check(copied == expectedBytes) { "Download was incomplete" }
            if (!matchesChecksum(partial, expectedSha256)) {
                check(partial.delete()) { "Could not clear corrupt download" }
                error("Download checksum did not match; retry to start again")
            }
            partial
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun matchesChecksum(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == expected
    }
}

internal fun validContentRange(value: String?, start: Long, total: Long, contentLength: Long): Boolean {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value ?: return false) ?: return false
    val actualStart = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].toLongOrNull() ?: return false
    val actualTotal = match.groupValues[3].toLongOrNull() ?: return false
    return actualStart == start && end >= start && end < total && actualTotal == total &&
        (contentLength < 0 || contentLength == end - start + 1)
}

private fun openHttps(address: String, offset: Long): HttpURLConnection {
    var current = address
    repeat(6) {
        val url = URL(current)
        check(url.protocol == "https") { "Downloads must use HTTPS" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        val status = connection.responseCode
        if (status !in 300..399) return connection
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        check(!location.isNullOrBlank()) { "Download redirected without a location" }
        current = URL(url, location).toString()
    }
    error("Too many download redirects")
}
