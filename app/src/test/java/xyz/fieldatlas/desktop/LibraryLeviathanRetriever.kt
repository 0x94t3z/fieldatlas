package xyz.fieldatlas.desktop

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import xyz.fieldatlas.research.*

/** A separate source/index pair gives every engine ID an unambiguous pack namespace. */
internal data class LibraryRetrievalPack(val id: String, val source: File, val index: File?)

/** Test-only keyword comparison. Original passages and the production merge remain mandatory.
 * No model, pack registry, installed application data, or production backend is changed. */
internal class LibraryLeviathanRetriever(
    private val baseline: Retriever,
    private val packs: List<LibraryRetrievalPack>,
    private val binary: File,
    private val invoke: (LibraryRetrievalPack, List<String>) -> String = { _, command -> runLeviathan(command) },
) : Retriever by baseline {
    init {
        require(packs.isNotEmpty() && packs.size <= 128)
        require(packs.all { it.id.isNotBlank() } && packs.map { it.id }.distinct().size == packs.size)
        require(packs.map { it.source.canonicalFile }.distinct().size == packs.size) { "Duplicate source paths" }
    }

    private val queryLock = Mutex()
    var traces: List<JsonObject> = emptyList()
        private set

    override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) =
        searchQuestion(query, query, limit, false, onProgress)

    override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                                        onProgress: suspend (SearchProgress) -> Unit): List<Evidence> = queryLock.withLock {
        require(limit in 1..50 && query.isNotBlank())
        traces = emptyList()
        if (FtsQuery.from(query) == null) {
            onProgress(SearchProgress(1.0))
            return@withLock emptyList()
        }
        if (placesOnly) {
            traces = listOf(buildJsonObject {
                put("backend", "app"); put("reason", "placesOnly preserves production pack selection and destination semantics")
                put("packIds", JsonArray(packs.map { JsonPrimitive(it.id) }))
            })
            return@withLock baseline.searchQuestion(query, question, limit, true, onProgress)
        }
        val permits = Semaphore(MultiKnowledgeRetriever.MAX_PARALLEL_PACKS)
        val progressLock = Mutex()
        val fractions = DoubleArray(packs.size)
        var reported = 0.0
        suspend fun progress(slot: Int, fraction: Double) = progressLock.withLock {
            fractions[slot] = maxOf(fractions[slot], fraction.coerceIn(0.0, 1.0))
            val overall = fractions.sum() / packs.size
            if (overall > reported) { reported = overall; onProgress(SearchProgress(overall)) }
        }
        val results = coroutineScope {
            packs.mapIndexed { slot, pack ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        val started = System.nanoTime()
                        var rows = emptyList<Evidence>()
                        var backend = "unstarted"
                        var detail = emptyList<JsonObject>()
                        var failure: String? = null
                        try {
                            KnowledgeDatabase.open(pack.source).use { database ->
                                when {
                                    database.isArticleSections() -> backend = "app-article-only-skip"
                                    database.isPlaceListing() -> {
                                        backend = "app-place-fts"
                                        val destinations = DestinationScope.resolve(DestinationScope.candidates(question), database::holdsDestination)
                                        rows = FtsRetriever(database).search(query, limit, destinations) { progress(slot, it.fraction) }
                                    }
                                    else -> {
                                        backend = "leviathan-with-app-title-ranking"
                                        val index = checkNotNull(pack.index) { "Missing experimental index for ${pack.id}" }
                                        DesktopLeviathanRetriever(FtsRetriever(database), pack.source, index, binary,
                                            invoke = { command -> invoke(pack, command) }).use { retriever ->
                                            rows = retriever.searchQuestion(query, question, limit, false) { progress(slot, it.fraction) }
                                            detail = retriever.traces.toList()
                                        }
                                    }
                                }
                            }
                        } catch (error: Throwable) {
                            if (error is CancellationException || error is OutOfMemoryError) throw error
                            failure = "${error.javaClass.simpleName}: ${error.message}"
                        }
                        progress(slot, 1.0)
                        PackResult(rows, failure, buildJsonObject {
                            put("packId", pack.id); put("backend", backend)
                            put("status", if (failure == null) "COMPLETED" else "ERROR")
                            failure?.let { put("error", it) }
                            put("elapsedMs", (System.nanoTime() - started) / 1_000_000.0)
                            put("selectedChunkIds", JsonArray(rows.map { JsonPrimitive(it.chunkId) }))
                            put("engineTraces", JsonArray(detail))
                        })
                    }
                }
            }.awaitAll()
        }
        traces = results.map { it.trace }
        check(results.none { it.failure != null }) { "One or more pack searches failed; inspect per-pack traces" }
        onProgress(SearchProgress(1.0))
        MultiKnowledgeRetriever.mergeRelevantEvidence(results.map { it.rows }, query, question, limit)
    }

    private data class PackResult(val rows: List<Evidence>, val failure: String?, val trace: JsonObject)
}
