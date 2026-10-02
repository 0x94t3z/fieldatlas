package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialsLookupTest {
    private fun place(id: String, name: String, category: String, type: String, vararg extra: String) = Evidence(
        documentId = "osm-place-$id",
        chunkId = "osm-place-$id:0000",
        title = "Berlin — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = (listOf("Destination: Berlin", "Category: $category", "Place: $name", "Type: $type") + extra +
            listOf("Latitude: 52.5", "Longitude: 13.4", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)")).joinToString("\n"),
        score = 0.0,
    )

    private val pharmacy = place("1", "Apotheke am Platz", "Health", "pharmacy", "Phone: +49 30 123", "Hours in source: Mo-Sa 08:00-20:00")
    private val clinic = place("2", "Praxis Mitte", "Health", "clinic")
    private val hospital = place("3", "Charité", "Health", "hospital", "Emergency department: yes")
    private val atm = place("4", "ATM · Sparkasse", "Money", "ATM")
    private val toilets = place("5", "Toilets", "Toilets", "toilets", "Fee: no", "Wheelchair access: yes")
    private val cafe = place("6", "Leaf Bowl", "Eat", "restaurant", "Vegan: fully vegan")

    @Test fun everydayNeedsAreRecognisedAsNearMeQuestions() {
        for (q in listOf("Where is the nearest pharmacy?", "Find an ATM near me", "Is there a toilet nearby?",
            "nearest hospital", "Where can I withdraw cash near me?", "restroom close to me", "police station nearby")) {
            assertTrue(q, VenueLookup.isNearMe(q))
        }
        assertFalse(VenueLookup.isNearMe("How do pharmacies near me price insulin compared with hospitals?"))
        assertEquals(setOf("Health"), VenueLookup.nearbyCategories("pharmacy near me"))
        assertEquals(setOf("Money"), VenueLookup.nearbyCategories("where can I get cash nearby"))
        assertEquals(setOf("Toilets"), VenueLookup.nearbyCategories("nearest restroom"))
        assertEquals(setOf("Eat"), VenueLookup.nearbyCategories("vegan food near me"))
    }

    @Test fun aPharmacyQuestionListsOnlyPharmaciesWithDirectionAndPhone() {
        val result = VenueLookup.nearbyAnswer("Where is the nearest pharmacy?", listOf(
            NearbyPlace(cafe, .1, 10.0), NearbyPlace(clinic, .2, 90.0), NearbyPlace(pharmacy, .35, 45.0),
        ), 2.0)
        assertEquals(listOf(pharmacy), result.sources)
        assertTrue(result.answer, result.answer.contains("- **Apotheke am Platz** — pharmacy, 350 m north-east away."))
        assertTrue(result.answer.contains("Phone: +49 30 123."))
        assertTrue(result.answer.contains("In an emergency, call the local emergency number first."))
    }

    @Test fun practicalDetailsAreSpelledOut() {
        val h = VenueLookup.nearbyAnswer("hospital near me", listOf(NearbyPlace(hospital, 2.4, 180.0)), 5.0)
        assertTrue(h.answer, h.answer.contains("hospital, 2.4 km south away; emergency department."))
        val t = VenueLookup.nearbyAnswer("toilets nearby", listOf(NearbyPlace(toilets, .08, 270.0)), 2.0)
        assertTrue(t.answer, t.answer.contains("toilets, 80 m west away; free; wheelchair access: yes."))
        assertFalse(t.answer.contains("emergency number"))
        val none = VenueLookup.nearbyAnswer("ATM near me", listOf(NearbyPlace(cafe, .1)), 2.0)
        assertEquals("I couldn't find saved ATMs within 2.0 km of your location. " +
            "Saved collections cover places someone mapped; try naming a nearby city.", none.answer)
        val cash = VenueLookup.nearbyAnswer("where can I withdraw cash near me", listOf(NearbyPlace(atm, .3, 0.0)), 2.0)
        assertEquals(listOf(atm), cash.sources)
    }

    @Test fun cityQuestionsNameTheKindOfPlace() {
        val result = VenueLookup.answer("Where are pharmacies in Berlin?", listOf(pharmacy, clinic, cafe))!!
        assertEquals(listOf(pharmacy), result.sources)
        assertTrue(result.answer, result.answer.startsWith("OpenStreetMap lists these pharmacies in Berlin"))
    }

    @Test fun compassAndBearing() {
        assertEquals("north", GeoDistance.compass(0.0))
        assertEquals("north", GeoDistance.compass(359.0))
        assertEquals("north-east", GeoDistance.compass(44.0))
        assertEquals("east", GeoDistance.compass(90.0))
        assertEquals("south", GeoDistance.compass(202.0))
        assertEquals("south-west", GeoDistance.compass(210.0))
        // Alexanderplatz to the Brandenburg Gate is roughly west-south-west.
        assertEquals(255.0, GeoDistance.bearing(52.5219, 13.4132, 52.5163, 13.3777), 6.0)
        assertEquals("west", GeoDistance.compass(GeoDistance.bearing(52.5219, 13.4132, 52.5163, 13.3777)))
    }

    @Test fun pluralQuestionsKeepListingsNamedInTheSingular() {
        val embassy = place("7", "Embassy of Japan", "Safety", "embassy")
        for ((question, listing) in listOf("Which pharmacies are in Berlin?" to pharmacy, "Which embassies are in Berlin?" to embassy)) {
            val terms = FtsQuery.from(question)!!.terms
            assertEquals(question, listOf(listing), EvidenceRelevance.keep(listOf(listing), terms, question = question))
            assertEquals(question, listOf(listing), VenueLookup.answer(question, listOf(listing, cafe))?.sources)
        }
    }
}
