package xyz.fieldatlas.research

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.Closeable
import java.io.File

class InvalidKnowledgeDatabaseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class KnowledgeDatabase private constructor(private val database: SQLiteConnection) : Closeable {
    internal fun search(matchExpression: String, limit: Int): List<Evidence> = synchronized(database) {
        database.prepare(SEARCH_SQL).use { statement ->
            statement.bindText(1, matchExpression)
            statement.bindInt(2, limit)
            buildList {
                while (statement.step()) {
                    add(
                        Evidence(
                            documentId = statement.getText(0),
                            chunkId = statement.getText(1),
                            title = statement.getText(2),
                            source = statement.getText(3),
                            text = statement.getText(4),
                            // Raw FTS5 bm25: negative, MORE negative = stronger. The retriever
                            // sorts ascending and the cross-pack merge shifts by the minimum,
                            // both trusting this direction — do not negate here (an earlier
                            // negation inverted the phone's rankings and let the evidence-
                            // packing budget truncate the strongest chunks first).
                            score = statement.getDouble(5),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Title-boost candidates: (title, lede rowid) pairs for articles whose TITLE contains any
     * of the query terms. Cheap (no text column), capped generously — the retriever decides
     * which candidates earn boost slots by how many query terms the title covers, and a global
     * length cut here would drop long-but-perfect titles ("Allies of World War I") before that
     * decision could ever see them.
     */
    internal fun titleCandidateTitles(titleMatch: String): List<TitleCandidate> = synchronized(database) {
        database.prepare(TITLE_TITLES_SQL).use { statement ->
            statement.bindText(1, titleMatch)
            val rows = ArrayList<TitleCandidate>()
            while (statement.step()) rows += TitleCandidate(statement.getText(0), statement.getLong(1))
            rows
        }
    }

    /** The lede chunk (by rowid) of each chosen title-boost candidate, in the given order. */
    internal fun titleLeadChunks(candidates: List<TitleCandidate>): List<Evidence> {
        if (candidates.isEmpty()) return emptyList()
        return synchronized(database) {
            val rowIds = candidates.map { candidate -> candidate.rowid }
            val sql = TITLE_LEAD_SQL + rowIds.joinToString(",") { "?" } + ")"
            database.prepare(sql).use { statement ->
                rowIds.forEachIndexed { index, rowId -> statement.bindLong(index + 1, rowId) }
                val rows = ArrayList<Evidence>()
                while (statement.step()) {
                    rows += Evidence(
                        documentId = statement.getText(0),
                        chunkId = statement.getText(1),
                        title = statement.getText(2),
                        source = statement.getText(3),
                        text = statement.getText(4),
                        score = statement.getDouble(5),
                    )
                }
                val order = candidates.withIndex().associate { (index, candidate) -> candidate.title to index }
                rows.sortedBy { row -> order[row.title] ?: Int.MAX_VALUE }
            }
        }
    }

    /**
     * Cosine top-k over the optional chunk_vectors table (int8-quantized embeddings).
     * Returns evidence whose [Evidence.score] is the cosine similarity (POSITIVE scale,
     * higher = stronger — the opposite direction from the raw bm25 scores search() returns;
     * the retriever re-maps these before merging). Empty when the pack has no vectors.
     */
    internal fun vectorSearch(query: FloatArray, dim: Int, limit: Int): List<Evidence> {
        if (!hasVectorTable()) return emptyList()
        val top = synchronized(database) {
            val ids = ArrayList<Long>()
            database.prepare("SELECT rowid FROM chunk_vectors ORDER BY rowid").use { statement ->
                while (statement.step()) ids += statement.getLong(0)
            }
            database.prepare("SELECT quant FROM chunk_vectors WHERE rowid = ?").use { statement ->
                VectorMath.topK(query, dim, limit, ids.asSequence()) { rowId ->
                    statement.reset()
                    statement.bindLong(1, rowId)
                    if (statement.step()) statement.getBlob(0) else null
                }
            }
        }
        if (top.isEmpty()) return emptyList()
        val scores = top.toMap()
        return synchronized(database) {
            val sql = VECTOR_EVIDENCE_SQL + top.joinToString(",") { "?" } + ")"
            database.prepare(sql).use { statement ->
                top.forEachIndexed { index, (rowId, _) -> statement.bindLong(index + 1, rowId) }
                val rows = HashMap<Long, Evidence>()
                while (statement.step()) {
                    val rowId = statement.getLong(5)
                    rows[rowId] = Evidence(
                        documentId = statement.getText(0),
                        chunkId = statement.getText(1),
                        title = statement.getText(2),
                        source = statement.getText(3),
                        text = statement.getText(4),
                        score = scores[rowId] ?: 0.0,
                    )
                }
                top.mapNotNull { (rowId, _) -> rows[rowId] }
            }
        }
    }

    /** Cheap one-shot check for the optional vector table; cached for the handle's lifetime. */
    fun hasVectorTable(): Boolean {
        cachedHasVectors?.let { return it }
        val present = runCatching {
            synchronized(database) {
                database.prepare(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name = 'chunk_vectors'",
                ).use { statement -> statement.step() }
            }
        }.getOrDefault(false)
        cachedHasVectors = present
        return present
    }

    @Volatile private var cachedHasVectors: Boolean? = null

    internal fun countCapped(matchExpression: String, cap: Int): Int = synchronized(database) {
        database.prepare(
            "SELECT count(*) FROM (SELECT 1 FROM chunks_fts WHERE chunks_fts MATCH ? LIMIT ?)",
        ).use { statement ->
            statement.bindText(1, matchExpression)
            statement.bindInt(2, cap)
            if (statement.step()) statement.getInt(0) else 0
        }
    }

    override fun close() = database.close()

    private fun validate() {
        val version = database.prepare("PRAGMA user_version").use { statement ->
            requireCursor(statement.step(), "Missing user_version")
            statement.getInt(0)
        }
        requireCursor(version == SCHEMA_VERSION, "Unsupported user_version: $version")

        val columns = database.prepare("PRAGMA table_info(chunks_fts)").use { statement ->
            buildList {
                while (statement.step()) add(statement.getText(1))
            }
        }
        requireCursor(columns == EXPECTED_COLUMNS, "Unexpected chunks_fts columns: $columns")

        val createSql = database.prepare(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='chunks_fts'",
        ).use { statement ->
            requireCursor(statement.step(), "Missing chunks_fts table")
            statement.getText(0)
        }
        requireCursor(
            Regex("(?is)CREATE\\s+VIRTUAL\\s+TABLE.*USING\\s+fts5\\s*\\(").containsMatchIn(createSql),
            "chunks_fts is not an FTS5 virtual table",
        )
        // No PRAGMA quick_check here: import already verifies every artifact's SHA-256, and a
        // quick_check scans the whole database (multi-minute on 40 GB packs), which used to run
        // on every search because handles were reopened per query. The cheap structural checks
        // above still reject wrong-schema files at open time.
    }

    private fun requireCursor(condition: Boolean, message: String) {
        if (!condition) throw InvalidKnowledgeDatabaseException(message)
    }

    companion object {
        const val SCHEMA_VERSION = 1
        val EXPECTED_COLUMNS = listOf("chunk_id", "document_id", "title", "source", "text")

        private const val SEARCH_SQL = """
            SELECT document_id, chunk_id, title, source, text,
                   bm25(chunks_fts, 0.0, 0.0, 3.0, 0.0, 1.0) AS rank
            FROM chunks_fts
            WHERE chunks_fts MATCH ?
            ORDER BY rank ASC, chunk_id COLLATE BINARY ASC
            LIMIT ?
        """

        private const val TITLE_TITLES_SQL = """
            SELECT title, MIN(rowid) AS rowid
            FROM chunks_fts
            WHERE chunks_fts MATCH ?
            GROUP BY title
            LIMIT 5000
        """

        private const val TITLE_LEAD_SQL = """
            SELECT document_id, chunk_id, title, source, text, 0.0
            FROM chunks_fts
            WHERE rowid IN ("""

        private const val VECTOR_EVIDENCE_SQL = """
            SELECT document_id, chunk_id, title, source, text, rowid
            FROM chunks_fts
            WHERE rowid IN ("""

        fun open(file: File): KnowledgeDatabase {
            if (!file.isFile) throw InvalidKnowledgeDatabaseException("Knowledge database does not exist")
            val sqlite = try {
                BundledSQLiteDriver().open(
                    file.absolutePath,
                    SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX,
                )
            } catch (error: Exception) {
                throw InvalidKnowledgeDatabaseException("Unable to open knowledge database", error)
            }
            return try {
                KnowledgeDatabase(sqlite).also { it.validate() }
            } catch (error: InvalidKnowledgeDatabaseException) {
                sqlite.close()
                throw error
            } catch (error: Exception) {
                sqlite.close()
                throw InvalidKnowledgeDatabaseException("Knowledge database validation failed", error)
            }
        }
    }
}

/** A title-boost candidate: article title and the rowid of its lede chunk. */
internal data class TitleCandidate(val title: String, val rowid: Long)
