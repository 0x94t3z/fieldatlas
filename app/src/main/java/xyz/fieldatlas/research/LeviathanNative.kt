package xyz.fieldatlas.research

import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*

/** A read-only, persistent native store. Only packs built for both engines opt in. */
internal object LeviathanNative {
    val available: Boolean by lazy {
        try { System.loadLibrary("fieldatlas_leviathan"); true }
        catch (_: UnsatisfiedLinkError) { false }
    }
    external fun open(path: String): Long
    /** [values] are newline-separated, any of them matching [field]; an empty [field] searches the whole pack. */
    external fun search(handle: Long, query: String, limit: Int, field: String, values: String): String
    external fun interrupt(handle: Long)
    external fun close(handle: Long)
}

internal class LeviathanSession(file: File) : Closeable {
    private var handle = LeviathanNative.open(file.absolutePath)

    /** [places] keeps the search to listings of those destinations (folded, see [PLACE_FILTER]); empty searches everything. */
    suspend fun search(query: String, limit: Int, places: List<String> = emptyList()): List<Evidence> = runInterruptible(Dispatchers.IO) {
        require(places.none { it.isBlank() || '\n' in it })
        synchronized(this) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
            check(handle != 0L) { "Search store closed" }
            val worker = Thread.currentThread()
            val monitor = Any()
            var active = true
            var timedOut = false
            val start = System.nanoTime()
            // Repeated interruption also covers cancellation between SQLite statements.
            // The monitor prevents a late callback interrupting the next search on this handle.
            val watch = watchdog.scheduleAtFixedRate({
                synchronized(monitor) {
                    if (active) {
                        timedOut = System.nanoTime() - start > TimeUnit.SECONDS.toNanos(15)
                        if (worker.isInterrupted || timedOut) runCatching { LeviathanNative.interrupt(handle) }
                    }
                }
            }, 25, 25, TimeUnit.MILLISECONDS)
            val result = try {
                if (places.isEmpty()) LeviathanNative.search(handle, query, limit, "", "")
                else LeviathanNative.search(handle, query, limit, PLACE_FILTER, places.joinToString("\n"))
            }
            catch (failure: Exception) {
                if (worker.isInterrupted) throw InterruptedException("Search cancelled").apply { initCause(failure) }
                throw failure
            }
            finally { synchronized(monitor) { active = false; watch.cancel(false) } }
            check(!timedOut) { "Native search timed out" }
            if (worker.isInterrupted) throw InterruptedException("Search cancelled")
            decode(result, limit)
        }
    }

    override fun close() = synchronized(this) {
        if (handle != 0L) { LeviathanNative.close(handle); handle = 0 }
    }

    companion object {
        /** The filter place packs are indexed with: each listing's destination and its parents, folded. */
        const val PLACE_FILTER = "places[]"
        private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "knowledge-search-cancellation").apply { isDaemon = true }
        }
        internal fun decode(json: String, limit: Int): List<Evidence> {
            require(limit in 1..50 && json.length <= 4_194_304)
            val records = Json.parseToJsonElement(json).jsonArray
            check(records.size <= limit)
            val seen = HashSet<String>()
            return records.map { raw ->
                val hit = raw.jsonObject
                val row = hit.getValue("record").jsonObject
                fun field(name: String) = row.getValue(name).jsonPrimitive.let {
                    check(it.isString && it.content.isNotBlank()); it.content
                }
                val id = field("id")
                check(seen.add(id)) { "Duplicate source identity" }
                val relevance = hit.getValue("relevance").jsonPrimitive.double
                check(relevance.isFinite())
                // Negated into the keyword path's direction: more negative is stronger, as raw FTS5 bm25.
                Evidence(field("document_id"), id, field("title"), field("source"), field("text"), -relevance,
                    "Leviathan candidate; full saved passage; Leviathan BM25")
            }
        }
    }
}
