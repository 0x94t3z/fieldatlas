package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class DoseSafetyTest {
    @Test fun aDoseTheModelWritesNeverReachesTheAnswer() = runBlocking {
        val article = Evidence("wp-para", "wp-para:0000", "Paracetamol — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
            "Paracetamol is a common medicine for pain and fever. Taking far more than the label allows can badly damage the liver.", 0.0)
        val model = FakeInferenceGateway(listOf("Paracetamol eases pain and fever. The usual amount is 4000 mg every 4 hours. Never mix it with alcohol."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model).research("How does paracetamol work?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer, "4000 mg" in answer)
        assertTrue(answer, "Never mix it with alcohol." in answer)
        assertTrue(answer, "follow the package leaflet" in answer)
    }
}
