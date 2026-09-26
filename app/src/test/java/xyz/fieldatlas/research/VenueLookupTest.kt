package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class VenueLookupTest {
    private fun listing(id: String, destination: String, name: String, description: String) = Evidence(
        documentId = "wv-eat-$id",
        chunkId = "wv-eat-$id:0000",
        title = "$destination — $name",
        source = "local Wikivoyage",
        text = "Destination: $destination\nPlace to eat: $name\nAddress: Old Town\n" +
            "Description: $description\nListing last checked: 2025-11-20",
        score = -5.0,
        matchedBy = "keyword: vegan, chiang, mai",
    )

    @Test fun `simple city lookup uses only stated vegan listings and check dates`() {
        val oldCentre = listing("centre", "Chiang Mai", "Old Cultural Centre", "Best northern Thai food")
        val pai = listing("pai", "Pai", "Garden Cafe", "Vegan meals; road to Chiang Mai")
        val guanIm = listing("guan", "Chiang Mai", "Guan Im", "Pure vegan restaurant")

        val result = VenueLookup.answer("Best vegan restaurants in Chiang Mai?", listOf(oldCentre, pai, guanIm))
        assertNotNull(result)
        assertEquals(listOf(guanIm), result!!.sources)
        assertTrue(result.answer.contains("can't verify a current “best” ranking"))
        assertTrue(result.answer.contains("Listing last checked: 2025-11-20. [S1]"))
        assertFalse(result.answer.contains("Old Cultural Centre"))
        assertFalse(result.answer.contains("Garden Cafe"))
    }

    @Test fun `complex venue question stays on the model research path`() {
        val guanIm = listing("guan", "Chiang Mai", "Guan Im", "Pure vegan restaurant")
        assertNull(VenueLookup.answer("Compare vegan restaurants in Chiang Mai by price and accessibility", listOf(guanIm)))
    }

    @Test fun `simple venue lookup never invokes the language model`() = runBlocking {
        val guanIm = listing("guan", "Chiang Mai", "Guan Im", "Pure vegan restaurant")
        val inference = FakeInferenceGateway(listOf("should not be generated"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(guanIm) }, inference)
            .research("Best vegan restaurants in Chiang Mai?").toList()

        assertEquals(0, inference.generateCalls)
        assertEquals(listOf(guanIm), events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
        val answer = events.filterIsInstance<ResearchEvent.Token>().single().text
        assertTrue(answer.contains("Guan Im"))
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(0, metrics.generatedTokenCount)
        assertEquals(setOf("S1"), metrics.citedSourceIds)
    }
}
