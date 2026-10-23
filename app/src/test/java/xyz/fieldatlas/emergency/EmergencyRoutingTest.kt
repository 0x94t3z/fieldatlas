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
        // A small model's published guide for someone hurt now opens with "call now" (sets 53-54).
        assertTrue(text, text.startsWith("**Call your local emergency number now**"))
        assertTrue(text, text.contains("written for the United States"))
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }

    @Test fun theMeasurementSwitchLetsTheModelWriteFromTheGuideWithTheGuideAsItsNote() = runBlocking {
        val model = Recording("Keep him still, with the bite below the heart [S1].\n1. Call for help now [S1].")
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides },
            modelWritesGuides = true).research("My friend was bitten by a snake on the trail, what should we do?").toList()
        assertTrue("the guide is the model's one note", model.prompt.contains("OFFICIAL GUIDE:\n[S1]"))
        assertTrue(model.prompt.contains("Seek emergency medical attention as soon as possible"))
        val text = answer(events)
        assertTrue(text, text.startsWith("**If this is an emergency, call your local emergency number first**"))
        assertTrue(text, text.contains("Keep him still"))
        assertTrue(text, text.contains("the guide as published is in Sources"))
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().single().evidence.all { it.documentId == "emergency:snakebite" })
    }

    @Test fun aModelTrustedWithFirstAidWritesItAndOthersShowTheGuide() = runBlocking {
        class Trusted(reply: String, private val trusted: Boolean) : InferenceGateway by Recording(reply) {
            override val writesFirstAid: Boolean get() = trusted
        }
        val question = "My friend was bitten by a snake on the trail, what should we do?"
        val written = answer(ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Trusted("Keep him still [S1].", true),
            emergencyGuides = { guides }).research(question).toList())
        assertTrue(written, written.contains("Keep him still"))
        assertTrue(written, written.contains("Written for your situation from CDC/NIOSH's guide"))
        val shown = answer(ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Trusted("Keep him still [S1].", false),
            emergencyGuides = { guides }).research(question).toList())
        assertTrue(shown, shown.contains("shown as published by CDC/NIOSH [S1]"))
        assertFalse(shown, shown.contains("Keep him still"))
    }

    @Test fun theFallbackGivesNoTreatmentStepsTheSavedGuidesDoNotSupport() = runBlocking {
        val model = Recording("Don't use an expired pen.")
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides },
            modelFirstNotes = 0, smallModelFirstAid = "fallback")
            .research("my mum has had a water infection for days and is now shivering and confused, can she take my old antibiotics").toList()
        val text = answer(events)
        assertFalse("the model may plan the search but writes no answer", model.prompt.contains("ANSWER:"))
        // No guide fits and nothing says it is minor: "call now" (set 52).
        assertTrue(text, text.startsWith("**Call your local emergency number now**"))
        assertTrue(text, text.contains("No first-aid guide saved on this phone covers this situation"))
        assertFalse(text, text.contains("expired"))
        assertEquals(listOf("emergency:cpr", "emergency:recovery-position", "emergency:anaphylaxis"),
            events.filterIsInstance<ResearchEvent.Sources>().single().evidence.map { it.documentId })
    }

    @Test fun aSwallowedPoisonGetsCallNowAndThePoisoningGuideNotAMemoryAnswer() = runBlocking {
        // Set 47: "took a whole packet of paracetamol … feels fine" was answered from memory about taking her phone away.
        val model = Recording("Take her phone away tonight.")
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides }, modelFirstNotes = 0)
            .research("my 16 yr old daughter took a whole packet of paracetamol 2 hours ago after a fight. she says she feels fine, should i take her phone").toList()
        val text = answer(events)
        assertTrue(text, text.startsWith("**Call your local emergency number now**"))
        // Set 50: a swallowed medicine no keyword names now opens the NHS poisoning guide itself, as published.
        assertTrue(text, text.contains("shown as published by NHS"))
        assertFalse(text, text.contains("phone away"))
        assertEquals("emergency:poisoning-steps", events.filterIsInstance<ResearchEvent.Sources>().single().evidence.single().documentId)
    }

    @Test fun aModelTrustedWithFirstAidGetsTheMedicineSafetyRuleWhenNoGuideMatched() = runBlocking {
        class Trusted : InferenceGateway by Recording("Call now.") {
            override val writesFirstAid: Boolean get() = true
            override val knowledgeFirst: Boolean get() = true
            var asked = ""
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int) =
                flow { asked = prompt; emit("Keep giving her reliever puffs and call an ambulance.") }
        }
        val model = Trusted()
        ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, emergencyGuides = { guides })
            .research("my wife has sickle cell and is in a bad pain crisis, can she take the tramadol from my back operation").toList()
        assertTrue(model.asked, model.asked.contains("Never advise against using emergency medicine"))
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

    @Test fun firstAidPromptsCarryTheCprAndAspirinRules() {
        // Set 54: Gemma kept someone barely breathing in the recovery position, and gave aspirin against a doctor's advice.
        val guide = xyz.fieldatlas.research.PromptBuilder.buildGuideAnswer("he has collapsed", EmergencyGuideMatch.evidence(guides.first { it.id == "cpr" }), false, false)
        assertTrue(guide.prompt.contains("needs CPR now, not the recovery position"))
        assertTrue(guide.prompt.contains("Do not give aspirin to someone a doctor has told to avoid it"))
    }

    @Test fun theOfficialGuideIsOnScreenWhileATrustedModelWritesItsAnswer() = runBlocking {
        class Trusted : InferenceGateway by Recording("Lie him on his side [S1].") {
            override val writesFirstAid: Boolean get() = true
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Trusted(),
            emergencyGuides = { guides }).research("my mate drank too much and is passed out on his back snoring, we cant wake him").toList()
        val tokens = events.filterIsInstance<ResearchEvent.Token>().map { it.text }.filter { it.isNotBlank() }
        assertTrue(tokens.first(), tokens.first().contains("shown as published by"))
        assertTrue(tokens.first(), tokens.first().contains("while an answer for your situation is written"))
        // The finished answer stands alone; the guide as published is in Sources.
        assertFalse(tokens.last(), tokens.last().contains("while an answer for your situation is written"))
        assertTrue(tokens.last(), tokens.last().contains("Lie him on his side"))
    }

    @Test fun someoneIllAfterDrinkingWaterIsNotShownHowToTreatWater() = runBlocking {
        // 24 Oct, Redmi: "my son drank stream water … now vomiting and confused" was answered with the CDC steps for boiling
        // and disinfecting water. Preparation steps are no answer for someone already ill.
        val boil = "Bring clear water to a rolling boil for 1 minute"
        for (question in listOf(
            "My son drank stream water on our hike and now he is vomiting and confused",
            "we drank untreated lake water two days ago and now I have stomach cramps and a fever",
            "my friend has been drinking from a well and now he is vomiting and dizzy",
            "I think the water we drank camping made my kid sick, she keeps throwing up and is very sleepy",
            "my daughter drank from a river yesterday and has had diarrhoea all night",
            "after drinking floodwater my husband has bad diarrhoea and feels faint",
        )) {
            val small = Recording("unused")
            val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, small, emergencyGuides = { guides }).research(question).toList()
            val shown = events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.map { it.documentId }
            val text = answer(events)
            assertFalse("$question -> $shown", "emergency:safe-drinking-water" in shown)
            assertFalse(question, text.contains(boil))
            assertTrue(question, text.startsWith("**Call your local emergency number now**"))
            // The vetted dehydration guide, or the no-guide reply that gives no treatment steps; nothing written by the model.
            assertTrue("$question -> $shown", shown == listOf("emergency:dehydration") && text.contains("shown as published by NIH/NLM") ||
                text.contains("No first-aid guide saved on this phone covers this situation, so this answer gives no treatment steps for it."))
            assertEquals(question, "", small.prompt)
        }
        // Getting ready for a hike still gets the water guide, under the softer line.
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Recording("unused"), emergencyGuides = { guides })
            .research("How do I make stream water safe to drink when I am hiking?").toList()
        assertEquals(listOf("emergency:safe-drinking-water"), events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.map { it.documentId })
        val text = answer(events)
        assertTrue(text, text.startsWith("**If this is an emergency, call your local emergency number first**"))
        assertTrue(text, text.contains(boil))
    }

    @Test fun aLearningQuestionShowsItsGuideOnAModelWithNoKnowledgeRoute() = runBlocking {
        val question = "how many chest compressions a minute is it for CPR and how deep do you push? just want to know before my course next week"
        // The 2B: no knowledge route, so the CPR guide as published, no call for help now, nothing searched or written.
        val small = Recording("The number of compressions per minute is 40 +/- 9 [S1].")
        var searched = false
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searched = true; emptyList() }, small, emergencyGuides = { guides })
            .research(question).toList()
        assertFalse(searched)
        assertEquals("", small.prompt)
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().single().evidence.all { it.documentId == "emergency:cpr" })
        val text = answer(events)
        assertTrue(text, text.startsWith("**If this is an emergency, call your local emergency number first**"))
        assertTrue(text, text.contains("shown as published"))
        // A model that answers from its own knowledge keeps answering the question (22 Oct: the 4B and Gemma get it right).
        class Knowing(reply: String) : InferenceGateway by Recording(reply) {
            override val knowledgeFirst: Boolean get() = true
        }
        val known = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Knowing("100 to 120 a minute, 5 to 6 cm deep."),
            emergencyGuides = { guides }).research(question).toList()
        assertTrue(known.filterIsInstance<ResearchEvent.Sources>().none { s -> s.evidence.any { it.documentId == "emergency:cpr" } })
    }

    @Test fun aTripStillAheadIsPlanningNotAnEmergency() = runBlocking {
        // 23 Oct: the 4B and the 2B opened "before trekking to Everest Base Camp" with "call now" and the park guide as published.
        val question = "What should I know about altitude sickness before trekking to Everest Base Camp?"
        assertNull(EmergencyGuideMatch.match(question, guides))
        assertTrue(GuideAnswerCheck.learning(question))
        assertFalse(GuideAnswerCheck.firstAidQuestion(question))
        class Knowing(reply: String) : InferenceGateway by Recording(reply) {
            override val knowledgeFirst: Boolean get() = true
        }
        val known = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Knowing("Climb slowly: no more than 300-500 m higher each night above 3,000 m."),
            emergencyGuides = { guides }).research(question).toList()
        assertTrue(known.filterIsInstance<ResearchEvent.Sources>().none { s -> s.evidence.any { it.documentId.startsWith("emergency:") } })
        assertFalse(answer(known), answer(known).contains("call your local emergency number now", ignoreCase = true))
        // On the mountain, it is still the guide: a decision about going higher is not a plan.
        assertEquals("altitude-sickness", EmergencyGuideMatch.match(
            "we're at 4,800 m on the Everest trek, my friend is confused and stumbling, should we rest before we go higher", guides)?.id)
    }

    @Test fun aFirstAidQuestionNoSituationRuleMatchesGetsItsTopicsGuide() = runBlocking {
        // A knowledge-first 4B that does not write first aid: the burns guide, not the no-guide reply with CPR basics.
        class Knowing(reply: String) : InferenceGateway by Recording(reply) {
            override val knowledgeFirst: Boolean get() = true
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, Knowing("Cool it."), emergencyGuides = { guides })
            .research("How do I treat a minor burn at home, and when should I see a doctor?").toList()
        val shown = events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.map { it.documentId }
        assertTrue(shown.toString(), "emergency:burns" in shown)
        assertFalse(shown.toString(), "emergency:cpr" in shown)
    }
}
