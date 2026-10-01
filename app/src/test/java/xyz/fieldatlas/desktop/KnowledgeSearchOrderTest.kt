package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.KnowledgeDatabase

/** Uses desktop SQLite only; checks the index-first search against full-row ranking. */
class KnowledgeSearchOrderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun indexFirstSearchMatchesFullRowRankingIncludingTies() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val db = File(temp.root, "ties.sqlite")
        val rows = buildList {
            // Identical passages score identically. Insert them in REVERSE chunk_id order so
            // rowid order and chunk_id order disagree, as in a published biology pack.
            for (index in 9 downTo 0) add("tie:%02d".format(index) to "Local area network cables link computers in one building.")
            add("strong:00" to "Local area network. A local area network links computers locally in a local area.")
            add("weak:00" to "A network of rivers drains the wider area.")
            add("none:00" to "Buoyancy is the upward force exerted by a fluid.")
        }
        BundledSQLiteDriver().open(db.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            rows.forEach { (chunkId, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use { statement ->
                    statement.bindText(1, chunkId)
                    statement.bindText(2, chunkId.substringBefore(':'))
                    statement.bindText(3, "Passage")
                    statement.bindText(4, "https://example.org/$chunkId")
                    statement.bindText(5, text)
                    statement.step()
                }
            }
        }
        val reference = """
            SELECT chunk_id FROM chunks_fts WHERE chunks_fts MATCH ?
            ORDER BY bm25(chunks_fts, 0.0, 0.0, 3.0, 0.0, 1.0) ASC, chunk_id COLLATE BINARY ASC LIMIT ?
        """
        val database = KnowledgeDatabase.open(db)
        try {
            for (match in listOf("\"local\"* OR \"area\"* OR \"network\"*", "\"network\"*", "\"buoyancy\"*", "\"absent\"*")) {
                for (limit in listOf(1, 2, 3, 5, 11, 13, 50)) {
                    val expected = BundledSQLiteDriver().open(db.path).use { sql ->
                        sql.prepare(reference).use { statement ->
                            statement.bindText(1, match)
                            statement.bindInt(2, limit)
                            buildList { while (statement.step()) add(statement.getText(0)) }
                        }
                    }
                    assertEquals("$match / $limit", expected, database.search(match, limit).map { it.chunkId })
                }
            }
            // The cutoff falls inside the tie group: chunk_id, not insertion order, decides.
            assertEquals(listOf("tie:00", "tie:01", "tie:02"),
                database.search("\"cables\"*", 3).map { it.chunkId })
        } finally {
            database.close()
        }
    }
}
