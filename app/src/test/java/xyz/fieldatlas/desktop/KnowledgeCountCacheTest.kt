package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.FtsRetriever
import xyz.fieldatlas.research.KnowledgeDatabase

/** Tiny real FTS5 fixtures; no downloaded corpus or normal-app History is used. */
class KnowledgeCountCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun fixture(name: String = "counts.sqlite"): File {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = File(temp.root, name)
        BundledSQLiteDriver().open(file.path).use { sql ->
            fun execute(text: String) { sql.prepare(text).use { it.step() } }
            execute("PRAGMA user_version=1")
            execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            listOf("solar energy", "solar energy", "solarized material", "café 0xfffffffe", "cafe 0xffffffff")
                .forEachIndexed { index, text ->
                    sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use { row ->
                        row.bindText(1, "doc-$index:0000")
                        row.bindText(2, "doc-$index")
                        row.bindText(3, "Fixture $index")
                        row.bindText(4, "https://example.test/$index")
                        row.bindText(5, text)
                        row.step()
                    }
                }
        }
        return file
    }

    @Test fun capsPrefixesAbsentTermsAndIdentifiersMatchFreshSql() {
        val file = fixture()
        KnowledgeDatabase.open(file).use { database ->
            BundledSQLiteDriver().open(file.path).use { sql ->
                val expressions = listOf("\"solar\"", "\"solar\"*", "\"absent\"*", "\"café\"",
                    "\"0xffffffff\"", "\"0xfffffffe\"", "title : \"solar\"*", "text : \"solar\"*")
                repeat(3) {
                    for (expression in expressions) for (cap in listOf(1, 2, 100_000)) {
                        val expected = sql.prepare("SELECT count(*) FROM (SELECT 1 FROM chunks_fts WHERE chunks_fts MATCH ? LIMIT ?)").use {
                            it.bindText(1, expression)
                            it.bindInt(2, cap)
                            check(it.step())
                            it.getInt(0)
                        }
                        assertEquals("$expression / $cap", expected, database.countCapped(expression, cap))
                    }
                }
            }
        }
    }

    @Test fun newRetrieverInstancesKeepTheSameEvidenceAndTieOrder() = runBlocking {
        KnowledgeDatabase.open(fixture()).use { database ->
            val expected = FtsRetriever(database).search("solar energy", 5) {}
            repeat(3) {
                assertEquals(expected, FtsRetriever(database).search("solar energy", 5) {})
            }
        }
    }

    @Test fun invalidSqlIsNotCachedAsAZeroCount() {
        KnowledgeDatabase.open(fixture()).use { database ->
            repeat(2) {
                assertThrows(Exception::class.java) { database.countCapped("\"", 100_000) }
            }
            assertEquals(2, database.countCapped("\"solar\"", 100_000))
        }
    }

    @Test fun closingHandleDoesNotServePreviouslyCachedCounts() {
        val database = KnowledgeDatabase.open(fixture())
        assertEquals(2, database.countCapped("\"solar\"", 100_000))
        database.close()
        assertThrows(Exception::class.java) { database.countCapped("\"solar\"", 100_000) }
    }

    @Test fun replacementHandleCountsTheNewPackInsteadOfOldMemoizedValues() {
        val file = fixture()
        KnowledgeDatabase.open(file).use { database ->
            assertEquals(2, database.countCapped("\"solar\"", 100_000))
        }
        // Updating this tiny fixture after closing is analogous to a newly opened pack version.
        BundledSQLiteDriver().open(file.path).use { sql ->
            sql.prepare("DELETE FROM chunks_fts").use { it.step() }
        }
        KnowledgeDatabase.open(file).use { database ->
            assertEquals(0, database.countCapped("\"solar\"", 100_000))
        }
    }
}
