package xyz.fieldatlas.desktop

import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopGatewayTest {
    @Test fun rejectsRemoteEndpointsBeforeSendingAnyQuestion() {
        listOf("https://example.com", "http://192.168.1.2:8081", "http://127.0.0.1.evil.test").forEach {
            assertThrows(IllegalArgumentException::class.java) { DesktopGateway(it, "system") }
        }
    }

    @Test fun sendsIsolatedTurnsAndPreservesRawOutput() = runBlocking {
        val bodies = mutableListOf<JsonObject>()
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            repeat(2) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    var length = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) read += reader.read(body, read, length - read)
                    bodies += Json.parseToJsonElement(String(body)).jsonObject
                    val response = """{"choices":[{"message":{"content":"raw claim [S9]"},"finish_reason":"stop"}],"usage":{"completion_tokens":5}}"""
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${response.length}\r\nConnection: close\r\n\r\n$response".toByteArray())
                }
            }
        }
        try {
            val gateway = DesktopGateway("http://127.0.0.1:${server.localPort}", "research system")
            assertEquals(listOf("raw claim [S9]"), gateway.generate("question", 42, "planner system", 17).toList())
            gateway.generate("second question", 30).toList()
            assertEquals(2, bodies[1].getValue("messages").jsonArray.size)
            assertEquals("research system", bodies[1].getValue("messages").jsonArray[0].jsonObject.getValue("content").jsonPrimitive.content)
            assertEquals(17, bodies[0].getValue("seed").jsonPrimitive.int)
            assertEquals(42, bodies[0].getValue("max_tokens").jsonPrimitive.int)
            assertEquals("raw claim [S9]", gateway.calls[0].getValue("rawAnswer").jsonPrimitive.content)
        } finally { server.close(); worker.join(1000) }
    }
}
