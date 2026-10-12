package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class NamedMechanismTest {
    private val unrelated = Evidence("d", "d:0", "Temperature", "Local", "Metal gets warm when heated.", -1.0)
    private fun saved(topic: String) = "According to the saved sources, how does $topic work?"

    @Test fun sharedConsequencesDoNotSubstituteForTheNamedTopic() {
        for (topic in listOf("thermal expansion", "magnetic refrigeration", "reverse osmosis", "induction heating"))
            assertTrue(topic, EvidenceRelevance.missingNamedMechanism(saved(topic), listOf(unrelated)))
    }

    @Test fun subjectWordsInActualTextTitlesOrStrongConceptEvidenceKeepThePath() {
        assertFalse(EvidenceRelevance.missingNamedMechanism(saved("thermal expansion"),
            listOf(unrelated.copy(text = "Thermal expansion changes volume."))))
        assertFalse(EvidenceRelevance.missingNamedMechanism(saved("thermal expansion"),
            listOf(unrelated.copy(title = "Thermal expansion — Mechanism"))))
        assertFalse(EvidenceRelevance.missingNamedMechanism(saved("thermal expansion"),
            listOf(unrelated.copy(matchedBy = "concept match 0.95"))))
        assertTrue(EvidenceRelevance.missingNamedMechanism(saved("thermal expansion"),
            listOf(unrelated.copy(text = "Temperature changes volume.\nMeSH: Thermal expansion"))))
    }

    @Test fun otherQuestionFormsAndMultipleRequestedPartsAreNotRejected() {
        for (question in listOf("How does thermal expansion work?", saved("osmosis"),
            "According to the saved sources, compare thermal expansion and reverse osmosis.",
            "According to the saved sources, how does thermal expansion work and why does water expand?"))
            assertFalse(question, EvidenceRelevance.missingNamedMechanism(question, listOf(unrelated)))
    }

    @Test fun namedSourceGapDoesNotInvokeVocabularyPlanningOrGeneration() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Unsupported mechanism or engineer advice"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(unrelated) }, inference)
            .research(saved("magnetic refrigeration")).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.none { it is ResearchEvent.Sources })
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.contains("couldn't find supporting saved sources"))
    }
}
