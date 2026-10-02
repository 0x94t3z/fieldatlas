package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.assets.CoverageLevel
import xyz.fieldatlas.assets.PackDiscovery

class EvidenceMergeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun evidence(documentId: String, chunkId: String, source: String, score: Double) = Evidence(
        documentId = documentId,
        chunkId = chunkId,
        title = "$documentId #$chunkId",
        source = source,
        text = "body $documentId $chunkId",
        score = score,
    )

    @Test
    fun `out of scope or empty packs do not crash merge`() {
        assertEquals(emptyList<Evidence>(), MultiKnowledgeRetriever.mergeEvidence(listOf(emptyList()), limit = 8))
        val relevant = evidence("doc", "1", "reference", score = -4.0)
        assertEquals(listOf(relevant), MultiKnowledgeRetriever.mergeEvidence(
            listOf(emptyList(), listOf(relevant), emptyList()), limit = 8,
        ))
    }

    @Test
    fun `merged evidence leads with the strongest bm25 matches`() {
        // bm25 scores are negative and more negative is stronger, so the merge sorts ascending
        val first = listOf(
            evidence("doc-a", "1", "alpha", score = -9.0),
            evidence("doc-a", "2", "alpha", score = -4.0),
        )
        val second = listOf(
            evidence("doc-b", "1", "beta", score = -7.0),
            evidence("doc-b", "2", "beta", score = -2.0),
        )

        val merged = MultiKnowledgeRetriever.mergeEvidence(listOf(first, second), limit = 4)

        assertEquals(listOf(-9.0, -7.0, -4.0, -2.0), merged.map { evidence -> evidence.score })
    }

    @Test
    fun `packs are compared by relative not raw bm25 strength`() {
        // The topical pack's matches are raw-lower but its second hit is barely worse than
        // its best; the encyclopaedia pack's raw-strong matches fall away fast. Relative
        // ranking must let the encyclopaedia's strong cluster win the slots.
        val topical = listOf(
            evidence("doc-a", "1", "alpha", score = -50.0),
            evidence("doc-a", "2", "alpha", score = -45.0),
        )
        val encyclopedia = listOf(
            evidence("doc-b", "1", "beta", score = -20.0),
            evidence("doc-b", "2", "beta", score = -19.0),
        )

        val merged = MultiKnowledgeRetriever.mergeEvidence(listOf(topical, encyclopedia), limit = 4)

        assertEquals(listOf(-50.0, -20.0, -19.0, -45.0), merged.map { evidence -> evidence.score })
    }

    @Test
    fun `a pack's second-best match keeps its slot against weaker packs' fill`() {
        // The encyclopaedia's #2 article is far from its own top hit (relative shift ~10) while
        // the topical pack's #2 is barely worse than its best (shift ~1). Pure shift ranking
        // would fill the last slot with the weak pack's junk; rank-based reservation must keep
        // the encyclopaedia's second match - that is where the Lion article died in the E2E run.
        val encyclopedia = listOf(
            evidence("doc-a", "1", "alpha", score = -60.0),
            evidence("doc-a", "2", "alpha", score = -50.0),
        )
        val topical = listOf(
            evidence("doc-b", "1", "beta", score = -30.0),
            evidence("doc-b", "2", "beta", score = -29.0),
        )

        val merged = MultiKnowledgeRetriever.mergeEvidence(listOf(encyclopedia, topical), limit = 3)

        assertEquals(listOf(-60.0, -30.0, -50.0), merged.map { evidence -> evidence.score })
    }

    @Test
    fun `irrelevant pack cannot reserve slots ahead of relevant evidence`() {
        val question = "Why do Earth's hemispheres have opposite seasons?"
        val incidental = (1..3).map { index ->
            evidence("medical-$index", "1", "biology", -100.0 + index).copy(
                title = "Earth and seasonal medical trends",
                text = "Medical reports from Earth vary by season.",
            )
        }
        val useful = (1..3).map { index ->
            evidence("tilt-$index", "1", "geography", -12.0 + index).copy(
                title = "Earth's opposite hemispheres and seasons",
                text = "Earth's axial tilt gives opposite hemispheres different seasons.",
            )
        }

        val merged = MultiKnowledgeRetriever.mergeRelevantEvidence(
            listOf(incidental, useful), question, question, limit = 3,
        )

        assertEquals(listOf("tilt-1", "tilt-2", "tilt-3"), merged.map(Evidence::documentId))
    }

    @Test
    fun `expanded search is judged against the original question`() {
        val relevant = evidence("myocardial", "1", "medical", -8.0).copy(
            title = "Cardiac event treatment",
            text = "A heart attack is a cardiac event requiring urgent assessment.",
        )
        val result = MultiKnowledgeRetriever.mergeRelevantEvidence(
            listOf(listOf(relevant)),
            query = "heart attack cardiac event myocardial infarction emergency treatment",
            question = "Explain a heart attack",
            limit = 4,
        )
        assertEquals(listOf(relevant), result)
    }

    @Test
    fun `identical chunks from two packs are deduplicated`() {
        val first = listOf(evidence("shared", "1", "alpha", score = -9.0))
        val second = listOf(
            evidence("shared", "1", "alpha", score = -8.0),
            evidence("unique", "1", "beta", score = -7.0),
        )

        val merged = MultiKnowledgeRetriever.mergeEvidence(listOf(first, second), limit = 10)

        assertEquals(listOf("shared", "unique"), merged.map { evidence -> evidence.documentId })
        assertEquals(-9.0, merged.first().score, 0.0)
    }

    @Test
    fun `same chunk id under different sources is kept`() {
        val merged = MultiKnowledgeRetriever.mergeEvidence(
            listOf(
                listOf(evidence("a", "1", "alpha", -5.0)),
                listOf(evidence("a", "1", "beta", -4.0)),
            ),
            limit = 10,
        )
        assertEquals(listOf("alpha", "beta"), merged.map { evidence -> evidence.source })
    }

    @Test
    fun `vector hits lead keyword hits and win dedup`() {
        val hits = listOf(
            evidence("doc", "v1", "bio", score = 0.91),
            evidence("doc", "v2", "bio", score = 0.80),
        )
        val keyword = listOf(
            evidence("doc", "k1", "bio", score = -9.0),
            evidence("doc", "v2", "bio", score = -8.0),
            evidence("doc", "k2", "bio", score = -7.0),
        )
        val fused = MultiKnowledgeRetriever.fuseVectorAhead(hits, keyword)
        assertEquals(listOf("v1", "v2", "k1", "k2"), fused.map { evidence -> evidence.chunkId })
        // Anchoring keeps vector scores below (stronger than) any bm25 and in cosine order.
        assertTrue(fused[1].score < MultiKnowledgeRetriever.VECTOR_FLOOR)
        assertTrue(fused[0].score < fused[1].score)
        assertTrue(fused[2].score > MultiKnowledgeRetriever.VECTOR_FLOOR)
    }

    @Test
    fun `anchored vector hits still lead the cross-pack merge`() {
        val vectorPack = MultiKnowledgeRetriever.fuseVectorAhead(
            hits = listOf(evidence("v", "v1", "bio", score = 0.75)),
            keyword = listOf(evidence("v", "k9", "bio", score = -2.0)),
        )
        val other = listOf(evidence("o", "k1", "crypto", score = -12.0))
        val merged = MultiKnowledgeRetriever.mergeEvidence(listOf(vectorPack, other), limit = 4)
        assertEquals("v1", merged.first().chunkId)
    }

    @Test
    fun `search skips database opens for sanitized-empty queries`() {
        var opens = 0
        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { listOf(File("nowhere.sqlite")) },
            open = { _ -> opens += 1; throw IllegalStateException("must not open") },
        )
        val results = runBlocking { retriever.search("\"\"", limit = 5) }
        assertTrue(results.isEmpty())
        assertEquals(0, opens)
    }

    @Test
    fun `search skips database open for blank query`() {
        var opens = 0
        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { emptyList() },
            open = { _ -> opens += 1; throw IllegalStateException("must not open") },
        )
        val results = runBlocking { retriever.search("   ", limit = 5) }
        assertTrue(results.isEmpty())
        assertEquals(0, opens)
    }

    @Test
    fun `focused vector packs are skipped outside documented coverage`() {
        val biology = PackDiscovery(
            coverageSummary = "Biology, medicine, cells, organisms and ecology",
            exampleQuestions = listOf("How does a cell divide?"),
            coverageLevel = CoverageLevel.FOCUSED,
        )
        assertTrue(MultiKnowledgeRetriever.shouldSearchVectors(biology, "How do cells divide?"))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(biology, "Why are Earth's seasons opposite?"))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack("How do cells divide?"))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack("Why are Earth's seasons opposite?"))

        val installedBiology = biology.copy(
            coverageSummary = "PubMed abstracts (longevity, biodefense, indoor air), Fight Aging archive, longevity databases.",
            exampleQuestions = listOf("What does rapamycin do in aging studies?"),
            coverageLevel = CoverageLevel.BROAD,
        )
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack(
            "Why do Earth's hemispheres have opposite seasons?",
        ))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack(
            "What does rapamycin do in aging studies?",
        ))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack(
            "tell me about indonesian",
        ))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(installedBiology, "tell me about indonesian"))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack(
            "Why are Earth's seasons opposite?",
        ))

        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { listOf(java.io.File("/packs/world-knowledge-biology/1.2.0/content.sqlite")) },
            packDiscoveries = { listOf(installedBiology) },
        )
        assertTrue(retriever.hasEligiblePacks("Why are Earth's seasons opposite?"))
        assertTrue(retriever.hasEligiblePacks("What does rapamycin do in aging studies?"))
        assertTrue(retriever.hasEligiblePacks("tell me about indonesian"))
    }

    @Test fun `small vector indexes do not need keywords in their discovery summary`() {
        val discovery = PackDiscovery("Selected reference articles", emptyList(), CoverageLevel.FOCUSED)
        assertTrue(MultiKnowledgeRetriever.shouldSearchVectors(discovery, "Why do boats float?", 2633))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(discovery, "Why do boats float?", 708813))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(discovery, "?!", 2633))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(discovery, "Why do boats float?", 0))
    }

    @Test
    fun `biology comparison remains searchable when terms are absent from discovery summary`() {
        val discovery = PackDiscovery(
            coverageSummary = "PubMed abstracts (longevity, biodefense, indoor air), Fight Aging archive, longevity databases.",
            exampleQuestions = listOf("What does rapamycin do in aging studies?",
                "Which genes are associated with human longevity in GenAge?"),
            coverageLevel = CoverageLevel.BROAD,
        )
        val question = "Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction."
        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { listOf(java.io.File("/packs/world-knowledge-biology/1.2.0/content.sqlite")) },
            packDiscoveries = { listOf(discovery) },
        )
        assertTrue(retriever.hasEligiblePacks(question))
        assertTrue(MultiKnowledgeRetriever.shouldSearchPack(question))
        assertFalse(MultiKnowledgeRetriever.shouldSearchVectors(discovery, question))
        assertFalse(retriever.hasEligiblePacks("?!"))
        assertFalse(MultiKnowledgeRetriever(databaseFiles = { emptyList() }).hasEligiblePacks(question))
    }

    @Test
    fun `search attempts every enabled keyword database despite nonmatching summaries`() = runBlocking {
        val files = listOf(File("/packs/biology/content.sqlite"), File("/packs/travel/content.sqlite"))
        // Packs are opened concurrently, so the order of attempts varies between runs.
        val opened = java.util.Collections.synchronizedList(mutableListOf<File>())
        val discovery = PackDiscovery(
            coverageSummary = "Longevity studies",
            exampleQuestions = emptyList(),
            coverageLevel = CoverageLevel.FOCUSED,
        )
        val retriever = MultiKnowledgeRetriever(
            databaseFiles = { files },
            packDiscoveries = { listOf(discovery, discovery) },
            // Exercise actual routing without Android SQLite; a failed pack must not
            // prevent attempting the next enabled pack.
            open = { file -> opened += file; throw IllegalStateException("Unavailable test database") },
        )
        assertTrue(retriever.search("mitosis meiosis", 4).isEmpty())
        assertEquals(files.toSet(), opened.toSet())
        assertEquals(files.size, opened.size)
    }
}
