package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Runs against the guides actually bundled in the APK. */
class EmergencyGuideMatchTest {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun emergencyQuestionsOpenTheRightGuide() {
        val cases = mapOf(
            "someone collapsed and is not breathing" to "cpr",
            "how do I do CPR" to "cpr",
            "my child is choking" to "choking",
            "how to stop bleeding from a deep cut" to "severe-bleeding",
            "I got bitten by a snake what should I do" to "snakebite",
            "signs of hypothermia" to "hypothermia",
            "what are the symptoms of a stroke" to "stroke",
            "heart attack symptoms" to "heart-attack",
            "my friend burned his hand on the stove what to do" to "burns",
            "my child burned her hand on a hot pan, what should I do?" to "burns",
            "scalded by boiling water" to "burns",
            "is it safe to drink stream water" to "safe-drinking-water",
            "how to purify water" to "safe-drinking-water",
            "I'm lost on a hike" to "lost",
            "what to do during an earthquake" to "earthquake",
            "tsunami" to "tsunami",
            "frostbite on my toes" to "frostbite",
            "bee sting swelling" to "insect-stings",
            "how do I remove a tick" to "tick-bites",
            "dog bite rabies" to "animal-bites",
            "heat stroke" to "heat-illness",
            "altitude sickness symptoms" to "altitude-sickness",
            "how to signal for help" to "signaling",
            "allergic reaction throat swelling" to "anaphylaxis",
            "someone is having a seizure" to "seizures",
        )
        val wrong = cases.mapNotNull { (q, id) ->
            val actual = match(q)
            if (actual != id) "$q -> $actual (want $id)" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun ordinaryQuestionsWithTopicWordsAreLeftToResearch() {
        for (question in listOf(
            "history of the 2004 Indian Ocean tsunami",
            "what happened during the 1906 San Francisco earthquake",
            "how to cut onions without crying",
            "how to burn calories fast",
            "how to make a snake game in Python",
            "how to paint a snake",
            "I lost my phone how do I find it",
            "explain photosynthesis",
            "best vegan restaurants in Berlin",
            "how does water boil at altitude",
            "how to improve my swimming stroke",
            "what is the boiling point of water",
            "compare the revisions in my files",
        )) assertNull(question, match(question))
    }

    @Test fun detailedEmergencyRequestsStillMatch() {
        val question = "I was bitten by a snake while hiking with my friend this morning and we are still on the trail a long way from our car with no internet connection. What should I do?"
        assertTrue(question.split(' ').size > 30)
        assertEquals("snakebite", match(question))
        assertEquals("snakebite", match("We are a long way from our car. ".repeat(20) + question))
    }

    @Test fun lengthyHistoricalQuestionsStayInResearch() {
        assertNull(match("Describe the history of the 2004 Indian Ocean tsunami, including the geological causes, the countries affected, the economic consequences and the changes in coastal infrastructure that followed over the next two decades."))
        // Long research questions that name a hazard and a safety or symptom word are not first aid.
        for (question in listOf(
            "Compare the symptoms of heatstroke and dehydration in marathon runners and explain the physiological mechanisms that cause each, citing what research says about electrolyte balance, sweat rates, core temperature and recovery after long races in hot weather.",
            "Explain how earthquake early warning systems work in Japan and Mexico, how many seconds of warning they typically give, what limits their accuracy, and how public safety outcomes have changed since they were introduced in each country.",
            "Why did the Chernobyl disaster lead to long term changes in nuclear reactor safety design, and how do those design changes compare with the lessons drawn after the Fukushima tsunami in 2011 for coastal plants?",
        )) assertNull(question, match(question))
    }

    @Test fun aBystanderDescribingAStrangerGetsItsGuide() {
        // Found by an outside review: 31 words, third person, no "my" or "I was".
        assertEquals("cpr", match("A man has collapsed and is not breathing. How do I do CPR? We are outside the railway station and an ambulance has already been called but has not arrived yet."))
        assertEquals("cpr", match("A man has collapsed and is not breathing. How do I do CPR?"))
    }

    @Test fun aSwollenAnkleThatCannotTakeWeightIsASprainOrFracture() {
        assertEquals("fractures-sprains", match("My friend fell while we were climbing and now his ankle is swollen and he cannot put weight on it, we are three hours from the trailhead and it is getting dark, what should we do right now?"))
        assertEquals("fractures-sprains", match("I twisted my ankle on the trail, what should I do?"))
    }

    @Test fun aLongAccountOfAnInjuryStillGetsItsGuide() {
        assertEquals("burns", match("We are camping and my son touched the hot stove and burned his hand, the skin is red and blistering and he is crying a lot, there is no signal here and the nearest town is far, how do I treat it?"))
    }

    @Test fun excerptsIdentifyThePublisherWithoutClaimingOfficialAuthority() {
        for (id in listOf("choking", "drowning", "snakebite")) {
            val guide = book.guides.single { it.id == id }
            val excerpt = EmergencyGuideMatch.officialSteps(EmergencyGuideMatch.sections(guide).first(), 2, guide.source.publisher)
            assertTrue(excerpt, excerpt.startsWith("**Source excerpt from ${guide.source.publisher}, as published** [S2]"))
            assertFalse(excerpt, excerpt.contains("Official steps"))
        }
    }

    @Test fun answersKeepThePublishedWordingAndCiteTheSource() {
        val guide = book.guides.single { it.id == "snakebite" }
        val answer = EmergencyGuideMatch.answer(guide)
        assertTrue(answer.contains("[S1]"))
        assertTrue(answer.contains("CDC/NIOSH"))
        assertTrue(answer.contains("written for the United States"))
        for (step in guide.sections.flatMap { it.steps + it.paragraphs }) assertTrue(step, answer.contains(step))
        val evidence = EmergencyGuideMatch.evidence(guide)
        assertEquals(guide.source.url, evidence.source)
        assertTrue(evidence.text.contains("Do not try to suck out the venom."))
    }

    @Test fun everyGuideHasASourceWithALicence() {
        assertTrue(book.guides.size >= 30)
        for (guide in book.guides) {
            assertTrue(guide.id, guide.source.license.isNotBlank() && guide.source.url.startsWith("https://"))
            assertTrue(guide.id, guide.plainText().length > 100)
            assertTrue(guide.id, guide.category in EmergencyData.categoryLabels)
        }
    }
}
