package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.NearbyPlace
import xyz.fieldatlas.research.VenueLookup
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown

class PlaceCardsTest {
    private fun place(id: String, name: String, vegan: String, extra: List<String>, category: String = "Eat") = Evidence(
        documentId = "osm-place-$id",
        chunkId = "osm-place-$id:0000",
        title = "Berlin — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = (listOf("Destination: Berlin", "Category: $category", "Place: $name", "Type: restaurant", "Vegan: $vegan") + extra +
            listOf("Latitude: 52.5", "Longitude: 13.4", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)")).joinToString("\n"),
        score = 0.0,
    )

    private val daizu = place("1", "Daizu", "fully vegan",
        listOf("Cuisine: ramen", "Address: Falckensteinstraße 36, 10997 Berlin", "Listing last checked: 2025-09-01"))
    private val grill = place("2", "Best Grill", "vegan options", listOf("Cuisine: kebab", "Hours in source: Mo-Su 10:00-02:00"))

    @Test fun aCityLookupBecomesOneCardPerListingInAnswerOrder() {
        val answer = VenueLookup.answer("best vegan restaurants in Berlin", listOf(grill, daizu))!!
        val cards = placeCardModels(parseAnswerMarkdown(answer.answer), answer.sources)!!
        assertEquals(listOf("Daizu", "Best Grill"), cards.map { it.name })
        val first = cards.first()
        assertEquals("Fully vegan", first.badge)
        assertEquals("Ramen restaurant · Falckensteinstraße 36", first.summary)
        assertEquals("Hours not recorded", first.hours)
        assertEquals("Checked 2025-09-01", first.checked)
        assertEquals(answer.sources.indexOf(daizu), first.sourceIndex)
        assertEquals("Mo-Su 10:00-02:00", cards[1].hours)
        assertEquals("No check date", cards[1].checked)
    }

    @Test fun nearbyCardsCarryDistanceAndDirection() {
        val pharmacy = place("3", "Panorama Apotheke", "", listOf("Address: Panoramastraße 1, 10178 Berlin"), category = "Health")
            .let { it.copy(text = it.text.replace("Type: restaurant", "Type: pharmacy").replace("Vegan: \n", "")) }
        val result = VenueLookup.nearbyAnswer("Where is the nearest pharmacy?", listOf(NearbyPlace(pharmacy, 0.22, 270.0)), 2.0)
        val card = placeCardModels(parseAnswerMarkdown(result.answer), result.sources)!!.single()
        assertEquals("220 m", card.distance)
        assertEquals("west", card.direction)
        assertEquals(false, card.eat)
    }

    @Test fun ordinaryAnswersKeepTheirText() {
        val passage = Evidence("d", "d:0", "Photosynthesis — Overview", "https://example.org", "Photosynthesis is a process.", 0.0)
        assertNull(placeCardModels(parseAnswerMarkdown("- Plants make sugar [S1]\n- Light drives it [S1]"), listOf(passage)))
        assertNull(placeCardModels(parseAnswerMarkdown("Photosynthesis makes sugar. [S1]"), listOf(passage)))
    }
}
