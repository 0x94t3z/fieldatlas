package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
