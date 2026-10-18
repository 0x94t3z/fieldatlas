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
        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int) = flow {
            this@Recording.prompt = prompt
            if (fail) error("native failure")
            emit(reply)
        }
    }

    @Test fun aReadyModelCannotRewriteEmergencyInstructions() = runBlocking {
        val model = Recording("1. Seek emergency medical attention [S2].\n2. Do not try to suck out the venom [S3].")
        var searched = false
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, model, emergencyGuides = { guides })
            .research("My friend was bitten by a snake on the trail, what should we do?").toList()
        assertFalse("the library is not searched for a guide answer", searched)
        assertEquals("The guide is displayed without generation", "", model.prompt)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().single().evidence
        assertTrue(sources.all { it.documentId == "emergency:snakebite" })
        val text = answer(events)
        assertFalse(text, text.contains("**For this situation**"))
        assertFalse(text, text.contains("model isn't available"))
        assertTrue(text, text.contains("shown as published by CDC/NIOSH [S1]"))
        assertTrue(text, text.contains("Seek emergency medical attention as soon as possible to start antivenom (if needed) and stop irreversible damage."))
        assertTrue(text, text.contains("call your local emergency number first"))
        assertTrue(text, text.contains("written for the United States"))
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
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

    @Test fun aFailingNativeModelCannotBlockPublishedGuidance() = runBlocking {
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
            .research("what do I do if a snake bites me, according to my plan?", attachments = listOf(file)).toList()
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

    @Test fun aFollowUpIsNotAnsweredWithTheGuideItsAnswerAlreadyShowed() = runBlocking {
        val sunburn = EmergencyGuideMatch.evidence(guides.first { it.id == "sunburn" })
        val model = Recording("Coconut oil does not treat a sunburn [S1].")
        var searched = false
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, model, emergencyGuides = { guides })
            .research("my son has a bad sunburn, what should we do: is it ok to use coconut oil on it?",
                topic = xyz.fieldatlas.research.TopicEvidence.Carried("is it ok to use coconut oil on it?", listOf(sunburn))).toList()
        assertTrue("the follow-up is researched, not answered with the same guide", searched)
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().none { sources -> sources.evidence.any { it.documentId == "emergency:sunburn" } })
    }

    @Test fun aLaterFollowUpIsNotAnsweredWithTheGuideTheTopicAlreadyShowed() = runBlocking {
        // Guide, then a researched follow-up, then another: the joined reading still names the sunburn, but the topic
        // showed its guide two answers ago, so this one is researched too.
        val sunburn = EmergencyGuideMatch.evidence(guides.first { it.id == "sunburn" })
        val passage = xyz.fieldatlas.research.Evidence("Coconut oil — Uses", "c:1", "Coconut oil", "https://en.wikipedia.org", "Text.", 1.0)
        val first = "my son has a bad sunburn, what should we do"
        val earlier = listOf(
            xyz.fieldatlas.research.FollowUp.Turn(first, first, "**If this is an emergency…", listOf(sunburn)),
            xyz.fieldatlas.research.FollowUp.Turn("is it ok to use coconut oil on it?", "$first: is it ok to use coconut oil on it?",
                "Coconut oil does not treat a sunburn [S1].", listOf(passage)))
        var searched = false
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, Recording("Aloe may soothe the skin [S1]."),
            emergencyGuides = { guides })
            .research("$first: does aloe vera help it heal faster?",
                topic = xyz.fieldatlas.research.TopicEvidence.Carried("does aloe vera help it heal faster?", listOf(passage), earlier)).toList()
        assertTrue("the second follow-up is researched, not answered with the guide again", searched)
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().none { sources -> sources.evidence.any { it.documentId == "emergency:sunburn" } })
    }

    @Test fun anEmergencyToldAsAFollowUpGetsItsGuide() = runBlocking {
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Recording("unused"), emergencyGuides = { guides })
            .research("What are the best things to see in Oklahoma City over a long weekend with kids who like museums and parks: " +
                "tornado on the ground a few miles away and we're in a trailer",
                topic = xyz.fieldatlas.research.TopicEvidence.Carried("tornado on the ground a few miles away and we're in a trailer", emptyList())).toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().first().evidence.all { it.documentId == "emergency:tornado" })
    }
}
