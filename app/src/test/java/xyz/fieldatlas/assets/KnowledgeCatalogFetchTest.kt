package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KnowledgeCatalogFetchTest {
    private open class Connection(val code: Int = 200, val body: ByteArray = "catalog".toByteArray(),
        val declared: Long = -1, val location: String? = null) : HttpURLConnection(URL("https://example.org/catalog")) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = declared
        override fun getHeaderField(name: String) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body)
    }
    @Test fun timeoutsAndCancellationCloseTransportAndPropagate() = runBlocking {
        for (failure in listOf(java.net.SocketTimeoutException("timeout"), kotlinx.coroutines.CancellationException("cancel"))) {
            val connection = object : Connection() {
                override fun getResponseCode(): Int = throw failure
            }
            val actual = runCatching { KnowledgeCatalogFetch("https://example.org/catalog") { connection }.fetch() }.exceptionOrNull()
            assertEquals(failure.javaClass, actual?.javaClass)
            assertEquals(failure.message, actual?.message)
            assertTrue(connection.closed)
        }
    }
    @Test fun successUsesBoundedTimeoutsAndCloses() = runBlocking {
        val connection = Connection()
        val bytes = KnowledgeCatalogFetch("https://example.org/catalog") { connection }.fetch()
        assertEquals("catalog", bytes.decodeToString())
        assertTrue(connection.closed)
        assertEquals(20_000, connection.connectTimeout)
        assertEquals(20_000, connection.readTimeout)
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
        assertFalse(connection.instanceFollowRedirects)
    }
    @Test fun rejectsOversizeErrorsAndUnsafeRedirectsAndAlwaysCloses() = runBlocking {
        for (connection in listOf(Connection(declared = 1_048_577), Connection(body = ByteArray(1_048_577)),
            Connection(503), Connection(302), Connection(302, location = "http://example.org/catalog"))) {
            assertTrue(runCatching { KnowledgeCatalogFetch("https://example.org/catalog") { connection }.fetch() }.isFailure)
            assertTrue(connection.closed)
        }
    }
    @Test fun redirectLoopStopsAfterFiveRedirects() = runBlocking {
        var calls = 0
        assertTrue(runCatching {
            KnowledgeCatalogFetch("https://example.org/catalog") {
                calls++; Connection(302, location = "/again")
            }.fetch()
        }.isFailure)
        assertEquals(6, calls)
    }
    @Test fun acceptsExactLimitAndRelativeHttpsRedirect() = runBlocking {
        var calls = 0
        val result = KnowledgeCatalogFetch("https://example.org/catalog") {
            if (calls++ == 0) Connection(302, location = "/next") else {
                assertEquals("https://example.org/next", it.toString())
                Connection(body = ByteArray(1_048_576), declared = 1_048_576)
            }
        }.fetch()
        assertEquals(1_048_576, result.size)
    }
}
