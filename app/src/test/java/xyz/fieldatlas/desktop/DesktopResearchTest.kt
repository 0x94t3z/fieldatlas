package xyz.fieldatlas.desktop

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.fieldatlas.research.*
import xyz.fieldatlas.ui.AppContainer

/** Opt-in real-model run, NOT an accuracy assertion or part of the normal unit suite. */
class DesktopResearchTest {
    @Test fun recordRealModelAnswers() = runBlocking {
        val configPath = System.getenv("FIELDATLAS_DESKTOP_CONFIG")
        assumeTrue("Desktop evaluation is opt-in", !configPath.isNullOrBlank())
        val config = Json.parseToJsonElement(File(configPath!!).readText()).jsonObject
        val output = File(config.getValue("output").jsonPrimitive.content)
        val databases = config.getValue("databases").jsonArray.map { File(it.jsonPrimitive.content).canonicalFile }
        // Fail explicitly rather than allowing the production retriever's recovery path to
        // silently turn a broken database into a supposedly successful model-only test.
        databases.forEach { KnowledgeDatabase.open(it).use { } }
        val model = File(config.getValue("model").jsonPrimitive.content).canonicalFile
        check(model.isFile) { "Model file missing" }
        val identities = buildJsonArray {
            (listOf(model) + databases).forEach { file ->
                add(buildJsonObject { put("path", file.path); put("bytes", file.length()); put("sha256", sha256(file)) })
            }
        }
        val rows = mutableListOf<JsonObject>()
        val json = Json { prettyPrint = true }
        fun save() {
            output.parentFile.mkdirs()
            output.writeText(json.encodeToString(buildJsonObject {
                put("schemaVersion", 1)
                put("provenance", config["provenance"] ?: JsonNull)
                put("qualityVerdict", "UNSCORED: completion is not correctness")
                put("identities", identities)
                put("limitations", buildJsonArray {
                    add("Uses current app orchestration, keyword retrieval, prompt packing, and answer attribution directly.")
                    add("Keyword-only: vector encoder not enabled. Not full Android inference parity.")
                    add("Desktop backend, fixed answer seed 17, non-streaming; app first-token/count metrics are not comparable.")
                    add("Native repetition-loop stopping, UI, attachments/OCR and device resources are not tested here.")
                })
                put("runs", JsonArray(rows))
            }))
        }
        var failures = 0
        for (item in config.getValue("questions").jsonArray) {
            val question = item.jsonPrimitive.content
            // Read the existing private constant instead of keeping another prompt copy.
            val system = AppContainer::class.java.getDeclaredField("SYSTEM_PROMPT").apply { isAccessible = true }.get(null) as String
            val gateway = DesktopGateway(config.getValue("endpoint").jsonPrimitive.content, system)
            val multi = MultiKnowledgeRetriever({ databases })
            val searches = mutableListOf<JsonObject>()
            val recordingRetriever = object : Retriever {
                override fun hasEligiblePacks(query: String) = multi.hasEligiblePacks(query)
                override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                    val evidence = multi.searchForQuestion(query, question, limit, onProgress)
                    searches += buildJsonObject {
                        put("query", query); put("limit", limit)
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                    return evidence
                }
            }
            var sources = emptyList<Evidence>()
            val answer = StringBuilder()
            var metrics: ResearchMetrics? = null
            var error: String? = null
            ResearchOrchestrator(recordingRetriever, gateway).research(question).collect { event ->
                when (event) {
                    is ResearchEvent.Sources -> sources = event.evidence
                    is ResearchEvent.Token -> { if (event.replace) answer.clear(); answer.append(event.text) }
                    is ResearchEvent.Complete -> metrics = event.metrics
                    is ResearchEvent.Failed -> error = event.message
                    is ResearchEvent.InsufficientEvidence -> error = event.reason
                    else -> Unit
                }
            }
            if (metrics == null || error != null) failures++
            rows += buildJsonObject {
                put("question", question)
                put("status", if (metrics != null && error == null) "COMPLETED_UNSCORED" else "ERROR")
                error?.let { put("error", it) }
                put("searches", JsonArray(searches))
                put("sources", Json.encodeToJsonElement(sources))
                put("calls", JsonArray(gateway.calls))
                put("visibleAnswer", AnswerText.finalized(answer.toString(), sources.size))
                put("metrics", Json.encodeToJsonElement(metrics))
            }
            save()
            println("Desktop question recorded: $question -> ${rows.last()["status"]}")
        }
        check(failures == 0) { "$failures desktop runs failed; partial report: $output" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
