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
        val wrong = cases.mapNotNull { (q, id) -> match(q).takeIf { it != id }?.let { "$q -> $it (want $id)" } }
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
