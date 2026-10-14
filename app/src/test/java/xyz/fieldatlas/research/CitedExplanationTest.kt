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
    @Test fun theModelsOwnGapSentencesBecomeAnAppNoteNotAWithheldAnswer() {
        val (kept, gap) = CitedExplanation.withoutGapStatements(
            "Caloric restriction extends lifespan in mice and rats [S1]. However, the provided evidence does not contain information " +
                "regarding humans. Therefore, the answer is INSUFFICIENT_EVIDENCE.")
        assertTrue(gap)
        assertEquals("Caloric restriction extends lifespan in mice and rats [S1].", kept)
        // A cited absence claim is still a gap statement; citing a source for what it lacks proves nothing.
        assertEquals("An IPv6 address has 128 bits [S2]." to true, CitedExplanation.withoutGapStatements(
            "An IPv6 address has 128 bits [S2]. The router's password cannot be determined from the provided evidence [S1]."))
        // A citation written after the full stop stays with its own sentence.
        assertEquals("Velin is a liquid. [S1]" to true, CitedExplanation.withoutGapStatements(
            "Velin is a liquid. [S1] The sources do not mention its boiling point."))
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements("The excerpts do not specify a warranty."))
        // A draft that leads with the gap has nothing on topic to keep, whatever it cites next.
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements(
            "The provided evidence does not mention the Zelthar purifier. [S1] and [S3] discuss space suit requirements."))
        assertEquals("An IPv6 address has 128 bits [S1].", CitedExplanation.finish("ANSWER: An IPv6 address has 128 bits [S1].", 1))
        // Findings about what a study did not show are answers, not gaps.
        val finding = "The test does not prove the battery lasts 9 hours below zero, because it was run at 20 °C [S1]."
        assertEquals(finding to false, CitedExplanation.withoutGapStatements(finding))
    }

    @Test fun aRejectedPlaceDraftIsNotGeneratedAgainWithoutReview() = runBlocking {
        val question = "Which hostels in Hanoi have a rooftop pool?"
        assertFalse(PromptBuilder.allowsModelExplanation(question))
        val listing = Evidence("wv-hanoi", "wv-hanoi:0", "Hanoi — Sleep", "https://en.wikivoyage.org/w/index.php?oldid=1",
            "Hostels in Hanoi: Hanoi Backpackers Hostel in the Old Quarter has dorm beds, a rooftop bar and a pool table.", 0.0)
        val model = FakeInferenceGateway(listOf("Hanoi Backpackers Hostel has a rooftop pool [S1]."), reviewVerdict = "UNSUPPORTED")
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(listing) }, model).research(question).toList()
        val shown = StringBuilder()
        for (e in events) when (e) {
            is ResearchEvent.Lead -> { shown.clear(); shown.append(e.text) }
            is ResearchEvent.Token -> { if (e.replace) shown.clear(); shown.append(e.text) }
            else -> Unit
        }
        val answer = shown.toString().trim()
        // The rejected claim is not shown, with or without its citation.
        assertFalse(answer, "rooftop pool" in answer)
        assertTrue(answer, answer == CitedExplanation.GAP || answer == CitedExplanation.UNAVAILABLE)
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun aCitedAnswerGetsTheSameTimeRepairAsAnyOther() = runBlocking {
        val ferry = Evidence("ferry", "ferry:0", "Koh Samui — Get around", "https://en.wikivoyage.org/w/index.php?oldid=1",
            "The last ferry from Koh Samui to Koh Phangan leaves Nathon pier at 21:30.", 0.0)
        val model = FakeInferenceGateway(listOf("The last ferry from Koh Samui to Koh Phangan leaves at 21:30 (10:30 PM) [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(ferry) }, model)
            .research("When does the last ferry from Koh Samui to Koh Phangan leave? Give the time in 12-hour format.").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer, "10:30 PM" in answer)
        assertTrue(answer, "9:30" in answer)
    }

    @Test fun sourcesInTheWorldAreNotTheExcerpts() {
        // "Plant sources" are foods, not the excerpts: a cited finding, kept whole.
        val b12 = "Plant sources do not contain reliable vitamin B12 [S1]. Vegans need fortified foods or a supplement [S2]."
        assertEquals(b12 to false, CitedExplanation.withoutGapStatements(b12))
        val water = "Natural water sources do not contain enough iodine, so add 5 drops [S1]."
        assertEquals(water to false, CitedExplanation.withoutGapStatements(water))
        // A gap clause followed by a cited fact keeps the fact.
        assertEquals("The museum is open daily 10:00-18:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "The evidence does not state a Sunday closing time, but the museum is open daily 10:00-18:00 [S1]."))
    }

    @Test fun theseSourcesAfterFoodSourcesAreFood() {
        val fact = "Plant foods are not reliable sources; these sources do not provide B12, so vegans need fortified foods or supplements [S1]."
        assertEquals(fact to false, CitedExplanation.withoutGapStatements(fact))
    }

    @Test fun aGapClauseIsSplitOnlyAtARealClauseBreak() {
        // "yet" and "but" inside the gap clause are not a new clause; a bare citation is not an answer.
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements("The excerpts do not say whether the pass has reopened yet [S1]."))
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements("The sources do not mention any fees but the entry ticket [S1]."))
        // A sentence-initial "Sources do not…" is still the model talking about its excerpts.
        assertEquals("The museum opens at 10:00 [S1]." to true, CitedExplanation.withoutGapStatements("The museum opens at 10:00 [S1]. Sources do not mention Sunday hours."))
        // A cited clause before the gap is kept.
        assertEquals("The hostel has a rooftop bar [S1]." to true, CitedExplanation.withoutGapStatements(
            "The hostel has a rooftop bar [S1], but the excerpts do not say whether it has a pool."))
    }

    @Test fun gapSentencesInOtherWordsAreStillGaps() {
        for (gap in listOf("Unfortunately, sources don't mention Sunday hours.", "The two excerpts do not say whether it opens on public holidays.",
            "Both sources do not mention Sunday hours.", "However, excerpts do not specify the Sunday hours.",
            "The above excerpts do not mention Sunday hours.", "The supplied sources do not mention Sunday hours.")) {
            assertEquals(gap, "The museum opens daily at 10:00 [S1]." to true,
                CitedExplanation.withoutGapStatements("The museum opens daily at 10:00 [S1].\n\n$gap"))
        }
    }

    @Test fun aPointerToACitationIsNotAnAnswer() {
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements("The provided sources do not specify the Sunday hours, but see [S1]."))
        assertEquals("INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements("The provided sources do not specify the Sunday hours, but refer to [S1]."))
    }

    @Test fun citedClausesAroundAGapAreKept() {
        assertEquals("The museum opens daily at 10:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "Although the excerpts do not state the Sunday hours, the museum opens daily at 10:00 [S1]."))
        assertEquals("The museum is open every day from 10:00 to 18:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "While the sources do not mention Sunday specifically, the museum is open every day from 10:00 to 18:00 [S1]."))
        assertEquals("The museum opens daily at 10:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "The museum opens daily at 10:00 [S1]; the excerpts do not mention a Sunday closing time."))
        assertEquals("The museum opens daily at 10:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "The museum opens daily at 10:00 [S1], and the sources do not mention Sunday."))
        assertEquals("The hostel has a rooftop bar [S1]. Breakfast is included [S2]." to true, CitedExplanation.withoutGapStatements(
            "The hostel has a rooftop bar [S1], but the excerpts do not say whether it has a pool, however breakfast is included [S2]."))
    }

    @Test fun citedFactsThatMentionTheSourcesAreKept() {
        for (fact in listOf("According to the sources, the ticket does not include the audio guide [S1].",
            "The sources say the hostel is central, but rooms do not include breakfast [S1].",
            "The provided evidence shows that modern vaccines do not contain mercury [S1].",
            "The excerpts note that the 24-hour pass does not cover the airport train [S2].",
            "In this context, modern vaccines do not contain thimerosal [S1].",
            "In the context of travel insurance, most basic policies do not cover scuba diving [S1].",
            "A tree's age cannot be determined from its height alone [S1].",
            "The two sources do not give the same opening time: [S1] says 09:00, while [S2] says 10:00.")) {
            assertEquals(fact, fact to false, CitedExplanation.withoutGapStatements(fact))
        }
    }

    @Test fun aGapNextToACitedFactKeepsTheFactAndNothingGarbled() {
        for (draft in listOf("The museum opens at 10:00 [S1] but the sources do not say when it closes.",
            "The museum opens at 10:00 [S1] and the sources do not say when it closes.",
            "The museum opens at 10:00 [S1] — the sources do not say when it closes.",
            "Although the museum opens at 10:00 [S1], the excerpts do not say when it closes.",
            "While the museum opens at 10:00 [S1], the sources do not mention Sunday hours.")) {
            assertEquals(draft, "The museum opens at 10:00 [S1]." to true, CitedExplanation.withoutGapStatements(draft))
        }
        // The next sentence turning with "However" is related, so it is kept.
        assertEquals("However, the museum is open daily from 10:00 to 18:00 [S1]." to true, CitedExplanation.withoutGapStatements(
            "The sources do not mention Sunday hours specifically. However, the museum is open daily from 10:00 to 18:00 [S1]."))
        // When a clean split is not certain, nothing half-sentenced is shown.
        for (draft in listOf("The excerpts do not give opening hours, prices, and transport details, but the museum is in the Old Town [S1].",
            "While the excerpts do not mention Sunday, Monday or Tuesday hours, the museum opens daily at 10:00 [S1].")) {
            val (kept, _) = CitedExplanation.withoutGapStatements(draft)
            assertTrue(kept, kept == "INSUFFICIENT_EVIDENCE" || kept.startsWith("The museum"))
        }
        // Pointers to a source are not answers.
        for (pointer in listOf("they can be found in [S1].", "you can check [S1].", "they are listed in [S1].")) {
            assertEquals(pointer, "INSUFFICIENT_EVIDENCE" to true, CitedExplanation.withoutGapStatements(
                "The provided sources do not specify the Sunday hours, but $pointer"))
        }
    }

    @Test fun aPartlyAnsweredQuestionKeepsItsCitedPartWithTheGapStated() = runBlocking {
        val fact = Evidence("luma", "luma:0", "Luma projector", "fixture://luma",
            "The Luma projector produces a beam through its emitter.", 0.0)
        val model = FakeInferenceGateway(listOf("The Luma projector emits its beam through an emitter [S1]. " +
            "Its brightness cannot be determined from the provided evidence [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) }, model)
            .research("How does the Luma projector produce a beam?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertEquals("The Luma projector emits its beam through an emitter [S1].\n\n" + CitedExplanation.PARTIAL, answer)
        // Only the cited remainder is reviewed; the gap is the app's statement, not a sourced claim.
        assertFalse(model.reviewPrompts.single().contains("brightness"))
    }

    @Test fun negativeOrAmbiguousSupportReviewNeverPublishesTheDraft() = runBlocking {
        val fact = Evidence("luma", "luma:0", "Luma projector", "fixture://luma",
            "The Luma projector produces a beam through its emitter. The test establishes operation only after dry storage. Wet storage safety is unknown.", 0.0)
        for (verdict in listOf("UNSUPPORTED", "", "SUPPORTED but some claims are uncertain")) {
            val model = FakeInferenceGateway(listOf("The Luma projector is unsafe after wet storage [S1]."), verdict)
            val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) }, model)
                .research("How does the Luma projector produce a beam?").toList()
            // The rejected draft never appears; the question falls back to the original excerpts,
            // labelled as not a generated explanation.
            val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
            assertTrue(answer, answer.contains("Wet storage safety is unknown.") && answer.contains("not a generated explanation"))
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
