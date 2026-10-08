package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedResumableDownloadTest {
    private val body = "verified offline research pack".encodeToByteArray()

    @Test fun appendsOnlyAValidatedRange() = runBlocking {
        withPartial(body.copyOfRange(0, 9)) { partial ->
            var requestedOffset = -1L
            val downloader = VerifiedResumableDownload { _, offset ->
                requestedOffset = offset
                FakeConnection(206, body.copyOfRange(9, body.size), "bytes 9-${body.size - 1}/${body.size}")
            }
            val progress = mutableListOf<Long>()
            downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial, progress::add)
            assertEquals(9L, requestedOffset)
            assertEquals(9L, progress.first())
            assertEquals(body.size.toLong(), progress.last())
            assertArrayEquals(body, partial.readBytes())
        }
    }

    @Test fun serverIgnoringRangeRestartsInsteadOfAppending() = runBlocking {
        withPartial("old bytes".encodeToByteArray()) { partial ->
            val downloader = VerifiedResumableDownload { _, offset ->
                assertEquals(9L, offset)
                FakeConnection(200, body)
            }
            downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {}
            assertArrayEquals(body, partial.readBytes())
        }
    }

    @Test fun wrongContentRangeNeverTouchesSavedBytes() = runBlocking {
        val saved = body.copyOfRange(0, 9)
        withPartial(saved) { partial ->
            val downloader = VerifiedResumableDownload { _, _ ->
                FakeConnection(206, body.copyOfRange(9, body.size), "bytes 8-${body.size - 1}/${body.size}")
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {}
                }
            }
            assertArrayEquals(saved, partial.readBytes())
        }
    }

    @Test fun rejectedRangeCanFallBackToFreshDownload() = runBlocking {
        withPartial(body.copyOfRange(0, 9)) { partial ->
            val offsets = mutableListOf<Long>()
            val downloader = VerifiedResumableDownload { _, offset ->
                offsets += offset
                if (offset > 0) FakeConnection(416, byteArrayOf()) else FakeConnection(200, body)
            }
            downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {}
            assertEquals(listOf(9L, 0L), offsets)
            assertArrayEquals(body, partial.readBytes())
        }
    }

    @Test fun completeVerifiedPartialNeedsNoNetwork() = runBlocking {
        withPartial(body) { partial ->
            val downloader = VerifiedResumableDownload { _, _ -> error("Network must not be opened") }
            downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {}
            assertArrayEquals(body, partial.readBytes())
        }
    }

    @Test fun corruptCompletePartialIsReplaced() = runBlocking {
        withPartial(ByteArray(body.size)) { partial ->
            val downloader = VerifiedResumableDownload { _, offset ->
                assertEquals(0L, offset)
                FakeConnection(200, body)
            }
            downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {}
            assertArrayEquals(body, partial.readBytes())
        }
    }

    @Test fun interruptedResponseHeadersReleaseTheConnectionAndPreserveProgress() = runBlocking {
        val saved = body.copyOfRange(0, 9)
        withPartial(saved) { partial ->
            val response = object : FakeConnection(206, body) {
                override fun getResponseCode(): Int = throw IOException("Connection interrupted")
            }
            val downloader = VerifiedResumableDownload { _, _ -> response }
            assertThrows(IOException::class.java) {
                runBlocking { downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {} }
            }
            assertEquals(true, response.disconnected)
            assertArrayEquals(saved, partial.readBytes())
        }
    }

    @Test fun failingRangeRestartReleasesRejectedConnectionWithoutDiscardingPartial() = runBlocking {
        val saved = body.copyOfRange(0, 9)
        withPartial(saved) { partial ->
            val rejected = FakeConnection(416, byteArrayOf())
            val downloader = VerifiedResumableDownload { _, offset ->
                if (offset > 0) rejected else throw IOException("Offline")
            }
            assertThrows(IOException::class.java) {
                runBlocking { downloader.download("https://example.test/pack", body.size.toLong(), Sha256.digest(body), partial) {} }
            }
            assertEquals(true, rejected.disconnected)
            assertArrayEquals(saved, partial.readBytes())
        }
    }

    @Test fun redirectHeaderFailuresReleaseTheConnection() {
        val response = object : FakeConnection(302, byteArrayOf()) {
            override fun getHeaderField(name: String?): String? = throw IllegalStateException("Header failed")
        }
        assertThrows(IllegalStateException::class.java) {
            openHttps("https://example.test/pack", 0) { response }
        }
        assertEquals(true, response.disconnected)
    }

    @Test fun connectionConfigurationFailuresReleaseTheConnection() {
        val response = object : FakeConnection(200, body) {
            override fun setRequestProperty(key: String?, value: String?) = throw IllegalStateException("Configuration failed")
        }
        assertThrows(IllegalStateException::class.java) {
            openHttps("https://example.test/pack", 0) { response }
        }
        assertEquals(true, response.disconnected)
    }

    @Test fun openingResponseHeaderFailuresReleaseTheConnection() {
        val response = object : FakeConnection(200, body) {
            override fun getResponseCode(): Int = throw IOException("Header interrupted")
        }
        assertThrows(IOException::class.java) {
            openHttps("https://example.test/pack", 0) { response }
        }
        assertEquals(true, response.disconnected)
    }

    @Test fun insecureCredentialedOrFragmentRedirectsAreRejectedBeforeTheNextRequest() {
        for (target in listOf("http://mirror.test/file", "https://name:password@mirror.test/file",
            "https://mirror.test/file#fragment", "https://mirror.test:65536/file")) {
            var calls = 0
            val response = object : FakeConnection(302, byteArrayOf()) {
                override fun getHeaderField(name: String?): String? = target
            }
            assertThrows(IllegalStateException::class.java) {
                openHttps("https://example.test/pack", 9) { calls++; response }
            }
            assertEquals(1, calls)
            assertEquals(true, response.disconnected)
        }
    }

    @Test fun validSignedRedirectPreservesRangeAndTransfersConnectionOwnership() {
        val redirect = object : FakeConnection(302, byteArrayOf()) {
            override fun getHeaderField(name: String?): String? = "https://cdn.test/file?signature=fixture"
        }
        val final = FakeConnection(206, body)
        var calls = 0
        val returned = openHttps("https://example.test/pack", 9) { if (calls++ == 0) redirect else final }
        assertEquals(2, calls)
        assertEquals(true, redirect.disconnected)
        assertEquals(false, final.disconnected)
        assertEquals("bytes=9-", final.getRequestProperty("Range"))
        assertEquals("identity", final.getRequestProperty("Accept-Encoding"))
        returned.disconnect()
    }

    @Test fun redirectLoopsAreBoundedAndEveryHopIsReleased() {
        val responses = mutableListOf<FakeConnection>()
        assertThrows(IllegalStateException::class.java) {
            openHttps("https://example.test/pack", 0) {
                object : FakeConnection(302, byteArrayOf()) {
                    override fun getHeaderField(name: String?): String? = "/pack"
                }.also(responses::add)
            }
        }
        assertEquals(6, responses.size)
        assertEquals(true, responses.all { it.disconnected })
    }

    private suspend fun withPartial(bytes: ByteArray, block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("atlas-resume-test").toFile()
        try {
            val partial = File(directory, "download.part")
            partial.writeBytes(bytes)
            block(partial)
        } finally {
            directory.deleteRecursively()
        }
    }

    private open class FakeConnection(
        private val status: Int,
        private val bytes: ByteArray,
        private val range: String? = null,
    ) : HttpURLConnection(URL("https://example.test/pack")) {
        override fun connect() = Unit
        var disconnected = false
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = if (name == "Content-Range") range else null
    }
}
