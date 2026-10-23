package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

/** "A hospital near the Colosseum in Rome": the places around the landmark, measured from where the guide puts it. */
class LandmarkLookupTest {
    private fun guide(id: String, destination: String, place: String, vararg lines: String) = Evidence(
        documentId = "wv-place-$id", chunkId = "wv-place-$id:0000", title = "$destination — $place",
        source = "https://en.wikivoyage.org/wiki/${destination.replace(' ', '_')}",
        text = (listOf("Destination: $destination", "Category: See", "Place: $place") + lines).joinToString("\n"), score = 0.0)

    private fun hospital(id: String, name: String) = Evidence(
        documentId = "osm-place-$id", chunkId = "osm-place-$id:0000", title = "Rome — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = listOf("Destination: Rome", "Category: Health", "Place: $name", "Type: hospital", "Emergency department: yes",
            "Latitude: 41.88", "Longitude: 12.49", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"),
        score = 0.0)

    // As in the saved guide: the Colosseum's position sits in the second passage of its listing.
    private val colosseumHead = guide("col", "Rome/Colosseo", "Colosseum", "Description: Known properly as the Flavian Amphitheatre.")
    private val colosseumTail = colosseumHead.copy(chunkId = "wv-place-col:0001",
        text = "rance to the Colosseum. Inside you can take a tour.\nLatitude: 41.89059\nLongitude: 12.49156")
    private val tourCopy = guide("tour", "Ancient Rome tour", "Colosseum", "Latitude: 41.0", "Longitude: 12.0")

    @Test fun theLandmarkAndItsCityAreReadFromTheQuestion() {
        val colosseum = VenueLookup.landmark("Where can I find a hospital near the Colosseum in Rome?")!!
        assertEquals("Colosseum" to "Rome", colosseum.name to colosseum.city)
        assertEquals("the Colosseum", colosseum.label)
        assertEquals("Where can I find a hospital in Rome?", VenueLookup.withoutLandmark("Where can I find a hospital near the Colosseum in Rome?", colosseum))
        val square = VenueLookup.landmark("cheap hotels close to Times Square")!!
        assertEquals("Times Square", square.label)
        assertNull(square.city)
        assertEquals("Palace of Westminster", VenueLookup.landmark("a pharmacy near the Palace of Westminster")?.name)
        assertNull(VenueLookup.landmark("pharmacy near me"))
        assertNull(VenueLookup.landmark("vegan restaurants in Berlin"))
        // "Around Italy" parses as a name, but no sight or station is called Italy, so nothing stands there (see below).
        val italy = VenueLookup.landmark("Travelling around Italy in mid-August. Will lots of shops and restaurants be shut for Ferragosto?")
        val trattoria = Evidence(documentId = "wv-place-i", chunkId = "wv-place-i:0000", title = "Leeds — Italy", source = "x",
            text = "Destination: Leeds\nCategory: Eat\nPlace: Italy\nLatitude: 53.8\nLongitude: -1.55", score = 0.0)
        assertNull(italy?.let { VenueLookup.landmarkPoint(it, listOf(trattoria)) })
    }

    @Test fun theLandmarkStandsWhereItsCitysOwnListingPutsIt() {
        val point = VenueLookup.landmarkPoint(VenueLookup.Landmark("Colosseum", "Rome"), listOf(tourCopy, colosseumHead, colosseumTail))
        assertEquals(GeoPoint(41.89059, 12.49156), point)
        // Two cities list an "Eiffel Tower" and none was named: no position, so the city lookup answers as before.
        val paris = guide("p", "Paris", "Eiffel Tower", "Latitude: 48.858", "Longitude: 2.2953")
        val kolkata = guide("k", "Kolkata/East", "Eiffel Tower", "Latitude: 22.59422", "Longitude: 88.46653")
        assertNull(VenueLookup.landmarkPoint(VenueLookup.Landmark("Eiffel Tower", null), listOf(paris, kolkata)))
        // A restaurant that shares a landmark's name is not where the landmark stands.
        val cafe = Evidence(documentId = "wv-place-c", chunkId = "wv-place-c:0000", title = "Rome — Colosseum", source = "x",
            text = "Destination: Rome\nCategory: Eat\nPlace: Colosseum\nLatitude: 41.95\nLongitude: 12.40", score = 0.0)
        assertEquals(GeoPoint(41.89059, 12.49156), VenueLookup.landmarkPoint(VenueLookup.Landmark("Colosseum", "Rome"),
            listOf(cafe, colosseumHead, colosseumTail)))
        // Paris, Texas has its own Eiffel Tower: another city, not a second Paris listing.
        val texas = guide("tx", "Paris (Texas)", "Eiffel Tower", "Latitude: 33.639867", "Longitude: -95.523867")
        assertEquals(GeoPoint(48.858, 2.2953), VenueLookup.landmarkPoint(VenueLookup.Landmark("Eiffel Tower", "Paris"), listOf(texas, paris)))
        // The same tower on an arrondissement page too: one place.
        val seventh = guide("p7", "Paris/7th arrondissement", "Eiffel Tower", "Latitude: 48.8584", "Longitude: 2.2945")
        assertEquals(GeoPoint(48.858, 2.2953), VenueLookup.landmarkPoint(VenueLookup.Landmark("Eiffel Tower", "Paris"), listOf(paris, seventh, kolkata)))
        assertEquals(GeoPoint(48.858, 2.2953), VenueLookup.landmarkPoint(VenueLookup.Landmark("Eiffel Tower", "Paris"), listOf(paris, kolkata)))
    }

    @Test fun aPlaceQuestionAroundALandmarkIsAnsweredFromItsPosition() = runBlocking {
        val near = hospital("1", "Ospedale San Giovanni")
        val far = hospital("2", "Aurelia Hospital")
        var askedAround: GeoPoint? = null
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) =
                listOf(tourCopy, colosseumHead, colosseumTail)
            override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>): List<NearbyPlace> {
                askedAround = point
                return listOf(NearbyPlace(far, 8.1), NearbyPlace(near, 1.3))
            }
        }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("unused")))
            .research("Where can I find a hospital near the Colosseum in Rome?").toList()
        assertEquals(GeoPoint(41.89059, 12.49156), askedAround)
        val answer = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertTrue(answer, answer.contains("Nearest saved places to the Colosseum in Rome"))
        assertTrue(answer, answer.contains("Ospedale San Giovanni"))
        // 8.1 km off is not near the Colosseum: past three times the nearest's distance and a 1.5 km walk.
        assertFalse(answer, answer.contains("Aurelia Hospital"))
        assertTrue(answer, answer.contains("straight-line from the Colosseum"))
        assertFalse(answer, answer.contains("this phone's location"))
    }

    @Test fun aListRequestWithItsKindFirstIsAPlaceLookup() {
        val veg = Evidence(documentId = "osm-place-9", chunkId = "osm-place-9:0000", title = "Berlin — Udonge Vegan",
            source = "https://www.openstreetmap.org/node/9", text = listOf("Destination: Berlin", "Category: Eat", "Place: Udonge Vegan",
                "Type: restaurant", "Vegan: fully vegan", "Latitude: 52.4", "Longitude: 13.05",
                "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"), score = 0.0)
        val answer = VenueLookup.answer("vegan restaurants in Berlin", listOf(veg))
        assertTrue(answer?.answer.orEmpty(), answer?.answer.orEmpty().contains("Udonge Vegan"))
    }
}
