package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyClassifier
import xyz.fieldatlas.emergency.EmergencyData
import xyz.fieldatlas.inference.FakeInferenceGateway

/** Messages the keyword rules miss get their guide from the bundled classifier, within its limits. */
class ClassifiedGuideTest {
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
    private val classifier = EmergencyClassifier.read(File("src/main/assets/emergency/classifier.bin").readBytes())

    private fun guideShown(question: String, withClassifier: Boolean = true): String? = runBlocking {
        val orchestrator = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(listOf("model answer")),
            emergencyGuides = { guides }, emergencyClassifier = { if (withClassifier) classifier else null })
        orchestrator.research(question).toList().filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }
            .firstOrNull { it.documentId.startsWith("emergency:") }?.documentId?.removePrefix("emergency:")
    }

    @Test fun aStrokeToldInEverydayWordsOpensTheStrokeGuide() {
        val stroke = "grandad cant lift his right arm and his smile crooked since 10 min ago"
        // Sealed set 61 (2 Nov 2026): the keyword rules gave this no guide, and the topic fallback (on a model with no
        // knowledge route, as here) gave CPR.
        assertEquals("cpr", guideShown(stroke, withClassifier = false))
        assertEquals("stroke", guideShown(stroke))
        assertEquals("seizures", guideShown("coworker shaking on the floor for like 6 minutes now and it is not stopping"))
    }

    @Test fun labourWithoutBloodTravelWaterAndPastEventsOpenNoGuideFromIt() {
        assertTrue(guideShown("my wife is 34 weeks pregnant and her waters just broke on the ferry") != "pregnancy-bleeding")
        assertEquals("pregnancy-bleeding", guideShown("pregnant 31 weeks, suddenly bleeding and belly is hard and really painful"))
        assertTrue(guideShown("is tap water safe to drink in mexico city") != "safe-drinking-water")
        assertEquals(null, guideShown("my grandad had a stroke back in 1998, his smile went crooked and his arm was weak"))
    }
}
