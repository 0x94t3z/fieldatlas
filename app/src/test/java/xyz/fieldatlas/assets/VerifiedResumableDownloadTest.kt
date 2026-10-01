package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.File
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

    private class FakeConnection(
        private val status: Int,
        private val bytes: ByteArray,
        private val range: String? = null,
    ) : HttpURLConnection(URL("https://example.test/pack")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = if (name == "Content-Range") range else null
    }
}
