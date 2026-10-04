package xyz.fieldatlas.emergency

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever
import xyz.fieldatlas.research.VenueLookup

class EmergencyRoutingTest {
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
    /** Rebuilds the answer the way the view model does: a lead replaces, tokens append. */
    private fun answer(events: List<ResearchEvent>) = events.fold("") { acc, event ->
        when (event) {
            is ResearchEvent.Lead -> event.text
            is ResearchEvent.Token -> if (event.replace) event.text else acc + event.text
            else -> acc
        }
    }

    /** Records the prompt; optionally not ready or failing. */
    private class Recording(private val reply: String, ready: Boolean = true, private val fail: Boolean = false) : InferenceGateway {
        override val state = MutableStateFlow<InferenceState>(if (ready) InferenceState.Ready else InferenceState.Loading)
        var prompt = ""
        override suspend fun load(modelPath: String, systemPrompt: String) = Unit
        override suspend fun unload() = Unit
        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int) = flow {
            this@Recording.prompt = prompt
            if (fail) error("native failure")
            emit(reply)
        }
    }

    @Test fun theModelAnswersFromTheGuideOnlyInStrictCitedMode() = runBlocking {
        val model = Recording("1. Seek emergency medical attention [S2].\n2. Do not try to suck out the venom [S3].")
        var searched = false
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, model, emergencyGuides = { guides })
            .research("My friend was bitten by a snake on the trail, what should we do?").toList()
        assertFalse("the library is not searched for a guide answer", searched)
        assertTrue(model.prompt.contains("first-aid and safety assistant"))
        assertTrue(model.prompt.contains("never add treatments"))
        assertFalse("model knowledge is not invited", model.prompt.contains("your knowledge"))
        assertTrue(model.prompt.contains("If a snake bites you"))
        val sources = events.filterIsInstance<ResearchEvent.Sources>().single().evidence
        assertTrue(sources.all { it.documentId == "emergency:snakebite" })
        // The official steps are on screen before the model writes anything.
        val first = events.filterIsInstance<ResearchEvent.Lead>().single().text
        assertTrue(first, first.startsWith("**Official steps from CDC/NIOSH, as published** [S1]"))
        val text = answer(events)
        assertTrue(text, text.contains("**For this situation**\n\n1. Seek emergency medical attention"))
        assertTrue(text, text.contains("Official steps from CDC/NIOSH, as published"))
        // The app shows the key section word for word, whatever the model wrote.
        assertTrue(text, text.contains("- Seek emergency medical attention as soon as possible to start antivenom (if needed) and stop irreversible damage."))
        assertTrue(text, text.contains("call your local emergency number first"))
        assertTrue(text, text.contains("written for the United States"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun theGuideIsShownAsPublishedWhenTheModelIsNotReady() = runBlocking {
        val model = Recording("unused", ready = false)
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides })
            .research("someone is choking").toList()
        assertEquals("", model.prompt)
        val text = answer(events)
        assertTrue(text, text.contains("this is the guide as published"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun theGuideIsShownAsPublishedWhenGenerationFails() = runBlocking {
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Recording("", fail = true), emergencyGuides = { guides })
            .research("signs of hypothermia").toList()
        assertTrue(events.none { it is ResearchEvent.Failed })
        assertTrue(answer(events).contains("this is the guide as published"))
    }

    @Test fun ordinaryQuestionsStillReachResearch() = runBlocking {
        val inference = FakeInferenceGateway(listOf("An explanation."))
        var searched = false
        ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, inference, emergencyGuides = { guides })
            .research("history of the 2004 Indian Ocean tsunami").toList()
        assertTrue(searched)
    }

    @Test fun attachedFilesAreNeverReplacedByAGuide() = runBlocking {
        val model = Recording("From your file [S1].")
        val file = xyz.fieldatlas.attachments.ExtractedAttachment("f", "first-aid-plan.txt",
            listOf(xyz.fieldatlas.attachments.AttachmentPage(1, "Our group plan for a snakebite: call the ranger on channel 3.")))
        ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides })
            .research("what should I do after a snake bite according to my plan?", attachments = listOf(file)).toList()
        assertTrue(model.prompt.contains("channel 3"))
        assertFalse(model.prompt.contains("first-aid and safety assistant"))
    }

    @Test fun slowPhonesStillGetTheWhatToDoSections() {
        val guide = guides.single { it.id == "snakebite" }
        val picked = EmergencyGuideMatch.select("I got bitten by a snake, what should I do?", guide, 350)
        assertTrue(picked.joinToString { it.title }, picked.any { it.title.contains("If a snake bites you") })
        val signs = EmergencyGuideMatch.select("what are the signs of hypothermia", guides.single { it.id == "hypothermia" }, 350)
        assertTrue(signs.joinToString { it.title }, signs.any { it.title.contains("look out") })
    }

    @Test fun theOverallProcedureIsKeptWhenNotEverythingFits() {
        val picked = EmergencyGuideMatch.select("someone is choking and can't breathe", guides.single { it.id == "choking" }, 700)
        println("choking@700: " + picked.joinToString { it.title })
        assertTrue(picked.joinToString { it.title }, picked.first().title.contains("General strategy"))
    }

    @Test fun nearestHelpQuestionsAreNearMeLookups() {
        for (question in listOf("Nearest hospital near me", "Nearest pharmacy near me", "Nearest police station near me",
            "Drinking water near me", "Toilets near me")) {
            assertTrue(question, VenueLookup.isNearMe(question))
        }
    }
}
