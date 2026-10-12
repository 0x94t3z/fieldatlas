package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class MultipartPackingTest {
    private val question = "How does the Luma projector produce a beam, and what limits wet storage?"
    private fun row(id: String, text: String) = Evidence(id, "$id:0", "Fixture $id", "fixture://$id", text, 0.0)
    private val beam = row("beam", "The Luma projector produces a beam using its installed emitter. " +
        "The housing fixes the emitter in the tested position. ".repeat(8))
    private val wet = row("wet", "Wet storage is not covered by the test. Only dry storage was evaluated.")

    @Test fun slowBudgetCannotSpendEverySlotOnTheFirstClause() {
        val items = (1..8).map { beam.copy(documentId = "beam$it", chunkId = "beam$it:0") } + wet
        for (q in listOf(question, "According to the saved sources, $question")) {
            val packed = PromptBuilder.buildKnowledge(q, items, emptyList(), emptyList(), 8192, 1.0)
            assertTrue(q, packed.sources.any { it.evidence == wet && it.excerpt == wet.text })
            assertTrue(packed.sources.any { it.evidence == items.first() })
            assertEquals((1..packed.sources.size).map { "S$it" }, packed.sources.map { it.citationId })
            assertTrue(packed.prompt.contains("could not fit"))
        }
    }

    @Test fun paragraphsForBothPartsAndTheirCaveatsRemainConnected() {
        val first = "The Kera shell resists small impacts through a flexible layer, but large impacts can crack it. " +
            "The Kera shell and its outer layer were observed in the small impact test. ".repeat(6)
        val middle = "The sample carried the identifier Kera."
        val second = "Humid air may affect the surface, but the test does not establish that response."
        val caveat = "However, a dry-air result does not establish a humid-air result."
        val item = row("kera", listOf(first, middle, second, caveat).joinToString("\n\n"))
        val q = "How does the Kera shell resist small impacts, and how does humid air affect its surface?"
        val packed = PromptBuilder.buildKnowledge(q, listOf(item), emptyList(), emptyList(), 8192, 1.0)
        assertEquals(item.text, packed.sources.single().excerpt)
        assertEquals(item, packed.sources.single().evidence)
    }

    @Test fun requestedSubjectsKeepTheirSlotsAlongsideTheAdditionalPart() {
        val other = row("lens", "The lens directs the beam through the housing.")
        val packed = PromptBuilder.buildKnowledge(question, listOf(beam, other, wet), listOf(beam, other),
            listOf(beam), 8192, 1.0)
        assertEquals(listOf(beam, other), packed.sources.take(2).map { it.evidence })
        assertTrue(packed.sources.any { it.evidence == wet })
    }

    @Test fun singlePartPackingRemainsExactlyTheExistingPath() {
        val q = "How does the Luma projector produce a beam?"
        val items = listOf(beam, wet)
        assertEquals(PromptBuilder.forReadingSpeed(q, PromptBuilder.build(q, items, 8192), 1.0),
            PromptBuilder.buildKnowledge(q, items, emptyList(), emptyList(), 8192, 1.0))
    }

    @Test fun unknownSpeedKeepsOrderingAndMissingSupportIsNotInvented() {
        val items = listOf(beam, beam.copy(documentId = "other", chunkId = "other:0"), wet)
        for (speed in listOf(null, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(PromptBuilder.build(question, items, 8192),
                PromptBuilder.buildKnowledge(question, items, emptyList(), emptyList(), 8192, speed))
        }
        val absent = PromptBuilder.buildKnowledge(question, listOf(beam), emptyList(), emptyList(), 8192, 1.0)
        assertEquals(listOf(beam), absent.sources.map { it.evidence })
        assertFalse(absent.sources.any { it.excerpt.contains("Wet storage") })
    }

    @Test fun matchingChunkNamesAcrossPacksStayDistinctAndWholeUnitsStillObeyHardBudget() {
        val sameChunk = wet.copy(chunkId = beam.chunkId)
        val packed = PromptBuilder.buildKnowledge(question, listOf(beam, sameChunk), emptyList(), emptyList(), 8192, 1.0)
        assertEquals(setOf(beam, sameChunk), packed.sources.map { it.evidence }.toSet())
        val small = PromptBuilder.buildKnowledge(question, listOf(beam, sameChunk), emptyList(), emptyList(), 128, 1.0)
        assertFalse(small.sources.any { it.evidence == beam })
        assertTrue(small.sources.all { it.excerpt == it.evidence.text })
        assertTrue(small.prompt.contains("could not fit"))
    }

    @Test fun distantParagraphsAreNotStitchedAndFollowingCaveatsAreNotClipped() {
        val first = beam.text
        val distant = row("distant", first + "\n\n" + "Unrelated contextual description. ".repeat(90) + "\n\n" + wet.text)
        val excerpt = PromptBuilder.buildKnowledge(question, listOf(distant), emptyList(), emptyList(), 8192, 1.0)
            .sources.single().excerpt
        assertEquals(first + " …", excerpt)
        val caveat = "However, the observation has limits. " + "The context remains relevant. ".repeat(90)
        val qualified = row("qualified", first + "\n\n" + wet.text + "\n\n" + caveat)
        val whole = PromptBuilder.buildKnowledge(question, listOf(qualified), emptyList(), emptyList(), 8192, 1.0)
        assertEquals(qualified.text.trim(), whole.sources.single().excerpt)
    }

    @Test fun orchestratorPacksBothPartsWithoutChangingQuestionPolicyOrGeneratingClaims() = runBlocking {
        val contextualLimit = wet.copy(text = "The Luma projector's wet storage limit is unknown because only dry storage was evaluated. " +
            "The test does not establish how wet storage affects the light emitter or its beam.")
        // The end-to-end path also enforces the relevance gate. Give the topic an
        // explicit reference definition rather than assuming a partial fragment passes it.
        val overview = beam.copy(title = "Luma projector — Overview", text = "The Luma projector is a beam-producing device. " + beam.text)
        val items = (1..3).map { overview.copy(documentId = "beam$it", chunkId = "beam$it:0") } + contextualLimit
        for (q in listOf(question, "According to the saved sources, $question")) {
            var observed: PackedPrompt? = null
            val inference = FakeInferenceGateway(listOf("Diagnostic fixture answer."))
            val events = ResearchOrchestrator(Retriever { _, _, _ -> items },
                inference, prefillSpeed = InMemoryPrefillSpeed(1.0),
                onEvidencePacked = { observed = it }).research(q, contextTokenBudget = 8192).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            assertNotNull(observed)
            assertTrue(q + observed!!.sources.map { it.evidence.chunkId }, observed!!.sources.any { it.evidence == contextualLimit })
            assertTrue(observed!!.sources.any { it.evidence.documentId.startsWith("beam") })
            assertEquals(PromptBuilder.allowsModelExplanation(q), observed!!.mixedAnswer)
            assertTrue(observed!!.prompt.contains(q))
            assertEquals(0, inference.generateCalls)
            assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.contains("Original excerpts, not a generated explanation"))
        }
    }
}
