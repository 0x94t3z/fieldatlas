package xyz.fieldatlas.research

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState

class KnowledgeReadingBudgetTest {
    private val question = "According to the saved sources, compare the network designs."
    private val items = (1..12).map { index -> Evidence("d$index", "d$index:0", "Design $index", "Local $index",
        "Design $index connects nodes. " + "A network needs routing. ".repeat(20) +
            "\n\nHowever, this design does not apply without independent power.", -index.toDouble()) }

    @Test fun slowReadingBudgetKeepsThreeWholeUnitsWithOriginalViewerIdentity() {
        val full = PromptBuilder.build(question, items, 8192)
        val slow = PromptBuilder.forReadingSpeed(question, full, 15.0)
        assertEquals(3, slow.sources.size)
        assertEquals(full.sources.take(3), slow.sources)
        assertTrue(slow.prompt.length < full.prompt.length)
        assertTrue(slow.prompt.contains("could not fit"))
        assertTrue(slow.prompt.contains("does not apply without independent power"))
        assertEquals(items.take(3), slow.sources.map { it.evidence })
        assertEquals(full.savedPrefixChars, slow.savedPrefixChars)
    }

    @Test fun unknownInvalidAndAlreadySmallPromptsRemainIdentical() {
        val full = PromptBuilder.build(question, items, 8192)
        for (rate in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0))
            assertSame(full, PromptBuilder.forReadingSpeed(question, full, rate))
        val short = PromptBuilder.build(question, items.take(2), 8192)
        assertSame(short, PromptBuilder.forReadingSpeed(question, short, 1.0))
    }

    @Test fun fasterReadingAllowsMoreWithoutChangingAnyExcerpt() {
        val full = PromptBuilder.build(question, items, 8192)
        val slow = PromptBuilder.forReadingSpeed(question, full, 15.0)
        val fast = PromptBuilder.forReadingSpeed(question, full, 200.0)
        assertTrue(fast.sources.size > slow.sources.size)
        for (source in fast.sources) assertEquals(full.sources.first { it.evidence == source.evidence }.excerpt, source.excerpt)
        assertEquals((1..fast.sources.size).map { "S$it" }, fast.sources.map { it.citationId })
    }

    @Test fun explanatoryDepthFitsBeforeIncidentalHistoryAndApplications() {
        val overview = items[0].copy(title = "Heat pump — Overview")
        val application = items[1].copy(title = "Heat pump — Applications")
        val history = items[2].copy(title = "Heat pump — History")
        val mechanism = items[3].copy(title = "Heat pump — Operating principle")
        val packed = PromptBuilder.buildKnowledge("How does a heat pump work?",
            listOf(overview, application, history), listOf(overview), listOf(mechanism), 8192, 15.0)
        assertEquals(listOf(overview, mechanism), packed.sources.map { it.evidence })
        assertEquals(EvidenceRelevance.passageText(mechanism.text), packed.sources[1].excerpt)
        assertTrue(packed.prompt.contains("could not fit"))
    }

    @Test fun allComparisonSubjectsKeepTheirSlotsBeforeDepth() {
        val subjects = items.take(3)
        val packed = PromptBuilder.buildKnowledge(question, items, subjects, items.drop(3).take(2), 8192, 1.0)
        assertEquals(subjects, packed.sources.map { it.evidence })
        assertEquals(listOf("S1", "S2", "S3"), packed.sources.map { it.citationId })
    }

    @Test fun noSubjectOrDepthRetainsTheExistingPackingPath() {
        val before = PromptBuilder.forReadingSpeed(question, PromptBuilder.build(question, items, 8192), 15.0)
        assertEquals(before, PromptBuilder.buildKnowledge(question, items, emptyList(), emptyList(), 8192, 15.0))
    }

    @Test fun twoSubjectComparisonDoesNotForceAnUnrelatedThirdUnit() {
        val packed = PromptBuilder.buildKnowledge(question, items, items.take(2), items.drop(2).take(2), 8192, 1.0)
        assertEquals(items.take(2), packed.sources.map { it.evidence })
    }

    @Test fun orchestrationActuallyAppliesTheKnowledgeBudget() = runBlocking {
        var packed: PackedPrompt? = null
        var generationCalls = 0
        val gateway = object : InferenceGateway {
            override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int) = flow {
                generationCalls++
                emit(if (prompt.endsWith("VERDICT:")) "SUPPORTED" else "Answer [S1]")
            }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> items.take(8) }, gateway,
            prefillSpeed = InMemoryPrefillSpeed(15.0), onEvidencePacked = { packed = it })
            .research(question, contextTokenBudget = 8192).toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().last().evidence
        assertEquals(3, sources.size)
        assertEquals(sources, checkNotNull(packed).sources.map { it.evidence })
        assertTrue(packed!!.prompt.contains("could not fit"))
        assertFalse(packed!!.mixedAnswer)
        assertEquals(2, generationCalls) // answer generation plus support review
    }
}
