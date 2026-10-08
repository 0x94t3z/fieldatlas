package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*
import xyz.fieldatlas.research.*

/** Test-only candidate engine. Specialized app routes stay intact; cards are NEVER evidence. */
internal class DesktopLeviathanRetriever(
    private val baseline: Retriever,
    source: File,
    private val index: File,
    private val binary: File,
    private val invoke: (List<String>) -> String = ::runLeviathan,
) : Retriever by baseline, Closeable {
    private val original = BundledSQLiteDriver().open(source.path, SQLITE_OPEN_READONLY)
    private val indexed = try { BundledSQLiteDriver().open(index.path, SQLITE_OPEN_READONLY) }
        catch (failure: Throwable) { original.close(); throw failure }
    private val rankingDatabase = try { KnowledgeDatabase.open(source) }
        catch (failure: Throwable) { try { indexed.close() } finally { original.close() }; throw failure }
    private val ranking = FtsRetriever(rankingDatabase)
    val traces = mutableListOf<JsonObject>()

    override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) =
        searchQuestion(query, query, limit, false, onProgress)

    override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                                        onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
        require(limit in 1..50 && query.isNotBlank())
        if (placesOnly) {
            traces += buildJsonObject { put("backend", "app"); put("reason", "placesOnly preserves app coordinate/category semantics") }
            return baseline.searchQuestion(query, question, limit, true, onProgress)
        }
        // Preserve app normalization/stop-word handling. Passing conversational wrappers
        // directly to upstream ranks generic dates/holidays above the intended subject.
        val terms = FtsQuery.from(query)?.terms
        if (terms == null) {
            traces += buildJsonObject { put("backend", "app-query-guard"); put("query", query); put("reason", "no searchable app terms") }
            onProgress(SearchProgress(1.0))
            return emptyList()
        }
        val engineQuery = terms.joinToString(" ")
        val start = System.nanoTime()
        // Separate argv entries: query text never becomes shell syntax or command-line flags.
        val response = runInterruptible(Dispatchers.IO) {
            invoke(listOf(binary.path, "search", "--index", index.path, "--json", "--limit", "$limit", "--no-fallback", "--", engineQuery))
        }
        val searchMs = (System.nanoTime() - start) / 1_000_000.0
        val result = Json.parseToJsonElement(response).jsonObject
        check(result.getValue("status").jsonPrimitive.content == "ok") { "Engine did not report success" }
        val cards = result.getValue("results").jsonArray
        check(cards.size <= limit) { "Engine exceeded the result limit" }
        val ids = cards.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        check(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "Invalid or duplicate engine IDs" }
        onProgress(SearchProgress(0.5))
        val hydrationStart = System.nanoTime()
        val evidence = ids.map { id ->
            val doc = indexed.prepare("SELECT doc FROM records WHERE id=?").use { row ->
                row.bindText(1, id)
                check(row.step()) { "Unknown engine ID: $id" }
                val value = Json.parseToJsonElement(row.getText(0)).jsonObject
                check(!row.step()) { "Non-unique index ID" }
                value
            }
            val rowid = doc.getValue("source_rowid").jsonPrimitive.long
            check(rowid > 0)
            original.prepare("SELECT chunk_id,document_id,title,source,text FROM chunks_fts WHERE rowid=?").use { row ->
                row.bindLong(1, rowid)
                check(row.step()) { "Original passage missing: $id" }
                val values = (0..4).map { row.getText(it) }
                check(!row.step())
                check(values[0] == id && listOf("id", "document_id", "title", "source", "text").map { doc.getValue(it).jsonPrimitive.content } == values) {
                    "Index does not match the full original passage: $id"
                }
                Evidence(values[1], values[0], values[2], values[3], values[4], 0.0,
                    "experimental Leviathan candidate; full original passage verified; rank is not app BM25")
            }
        }
        val hydrationMs = (System.nanoTime() - hydrationStart) / 1_000_000.0
        val rankingStart = System.nanoTime()
        val ranked = ranking.rankVerifiedCandidates(query, evidence, limit).map { row ->
            row.copy(score = 0.0, matchedBy = if (row.chunkId in ids)
                "experimental Leviathan candidate; full original passage verified; app title/diversity ranking; not BM25 confidence"
                else "app title anchor from original pack; experimental candidate ranking; not a Leviathan hit or BM25 confidence")
        }
        traces += buildJsonObject {
            put("backend", "leviathan"); put("query", query); put("candidateMs", searchMs)
            put("engineQuery", engineQuery); put("queryNormalization", "app FtsQuery terms; upstream ranking remains experimental")
            put("hydrationMs", hydrationMs); put("appRankingMs", (System.nanoTime() - rankingStart) / 1_000_000.0)
            put("appTitleAnchorIds", JsonArray(ranked.filter { it.chunkId !in ids }.map { JsonPrimitive(it.chunkId) }))
            put("selectedChunkIds", JsonArray(ranked.map { JsonPrimitive(it.chunkId) }))
            put("cardChars", response.length); put("fullPassageChars", evidence.sumOf { it.text.length })
            put("chunkIds", JsonArray(ids.map(::JsonPrimitive))); put("fullPassagesVerified", true)
        }
        onProgress(SearchProgress(1.0))
        return ranked
    }

    override fun close() { try { rankingDatabase.close() } finally { try { indexed.close() } finally { original.close() } } }
}

/** Bounded streams avoid pipe deadlocks and unbounded allocation; only terminate our own child. */
internal fun runLeviathan(command: List<String>, timeoutMs: Long = 30_000, maxBytes: Long = 4_194_304): String {
    val stdout = File.createTempFile("fieldatlas-leviathan-", ".out")
    val stderr = File.createTempFile("fieldatlas-leviathan-", ".err")
    try {
        val process = ProcessBuilder(command).redirectOutput(stdout).redirectError(stderr).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                check(stdout.length() <= maxBytes && stderr.length() <= maxBytes) { "Leviathan output exceeded its bound" }
                check(System.nanoTime() < deadline) { "Leviathan search timed out" }
            }
            check(stdout.length() <= maxBytes && stderr.length() <= maxBytes) { "Leviathan output exceeded its bound" }
            check(process.exitValue() == 0) { "Leviathan exited ${process.exitValue()}: ${stderr.readText().take(1000)}" }
            return stdout.readText()
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
        }
    } finally { stdout.delete(); stderr.delete() }
}
