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

    @Test fun anOpenNowAnswerPutsEachPlacesStatusOnItsCard() {
        val answer = VenueLookup.answer("Which vegan restaurants in Berlin are open now?", listOf(grill, daizu),
            java.time.LocalDateTime.of(2026, 10, 4, 23, 0))!!
        val cards = placeCardModels(parseAnswerMarkdown(answer.answer), answer.sources)!!
        assertEquals(listOf("Best Grill", "Daizu"), cards.map { it.name })
        assertEquals("Open now", cards[0].openNow)
        assertNull(cards[1].openNow)
        assertNull(placeCardModels(parseAnswerMarkdown(VenueLookup.answer("Which vegan restaurants are in Berlin?", listOf(grill))!!.answer),
            listOf(grill))!!.single().openNow)
    }

    @Test fun anAddressWithoutAStreetIsShownWhole() {
        val ramen = place("4", "T's TanTan", "fully vegan", listOf("Cuisine: ramen", "Address: 1, 110-0005 台東区"))
        assertEquals("Ramen restaurant · 1, 110-0005 台東区", placeCardModel(0, ramen)!!.summary)
    }

    @Test fun ordinaryAnswersKeepTheirText() {
        val passage = Evidence("d", "d:0", "Photosynthesis — Overview", "https://example.org", "Photosynthesis is a process.", 0.0)
        assertNull(placeCardModels(parseAnswerMarkdown("- Plants make sugar [S1]\n- Light drives it [S1]"), listOf(passage)))
        assertNull(placeCardModels(parseAnswerMarkdown("Photosynthesis makes sugar. [S1]"), listOf(passage)))
    }

    @Test fun aPlaceAnswerOpensWithItsResultAndKeepsItsQualifications() {
        val answer = VenueLookup.answer("Which vegan restaurants in Berlin are open now?", listOf(grill, daizu),
            java.time.LocalDateTime.of(2026, 10, 4, 23, 0))!!.answer
        val (lead, notes) = placeIntroParts(placeIntroText(answer))
        assertEquals("1 of the 2 places below is open now by the opening hours mappers recorded, checked against " +
            "this phone's clock (Sun 23:00); open places are listed first.", lead)
        assertEquals(true, notes!!.startsWith("1 has no hours I can check. That assumes the phone is set to Berlin's local time."))
        assertEquals(true, notes.endsWith("(map data as of 2026-10-02):"))
        assertEquals("Nearest saved places to your current location:" to null,
            placeIntroParts("Nearest saved places to your current location:"))
    }
}
