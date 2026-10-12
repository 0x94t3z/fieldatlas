package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.*

/** Tiny actual SQLite fixtures, never a corpus score or real-model correctness claim. */
class TechnicalRuleRetrievalTest {
    @get:Rule val temp = TemporaryFolder()
    private val question = "Under QRS-42, what does readFlag return for 0xface?"

    private fun row(id: String, text: String, title: String = "QRS-42: Specification") =
        Evidence(id, "$id:0", title, "fixture://$id", text, 0.0)

    private fun fixture(name: String, rows: List<Evidence>): File {
        assumeTrue("Requires opt-in desktop SQLite", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            db.prepare("PRAGMA user_version=1").use { it.step() }
            db.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)").use { it.step() }
            rows.forEach { row ->
                db.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, row.chunkId); it.bindText(2, row.documentId); it.bindText(3, row.title)
                    it.bindText(4, row.source); it.bindText(5, row.text); it.step()
                }
            }
        }
        return file
    }

    private fun distractors() = listOf(
        row("abstract", "QRS-42 specifies readFlag return results.", "QRS-42: Standard — Abstract"),
        row("summary", "QRS-42 defines a readFlag interface.", "QRS-42: Standard — Simple Summary"),
        row("foreign", "QRS-42 readFlag returns true for 0xface.", "ABC-99: QRS-42 extension"),
        row("near-hex", "readFlag returns true for 0xface0."),
        row("near-prefix", "readFlag returns true for x0xface."),
        row("near-symbol", "readFlagExtra returns true for 0xface."),
        row("different-condition", "readFlag returns true for 0xcafe."),
    ) + (0 until 30).map { row("noise-$it", "QRS-42 readFlag returns a flag. ".repeat(5), "QRS-42: Read flag notes $it") }

    @Test fun `exact pass still finds rule when ordinary pool is full at limit one`() = runBlocking {
        val exact = row("exact", "The readFlag function returns false for 0xface. " + "Additional independent qualifications remain part of the original rule. ".repeat(12))
        for (reversed in listOf(false, true)) {
            val rows = (distractors() + exact).let { if (reversed) it.reversed() else it }
            KnowledgeDatabase.open(fixture("full-$reversed", rows)).use { db ->
                val progress = mutableListOf<Double>()
                val actual = FtsRetriever(db).search(question, 1) { progress += it.fraction }.single()
                assertEquals(exact.documentId, actual.documentId)
                assertEquals(exact.chunkId, actual.chunkId)
                assertEquals(exact.source, actual.source)
                assertEquals(exact.text, actual.text)
                assertEquals(progress.sorted(), progress)
                assertEquals(1.0, progress.last(), 0.0)
            }
        }
    }

    @Test fun `exact query returns original applicable rows and rejects near matches`() {
        val yes = row("yes", "readFlag returns true for 0xface when the flag is enabled.")
        val no = row("no", "readFlag returns false for 0xface when the flag is disabled.")
        val focus = TechnicalRuleFocus.from(question)!!
        KnowledgeDatabase.open(fixture("expression", distractors() + listOf(yes, no))).use { db ->
            val found = db.search(focus.matchExpression, 8).filter(focus::matches)
            assertEquals(setOf("yes", "no"), found.map { it.documentId }.toSet())
            assertEquals(setOf(yes.text, no.text), found.map { it.text }.toSet())
        }
    }

    @Test fun `no exact rule never becomes a synthetic focused result`() = runBlocking {
        val focus = TechnicalRuleFocus.from(question)!!
        KnowledgeDatabase.open(fixture("missing", distractors())).use { db ->
            assertTrue(db.search(focus.matchExpression, 8).filter(focus::matches).isEmpty())
            assertTrue(FtsRetriever(db).search(question, 8) { }.none(focus::matches))
        }
    }

    @Test fun `direct question API preserves original focus despite a changed planner query`() = runBlocking {
        val exact = row("exact", "readFlag returns false for 0xface.")
        KnowledgeDatabase.open(fixture("original", distractors() + exact)).use { db ->
            // None of the expansion's words exists. The focus still belongs to the original
            // question and does not depend on any ordinary expanded term surviving a probe.
            val actual = FtsRetriever(db).searchQuestion("unfindableexpandedterm", question, 1, false) { }
            assertEquals(listOf("exact"), actual.map { it.documentId })
            // Conversely, planner text must not create an exact-condition ranking obligation.
            val base = FtsRetriever(db).searchFocused(question, 1, emptyList(), null) { }
            val ordinary = FtsRetriever(db).searchQuestion(question, "Explain QRS-42", 1, false) { }
            assertEquals(base, ordinary)
        }
    }

    @Test fun `two rules in one document precede summaries without selecting their polarity`() = runBlocking {
        val yes = row("rules", "readFlag returns true for 0xface when the flag is enabled.").copy(chunkId = "rules:1")
        val no = row("rules", "readFlag returns false for 0xface when the flag is disabled.").copy(chunkId = "rules:2")
        KnowledgeDatabase.open(fixture("conflict", distractors() + listOf(yes, no))).use { db ->
            val actual = FtsRetriever(db).search(question, 2) { }
            assertEquals(setOf("rules:1", "rules:2"), actual.map { it.chunkId }.toSet())
        }
    }

    @Test fun `existing ordinary ordering is unchanged when focus is absent`() = runBlocking {
        KnowledgeDatabase.open(fixture("ordinary", distractors())).use { db ->
            val plain = "Explain QRS-42 readFlag"
            assertNull(TechnicalRuleFocus.from(plain))
            assertEquals(FtsRetriever(db).searchFocused(plain, 8, emptyList(), null) { },
                FtsRetriever(db).search(plain, 8) { })
        }
    }

    @Test fun `focused multi pack failure and cancellation preserve subsequent search`() = runBlocking {
        val exact = row("exact", "readFlag returns false for 0xface.")
        val file = fixture("cancel", distractors() + exact)
        val opened = mutableListOf<KnowledgeDatabase>()
        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { listOf(File(temp.root, "missing.sqlite"), file) },
            open = { path -> KnowledgeDatabase.open(path).also { synchronized(opened) { opened += it } } },
        )
        try {
            try {
                retriever.searchQuestion("unfindableexpandedterm", question, 1, false) {
                    if (it.fraction >= 1.0) throw CancellationException("fixture cancellation")
                }
                fail("Cancellation must propagate instead of becoming an empty successful answer")
            } catch (_: CancellationException) { }
            val progress = mutableListOf<Double>()
            val actual = retriever.searchQuestion("unfindableexpandedterm", question, 1, false) { progress += it.fraction }
            assertEquals(listOf("exact"), actual.map { it.documentId })
            assertEquals(progress.sorted(), progress)
            assertEquals(1.0, progress.last(), 0.0)
        } finally {
            opened.forEach { it.close() }
        }
    }
}
