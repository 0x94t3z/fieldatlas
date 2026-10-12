package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.*

/** Real SQLite and the actual retrieval/packing path, never model accuracy. */
class MultipartRetrievalTest {
    @get:Rule val temp = TemporaryFolder()
    private val q = "How does the Luma projector produce a beam, and what limits wet storage?"
    private val beam = Evidence("wp-luma", "wp-luma:0", "Luma projector — Beam", "fixture://luma",
        "The Luma projector produces a beam through its installed emitter.", 0.0)
    private val wet = beam.copy(documentId = "file-storage", chunkId = "file-storage:0", title = "Test notes",
        source = "fixture://storage-file", text = "Luma projector wet storage limits remain unknown. " +
            "Only dry storage was evaluated. Wet storage safety is not established.")
    private val wrong = wet.copy(documentId = "wp-aquarium", chunkId = "wp-aquarium:0",
        title = "Aquarium — Storage", source = "fixture://aquarium", text =
            "Luma projectorson wet storage limits remain unknown. Only dry storage was evaluated.")
    private val metadata = wet.copy(documentId = "paper", chunkId = "paper:0", title = "Storage test",
        source = "fixture://paper", text = "PMID 1\nAuthors: Luma projector\nMeSH: Luma projector\n" +
            "Abstract: Wet storage limits remain unknown. Only dry storage was evaluated.")
    private val wrongModifier = wrong.copy(documentId = "prefix-modifier", chunkId = "prefix-modifier:0",
        title = "Projector — Storage", text = "Lumatic projector wet storage limits remain unknown.")

    private fun pack(rows: List<Evidence>): File {
        assumeTrue("Requires explicit host SQLite opt-in", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = temp.newFile("pack-${temp.root.list()!!.size}.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            db.prepare("PRAGMA user_version=1").use { it.step() }
            db.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
                .use { it.step() }
            for (row in rows) db.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                it.bindText(1, row.chunkId); it.bindText(2, row.documentId); it.bindText(3, row.title)
                it.bindText(4, row.source); it.bindText(5, row.text); it.step()
            }
        }
        return file
    }

    private suspend fun packed(files: List<File>, question: String): PackedPrompt {
        val opened = mutableListOf<KnowledgeDatabase>()
        val retriever = MultiKnowledgeRetriever({ files }, open = { file ->
            KnowledgeDatabase.open(file).also { synchronized(opened) { opened += it } }
        })
        var result: PackedPrompt? = null
        try {
            val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("Fixture response [S1].")),
                prefillSpeed = InMemoryPrefillSpeed(1.0), onEvidencePacked = { result = it })
                .research(question, contextTokenBudget = 8192).toList()
            assertTrue(events.toString(), events.last() is ResearchEvent.Complete)
            return checkNotNull(result)
        } finally { opened.forEach { it.close() } }
    }

    @Test fun independentPackOrderRetainsBothQualifiedClausesWithoutAStorageDistractor() = runBlocking {
        val files = listOf(pack(listOf(beam)), pack(listOf(wrong, metadata, wrongModifier)), pack(listOf(wet)))
        for (order in listOf(files, files.reversed())) for (question in listOf(q, "According to the saved sources, $q")) {
            val result = packed(order, question)
            assertEquals(setOf(beam.chunkId, wet.chunkId), result.sources.map { it.evidence.chunkId }.toSet())
            assertTrue(result.sources.any { it.excerpt == wet.text })
            assertEquals(PromptBuilder.allowsModelExplanation(question), result.mixedAnswer)
            assertTrue(result.prompt.contains(question))
        }
    }

    @Test fun existingArticlesKeepRechargeCaveatsAndCompleteProperNames() = runBlocking {
        val path = System.getenv("FIELDATLAS_MULTIPART_LIBRARY")
        assumeTrue("Requires explicitly selected retained local encyclopedia", !path.isNullOrBlank())
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = File(path!!).canonicalFile
        check(file.isFile && file.name == "content.sqlite")
        val plain = "What is a rechargeable battery, and what limits recharge life?"
        for (question in listOf(plain, "According to the saved sources, $plain")) {
            val result = packed(listOf(file), question)
            assertTrue(result.sources.toString(), result.sources.any {
                it.evidence.title.startsWith("Rechargeable battery —")
            })
            assertTrue(result.sources.map { it.excerpt }.toString(), result.sources.any {
                it.excerpt.contains("cannot be recharged forever", ignoreCase = true)
            })
            assertTrue(result.sources.none { it.evidence.title.contains("Battery (crime)") })
        }
        val proper = "Explain how Formula One championships work."
        for (question in listOf(proper, "According to the saved sources, $proper")) {
            val result = packed(listOf(file), question)
            assertTrue(result.sources.any { it.evidence.title.startsWith("Formula One —") })
            assertTrue(result.sources.none { it.evidence.title.startsWith("ONE Championship —", true) })
        }
    }
}
