package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.text.MarkdownLiteral

class PartialComparisonTest {
    private val battery = Evidence("b", "b:0", "Battery — Overview", "Local", "A battery stores chemical energy.\n\nSome batteries cannot be recharged.", -1.0)
    private val capacitor = battery.copy(documentId = "c", chunkId = "c:0", title = "Capacitor — Overview", text = "A capacitor stores energy in an electric field.")
    private val question = "Compare a battery with a capacitor."

    @Test fun preservesActualExcerptAndQualificationWhileDisclosingOnlySelectedGap() {
        val packed = PromptBuilder.build(question, listOf(battery), 2048)
        val answer = PartialComparison.answer(question, packed)!!
        assertTrue(MarkdownLiteral.decode(answer).contains("Some batteries cannot be recharged."))
        assertTrue(answer.contains("[S1]"))
        assertTrue(answer.contains("These selected passages do not mention capacitor"))
        assertFalse(answer.contains("No saved sources"))
        assertNull(PartialComparison.answer(question, PromptBuilder.build(question, listOf(battery, capacitor), 2048)))
    }

    @Test fun onlyTheActualPackedExcerptCountsNotOmittedOriginalText() {
        val full = battery.copy(text = battery.text + " A capacitor stores energy in an electric field.")
        val packed = PromptBuilder.build(question, listOf(full), 2048)
        val limited = packed.copy(sources = packed.sources.map { it.copy(excerpt = battery.text) })
        assertNotNull(PartialComparison.answer(question, limited))
        assertNull(PartialComparison.answer(question, packed))
    }

    @Test fun distinguishingQuestionDiscoversBothSubjects() {
        val question = "What distinguishes a battery from a capacitor?"
        assertTrue(SubjectLookup.leads(question))
        assertEquals(2, SubjectLookup.subjectsWanted(question))
        assertEquals(listOf("battery", "capacitor"), SubjectLookup.candidates(question, 2).map { it.text }.distinct())
        assertEquals(listOf("battery", "capacitor"), EvidenceRelevance.comparisonTerms(question))
        assertEquals(listOf("battery", "capacitor"), EvidenceRelevance.comparisonTerms("According to the saved sources, $question"))
    }

    @Test fun partialKnowledgeComparisonDoesNotGenerateUnsupportedOtherSide() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Invented comparison"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(battery) }, inference).research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(MarkdownLiteral.decode(answer).contains("Some batteries cannot be recharged."))
        assertTrue(answer.contains("do not support a complete comparison"))
    }
}
