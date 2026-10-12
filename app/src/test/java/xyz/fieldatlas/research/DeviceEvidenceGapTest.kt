package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class DeviceEvidenceGapTest {
    private fun evidence(text: String) = Evidence("manual", "m1", "Manual", "Local", text, -1.0)
    @Test fun missingModelsNeverInheritGenericSpecifications() {
        for (model in listOf("KT-410", "NX-992", "AB-7")) {
            assertNotNull(DeviceEvidenceGap.response("Which replacement filter does my $model pump require?",
                listOf(evidence("Water pumps use many kinds of filters."))))
        }
        assertNull(DeviceEvidenceGap.response("Which filter does KT-410 require?", listOf(evidence("KT-410 uses the A cartridge."))))
        assertNull(DeviceEvidenceGap.response("Explain RFC-9110 compatibility", emptyList()))
        assertNull(DeviceEvidenceGap.response("How does a water filter work?", emptyList()))
        assertNotNull(DeviceEvidenceGap.response("Which filter does KT-410 require?", listOf(evidence("KT-4100 uses B."))))
    }
    @Test fun missingModelCompletesWithoutInventedAnswer() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Invented filter."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research("Which replacement cartridge fits my NX-992 pump?").toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().any { it.text.contains("can't verify") })
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }
    @Test fun coordinatesUsePhoneRouteAndNeverModelInstructions() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Invented location instructions"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> error("Must use phone location") }, inference)
            .research("What are my current GPS coordinates right now?").toList()
        assertEquals(0, inference.generateCalls)
        assertEquals(VenueLookup.LOCATION_UNAVAILABLE, events.filterIsInstance<ResearchEvent.Token>().single().text)
        assertFalse(VenueLookup.isWhereAmI("How do latitude and longitude describe a location?"))
    }
}
