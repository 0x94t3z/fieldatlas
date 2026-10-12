package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class PreciseReferenceAnswerTest {
    private val question = "Under QRS-42, what should readFlag return for code 0xface?"
    private val summary = Evidence("qrs-summary", "qrs-summary:0", "QRS-42 — Summary", "Local revision 1",
        "QRS-42 defines the readFlag interface. Implementations return supported flags.", -10.0)
    private val rule = Evidence("qrs-rule", "qrs-rule:0", "QRS-42 — Specification", "Local revision 1",
        "The readFlag function returns the following results:\n- true for code 0xface.\n" +
            "- false for unsupported codes.\n\nHowever, callers must first establish that the extension is enabled.", -1.0)

    private fun retriever(items: List<Evidence>) = Retriever { _, _, _ -> items }
    private fun answer(events: List<ResearchEvent>) = events.filterIsInstance<ResearchEvent.Token>().last().text

    @Test fun preciseReturnValueUsesOriginalRuleNotAnInventedParaphrase() = runBlocking {
        val inference = FakeInferenceGateway(listOf("The specification defines no behavior for that code."))
        val events = ResearchOrchestrator(retriever(listOf(summary, rule)), inference)
            .research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(answer(events).contains("true for code 0xface"))
        assertTrue(answer(events).contains("callers must first establish"))
        assertFalse(answer(events).contains("defines no behavior"))
        assertTrue(answer(events).contains("[S1]"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun genericSummaryCannotBecomeAClaimThatTheStandardHasNoRule() = runBlocking {
        val inference = FakeInferenceGateway(listOf("There is no defined return value."))
        val events = ResearchOrchestrator(retriever(listOf(summary)), inference).research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(answer(events).contains("retrieved passages"))
        assertTrue(answer(events).contains("do not establish"))
        assertFalse(answer(events).contains("There is no defined"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun bothRulePolaritiesAndConflictingOriginalsRemainVisible() = runBlocking {
        val conflicting = rule.copy(documentId = "qrs-new", chunkId = "qrs-new:0", source = "Local revision 2",
            text = "The readFlag function must return false for code 0xface. This revision changes the previous rule.")
        for (items in listOf(listOf(rule, conflicting), listOf(conflicting, rule))) {
            val inference = FakeInferenceGateway(listOf("Always true."))
            val events = ResearchOrchestrator(retriever(items), inference).research(question).toList()
            assertEquals(0, inference.generateCalls)
            assertTrue(answer(events).contains("true for code 0xface"))
            assertTrue(answer(events).contains("false for code 0xface"))
            assertEquals(setOf("S1", "S2"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
        }
    }

    @Test fun tooSmallBudgetProducesScopedGapInsteadOfClippedRuleOrModelAnswer() = runBlocking {
        val large = rule.copy(text = "QRS-42 readFlag requirements. " + "A condition remains relevant. ".repeat(100) + rule.text)
        val inference = FakeInferenceGateway(listOf("An invented return value."))
        val events = ResearchOrchestrator(retriever(listOf(large)), inference)
            .research(question, contextTokenBudget = 128).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(answer(events).contains("do not establish"))
        assertFalse(answer(events).contains("invented"))
    }

    @Test fun wholeRuleIdentityDoesNotProtectAnotherDocumentsCollidingChunkId() {
        val complete = rule.copy(chunkId = "shared:0", text = rule.text + "\n\n" + "Independent qualification. ".repeat(40))
        val other = summary.copy(chunkId = "shared:0", text = "General background. ".repeat(150))
        val packed = PromptBuilder.build("Explain general background.", listOf(complete, other), 8192,
            wholeEvidence = setOf(Triple(complete.documentId, complete.chunkId, complete.source)))
        assertEquals(EvidenceRelevance.passageText(complete.text), packed.sources.first().excerpt)
        assertNotEquals(other.text, packed.sources.last().excerpt)
        assertEquals(listOf(complete, other), packed.sources.map { it.evidence })
    }

    @Test fun criticalRuleStaysWholeAtSlowUnknownAndFastReadingSpeeds() {
        val longRule = rule.copy(text = "Specification context. ".repeat(45) + "\n\n" + rule.text)
        for (rate in listOf(null, 1.0, 200.0)) {
            val packed = PromptBuilder.buildKnowledge(question, listOf(summary, longRule), emptyList(), emptyList(), 2048, rate)
            assertEquals(listOf(longRule), packed.sources.map { it.evidence })
            assertEquals(longRule.text, packed.sources.single().excerpt)
            assertTrue(PreciseReferenceAnswer.answer(question, packed)!!.contains("callers must first establish"))
        }
    }

    @Test fun savedSourceLookupKeepsEstablishedStandardAliasesThroughTheLegacyIdentifierGuard() = runBlocking {
        val alias = rule.copy(title = "EIP-165 — Specification",
            text = "The supportsInterface function must return false for interface ID 0xffffffff.")
        val inference = FakeInferenceGateway(listOf("Unsupported alias answer."))
        val events = ResearchOrchestrator(retriever(listOf(alias)), inference)
            .research("According to saved sources, under ERC-165, what should supportsInterface return for interface ID 0xffffffff?").toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(answer(events).contains(alias.text))
        assertTrue(answer(events).contains("[S1]"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }
}
