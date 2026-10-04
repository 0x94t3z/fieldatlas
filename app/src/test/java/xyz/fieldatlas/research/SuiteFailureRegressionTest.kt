package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Failures from the October 2 24-question run, each reduced to the rule that caused it. */
class SuiteFailureRegressionTest {
    @Test fun instructionWordsAreNotSearchTerms() {
        assertEquals(listOf("rapamycin", "aging"),
            FtsQuery.from("Summarize what saved sources say about rapamycin and aging. Separate reported findings from remaining uncertainty.")!!.terms)
    }

    @Test fun todaysNewsGetsAnHonestAnswerWithoutTheModel() {
        assertEquals(QuestionRequirements.RECENT_NEWS, QuestionRequirements.response("What important longevity study was published today?", false))
        assertEquals(QuestionRequirements.RECENT_NEWS, QuestionRequirements.response("What is the latest research on autophagy?", false))
        assertNull(QuestionRequirements.response("What did the 2019 study on rapamycin publish?", false))
        assertNull(QuestionRequirements.response("Why do Earth's hemispheres have opposite seasons?", false))
    }

    private fun place(category: String, name: String) = Evidence(
        "osm-place-$name", "osm-place-$name:0000", "Berlin — $name", "https://www.openstreetmap.org/node/1",
        "Destination: Berlin\nCategory: $category\nPlace: $name\nType: restaurant\nVegan: fully vegan\nLatitude: 52.5\nLongitude: 13.4", 0.0,
    )

    @Test fun aFoodStopQuestionKeepsEatListingsBesideMuseums() {
        val question = "Using saved Berlin listings, suggest a vegan food stop and a museum visit."
        val eat = place("Eat", "Daizu")
        val see = place("See", "Pergamon").let { it.copy(text = it.text.replace("Type: restaurant\nVegan: fully vegan\n", "Type: museum\n")) }
        val kept = EvidenceRelevance.keep(listOf(eat, see), FtsQuery.from(question)!!.terms, question = question)
        assertTrue(eat in kept)
        assertTrue(see in kept)
    }

    @Test fun sharingOnlyResearchVocabularyDoesNotMakeAPaperRelevant() {
        val question = "Two studies report different lifespan outcomes. What study details would you need before trying to combine their conclusions?"
        val surgery = Evidence("p1", "p1:0", "Risk factors for pleural effusion", "https://pubmed.example/1",
            "PMID 1 Abstract: This study reports different outcomes after surgery in two groups of patients.", 0.0)
        assertFalse(surgery in EvidenceRelevance.keep(listOf(surgery), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun anOpenNowLookupChecksRecordedHoursAgainstThePhoneClock() {
        val question = "Which of the saved Berlin vegan restaurants is open right now?"
        val listing = place("Eat", "Daizu").let { it.copy(text = it.text + "\nHours in source: Mo-Su 12:00-22:00\nMap data snapshot: 2026-10-02 (© OpenStreetMap contributors)") }
        val answer = VenueLookup.answer(question, listOf(listing), java.time.LocalDateTime.of(2026, 10, 4, 13, 0))!!.answer
        assertTrue(answer, answer.startsWith("This place is open now by the opening hours mappers recorded, checked against this phone's clock (Sun 13:00)."))
        assertTrue(answer, "That assumes the phone is set to Berlin's local time." in answer)
        assertTrue(answer, "**Open now** until 22:00." in answer)
    }

    @Test fun aTripQuestionNamingACityAsksThePlaceCollectionsForVeganFood() {
        val museum = Evidence("wv-place-1", "wv-place-1:0000", "Berlin/Mitte — Pergamon Museum", "https://en.wikivoyage.org",
            "Destination: Berlin/Mitte\nCategory: See\nPlace: Pergamon Museum", 0.0)
        val question = "Using saved Berlin listings, suggest a vegan food stop and a museum visit. Explain what cannot be verified offline."
        assertEquals("fully vegan Berlin", VenueLookup.fullyVeganQuery(question, listOf(museum)))
        assertNull(VenueLookup.fullyVeganQuery("Explain the history of Berlin's museums.", listOf(museum)))
    }
}
