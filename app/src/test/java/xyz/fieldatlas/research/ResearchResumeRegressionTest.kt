package xyz.fieldatlas.research

import java.time.LocalDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

/** Integration coverage for the unfinished research changes, not a model-quality score. */
class ResearchResumeRegressionTest {
    private fun record(title: String, text: String, id: String = "wvs-" + title) =
        Evidence(id, "$id:0000", title,
            if (id.startsWith("wvs-")) "https://en.wikivoyage.org/w/index.php?oldid=1" else "https://en.wikipedia.org/w/index.php?oldid=1", text, 0.0)

    private fun saved(records: List<Evidence>, destinations: List<String> = emptyList()) = object : Retriever {
        override fun hasEligiblePacks(query: String) = false
        override suspend fun destinations(question: String) = destinations
        override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
        override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> = records.filter { row ->
            row.title.substringBefore(" — ").equals(phrase, true) && sections.any { row.title.substringAfter(" — ").contains(it, true) }
        }.take(limit)
        override suspend fun headed(phrase: String, words: List<String>, limit: Int, sections: List<String>) = records.filter { row ->
            row.title.contains(phrase, true) && words.any { row.title.contains(it, true) }
        }.take(limit)
        override suspend fun article(article: String, limit: Int) = records.filter { it.title.substringBefore(" — ").equals(article, true) }.take(limit)
    }

    @Test fun completeCalculatorsDoNotInvokeRetrievalOrTheModel() = runBlocking {
        val model = FakeInferenceGateway(listOf("Must not run"))
        val retriever = Retriever { _, _, _ -> error("A complete sum does not retrieve") }
        val question = "train leaves at 8:15, the trip is 420 km and it averages 140 km/h while moving, plus two 10-minute stops. what time do we get in?"
        assertTrue(Calculators.solve(question)!!.complete)
        val events = ResearchOrchestrator(retriever, model).research(question).toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.contains("11:35"))
        assertEquals(0, model.generateCalls)
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }

    @Test fun unsolvedSelfContainedArithmeticDoesNotBorrowNumbersFromCollections() = runBlocking {
        val model = FakeInferenceGateway(listOf("7 × 11 = 70 jars\n70 jars + 5 jars = 75 jars\nAnswer: 75 jars"))
        val question = "A crate has 7 rows of 11 jars and 5 jars outside. How many jars total?"
        assertTrue(WordProblem.isSelfContained(question))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> error("Do not import unrelated figures") }, model).research(question).toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, "82 jars" in answer)
        assertTrue(events.none { it is ResearchEvent.Sources })
        assertTrue((events.last() as ResearchEvent.Complete).metrics.citedSourceIds.isEmpty())
    }

    @Test fun lowercaseDestinationReadsItsSavedGuide() = runBlocking {
        val records = listOf(record("Finland — Overview", "Finland is a country in northern Europe."),
            record("Finland — Respect", "Finnish etiquette is relaxed; greetings are usually brief."),
            record("Finland — Do > Sauna", "In a sauna, shower before entering. Saunas are normally enjoyed without clothing."))
        val events = ResearchOrchestrator(saved(records), FakeInferenceGateway(listOf("Extra explanation")))
            .research("first time in finland. what's the sauna etiquette?").toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.any { "Sauna" in it.title })
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun countryFallbackPassesThroughARegionWithoutCycling() = runBlocking {
        val records = listOf(record("Littleton — Overview", "Littleton is a town in Midlands."),
            record("Midlands — Overview", "Midlands is a region in Avelia. Its capital is Littleton."),
            record("Avelia — Overview", "Avelia is a country."),
            record("Avelia — Get in > Entry requirements", "Visitors need a visa before entering Avelia. Apply for a visa before travel."))
        val events = ResearchOrchestrator(saved(records), FakeInferenceGateway(listOf("Explanation")))
            .research("Do I need a visa to enter Littleton?").toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.any { "Avelia — Get in" in it.title })
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun compoundQuestionCannotStopAfterOnlyTheFirstClause() = runBlocking {
        val visa = record("Avelia — Get in > Entry requirements", "Visitors need a visa before entering. Apply for a visa before travel.")
        val model = FakeInferenceGateway(listOf("The saved guide does not explain the second part."))
        val events = ResearchOrchestrator(saved(listOf(visa)), model)
            .research("Do I need a visa in Avelia? What happens if my passport is stolen?").toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue("The unanswered clause must reach generation", model.generateCalls > 0)
    }

    @Test fun relatedHistoryArticleIsActuallyRetrieved() = runBlocking {
        val union = record("Soviet Union — Overview", "The Soviet Union was a country from 1922 to 1991.", "swp-1-0000")
        val collapse = record("Dissolution of the Soviet Union — Overview", "The collapse resulted from economic stagnation and nationalism.", "enw-2-0000")
        assertEquals(collapse, SubjectLookup.relatedTitle("Soviet Union", listOf(collapse)))
        val events = ResearchOrchestrator(saved(listOf(union, collapse)), FakeInferenceGateway(listOf("A model explanation")), articleDepth = true)
            .research("Why did the Soviet Union collapse?").toList()
        assertTrue(events.toString(), events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.any { it.documentId == collapse.documentId })
    }

    @Test fun unknownEmergencyCitationsDoNotBorrowAnotherSourcesSupport() {
        val source = record("First aid", "Cover the wound with a clean cloth and apply pressure.")
        assertEquals("", AnswerChecks.removeUnsupportedSteps("Apply pressure with a clean cloth [S999].", listOf(source)))
    }

    @Test fun arithmeticChecksDoNotChangeGivenInputsOrDifferentCurrencies() {
        val answer = "5 × 5 = 20\n20 + 3 = 23\nAnswer: 23"
        val checked = AnswerChecks.checkArithmetic(answer, "There are 5 rows of 5 jars and a separate group of 20 with 3 extras.")
        assertTrue(checked, "20 + 3 = 23" in checked)
        val currencies = AnswerChecks.checkArithmetic("5 × 5 = €20\n$20 + $3 = $23")
        assertTrue(currencies, "$20 + $3 = $23" in currencies)
        assertEquals("15 cm + 15 m = 30 cm", AnswerChecks.checkArithmetic("15 cm + 15 m = 30 cm"))
        assertEquals("15% × €200 = €30", AnswerChecks.checkArithmetic("15% × €200 = €30"))
    }

    @Test fun openingOnANamedDayDoesNotMissAShortEarlyWindow() {
        val sunday = LocalDate.of(2026, 1, 11)
        assertTrue(OpeningHours.during("Su 06:00-07:00", sunday) is OpeningHours.Status.Open)
        assertTrue(OpeningHours.during("Su 06:00-07:00", sunday, 6 * 60, 12 * 60) is OpeningHours.Status.Open)
        assertEquals(OpeningHours.Status.Closed, OpeningHours.during("Su 06:00-07:00", sunday, 12 * 60, 18 * 60))
        assertTrue(OpeningHours.during("Sa 22:00-02:00", sunday, 0, 120) is OpeningHours.Status.Open)
        assertEquals(OpeningHours.Status.Unknown, OpeningHours.during("Sunday lunch", sunday))
        assertEquals("13PM-14PM", VenueLookup.guideHours("13PM-14PM"))
    }

    @Test fun threeNumbersDoNotBypassResearchWithoutAnArithmeticRequest() {
        assertFalse(WordProblem.isSelfContained("I have version 2, revision 3 and firmware 7. Which supports pairing?"))
        assertFalse(WordProblem.isSelfContained("According to my saved sources, how many jars are in 7 rows of 11 plus 5?"))
    }

    @Test fun percentageResultsRespectTheWrittenScale() {
        assertEquals("15% × 20% = 3%", AnswerChecks.checkArithmetic("15% × 20% = 3%"))
        assertEquals("15% × 20% = 0.03", AnswerChecks.checkArithmetic("15% × 20% = 0.03"))
        assertEquals("20 ÷ 100 = 20%", AnswerChecks.checkArithmetic("20 ÷ 100 = 20%"))
        assertEquals("15% ÷ 5% = 3", AnswerChecks.checkArithmetic("15% ÷ 5% = 3"))
    }

    @Test fun oversizedCitationIdsDoNotCrashOrBorrowSupport() {
        val source = record("First aid", "Do not give fluids.")
        val unknown = "Do not give fluids [S999999999999999999999999]."
        assertEquals("", AnswerChecks.removeUnsupportedWarnings(unknown, listOf(source)))
        assertEquals("", AnswerChecks.removeUnsupportedSteps(unknown, listOf(source)))
        assertEquals(unknown, AnswerChecks.repairCitations(unknown, listOf(source, source)))
    }

    @Test fun provisionsDoNotEquateMassAndVolume() {
        val question = "3 people drink 20 g of coffee each morning for 2 days. It comes in 1 litre bottles. How many bottles?"
        val result = Calculators.solve(question)
        assertTrue(result == null || !result.complete)
    }

    @Test fun emergencyInstructionsAreNotReversedByAnAvailableModel() = runBlocking {
        val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
        val model = FakeInferenceGateway(listOf("Get everyone indoors immediately [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> error("Use the official guide only") }, model,
            emergencyGuides = { guides }).research("Several people in our cabin have headaches and feel dizzy near the gas heater. What should we do?").toList()
        val text = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(text, text.contains("Get everyone indoors immediately"))
        assertTrue(text, text.contains("Get to fresh air and seek medical help immediately"))
        assertFalse(text, text.contains("**For this situation**"))
        assertEquals(0, model.generateCalls)
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }
}
