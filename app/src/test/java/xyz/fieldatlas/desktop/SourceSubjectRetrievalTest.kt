package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.*

/** Actual small SQLite indexes; no large corpus, network or model inference. */
class SourceSubjectRetrievalTest {
    @get:Rule val temp = TemporaryFolder()
    private val direct = Evidence("direct", "direct:0", "Source reliability", "fixture://direct",
        "Source reliability depends on provenance; independent confirmation is still required.", 0.0)
    private val noise = (0 until 30).map { index -> direct.copy(documentId = "motor-$index",
        chunkId = "motor-$index:0", source = "fixture://motor-$index", title = "Motor reliability",
        text = "Motor reliability depends on cooling.") }

    private fun fixture(name: String, rows: List<Evidence>): File {
        assumeTrue("Requires opt-in desktop SQLite", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            db.prepare("PRAGMA user_version=1").use { it.step() }
            db.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
                .use { it.step() }
            rows.forEach { row ->
                db.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, row.chunkId); it.bindText(2, row.documentId); it.bindText(3, row.title)
                    it.bindText(4, row.source); it.bindText(5, row.text); it.step()
                }
            }
        }
        return file
    }

    @Test fun sqliteConjunctionIncludesTheSubstantiveSourceNoun() {
        KnowledgeDatabase.open(fixture("combined", noise + direct)).use { db ->
            for (question in listOf("Compare source reliability.",
                "According to the saved sources, compare source reliability.",
                "Based on reports compare source reliability.",
                "Using only sources, compare source reliability.",
                "Summarize what my saved sources say about source reliability.")) {
                val rows = db.search(FtsQuery.from(question)!!.matchExpression, 1)
                assertEquals(question, listOf(direct.text), rows.map { it.text })
                assertEquals(listOf(direct.source), rows.map { it.source })
            }
        }
    }

    @Test fun realMultiPackSearchKeepsTheSupportingLaterPackBeforeLimitAndPacking() = runBlocking {
        val files = listOf(fixture("noise", noise), fixture("direct", listOf(direct)))
        for (order in listOf(files, files.reversed())) {
            val opened = mutableListOf<KnowledgeDatabase>()
            val retriever = MultiKnowledgeRetriever(databaseFiles = { order }, open = { file ->
                KnowledgeDatabase.open(file).also { synchronized(opened) { opened += it } }
            })
            try {
                for (question in listOf("Compare source reliability.",
                    "According to the saved sources, compare source reliability.",
                    "Based on reports compare source reliability.",
                    "Using only sources, compare source reliability.",
                    "What do my saved sources say about source reliability?")) {
                    val selected = retriever.searchQuestion(question, question, 1, false) { }
                    assertEquals(question, listOf(direct.chunkId), selected.map { it.chunkId })
                    val packed = PromptBuilder.build(question, selected, 2048)
                    assertEquals(listOf(direct.text), packed.sources.map { it.evidence.text })
                    assertEquals(listOf(direct.source), packed.sources.map { it.evidence.source })
                    assertEquals(PromptBuilder.allowsModelExplanation(question), packed.mixedAnswer)
                    assertTrue(packed.prompt.contains(question))
                }
            } finally { opened.forEach { it.close() } }
        }
    }

    @Test fun unrelatedPackCannotManufactureSupportWhenTheAskedNounIsMissing() = runBlocking {
        val file = fixture("only-noise", noise)
        KnowledgeDatabase.open(file).use { db ->
            val retriever = MultiKnowledgeRetriever(databaseFiles = { listOf(file) }, open = { db })
            val question = "According to the saved sources, compare source reliability."
            assertTrue(retriever.searchQuestion(question, question, 8, false) { }.isEmpty())
        }
    }
}
