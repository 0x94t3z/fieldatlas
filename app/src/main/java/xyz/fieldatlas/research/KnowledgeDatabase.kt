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
                            score = -statement.getDouble(5),
                        ),
                    )
                }
            }
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

        val integrity = database.prepare("PRAGMA quick_check").use { statement ->
            requireCursor(statement.step(), "quick_check returned no result")
            statement.getText(0)
        }
        requireCursor(integrity == "ok", "SQLite quick_check failed: $integrity")
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
