package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import xyz.fieldatlas.inference.FakeInferenceGateway

class CitedExplanationTest {
    @Test fun shortAnswersAreBoundedWithoutTruncatingTheBudgetForDetailedRequests() {
        assertEquals(384, CitedExplanation.outputBudget("How does mitosis differ from meiosis?", 1536))
        assertEquals(64, CitedExplanation.outputBudget("What is autophagy?", 64))
        assertEquals(1536, CitedExplanation.outputBudget("Explain mitosis in detail.", 1536))
        assertEquals(1536, CitedExplanation.outputBudget("Explain mitosis step by step.", 1536))
    }
    @Test fun missingCitationCanBeRepairedWithoutExposingTheFirstDraft() = runBlocking {
        var turns = 0
        val fact = Evidence("luma", "luma:0", "Luma projector", "fixture://luma",
            "The Luma projector produces a beam through its emitter.", 0.0)
        val model = object : xyz.fieldatlas.inference.InferenceGateway by FakeInferenceGateway() {
            override fun explainEvidence(prompt: String, maxTokens: Int, savedPrefixChars: Int) = kotlinx.coroutines.flow.flow {
                turns++
                if (turns == 2) {
                    assertTrue(prompt.contains("The draft is missing source citations."))
                    assertFalse(prompt.contains("Uncited initial explanation."))
                    assertTrue(prompt.contains("using only these IDs: [S1]"))
                }
                emit(if (turns == 1) "Uncited initial explanation." else "The Luma projector emits its beam through an emitter [S1].")
            }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) }, model)
            .research("How does the Luma projector produce a beam?").toList()
        assertEquals(2, turns)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.endsWith("[S1]."))
        assertFalse(events.filterIsInstance<ResearchEvent.Token>().any { "Uncited initial" in it.text })
    }
    @Test fun negativeOrAmbiguousSupportReviewNeverPublishesTheDraft() = runBlocking {
        val fact = Evidence("luma", "luma:0", "Luma projector", "fixture://luma",
            "The Luma projector produces a beam through its emitter. The test establishes operation only after dry storage. Wet storage safety is unknown.", 0.0)
        for (verdict in listOf("UNSUPPORTED", "", "SUPPORTED but some claims are uncertain")) {
            val model = FakeInferenceGateway(listOf("The Luma projector is unsafe after wet storage [S1]."), verdict)
            val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) }, model)
                .research("How does the Luma projector produce a beam?").toList()
            assertEquals(CitedExplanation.UNAVAILABLE, events.filterIsInstance<ResearchEvent.Token>().last().text)
            assertEquals(2, model.reviewPrompts.size)
            assertTrue(model.reviewPrompts.all { it.contains(fact.text) })
            assertFalse(events.filterIsInstance<ResearchEvent.Token>().any { it.text.contains("unsafe after wet") })
        }
    }

    @Test fun unsupportedGuaranteesAreRejectedWithoutGuessingWhichQuantityBelongsToWhichSubject() {
        val selected = PackedPrompt("", listOf(source.copy(excerpt = "There is one entry in the case of a single tenant and two entries in the case of multiple tenants.")))
        // This lexical check cannot establish which subject a count belongs to.
        assertFalse(CitedExplanation.hasScopeRisk("A second process produces one entry [S1].", selected))
        assertFalse(CitedExplanation.hasScopeRisk("The entry count stays the same for each case [S1].", selected))
        assertTrue(CitedExplanation.hasScopeRisk("The method ensures that only one variable changes [S1].", selected))
        val reordered = selected.copy(sources = listOf(source.copy(excerpt = "The resulting cells contain one chromosome set only.")))
        assertFalse(CitedExplanation.hasScopeRisk("The resulting cells contain only one chromosome set [S1].", reordered))
    }

    @Test fun conflictingOriginalsStayAvailableToGenerationAndReview() {
        val a = source.copy(excerpt = "Fixture A reports eight units under dry conditions.")
        val b = source.copy(citationId = "S2", excerpt = "Fixture B reports six units under dry conditions.")
        val selected = PackedPrompt("", listOf(a, b))
        val answer = "The reports disagree: A reports eight units [S1], while B reports six [S2]."
        val review = CitedExplanation.reviewPrompt(answer, selected)
        assertTrue(review.contains(a.excerpt))
        assertTrue(review.contains(b.excerpt))
        assertTrue(review.contains(answer))
        assertTrue(CitedExplanation.reviewPassed("SUPPORTED"))
        assertFalse(CitedExplanation.reviewPassed("UNSUPPORTED"))
        assertFalse(CitedExplanation.reviewPassed("SUPPORTED\nExtra instructions"))
    }
    private val source = PromptSource("S1", Evidence("d", "c", "Study", "fixture://study", "Only mice were studied. Human effects were not measured.", 0.0))

    @Test fun repairNamesTheActualProblemWithoutReintroducingRejectedClaims() {
        val selected = PackedPrompt("", listOf(source))
        val invalid = CitedExplanation.repairInstruction("A claim [S9].", "Explain the study.", selected, "Keep it short.")
        assertTrue(invalid.contains("invalid source ID"))
        assertFalse(invalid.contains("A claim [S9]."))
        val unsupported = CitedExplanation.repairInstruction("Humans were studied [S1].", "Explain the study.", selected, "Keep it short.")
        assertTrue(unsupported.contains("not verified as fully supported"))
        val bounded = CitedExplanation.repairInstruction("x".repeat(10000), "Explain the study.", selected, "Keep it short.")
        assertTrue(bounded.length < 4000)
    }

    @Test fun anUncitedPassageCannotRescueTheWrongCitationDuringReview() {
        val first = source.copy(excerpt = "Fixture A reports eight units.")
        val second = source.copy(citationId = "S2", excerpt = "Fixture B reports six units.")
        val prompt = CitedExplanation.reviewPrompt("Fixture B reports six units [S1].", PackedPrompt("", listOf(first, second)))
        val evidenceBlock = prompt.substringAfter("EVIDENCE:").substringBefore("ANSWER:")
        assertTrue(evidenceBlock.contains(first.excerpt))
        assertFalse(evidenceBlock.contains(second.excerpt))
    }

    @Test fun comparisonCitationMustNameEverySubjectItIsAttachedTo() {
        val selected = PackedPrompt("", listOf(
            source.copy(excerpt = "Neral is a solid test material."),
            source.copy(citationId = "S2", excerpt = "Velin is a liquid test material.")))
        val question = "How does Neral differ from Velin?"
        assertTrue(CitedExplanation.hasWrongComparisonCitation("Neral is solid whereas Velin is liquid [S2].", question, selected))
        assertFalse(CitedExplanation.hasWrongComparisonCitation("Neral is solid [S1]. Velin is liquid [S2].", question, selected))
        assertFalse(CitedExplanation.hasWrongComparisonCitation("Neral is solid whereas Velin is liquid [S1][S2].", question, selected))
        assertFalse(CitedExplanation.hasWrongComparisonCitation("Neral is solid whereas Velin is liquid [S1], [S2].", question, selected))
        assertFalse(CitedExplanation.hasWrongComparisonCitation("Neral is solid whereas Velin is liquid [S1, S2].", question, selected))
        assertFalse(CitedExplanation.hasWrongComparisonCitation("Velin is liquid [S2].", question, selected))
    }

    @Test fun explicitSourceListsAreNormalizedButInvalidIdsAreNotHidden() {
        assertEquals("Claim [S1][S2].", CitedExplanation.finish("Claim [S1, S2].", 2))
        assertEquals("Claim [S1][S2].", CitedExplanation.finish("Claim [s1; 2].", 2))
        assertEquals(CitedExplanation.UNAVAILABLE, CitedExplanation.finish("Claim [S1, S9].", 2))
        assertEquals(CitedExplanation.UNAVAILABLE, CitedExplanation.finish("Claim [S1, -2].", 2))
        assertEquals("Values [1, 2].", AnswerText.finalized("Values [1, 2].", 2))
    }

    @Test fun acceptsCitedParaphraseWithoutAppendingOriginals() {
        val text = "The experiment covers mice, not human outcomes [S1]."
        assertEquals(text, CitedExplanation.finish(text, 1))
    }

    @Test fun missingInvalidAndPartiallyCitedOutputFailsWithoutFabricatedCitations() {
        for (text in listOf("Uncited claim.", "Claim [S2].", "Claim [S#].", "Claim [S1].\n\nUncited second claim.", "")) {
            assertEquals(text, CitedExplanation.UNAVAILABLE, CitedExplanation.finish(text, 1))
        }
    }

    @Test fun noEvidenceAndHiddenReasoningAreHandledWithoutExcerpts() {
        assertEquals(CitedExplanation.GAP, CitedExplanation.finish("INSUFFICIENT_EVIDENCE", 1))
        assertEquals("Mice were studied [S1].", CitedExplanation.finish("<think>private</think>Mice were studied [S1].", 1))
    }

    @Test fun explanationPromptPreservesOriginalsAndRequiresScopeConflictsAndAttribution() {
        val original = PackedPrompt("old", listOf(source))
        val packed = PromptBuilder.explain("Explain the experiment.", original)
        assertEquals(original.sources, packed.sources)
        assertFalse(packed.mixedAnswer)
        for (text in listOf("own words", "Cite every factual claim", "sources disagree", "negative statements unchanged", source.excerpt, "INSUFFICIENT_EVIDENCE")) {
            assertTrue(text, packed.prompt.contains(text))
        }
        assertTrue(packed.savedPrefixChars > 0)
        assertFalse(CitedExplanation.explicitlyRequestsQuotes("According to saved sources, explain aging."))
        assertTrue(CitedExplanation.explicitlyRequestsQuotes("Quote the exact wording."))
    }
}
