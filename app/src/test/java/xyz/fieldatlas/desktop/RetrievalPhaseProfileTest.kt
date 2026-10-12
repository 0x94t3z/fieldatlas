package xyz.fieldatlas.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.*

/** Opt-in host API timings. Stub inference deliberately excludes model/planner latency.
 * No Android, quality, cold page-cache, full-library, or SQL-call-count claim. */
class RetrievalPhaseProfileTest {
    @Test(timeout = 300_000) fun recordBoundedLocalPhases() = runBlocking {
        val path = System.getenv("FIELDATLAS_RETRIEVAL_PROFILE")
        assumeTrue("Explicit private profiling configuration required", !path.isNullOrBlank())
        check(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val config = Json.parseToJsonElement(File(path!!).readText()).jsonObject
        val output = File(config.getValue("output").jsonPrimitive.content).canonicalFile
        check("/build/private/" in output.path && !output.exists())
        val files = config.getValue("databases").jsonArray.map { File(it.jsonPrimitive.content).canonicalFile }
        check(files.size in 1..4 && files.distinct().size == files.size)
        val questions = config.getValue("questions").jsonArray.map { it.jsonPrimitive.content }
        check(questions.size in 1..4)
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val inputs = files.associateWith { hash(it) }
        val runs = mutableListOf<JsonObject>()
        repeat(2) { round -> for (question in questions) {
            val opened = Collections.synchronizedList(mutableListOf<KnowledgeDatabase>())
            val multi = MultiKnowledgeRetriever({ files }, open = { KnowledgeDatabase.open(it).also(opened::add) })
            val calls = mutableListOf<JsonObject>()
            suspend fun <T> timed(name: String, call: suspend () -> List<T>): List<T> {
                val start = System.nanoTime()
                val rows = call()
                calls += buildJsonObject {
                    put("api", name); put("elapsedMs", (System.nanoTime() - start) / 1e6); put("rows", rows.size)
                }
                return rows
            }
            val measured = object : Retriever by multi {
                override suspend fun destinations(question: String) = timed("destinations") { multi.destinations(question) }
                override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) =
                    timed("search") { multi.search(query, limit, onProgress) }
                override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                    onProgress: suspend (SearchProgress) -> Unit) = timed("searchQuestion") {
                        multi.searchQuestion(query, question, limit, placesOnly, onProgress)
                    }
                override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                    timed("titled") { multi.titled(phrase, sections, limit) }
                override suspend fun titled(phrase: String, sections: List<String>, limit: Int, anchored: Boolean) =
                    timed("titledAnchored") { multi.titled(phrase, sections, limit, anchored) }
                override suspend fun aliased(name: String, limit: Int) = timed("aliased") { multi.aliased(name, limit) }
                override suspend fun sections(article: String, limit: Int) = timed("sections") { multi.sections(article, limit) }
                override suspend fun headed(phrase: String, words: List<String>, limit: Int, sections: List<String>) =
                    timed("headed") { multi.headed(phrase, words, limit, sections) }
                override suspend fun article(article: String, limit: Int) = timed("article") { multi.article(article, limit) }
                override suspend fun airportOverviews(place: String, limit: Int) = timed("airportOverviews") { multi.airportOverviews(place, limit) }
                override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>) =
                    timed("nearby") { multi.nearby(point, radiusKm, limit, categories) }
            }
            var plannerCalls = 0
            var answerCalls = 0
            val gateway = object : InferenceGateway {
                override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
                override suspend fun load(modelPath: String, systemPrompt: String) = error("No model loads")
                override suspend fun unload() = Unit
                override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int) = flow {
                    if (systemPrompt != null) { plannerCalls++; emit("") }
                    else { answerCalls++; emit("Diagnostic stub; not an answer.") }
                }
            }
            val started = System.nanoTime()
            var packedAt: Long? = null
            var packed: PackedPrompt? = null
            try {
                val events = ResearchOrchestrator(measured, gateway, articleDepth = true,
                    prefillSpeed = InMemoryPrefillSpeed(1.0), onEvidencePacked = { packedAt = System.nanoTime(); packed = it })
                    .research(question, contextTokenBudget = 8192).toList()
                assertFalse(events.toString(), events.any { it is ResearchEvent.Failed })
                assertTrue(events.last() is ResearchEvent.Complete)
                val untilPackedMs = packedAt?.let { (it - started) / 1e6 }
                val apiMs = calls.sumOf { it.getValue("elapsedMs").jsonPrimitive.double }
                runs += buildJsonObject {
                    put("round", round); put("question", question); put("calls", JsonArray(calls))
                    put("untilPackedMs", untilPackedMs?.let(::JsonPrimitive) ?: JsonNull)
                    put("apiMs", apiMs)
                    put("otherOrchestrationIncludingPackingMs", untilPackedMs?.let { JsonPrimitive(it - apiMs) } ?: JsonNull)
                    put("stubPlannerCalls", plannerCalls); put("stubAnswerCalls", answerCalls)
                    put("packedChars", packed?.prompt?.length ?: 0)
                    put("sources", buildJsonArray { packed?.sources?.forEach { source -> add(buildJsonObject {
                        put("documentId", source.evidence.documentId); put("chunkId", source.evidence.chunkId)
                        put("source", source.evidence.source)
                    }) } })
                }
            } finally { opened.distinct().forEach { it.close() } }
        } }
        assertEquals(inputs, files.associateWith { hash(it) })
        val result = buildJsonObject {
            put("schemaVersion", 1); put("runs", JsonArray(runs))
            put("databaseSha256", buildJsonObject { inputs.forEach { (file, digest) -> put(file.path, digest) } })
            put("limitations", "Host-only two-round keyword profile; fresh handles, uncontrolled OS cache. Stub planner emits no keywords. No model inference, SQL counts, Android timing, quality score, or speedup comparison. Residual includes packing and other orchestration, not isolated packing.")
        }
        Files.newOutputStream(output.toPath(), CREATE_NEW).bufferedWriter().use { it.write(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), result)) }
    }
}
