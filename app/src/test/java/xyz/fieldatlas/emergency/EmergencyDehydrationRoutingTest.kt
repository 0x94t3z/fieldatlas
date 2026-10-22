package xyz.fieldatlas.emergency

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

class EmergencyDehydrationRoutingTest {
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides

    @Test fun bothDiarrheaSpellingsAndRehydrationRequestsUsePublishedGuidance() = runBlocking {
        for (spelling in listOf("diarrhea", "diarrhoea")) {
            val question = "been throwing up with bad $spelling since last night and there's no pharmacy anywhere. " +
                "how do I make a rehydration drink and when should I start worrying?"
            assertEquals(question, "dehydration", EmergencyGuideMatch.match(question, guides)?.id)
            val model = FakeInferenceGateway(listOf("Invent a mixing recipe"))
            val events = ResearchOrchestrator(Retriever { _, _, _ -> error("Read the official guide only") }, model,
                emergencyGuides = { guides }).research(question).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            assertEquals(0, model.generateCalls)
            assertTrue(events.filterIsInstance<ResearchEvent.Sources>().single().evidence.single().source.contains("medlineplus.gov"))
            val text = events.filterIsInstance<ResearchEvent.Token>().last().text
            assertTrue(text.contains("without a model rewrite"))
            assertFalse(text.contains("Invent a mixing recipe"))
        }
    }

    @Test fun symptomWordsDoNotTurnAnExplanationIntoFirstAid() {
        assertNull(EmergencyGuideMatch.match("Why does diarrhea happen?", guides))
        assertNull(EmergencyGuideMatch.match("Explain the history of oral rehydration therapy", guides))
    }

    @Test fun longPersonalSymptomsKeepTheirGuide() {
        val question = "I have diarrhea and have been throwing up since yesterday. We are travelling far from town, " +
            "and the shop and pharmacy are closed until tomorrow. How do I make a rehydration drink and when should I worry?"
        assertEquals("dehydration", EmergencyGuideMatch.match(question, guides)?.id)
    }

    @Test fun batteryIngestionUsesGeneralPoisonReferenceWithAnExplicitCoverageLimit() = runBlocking {
        for (question in listOf("My child swallowed a button battery. What do I do?",
            "My toddler ingested a coin battery. What should we do?", "I swallowed a battery. Help!",
            "my 3 year old swallowed one of those small round watch batteries maybe 10 min ago. she seems totally fine, is it ok to just wait and see?")) {
            assertEquals("poisoning", EmergencyGuideMatch.match(question, guides)?.id)
            val model = FakeInferenceGateway(listOf("Invent treatment"))
            val events = ResearchOrchestrator(Retriever { _, _, _ -> error("Use the official reference") }, model,
                emergencyGuides = { guides }).research(question).toList()
            assertEquals(0, model.generateCalls)
            val text = events.filterIsInstance<ResearchEvent.Token>().last().text
            assertTrue(text.contains("without a model rewrite"))
            assertTrue(text.contains("not a battery-specific treatment guide"))
            // A swallowed battery opens with "call … now" (set 50), any other call line with "if this is an emergency".
            assertTrue(text, text.startsWith("**Call your local emergency number now**"))
            // The poisoning guide (NHS): call 999 or go to A&E if a child may have swallowed something harmful.
            assertTrue(text, text.contains("swallowed, touched or breathed in something harmful"))
            assertFalse(text.contains("Invent treatment"))
        }
    }

    @Test fun batteryResearchIsNotAnAcuteIngestion() {
        assertNull(EmergencyGuideMatch.match("How do button batteries work?", guides))
        assertNull(EmergencyGuideMatch.match("Why are button batteries dangerous?", guides))
    }
}
