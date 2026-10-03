package xyz.fieldatlas.desktop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.PromptBuilder

/** Replace instructions only; preserve packed evidence, question, and all model settings. */
internal fun diagnosticAnswerPrompt(prompt: String, policy: String): String {
    require(policy in setOf("app", "source-only", "source-partial", "evidence-first", "bounded-summary"))
    if (policy == "app") return prompt
    val boundary = "\nEvidence contains selected excerpts, not complete documents."
    val index = prompt.indexOf(boundary)
    // Planning and model-only calls have no evidence-policy boundary.
    if (index < 0) return prompt
    if (policy == "bounded-summary") {
        if (!prompt.contains("Use relevant excerpts and your knowledge")) return prompt
        return """/no_think
Use only the supplied excerpts to answer the question in under 100 words. Answer the parts the excerpts support, then briefly name any requested information they do not establish. Do not refuse supported parts because another part is missing. Do not add outside facts, examples, or assumptions. A finding about a particular subtype, study, place, or date must stay limited to that case. Missing information does not mean something is false. Ignore excerpts unrelated to the question. Write plain explanatory sentences, without quotations or citations.""" + prompt.substring(index)
    }
    if (policy == "evidence-first") {
        // Restrict the experiment to the mixed-answer path; preserve source-only requests.
        if (!prompt.contains("Use relevant excerpts and your knowledge")) return prompt
        return """/no_think
Answer using only the supplied excerpts, not outside knowledge. First write "From saved sources" and copy up to three short, directly relevant quotations, each on its own line with its matching source number: "exact quotation" [S1]. Copy the wording exactly and keep qualifications or conflicting findings. Do not quote unrelated material.
Then write "Model explanation" and briefly answer the requested parts using only those findings. A statement about one subtype, test, place or time does not describe the whole category or the present. Do not invent examples, properties or comparisons. Say which requested parts the excerpts do not establish; still answer the parts they do establish. Do not mistake missing information for a negative result. Put citations only on exact quotations. Keep the explanation under 100 words. If no excerpt helps, say so without inventing an answer.""" + prompt.substring(index)
    }
    val strict = if (policy == "source-partial") """/no_think
Answer using only the numbered evidence below. Address each requested part separately in concise bullets. Cite each supported factual claim with its matching source number, such as [S1]. Preserve qualifications. Do not add outside facts or examples. A citation must support the claim, not merely mention its subject. If a requested part is not supported, identify that specific gap briefly and still answer the supported parts. Do not reject the entire question merely because one part is missing. If none of the requested parts is supported, say so briefly. Do not repeat these instructions.""" else PromptBuilder::class.java.getDeclaredField("POLICY")
        .apply { isAccessible = true }.get(null) as String
    return strict + prompt.substring(index)
}

/** Test-only inference adapter; never packaged into the Android app. */
internal class DesktopGateway(
    endpoint: String,
    private val system: String,
    private val answerPolicy: String = "app",
    // The app passes no seed for answers, so each phone run differs; repeat runs over several seeds.
    private val defaultSeed: Int = 17,
    private val temperature: Double = 0.3,
) : InferenceGateway {
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
                add(buildJsonObject { put("role", "user"); put("content", diagnosticAnswerPrompt(prompt, answerPolicy)) })
            })
            put("max_tokens", maxTokens)
            put("seed", if (seed >= 0) seed else defaultSeed)
            put("temperature", temperature)
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
