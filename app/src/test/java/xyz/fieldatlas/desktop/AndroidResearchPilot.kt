package xyz.fieldatlas.desktop

import android.os.Build
import com.arm.aichat.InferenceEngine
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import xyz.fieldatlas.inference.LlamaInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.*
import xyz.fieldatlas.ui.AppContainer

/** Opt-in adb shell harness, never packaged into or enabled in the installed app.
 * Uses real Android app classes and ARM inference, but not the app UID/UI/lifecycle. */
object AndroidResearchPilot {
    @JvmStatic fun main(args: Array<String>) {
        val code = try { runBlocking { run(File(args.single())) }; 0 }
            catch (failure: Throwable) { failure.printStackTrace(); 1 }
        exitProcess(code)
    }

    private suspend fun run(configFile: File) {
        check(System.getProperty("java.vm.name") == "Dalvik") { "Physical Android ART required" }
        val config = Json.parseToJsonElement(configFile.readText()).jsonObject
        val output = File(config.getValue("output").jsonPrimitive.content)
        val files = config.getValue("files").jsonObject
        fun pinned(name: String): File {
            val pin = files.getValue(name).jsonObject
            val file = File(pin.getValue("path").jsonPrimitive.content)
            check(file.length() == pin.getValue("bytes").jsonPrimitive.long && sha256(file) == pin.getValue("sha256").jsonPrimitive.content) { "Identity mismatch: $name" }
            return file
        }
        val source = pinned("source")
        val index = pinned("index")
        val binary = pinned("binary")
        val model = pinned("model")
        val nativeDir = config.getValue("nativeDir").jsonPrimitive.content
        KnowledgeDatabase.open(source).use { }
        println("Android pilot: subprocess preflight, tempDir=${System.getProperty("java.io.tmpdir")}")
        check(runLeviathan(listOf(binary.path, "--version")).trim() == "leviathan 0.1.0")
        // Instantiate the existing JNI engine with our extracted library directory, not an
        // installed app context. No vendor code is modified and no normal app files are read.
        val engine = Class.forName("com.arm.aichat.internal.InferenceEngineImpl")
            .getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(nativeDir) as InferenceEngine
        val initialized = withTimeout(60_000) {
            engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error }
        }
        if (initialized is InferenceEngine.State.Error) throw initialized.exception
        val native = LlamaInferenceGateway(engine)
        val system = AppContainer::class.java.getDeclaredField("SYSTEM_PROMPT").apply { isAccessible = true }.get(null) as String
        val rows = mutableListOf<JsonObject>()
        val json = Json { prettyPrint = true }
        fun save() {
            output.writeText(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("scope", "physical_android_shell_current_research_pipeline_and_native_inference")
                put("productionAdopted", false); put("qualityVerdict", "UNSCORED: completion is not correctness")
                put("device", buildJsonObject { put("model", Build.MODEL); put("api", Build.VERSION.SDK_INT) })
                put("provenance", config.getValue("provenance")); put("files", files)
                put("limitations", buildJsonArray {
                    add("adb shell UID with extracted debug APK classes/libraries, not installed release UI, app permission policy or lifecycle acceptance.")
                    add("Single source pack, keyword-only; forced answer seed 17; no independent held-out accuracy set or cold-cache latency study.")
                    add("Leviathan candidates plus shared app title/diversity ranking; app title anchors are not upstream hits.")
                    add("Process RSS includes native inference, not full Android app RAM or 12GB-device acceptance. Not a 20–50GB library test.")
                })
                put("runs", JsonArray(rows))
            }))
        }
        var failures = 0
        try {
            println("Android pilot: loading pinned model")
            native.load(model.path, system)
            for (item in config.getValue("questions").jsonArray) for (mode in listOf("app", "leviathan")) {
                val question = item.jsonPrimitive.content
                println("Android pilot: $mode / $question")
                val baseline = MultiKnowledgeRetriever({ listOf(source) })
                val experimental = if (mode == "leviathan") DesktopLeviathanRetriever(baseline, source, index, binary) else null
                try {
                    val searches = mutableListOf<JsonObject>()
                    val backend: Retriever = experimental ?: baseline
                    val recording = object : Retriever by backend {
                        override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) =
                            searchQuestion(query, question, limit, false, onProgress)
                        override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                                                            onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                            val start = System.nanoTime()
                            return backend.searchQuestion(query, question, limit, placesOnly, onProgress).also { evidence ->
                                searches += buildJsonObject {
                                    put("query", query); put("elapsedMs", (System.nanoTime() - start) / 1_000_000.0)
                                    put("candidates", Json.encodeToJsonElement(evidence))
                                }
                            }
                        }
                    }
                    var calls = 0
                    val gateway = object : InferenceGateway by native {
                        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> {
                            calls++
                            return native.generate(prompt, maxTokens, systemPrompt, if (seed < 0) 17 else seed, savedPrefixChars)
                        }
                    }
                    var sources = emptyList<Evidence>()
                    val answer = StringBuilder()
                    var metrics: ResearchMetrics? = null
                    var error: String? = null
                    ResearchOrchestrator(recording, gateway, articleDepth = true).research(question).collect { event ->
                        when (event) {
                            is ResearchEvent.Sources -> sources = event.evidence
                            is ResearchEvent.Lead -> { answer.clear(); answer.append(event.text) }
                            is ResearchEvent.Token -> { if (event.replace) answer.clear(); answer.append(event.text) }
                            is ResearchEvent.Complete -> metrics = event.metrics
                            is ResearchEvent.Failed -> error = event.message
                            is ResearchEvent.InsufficientEvidence -> error = event.reason
                            else -> Unit
                        }
                    }
                    if (metrics == null || error != null) failures++
                    rows += buildJsonObject {
                        put("backend", mode); put("question", question); put("answerSeed", 17)
                        put("status", if (metrics != null && error == null) "COMPLETED_UNSCORED" else "ERROR")
                        error?.let { put("error", it) }
                        put("sources", Json.encodeToJsonElement(sources)); put("searches", JsonArray(searches))
                        put("experimentalSearches", JsonArray(experimental?.traces.orEmpty()))
                        put("inferenceCalls", calls); put("visibleAnswer", AnswerText.finalized(answer.toString(), sources.size))
                        put("metrics", Json.encodeToJsonElement(metrics))
                        put("memoryStatus", File("/proc/self/status").readLines().filter { it.startsWith("VmRSS:") || it.startsWith("VmHWM:") }.joinToString("\n"))
                    }
                    save()
                    println("Android pilot recorded: $mode -> ${rows.last()["status"]}")
                } finally { experimental?.close() }
            }
            check(failures == 0) { "$failures pilot runs failed" }
            for (name in listOf("source", "index", "binary", "model")) pinned(name)
        } finally {
            try { if (native.state.value is InferenceState.Ready || native.state.value is InferenceState.Failed) native.unload() }
            finally { engine.destroy() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
