package xyz.fieldatlas.desktop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState

/** Test-only inference adapter; never packaged into the Android app. */
internal class DesktopGateway(endpoint: String, private val system: String) : InferenceGateway {
    private val base = URI(endpoint).also {
        require(it.scheme == "http" && it.host in setOf("127.0.0.1", "localhost", "[::1]") &&
            it.userInfo == null && it.query == null && it.fragment == null && it.path in listOf("", "/")) {
            "Desktop inference requires a loopback-only HTTP endpoint"
        }
    }
    override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
    val calls = mutableListOf<JsonObject>()
    override suspend fun load(modelPath: String, systemPrompt: String) = Unit
    override suspend fun unload() = Unit
    override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
        val request = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt ?: system) })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
            put("max_tokens", maxTokens)
            put("seed", if (seed >= 0) seed else 17)
            put("temperature", 0.3)
            put("top_k", 40); put("top_p", 0.95); put("min_p", 0.05)
            put("repeat_penalty", 1.10); put("repeat_last_n", 128)
            put("stream", false)
            put("cache_prompt", false)
            put("chat_template_kwargs", buildJsonObject { put("enable_thinking", false) })
        }
        val started = System.nanoTime()
        val connection = base.resolve("/v1/chat/completions").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 180_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Local inference HTTP ${connection.responseCode}" }
            val response = connection.inputStream.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
            val choice = response.getValue("choices").jsonArray.first().jsonObject
            val raw = choice.getValue("message").jsonObject.getValue("content").jsonPrimitive.content
            calls += buildJsonObject {
                put("request", request); put("rawAnswer", raw)
                put("response", response)
                put("durationMillis", (System.nanoTime() - started) / 1_000_000)
            }
            emit(raw)
        } catch (error: Exception) {
            calls += buildJsonObject { put("request", request); put("error", error.toString()) }
            throw error
        } finally { connection.disconnect() }
    }
}
