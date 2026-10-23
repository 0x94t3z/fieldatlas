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
        assertEquals("Ramen restaurant · Falckensteinstraße\u00A036", first.summary)
        assertEquals("Hours not recorded", first.hours)
        assertEquals("Checked 2025-09-01", first.checked)
        assertEquals(answer.sources.indexOf(daizu), first.sourceIndex)
        assertEquals("Daily 10:00–02:00", cards[1].hours)
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

    @Test fun aDistanceInsideTheGuidesDescriptionIsNotTheDistanceFromThePhone() {
        val museum = Evidence("wv-place-alcazar", "wv-place-alcazar:0", "Seville — Real Alcázar", "https://en.wikivoyage.org/w/index.php?oldid=1",
            "Destination: Seville\nCategory: See\nPlace: Real Alcázar\nDescription: The royal palace, 200 m north of the cathedral.", 0.0)
        val result = VenueLookup.answer("which museums in Seville should we see?", listOf(museum))
        result?.let { r -> placeCardModels(parseAnswerMarkdown(r.answer), r.sources)?.forEach { assertNull(it.distance) } }
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

    @Test fun anAddressWithoutAStreetDropsItsLoneBlockNumber() {
        // "1," means nothing without its block: the postcode and ward are what someone can use.
        val ramen = place("4", "T's TanTan", "fully vegan", listOf("Cuisine: ramen", "Address: 1, 110-0005 台東区"))
        assertEquals("Ramen restaurant · 110-0005 台東区", placeCardModel(0, ramen)!!.summary)
    }

    private fun osm(id: String, name: String, category: String, vararg extra: String) = Evidence(
        documentId = "osm-place-$id", chunkId = "osm-place-$id:0000", title = "Rome — $name", source = "https://www.openstreetmap.org/node/$id",
        text = (listOf("Destination: Rome", "Category: $category", "Place: $name") + extra +
            listOf("Latitude: 41.88", "Longitude: 12.49", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)")).joinToString("\n"),
        score = 0.0)

    @Test fun aCardSaysWhatThePlaceIsOnceAndShowsItsKindByItsTile() {
        val starbucks = osm("5", "Starbucks", "Eat", "Type: cafe", "Cuisine: coffee_shop", "Hours in source: Mo-Fr 07:00-22:30")
        val card = placeCardModel(0, starbucks)!!
        assertEquals("Coffee shop", card.kindLabel)
        assertEquals(PlaceKind.Cafe, card.kind)
        assertEquals("Mon–Fri 07:00–22:30" to PlaceStatusTone.Fact, card.status)
        assertEquals(PlaceKind.Food, placeCardModel(0, daizu)!!.kind)
        assertEquals(PlaceKind.Stay, placeCardModel(0, osm("6", "Hotel Roma", "Sleep", "Type: hotel"))!!.kind)
    }

    @Test fun aHospitalsStatusIsItsEmergencyDepartment() {
        val celio = placeCardModel(0, osm("7", "Policlinico militare Celio", "Health", "Type: hospital"))!!
        assertEquals(PlaceKind.Health, celio.kind)
        assertEquals("Emergency department not recorded" to PlaceStatusTone.Unknown, celio.status)
        val britannico = placeCardModel(0, osm("8", "Ospedale Britannico", "Health", "Type: hospital", "Emergency department: no"))!!
        assertEquals("No emergency department" to PlaceStatusTone.Fact, britannico.status)
        // A pharmacy is health too, but its line is its hours.
        assertEquals("Hours not recorded" to PlaceStatusTone.Unknown, placeCardModel(0, osm("9", "Farmacia", "Health", "Type: pharmacy"))!!.status)
    }

    @Test fun aHospitalListNamesTheNearestWithAnEmergencyDepartment() {
        // On the emulator (23 Oct) the one hospital with an emergency department was sixth of six, at 1.0 km.
        fun hospital(id: String, name: String, er: String?) = placeCardModel(0, osm(id, name, "Health", *listOfNotNull("Type: hospital",
            er?.let { "Emergency department: $it" }, "Address: Via di Santo Stefano Rotondo 6, 00184 Roma").toTypedArray()),
            line = "- **$name** — hospital, 690 m south-east away. [S1]")!!
        val celio = hospital("10", "Policlinico militare Celio", null)
        val britannico = hospital("11", "Ospedale Britannico", "no")
        val sanGiovanni = hospital("12", "Ospedale San Giovanni", "yes")
        // The line names it and opens its card's source.
        assertEquals("Nearest emergency department: Ospedale San Giovanni, 690 m south-east" to sanGiovanni.sourceIndex,
            hospitalEmergencyLine(listOf(celio, britannico, sanGiovanni)))
        assertEquals("None of these is recorded with an emergency department." to null, hospitalEmergencyLine(listOf(celio, britannico)))
        assertNull(hospitalEmergencyLine(listOf(placeCardModel(0, daizu)!!)))
        assertEquals("Emergency department" to PlaceStatusTone.Open, sanGiovanni.status)
        // The house number stays on its street's line.
        assertEquals("Via di Santo Stefano Rotondo\u00A06", britannico.street)
    }

    @Test fun anOldGuideListingSaysWhenItWasListedAndKeepsTheGuidesWords() {
        val excelsior = Evidence("wv-place-x", "wv-place-x:0000", "Tokyo/Shibuya — Excelsior Cafe", "https://en.wikivoyage.org/wiki/Tokyo/Shibuya",
            listOf("Destination: Tokyo/Shibuya", "Category: Drink", "Place: Excelsior Cafe", "Description: Great coffee and delicious sandwiches, with a warm smile.",
                "Address: 1-20-12 Jinnan, Toyama Building 1st/2nd Floors", "Listing last checked: 2015-05-14", "Latitude: 35.66", "Longitude: 139.70").joinToString("\n"), 0.0)
        val card = placeCardModel(0, excelsior, today = java.time.LocalDate.of(2026, 10, 23))!!
        assertEquals("Travel guide listing · 1-20-12 Jinnan", card.kindLine)
        assertEquals("Great coffee and delicious sandwiches, with a warm smile.", card.quote)
        assertEquals("Hours not recorded · listed in 2015", card.status.first)
        assertEquals(PlaceKind.Cafe, card.kind)
        // A recent check says nothing about age.
        assertNull(placeCardModel(0, daizu, today = java.time.LocalDate.of(2026, 10, 23))!!.listedYear)
    }

    @Test fun aPlaceListOpensWithWhatWasFoundCountedAndEndsWithAShortFootnote() {
        val lead = "Nearest saved places to Shibuya Crossing in Tokyo (map data as of 2026-10-02):"
        assertEquals("**2 coffee shops** near Shibuya Crossing in Tokyo, nearest first.", placeLead(lead, "Coffee shops near Shibuya Crossing in Tokyo?", 2))
        assertEquals("**1 hospital** near you, nearest first.", placeLead("Nearest saved places to your current location:", "hospital near me", 1))
        assertEquals("**3 places** near you, nearest first.", placeLead("Nearest saved places to your current location:", "what's around me", 3))
        // A qualified opening is shown as written.
        val vegan = "Nearest saved places to your current location that OpenStreetMap tags as fully vegan or serving vegan options:"
        assertEquals(vegan, placeLead(vegan, "vegan food near me", 2))
        assertEquals("2 Oct 2026", placeMapDate(lead))
        assertEquals("Straight-line distances from Shibuya Crossing. Map data 2 Oct 2026; hours can be out of date.",
            placeFootnote("Distances are straight-line from Shibuya Crossing, by its position in the saved collections; directions are compass bearings.", "2 Oct 2026"))
        assertEquals("Straight-line distances from your location. Hours can be out of date.",
            placeFootnote("Distances are straight-line from this phone's location, which stayed on the phone; directions are compass bearings.", null))
        assertEquals("2 embassies or consulates", VenueLookup.placeCount("nearest embassy", 2))
        assertEquals("1 embassy or consulate", VenueLookup.placeCount("nearest embassy", 1))
        assertEquals("1 pharmacy", VenueLookup.placeCount("pharmacy open now", 1))
        assertNull(VenueLookup.placeCount("things to do on a rainy day", 4))
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
