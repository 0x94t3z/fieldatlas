package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class OsmVenueLookupTest {
    private fun osm(
        id: String,
        name: String,
        vegan: String,
        type: String = "restaurant",
        destination: String? = "Berlin",
        checked: String? = null,
        category: String = "Eat",
    ) = Evidence(
        documentId = "osm-place-$id",
        chunkId = "osm-place-$id:0000",
        title = listOfNotNull(destination, name).joinToString(" — "),
        source = "https://www.openstreetmap.org/node/${id.drop(1)}",
        text = listOfNotNull(
            destination?.let { "Destination: $it" },
            "Category: $category",
            "Place: $name",
            "Type: $type",
            "Vegan: $vegan",
            "Cuisine: thai, vietnamese",
            "Address: Oranienstraße 1, 10999 Berlin",
            "Hours in source: Mo-Su 12:00-22:00",
            "Latitude: 52.498600",
            "Longitude: 13.403000",
            checked?.let { "Listing last checked: $it" },
            "Map data snapshot: 2026-09-28 (© OpenStreetMap contributors)",
        ).joinToString("\n"),
        score = -1.0,
    )

    private val options = osm("n1", "Curry Corner", "vegan options")
    private val iceCream = osm("n2", "Frozen Joy", "fully vegan", type = "ice cream")
    private val fully = osm("n3", "Green Garden", "fully vegan", checked = "2026-09-08")
    private val question = "Tell me the best vegan restaurants in Berlin"

    @Test fun `fully vegan meal places lead and every place states its label and date`() {
        val result = VenueLookup.answer(question, listOf(options, iceCream, fully))!!
        // Ice cream is not a restaurant when meal places exist; fully vegan comes first.
        assertEquals(listOf(fully, options), result.sources)
        val answer = result.answer
        assertTrue(answer.startsWith("I can't verify a current “best” ranking offline; fully vegan places are listed first."))
        assertTrue(answer.contains("OpenStreetMap lists these places in Berlin as fully vegan or serving vegan options (map data as of 2026-09-28)"))
        assertTrue(answer.contains("- **Green Garden** — fully vegan restaurant; thai, vietnamese."))
        assertTrue(answer.contains("Listing last checked: 2026-09-08. [S1]"))
        assertTrue(answer.contains("- **Curry Corner** — vegan options restaurant"))
        assertTrue(answer.contains("No check date recorded. [S2]"))
        assertTrue(answer.contains("Map data © OpenStreetMap contributors (ODbL)."))
        assertFalse(answer.contains("Frozen Joy"))
        assertFalse(answer.contains("Wikivoyage"))
    }

    @Test fun `osm places are preferred over free-text listings and other cities are excluded`() {
        val wikivoyage = Evidence("wv-place-1-0001", "wv-place-1-0001:0000", "Berlin — Old Diner", "https://en.wikivoyage.org",
            "Destination: Berlin\nCategory: Eat\nPlace: Old Diner\nDescription: Some vegan dishes.", -2.0)
        val hamburg = osm("n9", "Harbour Greens", "fully vegan", destination = "Hamburg")
        val result = VenueLookup.answer(question, listOf(wikivoyage, hamburg, options))!!
        assertEquals(listOf(options), result.sources)
    }

    @Test fun `cafe questions keep cafes and restaurant-only filtering falls back when needed`() {
        val cafe = osm("n4", "Bean Room", "vegan options", type = "cafe")
        assertEquals(listOf(cafe), VenueLookup.answer("Which vegan cafes are in Berlin?", listOf(cafe))?.sources)
        // No meal place matched: keep what exists instead of answering with nothing.
        assertEquals(listOf(iceCream), VenueLookup.answer(question, listOf(iceCream))?.sources)
    }

    @Test fun `places without a destination never answer a city question`() {
        val nowhere = osm("n5", "Lone Kitchen", "fully vegan", destination = null)
        assertNull(VenueLookup.answer(question, listOf(nowhere)))
        assertTrue(EvidenceRelevance.keep(listOf(nowhere), FtsQuery.from(question)!!.terms, question = question).isEmpty())
    }

    @Test fun `fully vegan follow-up query targets the matched destination`() {
        assertEquals("fully vegan Berlin restaurant", VenueLookup.fullyVeganQuery(question, listOf(options)))
        assertEquals("fully vegan Berlin cafe", VenueLookup.fullyVeganQuery("Which vegan cafes are in Berlin?", listOf(options)))
        assertNull(VenueLookup.fullyVeganQuery("Tell me the best restaurants in Berlin", listOf(options)))
        assertNull(VenueLookup.fullyVeganQuery("Best vegan restaurants in Paris", listOf(options)))
        assertNull(VenueLookup.fullyVeganQuery(question, emptyList()))
    }

    @Test fun `orchestrator runs the focused search and answers without the model`() = runBlocking {
        val queries = mutableListOf<String>()
        val retriever = Retriever { query, _, _ ->
            queries += query
            if (query.startsWith("fully vegan")) listOf(fully) else listOf(options)
        }
        val inference = FakeInferenceGateway(listOf("must not run"))
        val events = ResearchOrchestrator(retriever, inference).research(question).toList()
        assertEquals(listOf(question, "fully vegan Berlin restaurant"), queries)
        assertEquals(0, inference.generateCalls)
        assertEquals(listOf(fully, options), events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
        assertTrue(events.last() is ResearchEvent.Complete)
    }
}
