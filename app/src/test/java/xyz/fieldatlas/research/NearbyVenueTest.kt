package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
            "vegan restaurants near me",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(iceCream, 0.2), NearbyPlace(farFully, 8.4),
                NearbyPlace(nearFully, 0.8)),
            15.0,
        )
        // Within 1 km: fully vegan first, then options; the 8.4 km fully vegan place comes last.
        assertEquals(listOf(nearFully, corner, farFully), result.sources)
        val answer = result.answer
        assertTrue(answer, answer.contains("- **Leaf Bowl** — fully vegan restaurant, 800 m away; thai. Coordinates: 52.50000, 13.40000."))
        assertTrue(answer.contains("- **Corner Curry** — vegan options restaurant, 350 m away"))
        assertTrue(answer.contains("8.4 km away"))
        assertTrue(answer.contains("(map data as of 2026-10-02)"))
        assertTrue(answer.contains("location, which stayed on the phone"))
        assertFalse(answer.contains("Cold Cone"))
    }

    private fun guide(id: String, name: String, description: String) = Evidence(
        documentId = "wv-place-$id", chunkId = "wv-place-$id:0000", title = "Berlin — $name",
        source = "https://en.wikivoyage.org/w/index.php?oldid=$id",
        text = "Destination: Berlin\nCategory: Eat\nPlace: $name\nDescription: $description", score = 0.0,
    )

    @Test fun theBestAroundThePhoneAreTheCitysBestNotTheClosest() {
        val chain = (5..7).map { place("$it", "Vegan Burger Co", "fully vegan", type = "fast food") }
        val favourite = guide("9", "Far Garden", "A local favourite for green curry and noodles.")
        val result = VenueLookup.nearbyAnswer(
            "Tell me the best vegan restaurants in the city I am currently in",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(iceCream, 0.2), NearbyPlace(nearFully, 0.8),
                NearbyPlace(farFully, 8.4), NearbyPlace(favourite, 8.5)) + chain.mapIndexed { i, it -> NearbyPlace(it, 0.1 + i) },
            15.0,
        )
        // Fully vegan first; within that, the place the city's guide recommends, then a sit-down
        // restaurant before a fast-food chain; the closest "vegan options" place comes after them.
        assertEquals(listOf(farFully, nearFully, chain.first(), corner, favourite), result.sources)
        val answer = result.answer
        assertTrue(answer, answer.startsWith("Near you in **Berlin**. There are no ratings offline"))
        // A mapped place without an address gives its coordinates, as the city lookups do.
        assertTrue(answer, answer.contains("- **Far Garden** — fully vegan restaurant; thai; 8.4 km away. Coordinates: 52.50000, 13.40000. [S1] " +
            "The Wikivoyage guide: “A local favourite for green curry and noodles.” [S5]"))
        assertFalse("one entry per chain", answer.indexOf("Vegan Burger Co") != answer.lastIndexOf("Vegan Burger Co"))
        assertFalse(answer.contains("Cold Cone"))
        assertTrue(answer.contains("location, which stayed on the phone"))
    }

    @Test fun anOldFixIsUsedOnlyWithItsAge() {
        val fresh = VenueLookup.nearbyAnswer("best vegan restaurants near me", listOf(NearbyPlace(nearFully, 0.8)), 15.0)
        assertFalse(fresh.answer.contains("last known location"))
        val old = VenueLookup.nearbyAnswer("best vegan restaurants near me", listOf(NearbyPlace(nearFully, 0.8)), 15.0, fixAgeMinutes = 95)
        assertTrue(old.answer, old.answer.startsWith("Using this phone's last known location, from 2 hours ago"))
        // A plain list of the nearest places says so too.
        val list = VenueLookup.nearbyAnswer("vegan restaurants near me", listOf(NearbyPlace(nearFully, 0.8)), 15.0, fixAgeMinutes = 30)
        assertTrue(list.answer, list.answer.startsWith("Using this phone's last known location, from 30 minutes ago"))
    }

    @Test fun onlyAQualityQuestionRanksTheCity() {
        assertTrue(VenueLookup.asksBest("Tell me the best vegan restaurants in the city I am currently in"))
        assertTrue(VenueLookup.asksBest("Can you recommend vegan food near me?"))
        assertFalse(VenueLookup.asksBest("nearest vegan restaurant, the best you can find"))
        assertFalse(VenueLookup.asksBest("vegan restaurants near me"))
        assertFalse(VenueLookup.asksBest("best pharmacy near me"))
    }

    @Test fun cuisineWordsStillFilterButLocationWordsDoNot() {
        val sushi = place("5", "Roll Haus", "vegan options", cuisine = "sushi")
        val result = VenueLookup.nearbyAnswer("vegan sushi near me", listOf(NearbyPlace(corner, 0.1), NearbyPlace(sushi, 2.0)), 15.0)
        assertEquals(listOf(sushi), result.sources)
    }

    @Test fun walkingWordsAskAboutThePhonesPosition() {
        assertTrue(VenueLookup.isNearMe("need sunscreen and some painkillers, is there a pharmacy or drugstore I can walk to from here?"))
        assertTrue(VenueLookup.isNearMe("it's pouring with rain. any museum or gallery within walking distance of me?"))
        // Walking distance of a named place is that place, not the phone's position.
        assertFalse(VenueLookup.isNearMe("any good museums within walking distance of the Louvre in Paris?"))
    }

    @Test fun sightsNearMeAreAskedOfTheCityThePhoneIsIn() {
        assertTrue(VenueLookup.asksGuideKind("any museum or gallery within walking distance of me?"))
        assertFalse(VenueLookup.asksGuideKind("is there a pharmacy I can walk to from here?"))
        assertEquals("it's pouring with rain. any museum or gallery in Stockholm?",
            VenueLookup.inCity("it's pouring with rain. any museum or gallery within walking distance of me?", "Stockholm"))
        assertEquals("museums in Lyon?", VenueLookup.inCity("museums near me?", "Lyon"))
        assertFalse(VenueLookup.isNearMe(VenueLookup.inCity("any museum within walking distance of me?", "Stockholm")))
    }

    @Test fun tripWordsAndTimesAreNotReportedAsMissingFromListings() {
        // "It's 11pm and I'm starving": no listing is found by a time, so none is reported missing.
        val late = VenueLookup.nearbyAnswer("It's 11pm and I'm starving. Anywhere still serving food near where I am?",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(nearFully, 0.8)), 15.0)
        assertFalse(late.answer, late.answer.contains("None of these saved listings mentions"))
    }

    @Test fun wordsNoListingCarriesDoNotEmptyTheArea() {
        // "hungry, any good local places that aren't too expensive?": no listing says "hungry" or
        // "expensive", so the places nearby that fit the rest are listed and the gap is stated.
        val result = VenueLookup.nearbyAnswer("hungry. any good local places to eat around here that aren't too expensive?",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(nearFully, 0.8)), 15.0)
        // "good" ranks the city's places, and without a diet word the closer restaurant leads.
        assertEquals(listOf(corner, nearFully), result.sources)
        assertFalse(result.answer, result.answer.contains("fit the rest of the question"))
        // A word no listing carries at all is reported rather than emptying the area.
        val rooftop = VenueLookup.nearbyAnswer("vegan places with a rooftop near me", listOf(NearbyPlace(corner, 0.35), NearbyPlace(nearFully, 0.8)), 15.0)
        assertEquals(listOf(nearFully, corner), rooftop.sources)
        assertTrue(rooftop.answer, rooftop.answer.contains("None of these saved listings mentions “rooftop”; they are the places nearby that fit the rest of the question."))
        // A cuisine some listing carries still filters (see cuisineWordsStillFilterButLocationWordsDoNot).
        val sushi = place("5", "Roll Haus", "vegan options", cuisine = "sushi")
        assertEquals(listOf(sushi), VenueLookup.nearbyAnswer("vegan sushi near me", listOf(NearbyPlace(corner, 0.1), NearbyPlace(sushi, 2.0)), 15.0).sources)
    }

    private fun atm(id: String, name: String) = Evidence(
        documentId = "osm-place-$id", chunkId = "osm-place-$id:0000", title = "Rome — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = listOf("Destination: Rome", "Category: Money", "Place: $name", "Type: ATM", "Latitude: 41.9", "Longitude: 12.5",
            "Map data snapshot: 2026-10-05 (© OpenStreetMap contributors)").joinToString("\n"),
        score = 0.0,
    )

    @Test fun anExcludedBrandIsLeftOutAndABankIsPreferred() {
        val euronet = atm("11", "ATM · Euronet")
        val worldwide = atm("12", "ATM · Euronet Worldwide")
        val bank = atm("13", "ATM · Banca Intesa")
        val plain = atm("14", "ATM")
        val result = VenueLookup.nearbyAnswer("where's the closest ATM? ideally a proper bank one, not Euronet",
            listOf(NearbyPlace(euronet, 0.05), NearbyPlace(worldwide, 0.1), NearbyPlace(plain, 0.2), NearbyPlace(bank, 0.4)), 5.0)
        // Within the same distance band the bank's machine leads; both Euronet entries are left out.
        assertEquals(listOf(bank, plain), result.sources)
        assertTrue(result.answer, result.answer.contains("Coordinates: 41.90000, 12.50000"))
        // "not too expensive" names no brand and excludes nothing.
        assertEquals(2, VenueLookup.nearbyAnswer("ATM near me, not too expensive", listOf(NearbyPlace(euronet, 0.05), NearbyPlace(bank, 0.4)), 5.0).sources.size)
    }

    @Test fun theBestOrderClaimsTheGuideOnlyWhenAGuideListingIsThere() {
        val result = VenueLookup.nearbyAnswer("Tell me the best vegan restaurants in the city I am currently in",
            listOf(NearbyPlace(corner, 0.35), NearbyPlace(nearFully, 0.8)), 15.0)
        assertFalse(result.answer, result.answer.contains("Wikivoyage guide also recommends"))
        assertTrue(result.answer, result.answer.contains("fully vegan places first, then sit-down restaurants before cafés and fast food"))
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
            override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>): List<NearbyPlace> {
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

    private fun sleep(id: String, name: String, type: String) = Evidence(
        documentId = "osm-place-$id", chunkId = "osm-place-$id:0000", title = "Munich — $name",
        source = "https://www.openstreetmap.org/node/$id",
        text = listOf("Destination: Munich", "Category: Sleep", "Place: $name", "Type: $type", "Latitude: 48.1", "Longitude: 11.5",
            "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"),
        score = 0.0,
    )

    @Test fun hostelsOrHotelsNearMeKeepsBothKinds() {
        val hostel = sleep("21", "Jaegers Hostel", "hostel")
        val hotel = sleep("22", "City Hotel", "hotel")
        val question = "missed the last train out. any hostels or cheap hotels i can walk to from where i am?"
        assertTrue(VenueLookup.isNearMe(question))
        assertEquals(setOf("Sleep"), VenueLookup.nearbyCategories(question))
        val result = VenueLookup.nearbyAnswer(question, listOf(NearbyPlace(hostel, 1.1), NearbyPlace(hotel, 0.4)), 5.0)
        // "cheap" puts the hostel first although the hotel is closer; without it the nearer place leads.
        assertEquals(listOf(hostel, hotel), result.sources)
        assertEquals(listOf(hotel, hostel), VenueLookup.nearbyAnswer("hostels or hotels near me", listOf(NearbyPlace(hostel, 1.1), NearbyPlace(hotel, 0.4)), 5.0).sources)
        // "walk" is how to get there, not a word a listing must carry.
        assertTrue(result.answer, result.answer.contains("None of these saved listings mentions “missed”, “train”, “out”"))
        assertFalse(result.answer, result.answer.contains("“walk”"))
    }

    private fun athens(id: String, name: String, type: String, cuisine: String?, vegan: String?, km: Double) = NearbyPlace(Evidence(
        documentId = "osm-place-$id", chunkId = "osm-place-$id:0000", title = "Athens — $name", source = "https://www.openstreetmap.org/node/$id",
        text = listOfNotNull("Destination: Athens", "Category: Eat", "Place: $name", "Type: $type", vegan?.let { "Vegan: $it" }, cuisine?.let { "Cuisine: $it" },
            "Latitude: 37.97", "Longitude: 23.73", "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"), 0.0), km)

    private fun taverna(ordinal: Int, name: String, description: String, km: Double) = NearbyPlace(Evidence(
        documentId = "wv-place-1808-%04d".format(ordinal), chunkId = "wv-place-1808-%04d:0000".format(ordinal), title = "Athens — $name",
        source = "https://en.wikivoyage.org/w/index.php?oldid=1808",
        text = "Destination: Athens\nCategory: Eat\nPlace: $name\nDescription: $description\nLatitude: 37.97\nLongitude: 23.73\nSource page revision: 2026-08-01", 0.0), km)

    @Test fun aDishNamesItsKitchenAndGuideListingsJoinTheMapsPlaces() {
        val burgers = athens("31", "Goody's Burger House", "fast food", null, "vegan options", 0.08)
        val indian = athens("32", "Babaji", "restaurant", "indian", "vegan options", 0.15)
        val greek = athens("33", "W", "restaurant", "greek", "vegan options", 0.38)
        val kostas = taverna(69, "Σουβλάκι Κώστας 1950 (Kostas)", "It's authentic, friendly and fast.", 0.26)
        val traditional = taverna(72, "The Traditional", "Traditional Greek food in a village tavern.", 0.58)
        val question = "Starving. Where can I get a proper souvlaki or a decent local meal near here that isn't a tourist trap?"
        assertTrue(VenueLookup.isNearMe(question))
        val result = VenueLookup.nearbyAnswer(question, listOf(burgers, indian, greek, kostas, traditional), 2.0)
        // Greek kitchens and the souvlaki stand, nearest first; the burger chain and the Indian place are not souvlaki.
        assertEquals(listOf(kostas.evidence, greek.evidence, traditional.evidence), result.sources)
        assertFalse(result.answer, result.answer.contains("None of these saved listings mentions"))
        assertTrue(result.answer, result.answer.contains("- **Σουβλάκι Κώστας 1950 (Kostas)** — listed in the Wikivoyage guide, 260 m away. “It's authentic, friendly and fast.” Coordinates: 37.97000, 23.73000."))
        assertTrue(result.answer, result.answer.contains("Map data © OpenStreetMap contributors (ODbL). Guide listings are from the saved Wikivoyage guide."))
        // Only the vegan-tagged map collection answered a plain food question: the answer says so.
        val veganOnly = VenueLookup.nearbyAnswer("where can I get a decent local meal near here?", listOf(burgers, indian, greek), 2.0)
        assertTrue(veganOnly.answer, veganOnly.answer.contains("the saved map collection holds only those tagged for vegan food"))
        assertFalse(result.answer.contains("holds only those tagged"))
        assertFalse(VenueLookup.nearbyAnswer("vegan food near me", listOf(burgers, indian), 2.0).answer.contains("holds only those tagged"))
    }

    @Test fun distanceLabelsAndHaversine() {
        assertEquals("350 m", GeoDistance.label(0.354))
        assertEquals("1.2 km", GeoDistance.label(1.234))
        assertEquals("12 km", GeoDistance.label(12.7))
        // Berlin Alexanderplatz to Brandenburg Gate is about 2.6 km.
        assertEquals(2.6, GeoDistance.km(52.5219, 13.4132, 52.5163, 13.3777), 0.15)
    }


    @Test fun whereAmINamesTheTownFromTheNearestAddresses() {
        fun mapped(id: String, name: String, type: String, address: String?, km: Double, bearing: Double) = NearbyPlace(
            Evidence("osm-place-$id", "osm-place-$id:0", "Amsterdam — $name", "https://www.openstreetmap.org/node/$id",
                listOfNotNull("Destination: Amsterdam", "Category: Buy", "Place: $name", "Type: $type", address?.let { "Address: $it" },
                    "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"), 0.0), km, bearing)
        val places = listOf(
            mapped("1", "SPAR Poliste", "supermarket", "Verlengde Voorstraat 56, 1949CN Wijk aan Zee", 0.12, 0.0),
            mapped("2", "Toilets", "toilets", null, 0.3, 90.0),
            mapped("3", "De Zon", "pharmacy", "Relweg 2, 1949EC Wijk aan Zee", 0.4, 180.0),
            mapped("4", "Station", "bus station", "Stationsplein 1, 1976 IJmuiden", 4.0, 200.0),
        )
        assertTrue(VenueLookup.isWhereAmI("Where am I?"))
        assertTrue(VenueLookup.isWhereAmI("what city am I in right now"))
        assertFalse(VenueLookup.isWhereAmI("Where am I supposed to buy a metro ticket in Paris?"))
        val answer = VenueLookup.whereAnswer(GeoPoint(52.4936, 4.5973), places)
        assertTrue(answer.answer, answer.answer.startsWith("You're in **Wijk aan Zee**"))
        assertTrue(answer.answer, "The travel guides cover this area under **Amsterdam**." in answer.answer)
        assertTrue(answer.answer, "52.49360° N, 4.59730° E" in answer.answer)
        assertTrue(answer.answer, "- **SPAR Poliste** (supermarket), 120 m to the north. [S1]" in answer.answer)
        assertEquals(3, answer.sources.size)
        assertEquals("Wijk aan Zee", VenueLookup.townOf("Verlengde Voorstraat 56, 1949CN Wijk aan Zee"))
        assertEquals("London", VenueLookup.townOf("10 Downing Street, SW1A 2AA London"))
        assertNull(VenueLookup.townOf("Hoofddorpplein 6H"))
        // Without any mapped places the position is still given.
        val bare = VenueLookup.whereAnswer(GeoPoint(-33.9, 151.2), emptyList())
        assertTrue(bare.answer, "33.90000° S, 151.20000° E" in bare.answer && bare.sources.isEmpty())
    }
}
