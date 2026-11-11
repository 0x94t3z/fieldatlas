package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyClassifier
import xyz.fieldatlas.emergency.EmergencyData
import xyz.fieldatlas.emergency.GuideAnswerCheck
import xyz.fieldatlas.inference.FakeInferenceGateway

/**
 * A learned reading of the whole message beside the keyword rules: when it is sure someone is in trouble now and the
 * rules passed the message over, the answer opens with the call line. It changes no route and replaces no answer.
 */
class UrgencyRoutingTest {
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
    private val model = EmergencyClassifier.read(File("src/main/assets/emergency/urgent.bin").readBytes())
    private val shipped: (String) -> Double? = { model.probability(it, "urgent") }
    private val sure: (String) -> Double? = { 0.99 }

    private fun answer(question: String, urgency: (String) -> Double?): String = runBlocking {
        ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(listOf("model answer")), emergencyGuides = { guides },
            urgency = urgency, modelFirstNotes = 0, smallModelFirstAid = "fallback")
            .research(question).toList().fold("") { acc, event ->
                when (event) {
                    is ResearchEvent.Lead -> event.text
                    is ResearchEvent.Token -> if (event.replace) event.text else acc + event.text
                    else -> acc
                }
            }
    }

    private val callLine = "**If this is an emergency, call your local emergency number first** (112 works on mobile phones in many countries).\n\n"
    private fun emergencyReply(text: String) = text.contains("No first-aid guide saved on this phone covers this situation")

    /** The answer the rules alone give, and whether [urgency] puts only the call line above it. */
    private fun addsOnlyTheCallLine(question: String, urgency: (String) -> Double?): Boolean {
        val before = answer(question) { null }
        return !before.contains("emergency number") && answer(question, urgency) == callLine + before
    }
    private fun changesNothing(question: String, urgency: (String) -> Double?) = answer(question, urgency) == answer(question) { null }

    private val hotCar = "theres a baby strapped in a car seat in a locked car in the supermarket car park, windows up, 34 degrees out, " +
        "the baby is bright red and has stopped crying"
    private val ordinary = "how do i get from the airport to the old town with two suitcases"

    @Test fun theShippedModelReadsMessagesExactlyAsTheTrainingScriptDid() {
        // Messages the training script scored with these same weights (tools/emergency-classifier/export_urgent.py).
        val vectors = Json.parseToJsonElement(File("src/test/resources/emergency-urgent-parity.json").readText()).jsonArray
        assertTrue(vectors.size >= 100)
        for (element in vectors) {
            val v = element.jsonObject
            val q = v["q"]!!.jsonPrimitive.content
            assertEquals(q, v["p"]!!.jsonPrimitive.double, model.probability(q, "urgent")!!, 1e-3)
        }
        assertEquals(listOf("no", "urgent"), model.labels)
        assertNull(model.probability(hotCar, "emergency"))
    }

    @Test fun anEmergencyTheRulesPassOverOpensWithTheCallLineAndKeepsItsAnswer() {
        val retention = "havent been able to pee since last night, about 14 hours now, lower belly is swollen like a football and the pain is making me cry"
        for (asked in listOf(hotCar, retention)) {
            assertFalse(asked, GuideAnswerCheck.firstAidQuestion(asked))
            assertTrue(asked, addsOnlyTheCallLine(asked, shipped))
            assertTrue(asked, answer(asked, shipped).contains("model answer"))
        }
    }

    @Test fun aRuleMatchIsReadByTheRulesAlone() {
        val matched = listOf(
            // The reading weighs a message as a whole: here the question about the number outweighs the injury (0.09).
            "My friend Chad is hurt badly. What is the ambulance number?",
            "my mum has had a water infection for days and is now shivering and confused, can she take my old antibiotics",
            "How do I treat a minor burn at home, and when should I see a doctor?",
            "my husband collapsed in the garden and hes not breathing, what do i do",
            "Bali or Lombok for a quiet week on the beach? We don't care about nightlife.")
        for (asked in matched) {
            assertTrue(asked, GuideAnswerCheck.firstAidQuestion(asked))
            for (reading in listOf(shipped, sure, { 0.0 }, { Double.NaN })) assertTrue(asked, changesNothing(asked, reading))
        }
        assertTrue(emergencyReply(answer(matched.first(), shipped)))
    }

    @Test fun theReadingNeedsToBeSureAndNeverMakesALessonUrgent() {
        assertTrue(changesNothing(ordinary) { 0.69 })
        assertTrue(addsOnlyTheCallLine(ordinary) { 0.7 })
        // No usable reading is no reading.
        assertTrue(changesNothing(ordinary) { Double.NaN })
        assertTrue(changesNothing(ordinary) { Double.POSITIVE_INFINITY })
        assertTrue(changesNothing(ordinary) { error("unreadable") })
        // A question asked to learn stays as it is whatever the reading says.
        assertTrue(changesNothing("how do you check someone is breathing, just want to know before my course", sure))
        // A story that is over reads as over.
        assertTrue(changesNothing("my overdose was 4 yrs ago and im in a really good place now. do i have to declare it for life insurance", shipped))
    }

    @Test fun aGreetingOrAWordOrTwoIsTooLittleToRead() {
        assertTrue(shipped("hello")!! >= 0.7)
        for (said in listOf("hello", "Hello", "no", "now", "help", "she is", "hello there how are you", "something is wrong")) {
            assertTrue(said, changesNothing(said, shipped))
            assertTrue(said, changesNothing(said, sure))
        }
        // Three words are too few, four are enough.
        assertTrue(addsOnlyTheCallLine("something is wrong here", sure))
    }

    @Test fun aQuestionTheAppAnswersItselfOrReadsAsAPlaceQuestionIsLeftAlone() {
        assertTrue(shipped("where are we right now?")!! >= 0.7)
        for (asked in listOf("where are we right now?", "how do i say my friend is not waking up in thai", "what is 15% of 80 euros",
            "its minus 20 celsius and we are stuck in the car overnight with a toddler, engine dead",
            "nearest hospital near me, she needs help now", "is there a pharmacy near me that is open late",
            "contractions every 2 minutes i need to push and we are stuck on a mountain road an hour from hospital")) {
            assertTrue(asked, changesNothing(asked, sure))
            assertTrue(asked, changesNothing(asked, shipped))
        }
    }

    @Test fun theLineAloneIsNeverLeftAsTheAnswer() {
        // A model that writes nothing: whatever the app shows then, it is not the call line on its own.
        fun shown(urgency: (String) -> Double?) = runBlocking {
            ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(List(6) { "" }), emergencyGuides = { guides },
                urgency = urgency, modelFirstNotes = 0, smallModelFirstAid = "fallback").research(ordinary).toList().fold("") { acc, event ->
                when (event) {
                    is ResearchEvent.Lead -> event.text
                    is ResearchEvent.Token -> if (event.replace) event.text else acc + event.text
                    else -> acc
                }
            }
        }
        val before = shown { null }
        val now = shown(sure)
        assertTrue(now, if (before.isBlank()) now.isBlank() else now == callLine + before)
        assertTrue(now, AnswerText.withoutCallLine(now) == before)
    }

    @Test fun aFollowUpIsLeftAsItIs() {
        fun shown(urgency: (String) -> Double?) = runBlocking {
            ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(listOf("model answer")), emergencyGuides = { guides },
                urgency = urgency, modelFirstNotes = 0, smallModelFirstAid = "fallback")
                .research("$hotCar: should i break the window", topic = TopicEvidence.Carried("should i break the window", emptyList(), emptyList()))
                .toList().filterIsInstance<ResearchEvent.Token>().last().text
        }
        assertEquals(shown { null }, shown(sure))
    }

    @Test fun noAnswerIsEverReplaced() {
        // Ordinary trouble the reading mistakes for an emergency keeps its whole answer under the line.
        for (asked in listOf("I lost my passport while travelling in Bangkok, what do I do now",
            "i am locked out of my airbnb in Berlin and the host is not answering",
            "we are stuck in a traffic jam and will miss our flight",
            "the burn from 2 weeks ago is oozing yellow and the red is spreading up the arm", ordinary)) {
            val before = answer(asked) { null }
            for (reading in listOf(shipped, sure)) {
                val now = answer(asked, reading)
                assertTrue("$asked: $now", now.endsWith(before) && !emergencyReply(now))
                assertTrue("$asked: $now", now == before || now == callLine + before)
            }
        }
    }
}
