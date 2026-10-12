package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.*

class LibraryLeviathanRetrieverTest {
    @get:Rule val temp = TemporaryFolder()
    private val question = "Which observatory discovered the planet Neptune in 1846?"
    private val partial = "This observatory recorded images of the planet Neptune for an observing programme."
    private val full = "The Berlin Observatory discovered the planet Neptune in 1846."
    private val baseline = Retriever { _, _, _ -> error("Unexpected full baseline fallback") }

    private fun fixture(id: String, text: String, document: String = "doc-$id", indexText: String = text): LibraryRetrievalPack {
        assumeTrue("Requires opt-in desktop SQLite", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val source = File(temp.root, "$id-source.sqlite")
        val index = File(temp.root, "$id-index.sqlite")
        BundledSQLiteDriver().open(source.path).use { db ->
            db.prepare("PRAGMA user_version=1").use { it.step() }
            db.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)").use { it.step() }
            db.prepare("INSERT INTO chunks_fts VALUES('shared',?,'Observatory notes',?,?)").use {
                it.bindText(1, document); it.bindText(2, "https://example.org/$id"); it.bindText(3, text); it.step()
            }
        }
        val doc = buildJsonObject {
            put("id", "shared"); put("document_id", document); put("title", "Observatory notes")
            put("source", "https://example.org/$id"); put("text", indexText); put("source_rowid", 1)
        }
        BundledSQLiteDriver().open(index.path).use { db ->
            db.prepare("CREATE TABLE records(id TEXT UNIQUE,doc TEXT)").use { it.step() }
            db.prepare("INSERT INTO records VALUES('shared',?)").use { it.bindText(1, doc.toString()); it.step() }
        }
        return LibraryRetrievalPack(id, source, index)
    }

    private fun returnedId() = """{"status":"ok","results":[{"id":"shared","snippet":"clipped text is not evidence"}]}"""
    private fun pin(file: File) = LibraryPinnedFile(file, file.length(), libraryDigest(file))

    @Test fun verifiesFullPassagesAcrossPacksAndUsesQuestionAwareMerge() = runBlocking {
        val packs = listOf(fixture("first", partial), fixture("later", full))
        val retriever = LibraryLeviathanRetriever(baseline, packs, File("/unused")) { _, _ -> returnedId() }
        val progress = mutableListOf<Double>()

        val rows = retriever.search(question, 2) { progress += it.fraction }

        assertEquals(listOf(full, partial), rows.map { it.text })
        assertEquals(listOf("later", "first"), rows.map { it.source.substringAfterLast('/') })
        assertEquals(listOf("first", "later"), retriever.traces.map { it.getValue("packId").jsonPrimitive.content })
        assertEquals(progress.sorted(), progress); assertEquals(1.0, progress.last(), 0.0)
        assertTrue(retriever.traces.all { it.getValue("engineTraces").jsonArray.single().jsonObject.getValue("fullPassagesVerified").jsonPrimitive.boolean })
        val origins = librarySourceOrigins(packs.map { LibraryPilotPack(it.id, pin(it.source), pin(it.index!!)) }, rows)
        assertEquals(listOf("later", "first"), origins.map { it.getValue("packIds").jsonArray.single().jsonPrimitive.content })
        try { librarySourceOrigins(packs.map { LibraryPilotPack(it.id, pin(it.source), null) }, listOf(rows.first().copy(text = "forged")))
            fail("Changed original accepted") } catch (_: IllegalStateException) { }
    }

    @Test fun reverseOrderOnlyChangesTiedSourceOrder() = runBlocking {
        val packs = listOf(fixture("a", full), fixture("b", full))
        for (ordered in listOf(packs, packs.reversed())) {
            val retriever = LibraryLeviathanRetriever(baseline, ordered, File("/unused")) { _, _ -> returnedId() }
            val rows = retriever.search(question, 1) { }
            assertEquals("https://example.org/${ordered.first().id}", rows.single().source)
        }
    }

    @Test fun mismatchedIndexFailsWithPackIdentityInsteadOfQuietEmptyResults() = runBlocking {
        val packs = listOf(fixture("good", full), fixture("bad", partial, indexText = "altered"))
        val retriever = LibraryLeviathanRetriever(baseline, packs, File("/unused")) { _, _ -> returnedId() }
        try { retriever.search(question, 5) { }; fail("Mismatched index accepted") } catch (_: IllegalStateException) { }
        assertEquals("ERROR", retriever.traces.single { it.getValue("packId").jsonPrimitive.content == "bad" }.getValue("status").jsonPrimitive.content)
        assertEquals("COMPLETED", retriever.traces.single { it.getValue("packId").jsonPrimitive.content == "good" }.getValue("status").jsonPrimitive.content)
    }

    @Test fun sectionPacksRetainArticleOnlyRouteWithoutAnIndex() = runBlocking {
        val pack = fixture("article", full, document = "enw-123-0000").copy(index = null)
        val retriever = LibraryLeviathanRetriever(baseline, listOf(pack), File("/unused")) { _, _ -> error("Article-only pack must not call engine") }
        assertTrue(retriever.search(question, 5) { }.isEmpty())
        assertEquals("app-article-only-skip", retriever.traces.single().getValue("backend").jsonPrimitive.content)
    }

    @Test fun placeSelectionAndEmptyQueriesDelegateOrSkipExplicitly() = runBlocking {
        val pack = fixture("a", full)
        var fallback = 0
        val base = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                                                onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                assertTrue(placesOnly); fallback++; onProgress(SearchProgress(1.0)); return emptyList()
            }
        }
        val retriever = LibraryLeviathanRetriever(base, listOf(pack), File("/unused")) { _, _ -> error("Unexpected engine invocation") }
        retriever.searchQuestion("vegan Berlin", "vegan Berlin", 5, true) { }
        assertEquals(1, fallback); assertEquals("app", retriever.traces.single().getValue("backend").jsonPrimitive.content)
        assertTrue(retriever.search("?!", 5) { }.isEmpty()); assertTrue(retriever.traces.isEmpty())
    }

    @Test fun limitsConcurrentExperimentalPackWorkToTheProductionBound() {
        val packs = (0 until 6).map { fixture("pack-$it", full) }
        val firstWave = CountDownLatch(MultiKnowledgeRetriever.MAX_PARALLEL_PACKS)
        val release = CountDownLatch(1)
        val overflow = CountDownLatch(1)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val retriever = LibraryLeviathanRetriever(baseline, packs, File("/unused")) { _, _ ->
            val current = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, current) }
            firstWave.countDown()
            if (current > MultiKnowledgeRetriever.MAX_PARALLEL_PACKS) overflow.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
                returnedId()
            } finally { active.decrementAndGet() }
        }
        val runner = Executors.newSingleThreadExecutor()
        try {
            val result = runner.submit<List<Evidence>> { runBlocking { retriever.search(question, 10) { } } }
            try {
                assertTrue(firstWave.await(5, TimeUnit.SECONDS))
                assertFalse(overflow.await(250, TimeUnit.MILLISECONDS))
            } finally { release.countDown() }
            assertEquals(packs.size, result.get(10, TimeUnit.SECONDS).size)
            assertEquals(MultiKnowledgeRetriever.MAX_PARALLEL_PACKS, peak.get())
            assertEquals(0, active.get())
        } finally {
            release.countDown()
            runner.shutdownNow()
        }
    }
}
