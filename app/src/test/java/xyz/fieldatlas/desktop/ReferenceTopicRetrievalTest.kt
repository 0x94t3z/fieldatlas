package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.*

/** Small real indexes and an optional pinned original-derived fixture; not model/phone accuracy. */
class ReferenceTopicRetrievalTest {
    @Test fun titleNominationSkipsCreditOnlyLeadWithoutCrossingDocuments() = runBlocking {
        val credit = Evidence("reference", "reference:0", "Vaccines", "fixture://reference",
            "Source: Reference publisher.", 0.0)
        val body = credit.copy(chunkId = "reference:1", text = "Vaccines are preparations that train an immune response.")
        val file = fixture(listOf(credit, body))
        KnowledgeDatabase.open(file).use { db ->
            val titles = db.titleCandidateTitles("{title}: vaccines")
            assertEquals(listOf(body.chunkId), db.titleLeadChunks(titles).map { it.chunkId })
        }
        val found = search(listOf(file), "What is a vaccine?", 1)
        assertEquals(listOf(body.chunkId), found.map { it.chunkId })
    }

    @get:Rule val temp = TemporaryFolder()
    private val question = "Compare solar electricity and battery storage for evening demand."
    private val wrong = Evidence("wp-vehicle", "wp-vehicle:0", "Solaris vehicle — Overview", "fixture://vehicle",
        "A Solaris vehicle is a bus. Its batteries supply electricity. The storage tanks hold fuel, " +
            "and demand varies during service.", 0.0)
    private val direct = Evidence("wp-grid", "wp-grid:0", "Grid operation — Overview", "fixture://grid",
        "Grid operation is the coordination of generators. Battery storage supplies evening demand " +
            "because stored solar electricity remains available after sunset.", 0.0)

    private fun fixture(rows: List<Evidence>): File {
        assumeTrue("Requires opt-in bundled SQLite", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val file = temp.newFile("pack-${temp.root.list()!!.size}.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            db.prepare("PRAGMA user_version=1").use { it.step() }
            db.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
                .use { it.step() }
            rows.forEach { row -> db.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                it.bindText(1, row.chunkId); it.bindText(2, row.documentId); it.bindText(3, row.title)
                it.bindText(4, row.source); it.bindText(5, row.text); it.step()
            } }
        }
        return file
    }

    private suspend fun search(files: List<File>, q: String, limit: Int): List<Evidence> {
        val opened = mutableListOf<KnowledgeDatabase>()
        val retriever = MultiKnowledgeRetriever(databaseFiles = { files }, open = { file ->
            KnowledgeDatabase.open(file).also { synchronized(opened) { opened += it } }
        })
        try { return retriever.searchQuestion(q, q, limit, false) { } }
        finally { opened.forEach { it.close() } }
    }

    @Test fun discoverableWrongTopicCannotBecomeSupport() = runBlocking {
        val file = fixture(listOf(wrong))
        KnowledgeDatabase.open(file).use { db ->
            assertEquals(listOf(wrong.chunkId), db.search("\"solar\"*", 10).map { it.chunkId })
        }
        for (q in listOf(question, "According to the saved sources, $question")) {
            assertTrue(q, search(listOf(file), q, 8).isEmpty())
        }
    }

    @Test fun supportedCrossArticlePassageSurvivesBothPackOrdersLimitAndPacking() = runBlocking {
        val files = listOf(fixture(listOf(wrong)), fixture(listOf(direct)))
        for (order in listOf(files, files.reversed())) for (q in listOf(question, "According to the saved sources, $question")) {
            val found = search(order, q, 1)
            assertEquals(q, listOf(direct.chunkId), found.map { it.chunkId })
            val packed = PromptBuilder.build(q, found, 2048)
            assertEquals(listOf(direct.text), packed.sources.map { it.evidence.text })
            assertEquals(listOf(direct.source), packed.sources.map { it.evidence.source })
            assertEquals(PromptBuilder.allowsModelExplanation(q), packed.mixedAnswer)
        }
    }

    @Test fun boundedMergeCannotPackAnIneligibleDefinitionFromAnyPack() {
        val noise = (0 until 30).map { listOf(wrong.copy(documentId = "wp-noise-$it", chunkId = "wp-noise-$it:0")) }
        for (q in listOf(question, "According to the saved sources, $question")) {
            for (runs in listOf(noise + listOf(listOf(direct)), listOf(listOf(direct)) + noise)) {
                val found = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, q, q, 1)
                assertEquals(listOf(direct), found)
                assertEquals(listOf(direct), PromptBuilder.build(q, found, 2048).sources.map { it.evidence })
            }
            assertTrue(MultiKnowledgeRetriever.mergeRelevantEvidence(noise, q, q, 1).isEmpty())
        }
    }

    @Test fun retainedOriginalDerivedDistractorsAreRejectedByGateMergeAndActualSQLite() = runBlocking {
        val path = System.getenv("FIELDATLAS_TOPIC_FIXTURE")
        assumeTrue("Requires explicitly selected private derived fixture", path != null)
        val cases = Json.parseToJsonElement(File(path!!).readText()).jsonObject.getValue("fixtures").jsonArray
        assertEquals(2, cases.size)
        for (element in cases) {
            val case = element.jsonObject
            val raw = case.getValue("derivedChunk").jsonObject
            fun field(name: String) = raw.getValue(name).jsonPrimitive.content
            val item = Evidence(field("documentId"), field("chunkId"), field("title"), field("source"), field("text"), 0.0)
            val hash = MessageDigest.getInstance("SHA-256").digest(item.text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(case.getValue("chunkTextSHA256").jsonPrimitive.content, hash)
            val plain = case.getValue("question").jsonObject.getValue("prompt").jsonPrimitive.content
            val file = fixture(listOf(item))
            for (q in listOf(plain, "According to the saved sources, $plain")) {
                assertTrue(q, EvidenceRelevance.keep(listOf(item), FtsQuery.from(q)!!.terms, question = q).isEmpty())
                assertTrue(q, MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(item)), q, q, 1).isEmpty())
                assertTrue(q, search(listOf(file), q, 8).isEmpty())
            }
        }
    }
}
