package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.KnowledgeDatabase
import xyz.fieldatlas.research.MultiKnowledgeRetriever
import xyz.fieldatlas.research.search

/** Uses desktop SQLite only; a pack of whole articles by section is read article by article, never searched. */
class ArticleSectionsPackTest {
    @get:Rule val temp = TemporaryFolder()

    /** Rows are (chunk id, title, text), written in document order as the pack builder writes them. */
    private fun pack(name: String, rows: List<Triple<String, String, String>>): File {
        val file = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(file.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text,tokenize='porter unicode61')")
            rows.forEach { (chunk, title, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, chunk); it.bindText(2, chunk.substringBefore(':')); it.bindText(3, title)
                    it.bindText(4, "https://example.org/${chunk.substringBefore(':')}"); it.bindText(5, text); it.step()
                }
            }
        }
        return file
    }

    private fun packs(): List<File> = listOf(
        pack("simplewiki", listOf(
            Triple("swp-1-0000:0000", "Heat pump — Overview", "A heat pump moves heat from a cold place to a warm place."),
            Triple("swp-1-0001:0000", "Heat pump — Details", "Heat pumps use electricity to move heat."),
        )),
        pack("vital", listOf(
            Triple("enw-5-0000:0000", "Heat pump — Overview", "A heat pump is a device that uses electric power to transfer heat."),
            Triple("enw-5-0000:0001", "Heat pump — Overview", "Heat pumps are more efficient than resistive heaters."),
            Triple("enw-5-0001:0000", "Heat pump — Operation", "A refrigerant absorbs heat outdoors and releases it inside the house."),
            Triple("enw-6-0000:0000", "Heat pumps in Europe — Overview", "Sales of heat pumps grew in Europe."),
        )),
    )

    @Test fun anArticlePackIsReadArticleByArticleAndNeverSearched() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val files = packs()
        val (simple, vital) = files.map(KnowledgeDatabase::open)
        try {
            assertFalse(simple.isArticleSections())
            assertTrue(vital.isArticleSections())
        } finally { listOf(simple, vital).forEach { it.close() } }
        val retriever = MultiKnowledgeRetriever({ files })
        assertTrue(retriever.search("heat pump house", 20).none { it.documentId.startsWith("enw-") })
        assertTrue(retriever.titled("heat pump", listOf("Overview"), 10).none { it.documentId.startsWith("enw-") })
        assertTrue(retriever.sections("Heat pump", 10).none { it.documentId.startsWith("enw-") })
        // Its whole page, in order, and nothing of the next page that starts with the same words.
        assertEquals(listOf("swp-1-0000:0000", "swp-1-0001:0000", "enw-5-0000:0000", "enw-5-0000:0001", "enw-5-0001:0000"),
            retriever.article("Heat pump", 80).map { it.chunkId })
        assertEquals(listOf("enw-5-0000:0000", "enw-5-0000:0001"), retriever.article("heat pump", 2).filter { it.documentId.startsWith("enw-") }.map { it.chunkId })
        assertTrue(retriever.article("Geothermal heating", 80).isEmpty())
    }
}
