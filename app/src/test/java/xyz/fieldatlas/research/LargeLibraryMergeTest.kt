package xyz.fieldatlas.research

import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small synthetic fixtures for pack-count scaling, not a corpus accuracy benchmark. */
class LargeLibraryMergeTest {
    private val question = "Which observatory discovered the planet Neptune in 1846?"
    private val terms = FtsQuery.from(question)!!.terms

    private fun partial(pack: Int) = Evidence(
        documentId = "observing-notes-$pack",
        chunkId = "observing-notes-$pack:0",
        title = "Neptune observing notes $pack",
        source = "fixture://pack-$pack/observing-notes",
        text = "This observatory recorded images of the planet Neptune for an observing programme.",
        score = -100.0 - pack,
    )

    private fun direct(pack: Int) = Evidence(
        documentId = "discovery-$pack",
        chunkId = "discovery-$pack:0",
        title = "Neptune discovery",
        source = "fixture://pack-$pack/discovery",
        text = "The Berlin Observatory discovered the planet Neptune in 1846.",
        // Raw BM25 values are not comparable between packs. The partial matches above
        // deliberately have stronger raw scores than this full-question match.
        score = -2.0,
    )

    private fun eligible(runs: List<List<Evidence>>) {
        // Establish that this is a merge/ranking failure, not an irrelevant-fixture test.
        runs.forEach { run ->
            assertEquals(run, EvidenceRelevance.keep(run, terms, question = question))
        }
    }

    @Test
    fun `a direct answer in a later pack survives more matching packs than candidate slots`() {
        val answer = direct(24)
        val runs = (0 until 24).map { listOf(partial(it)) } + listOf(listOf(answer))
        eligible(runs)

        val selected = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, limit = 24)

        assertEquals(24, selected.size)
        assertTrue("Pack order must not exclude the only full-question match", answer in selected)
        assertEquals("The full-question match must reach the front of the bounded result", answer, selected.first())
    }

    @Test
    fun `a direct answer remains in the eight sources used after an expanded candidate search`() {
        val answer = direct(9)
        val runs = (0 until 9).map { listOf(partial(it)) } + listOf(listOf(answer))
        eligible(runs)

        // ResearchOrchestrator requests resultLimit * 3 candidates and then takes resultLimit.
        val candidates = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, limit = 24)
        val finalSources = EvidenceRelevance.keep(candidates, terms, question = question).take(8)

        assertTrue("A larger candidate pool must still rank the supporting later pack into final sources", answer in finalSources)
        assertEquals(answer, finalSources.first())
    }

    @Test
    fun `equally relevant pack leaders retain input order when the budget fills`() {
        val tied = (0 until 26).map { direct(it) }
        val runs = tied.map(::listOf)
        eligible(runs)

        val selected = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, limit = 24)
        val reversed = MultiKnowledgeRetriever.mergeRelevantEvidence(runs.reversed(), question, question, limit = 24)

        assertEquals(tied.take(24), selected)
        assertEquals(tied.reversed().take(24), reversed)
    }

    @Test
    fun `full question coverage below a pack leader still survives cross pack selection`() {
        val answer = direct(24)
        val runs = (0 until 24).map { listOf(partial(it)) } + listOf(listOf(partial(25), answer))
        eligible(runs)

        val selected = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, limit = 24)

        assertEquals(answer, selected.first())
    }

    @Test
    fun `partial overlaps keep existing relative ranking and small library diversity`() {
        val a = listOf(partial(0), partial(1).copy(score = -90.0))
        val b = listOf(partial(2).copy(score = -20.0), partial(3).copy(score = -19.0))
        val runs = listOf(a, b)
        eligible(runs)

        assertEquals(MultiKnowledgeRetriever.mergeEvidence(runs, 3),
            MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, 3))
    }

    @Test
    fun `planner vocabulary cannot give a partial original question full coverage priority`() {
        val answer = direct(9)
        val runs = (0 until 9).map { listOf(partial(it)) } + listOf(listOf(answer))
        val expanded = "Neptune planet observatory images programme recorded"

        assertEquals(answer,
            MultiKnowledgeRetriever.mergeRelevantEvidence(runs, expanded, question, 8).first())
    }

    @Test
    fun `article titles and scholarly indexing headers do not supply body coverage`() {
        val titled = partial(1).copy(title = "Observatory discovered planet Neptune in 1846")
        val tagged = partial(2).copy(text = partial(2).text + "\nMeSH: discovered; 1846")
        val metadata = partial(3).copy(text = "PMID 1846\nTitle: Neptune discovery\n" +
            "Journal: Observatory discovered planet Neptune\nAbstract: " + partial(3).text)

        assertFalse(EvidenceRelevance.coversQuestionForMerge(titled, terms))
        assertFalse(EvidenceRelevance.coversQuestionForMerge(tagged, terms))
        assertFalse(EvidenceRelevance.coversQuestionForMerge(metadata, terms))
        assertTrue(EvidenceRelevance.coversQuestionForMerge(direct(4), terms))
    }

    @Test
    fun `a section heading says what its passage covers`() {
        // "Season — Causes and effects > Axial tilt" answers "what causes the seasons" without the word "causes" in its text.
        val section = Evidence("season", "season:0", "Season — Causes and effects > Axial tilt", "fixture://season",
            "The seasons result from the Earth's axis of rotation being tilted with respect to its orbital plane.", 0.0)
        val seasonTerms = FtsQuery.from("What causes the seasons on Earth?")!!.terms
        assertTrue(EvidenceRelevance.coversQuestionForMerge(section, seasonTerms))
        assertFalse("the article's own name is not its heading",
            EvidenceRelevance.coversQuestionForMerge(section.copy(title = "Causes — Overview"), FtsQuery.from("what causes rain on Earth?")!!.terms))
    }

    @Test
    fun `accepted concept matches retain priority when they use different wording`() {
        val question = "Explain myocardial infarction treatment"
        val terms = FtsQuery.from(question)!!.terms
        val semantic = direct(0).copy(title = "Heart attack care", text = "Urgent care for a heart attack.",
            matchedBy = "concept match 0.84")

        assertTrue(EvidenceRelevance.coversQuestionForMerge(semantic, terms))
        assertFalse(EvidenceRelevance.coversQuestionForMerge(semantic.copy(matchedBy = "concept match 0.60"), terms))
    }

    @Test
    fun `explicit comparisons retain their existing subject diversity merge`() {
        val question = "Compare mitosis and meiosis."
        val a = listOf(direct(0).copy(title = "Mitosis", text = "Mitosis produces daughter cells."))
        val b = listOf(direct(1).copy(title = "Meiosis", text = "Meiosis produces reproductive cells."))
        val c = listOf(direct(2).copy(title = "Cell division", text = "Mitosis and meiosis are forms of cell division."))

        assertEquals(MultiKnowledgeRetriever.mergeEvidence(listOf(a, b, c), 2),
            MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(a, b, c), question, question, 2))
    }

    @Test
    fun `place lookups retain structured listing ranking`() {
        val question = "best vegan restaurants in Berlin"
        fun place(index: Int, description: String) = direct(index).copy(
            documentId = "osm-place-$index",
            title = "Berlin — Restaurant $index",
            text = "Destination: Berlin\nCategory: Eat\nPlace: Restaurant $index\n" +
                "Type: restaurant\nVegan: fully vegan\nDescription: $description",
        )
        val runs = listOf(listOf(place(0, "Plant based meals")),
            listOf(place(1, "Best vegan restaurants in Berlin")))
        val terms = FtsQuery.from(question)!!.terms
        runs.forEach { assertEquals(it, EvidenceRelevance.keep(it, terms, question = question)) }

        assertEquals(MultiKnowledgeRetriever.mergeEvidence(runs, 2),
            MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, 2))
    }

    @Test
    fun `pack work is bounded while failed databases still finish progress and release permits`() {
        // This fixture measures the semaphore, not ConcurrentHashMap's per-bin
        // computeIfAbsent serialization. Colliding pending opens can prevent the
        // first-wave latch from reaching two, depending on coroutine scheduling.
        // Use distinct initial JVM cache bins while preserving every concurrency,
        // failure, progress and permit-release assertion below.
        val files = (0 until 128).map { File("/fixture/packs/$it/content.sqlite") }
            .distinctBy { file ->
                val hash = file.absolutePath.hashCode()
                (hash xor (hash ushr 16)) and 15
            }.take(12)
        assertEquals(12, files.size)
        val firstWave = CountDownLatch(MultiKnowledgeRetriever.MAX_PARALLEL_PACKS)
        val extraScan = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val attempts = Collections.synchronizedList(mutableListOf<File>())
        val progress = Collections.synchronizedList(mutableListOf<Double>())
        val runner = Executors.newSingleThreadExecutor()
        val dispatcher = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
        try {
            val retriever = MultiKnowledgeRetriever(
                databaseFiles = { files },
                ioDispatcher = dispatcher,
                open = { file ->
                    attempts += file
                    val current = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, current) }
                    firstWave.countDown()
                    if (current > MultiKnowledgeRetriever.MAX_PARALLEL_PACKS) extraScan.countDown()
                    try {
                        check(release.await(5, TimeUnit.SECONDS)) { "Fixture release timed out" }
                        throw IllegalStateException("Unavailable fixture database")
                    } finally {
                        active.decrementAndGet()
                    }
                },
            )
            val task = runner.submit<List<Evidence>> {
                runBlocking { retriever.search(question, 24) { progress += it.fraction } }
            }
            try {
                assertTrue("The first two pack scans should run concurrently", firstWave.await(5, TimeUnit.SECONDS))
                assertFalse("Later scans must wait for a permit", extraScan.await(250, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            assertTrue(task.get(10, TimeUnit.SECONDS).isEmpty())
            assertEquals(files.toSet(), attempts.toSet())
            assertEquals(files.size, attempts.size)
            assertEquals(MultiKnowledgeRetriever.MAX_PARALLEL_PACKS, peak.get())
            assertEquals(0, active.get())
            assertEquals(progress.sorted(), progress)
            assertEquals(1.0, progress.last(), 0.0)
        } finally {
            release.countDown()
            dispatcher.close()
            runner.shutdownNow()
        }
    }
}
