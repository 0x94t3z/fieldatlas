package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class NearbyVenueTest {
    private fun place(id: String, name: String, vegan: String, type: String = "restaurant", cuisine: String = "thai") = Evidence(
        documentId = "osm-place-$id",
        chunkId = "osm-place-$id:0000",
        title = "Berlin — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = listOf(
            "Destination: Berlin", "Category: Eat", "Place: $name", "Type: $type", "Vegan: $vegan",
            "Cuisine: $cuisine", "Latitude: 52.5", "Longitude: 13.4",
            "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)",
        ).joinToString("\n"),
        score = 0.0,
    )

    private val corner = place("1", "Corner Curry", "vegan options")
    private val farFully = place("2", "Far Garden", "fully vegan")
    private val nearFully = place("3", "Leaf Bowl", "fully vegan")
    private val iceCream = place("4", "Cold Cone", "fully vegan", type = "ice cream")

    @Test fun nearMeDetectionCoversNaturalPhrasingButNotNamedCitiesOrAnalysis() {
        for (question in listOf("Tell me the best vegan restaurants near me", "vegan food nearby",
            "Best vegan restaurants in the city I'm in", "Where can I eat vegan around here?",
            "Nearest vegan cafe")) {
            assertTrue(question, VenueLookup.isNearMe(question))
        }
        for (question in listOf("Tell me the best vegan restaurants in Berlin", "What is a nearby star?",
            "Compare vegan restaurants near me by price")) {
            assertFalse(question, VenueLookup.isNearMe(question))
        }
    }

    @Test fun distanceBandsComeBeforeFullyVeganAndRestaurantsExcludeIceCream() {
        val result = VenueLookup.nearbyAnswer(
            "Tell me the best vegan restaurants near me",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(iceCream, 0.2), NearbyPlace(farFully, 8.4),
                NearbyPlace(nearFully, 0.8)),
            15.0,
        )
        // Within 1 km: fully vegan first, then options; the 8.4 km fully vegan place comes last.
        assertEquals(listOf(nearFully, corner, farFully), result.sources)
        val answer = result.answer
        assertTrue(answer.contains("- **Leaf Bowl** — fully vegan restaurant, 800 m away; thai."))
        assertTrue(answer.contains("- **Corner Curry** — vegan options restaurant, 350 m away"))
        assertTrue(answer.contains("8.4 km away"))
        assertTrue(answer.contains("(map data as of 2026-10-02)"))
        assertTrue(answer.contains("location, which stayed on the phone"))
        assertFalse(answer.contains("Cold Cone"))
    }

    @Test fun cuisineWordsStillFilterButLocationWordsDoNot() {
        val sushi = place("5", "Roll Haus", "vegan options", cuisine = "sushi")
        val result = VenueLookup.nearbyAnswer("vegan sushi near me", listOf(NearbyPlace(corner, 0.1), NearbyPlace(sushi, 2.0)), 15.0)
        assertEquals(listOf(sushi), result.sources)
    }

    @Test fun nothingNearbySaysSoWithoutInventingPlaces() {
        val result = VenueLookup.nearbyAnswer("vegan restaurants near me", emptyList(), 15.0)
        assertTrue(result.sources.isEmpty())
        assertEquals("I couldn't find saved vegan places within 15 km of your location. " +
            "Saved collections cover places someone mapped; try naming a nearby city.", result.answer)
    }

    @Test fun orchestratorUsesLocationAndNeverTheModelOrKeywordSearch() = runBlocking {
        var searched = false
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                searched = true
                return emptyList()
            }
            override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int): List<NearbyPlace> {
                assertEquals(GeoPoint(52.5, 13.4), point)
                return listOf(NearbyPlace(nearFully, 0.8))
            }
        }
        val inference = FakeInferenceGateway(listOf("must not run"))
        val events = ResearchOrchestrator(retriever, inference, location = { GeoPoint(52.5, 13.4) })
            .research("vegan restaurants near me").toList()
        assertFalse(searched)
        assertEquals(0, inference.generateCalls)
        assertEquals(listOf(nearFully), events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
        assertEquals(setOf("S1"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
    }

    @Test fun withoutALocationTheAnswerExplainsHowToFixIt() = runBlocking {
        val inference = FakeInferenceGateway(listOf("must not run"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research("vegan restaurants near me").toList()
        assertEquals(VenueLookup.LOCATION_UNAVAILABLE, events.filterIsInstance<ResearchEvent.Token>().single().text)
        assertTrue(events.none { it is ResearchEvent.Sources })
        assertEquals(0, inference.generateCalls)
    }

    @Test fun distanceLabelsAndHaversine() {
        assertEquals("350 m", GeoDistance.label(0.354))
        assertEquals("1.2 km", GeoDistance.label(1.234))
        assertEquals("12 km", GeoDistance.label(12.7))
        // Berlin Alexanderplatz to Brandenburg Gate is about 2.6 km.
        assertEquals(2.6, GeoDistance.km(52.5219, 13.4132, 52.5163, 13.3777), 0.15)
    }
}
