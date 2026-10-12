package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class RequestedIdentifierTest {
    private val unrelated = Evidence("doc", "doc:0", "Pressure", "Local", "Operating pressure is 20 kPa.", -1.0)
    private fun question(id: String) = "According to the saved sources, what operating pressure does $id require?"

    @Test fun genericAttributesCannotReplaceAnExplicitIdentifier() {
        assertTrue(EvidenceRelevance.missingRequestedIdentifier(question("abc-7619"), listOf(unrelated)))
        assertFalse(EvidenceRelevance.missingRequestedIdentifier("Explain abc-7619", listOf(unrelated)))
        assertFalse(EvidenceRelevance.missingRequestedIdentifier("According to the saved sources, compare abc-7619 and xyz-2091", listOf(unrelated)))
        assertFalse(EvidenceRelevance.missingRequestedIdentifier("According to the saved sources, compare abc-7619 with a hand pump", listOf(unrelated)))
    }

    @Test fun exactFamilyAndDigitsMatchCommonSavedSpellingVariants() {
        for (spelling in listOf("ABC-7619", "abc 7619", "abc_7619", "abc7619")) {
            assertFalse(spelling, EvidenceRelevance.missingRequestedIdentifier(question("abc-7619"),
                listOf(unrelated.copy(text = "$spelling requires 20 kPa."))))
        }
        for (spelling in listOf("abc-76190", "xabc-7619", "xyz-7619", "abc-7619a")) {
            assertTrue(spelling, EvidenceRelevance.missingRequestedIdentifier(question("abc-7619"),
                listOf(unrelated.copy(text = "$spelling requires 20 kPa."))))
        }
    }

    @Test fun missingIdentifierFinishesWithAnHonestGapAndNoGeneration() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Must not invent a pressure"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(unrelated) }, inference)
            .research(question("abc-7619")).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.none { it is ResearchEvent.Sources })
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.contains("couldn't find supporting saved sources"))
    }
}
