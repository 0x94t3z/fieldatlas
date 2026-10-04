package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import java.time.LocalDateTime

class OpenNowLookupTest {
    private fun place(id: String, name: String, category: String, type: String, hours: String?) = Evidence(
        documentId = "osm-place-$id",
        chunkId = "osm-place-$id:0000",
        title = "Berlin — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = (listOf("Destination: Berlin", "Category: $category", "Place: $name", "Type: $type") +
            listOfNotNull(hours?.let { "Hours in source: $it" }) +
            listOf("Latitude: 52.5", "Longitude: 13.4", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)")).joinToString("\n"),
        score = 0.0,
    )

    // Sunday 4 October 2026, 21:30.
    private val sundayNight = LocalDateTime.of(2026, 10, 4, 21, 30)
    private val weekdays = place("1", "Apotheke am Platz", "Health", "pharmacy", "Mo-Sa 08:00-20:00")
    private val allNight = place("2", "Bahnhof-Apotheke", "Health", "pharmacy", "24/7")
    private val unreadable = place("3", "Linden-Apotheke", "Health", "pharmacy", "sunrise-sunset")
    private val noHours = place("4", "Stern-Apotheke", "Health", "pharmacy", null)

    @Test fun anOpenPlaceFarAwayComesBeforeAClosedOneAroundTheCorner() {
        val question = "pharmacy open now near me"
        assertTrue(VenueLookup.isNearMe(question))
        val result = VenueLookup.nearbyAnswer(question, listOf(NearbyPlace(weekdays, 0.2), NearbyPlace(allNight, 4.0)), 15.0, sundayNight)
        assertEquals(listOf(allNight, weekdays), result.sources)
        assertEquals(1, result.openNow)
        assertTrue(result.answer, result.answer.startsWith("1 of the 2 places below is open now by the opening hours mappers recorded, checked against this phone's clock (Sun 21:30); open places are listed first."))
        // The phone is where the places are, so there is no time-zone assumption to state.
        assertFalse(result.answer, "local time" in result.answer)
        assertTrue(result.answer, "**Open now**. Hours in source: 24/7." in result.answer)
        assertTrue(result.answer, "**Closed now**. Hours in source: Mo-Sa 08:00-20:00." in result.answer)
    }

    @Test fun withoutAnOpenNowQuestionTheNearestPlaceStillLeadsAndNoStatusIsShown() {
        val result = VenueLookup.nearbyAnswer("nearest pharmacy", listOf(NearbyPlace(weekdays, 0.2), NearbyPlace(allNight, 4.0)), 15.0, sundayNight)
        assertEquals(listOf(weekdays, allNight), result.sources)
        assertEquals(null, result.openNow)
        assertFalse(result.answer, "Open now" in result.answer || "Closed now" in result.answer)
    }

    @Test fun placesWithoutReadableHoursAreNeitherCalledOpenNorClosed() {
        val result = VenueLookup.answer("Which pharmacies in Berlin are open now?", listOf(weekdays, unreadable, noHours), sundayNight)!!
        assertEquals(listOf(unreadable, noHours, weekdays), result.sources)
        assertTrue(result.answer, result.answer.startsWith("None of the places below is open now by the opening hours mappers recorded, checked against this phone's clock (Sun 21:30). 2 have no hours I can check. That assumes"))
        assertTrue(result.answer, "Linden-Apotheke** — pharmacy. Coordinates: 52.50000, 13.40000. Hours in source: sunrise-sunset." in result.answer)
        assertTrue(result.answer, "Stern-Apotheke** — pharmacy. Coordinates: 52.50000, 13.40000. No opening hours recorded." in result.answer)
    }

    @Test fun whenNoHoursCanBeCheckedTheAnswerSaysSoWithoutAClockClaim() {
        val result = VenueLookup.answer("Which pharmacies in Berlin are open now?", listOf(unreadable, noHours), sundayNight)!!
        assertTrue(result.answer, result.answer.startsWith("I can't tell which of these places are open now: their hours aren't recorded in a form I can check. OpenStreetMap lists"))
        assertFalse(result.answer, "phone's clock" in result.answer)
    }

    @Test fun lateNightHoursFromTheDayBeforeStillCount() {
        val bar = place("5", "Nachtcafé", "Eat", "cafe", "Fr-Sa 18:00-03:00")
        val result = VenueLookup.answer("Which cafes in Berlin are still open?", listOf(bar), LocalDateTime.of(2026, 10, 4, 1, 15))!!
        assertTrue(result.answer, "**Open now** until 03:00." in result.answer)
    }

    @Test fun aListingWithoutAnAddressGivesItsCoordinates() {
        val noAddress = place("6", "Bon", "Eat", "restaurant", null)
        val result = VenueLookup.answer("Which restaurants are in Berlin?", listOf(noAddress), sundayNight)!!
        assertTrue(result.answer, "Coordinates: 52.50000, 13.40000." in result.answer)
        val withAddress = noAddress.copy(text = noAddress.text.replace("Type: restaurant", "Type: restaurant\nAddress: Hauptstraße 5, 10827 Berlin"))
        assertFalse("Coordinates" in VenueLookup.answer("Which restaurants are in Berlin?", listOf(withAddress), sundayNight)!!.answer)
    }

    @Test fun anOpenNowNearbySearchKeepsWideningWhileEverythingCloseIsClosed() = runBlocking {
        // "Mo-Su off" and "24/7" read the same at any hour, so the test does not depend on the clock.
        val closed = (1..6).map { NearbyPlace(place("c$it", "Apotheke $it", "Health", "pharmacy", "Mo-Su off"), 0.1 * it) }
        val radii = mutableListOf<Double>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>): List<NearbyPlace> {
                radii += radiusKm
                return if (radiusKm < 5.0) closed else closed + NearbyPlace(allNight, 4.0)
            }
        }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("must not run")), location = { GeoPoint(52.5, 13.4) })
            .research("pharmacy open now near me").toList()
        assertEquals(listOf(2.0, 5.0), radii)
        assertEquals(allNight, events.filterIsInstance<ResearchEvent.Sources>().single().evidence.first())

        radii.clear()
        ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("must not run")), location = { GeoPoint(52.5, 13.4) })
            .research("nearest pharmacy").toList()
        assertEquals(listOf(2.0), radii)
    }
}
