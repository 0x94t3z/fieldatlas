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

    @Test fun `venue answer distinguishes vegan options from vegan restaurants`() {
        val restaurant = listing("restaurant", "Berlin/Mitte", "Vedis", "Vegan Indian restaurant")
        val options = listing("options", "Berlin/Mitte", "Chay Viet",
            "Vegetarian Vietnamese family restaurant, many dishes are vegan")

        val answer = VenueLookup.answer("Best vegan restaurants in Berlin?", listOf(restaurant, options))!!.answer
        assertTrue(answer.contains("Some may offer dietary options rather than being entirely vegan"))
        assertTrue(answer.contains("Vegan Indian restaurant"))
        assertTrue(answer.contains("many dishes are vegan"))
        assertFalse(answer.contains("These vegan places"))
    }

    @Test fun `ordinary restaurant lookup is not restricted to vegan listings`() {
        val berlin = listing("berlin", "Berlin/Mitte", "Local Kitchen", "German dishes and soups")
        val hamburg = listing("hamburg", "Hamburg", "Harbor Cafe", "Cafe in Hamburg")

        val result = VenueLookup.answer("Which restaurants are in Berlin?", listOf(hamburg, berlin))
        assertEquals(listOf(berlin), result?.sources)
        assertTrue(result!!.answer.contains("unranked Wikivoyage listings for Berlin"))
        assertFalse(result.answer.contains("vegan food"))
    }

    @Test fun `specialized cuisine request never lists unrelated restaurants`() {
        val sushi = listing("sushi", "Berlin", "Sushi House", "Sushi and rice dishes")
        val unrelated = listing("stew", "Berlin", "Old Kitchen", "German stew and soups")

        val result = VenueLookup.answer("Best sushi restaurants in Berlin?", listOf(unrelated, sushi))
        assertEquals(listOf(sushi), result?.sources)
        assertNull(VenueLookup.answer("Best sushi restaurants in Berlin?", listOf(unrelated)))
    }

    @Test fun `new general travel pack supports dining without treating museums as restaurants`() {
        fun place(id: String, category: String, name: String) = Evidence(
            documentId = "wv-place-$id",
            chunkId = "wv-place-$id:0000",
            title = "Berlin — $name",
            source = "local Wikivoyage",
            text = "Destination: Berlin\nCategory: $category\nPlace: $name\n" +
                "Description: Local place\nSource page revision: 2026-09-20",
            score = -5.0,
        )
        val museum = place("museum", "See", "City Museum")
        val restaurant = place("dining", "Eat", "Local Kitchen")

        val result = VenueLookup.answer("Which restaurants are in Berlin?", listOf(museum, restaurant))
        assertEquals(listOf(restaurant), result?.sources)
        assertTrue(result!!.answer.contains("Listing check date not recorded"))
    }

    @Test fun `simple cafe lookup accepts drink listings without a best claim`() {
        val cafe = place("cafe", "Chiang Mai", "Old Town Cafe", "Coffee and pastries")
        val bar = place("bar", "Chiang Mai", "Night Bar", "Cocktails")
        val result = VenueLookup.answer("Which cafes are listed in Chiang Mai?", listOf(bar, cafe))
        assertEquals(listOf(cafe), result?.sources)
        assertFalse(result!!.answer.contains("best” ranking"))
    }

    @Test fun `simple museum and hotel lookups stay within their categories and named type`() {
        val museum = place("museum", "Berlin", "City Museum", "Local history")
        val monument = place("monument", "Berlin", "Old Gate", "Historic monument")
        val hotel = place("hotel", "Berlin", "City Hotel", "Rooms")
        val hostel = place("hostel", "Berlin", "Youth Hostel", "Shared dormitories")
        assertEquals(listOf(museum), VenueLookup.answer(
            "Which museums are listed in Berlin?", listOf(monument, museum, hotel))?.sources)
        assertEquals(listOf(hotel), VenueLookup.answer(
            "Which hotels are listed in Berlin?", listOf(hostel, hotel, museum))?.sources)
    }

    private fun place(id: String, destination: String, name: String, description: String, category: String =
        when {
            id.contains("cafe") || id.contains("bar") -> "Drink"
            id.contains("museum") || id.contains("monument") -> "See"
            id.contains("hotel") || id.contains("hostel") -> "Sleep"
            else -> "Eat"
        },
    ) = Evidence(
        documentId = "wv-place-$id", chunkId = "wv-place-$id:0000",
        title = "$destination — $name", source = "local Wikivoyage",
        text = "Destination: $destination\nCategory: $category\nPlace: $name\n" +
            "Description: $description\nSource page revision: 2026-09-20",
        score = -5.0,
    )

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

    @Test fun everydayPhrasingsGetTheListingsAndAPlaceInAnotherTownIsLeftOut() {
        fun osm(name: String, lat: Double, lon: Double, vegan: String = "fully vegan") = Evidence("osm-place-$name", "osm-place-$name:0",
            "Copenhagen — $name", "local", "Destination: Copenhagen\nCategory: Eat\nPlace: $name\nType: restaurant\nVegan: $vegan\n" +
                "Latitude: $lat\nLongitude: $lon\nMap data snapshot: 2026-10-02 (© OpenStreetMap contributors)", -1.0)
        val city = listOf(osm("Plant Power", 55.676, 12.568), osm("Green Bowl", 55.680, 12.575), osm("Root", 55.690, 12.560),
            osm("Malmo Cafe", 55.606, 13.002))
        val answer = VenueLookup.answer("I'm vegan and staying in Copenhagen for a week. Which restaurants should I try?", city)!!.answer
        assertTrue(answer, "Plant Power" in answer && "Green Bowl" in answer && "Root" in answer)
        assertTrue("a place 28 km away in Malmö is not in Copenhagen", "Malmo Cafe" !in answer)
        assertTrue(VenueLookup.answer("Where do vegans eat in Copenhagen?", city) != null)
    }

    @Test fun aRestaurantsBranchesAndDuplicateMapEntriesShowOnce() {
        assertEquals(VenueLookup.brand("Leaf House Vegan Restaurant"), VenueLookup.brand("Nhà Hàng Leaf House"))
        assertTrue(VenueLookup.brand("Leaf House") != VenueLookup.brand("Green Bowl"))
        fun osm(id: String, name: String, lat: Double) = Evidence("osm-place-$id", "osm-place-$id:0", "Avelton — $name", "local",
            "Destination: Avelton\nCategory: Eat\nPlace: $name\nType: restaurant\nVegan: fully vegan\nLatitude: $lat\nLongitude: 10.0\n" +
                "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)", -1.0)
        val city = listOf(osm("1", "Leaf House Vegan Restaurant", 50.001), osm("2", "Nhà Hàng Leaf House", 50.002),
            osm("3", "Leaf House Vegan Restaurant", 50.003), osm("4", "Green Bowl", 50.004))
        val answer = VenueLookup.answer("Which vegan restaurants should I try in Avelton?", city)!!.answer
        assertEquals(answer, 1, Regex("Leaf House").findAll(answer).count())
        assertTrue(answer, "Green Bowl" in answer)
    }
    private fun guide(page: Int, ordinal: Int, destination: String, category: String, name: String, description: String, hours: String? = null) = Evidence(
        documentId = "wv-place-$page-%04d".format(ordinal), chunkId = "wv-place-$page-%04d:0000".format(ordinal),
        title = "$destination — $name", source = "https://en.wikivoyage.org/w/index.php?oldid=$page",
        text = listOfNotNull("Destination: $destination", "Category: $category", "Place: $name", "Description: $description",
            hours?.let { "Hours in source: $it" }, "Source page revision: 2026-09-20").joinToString("\n"),
        score = -5.0,
    )

    @Test fun everyKindNamedCountsAndAFoodBarIsAPlaceToEat() {
        // "hostels or cheap hotels": both kinds, so a hostel is no longer dropped by the hotel test.
        val hostel = place("hostel", "Munich", "Jaegers Hostel", "Dormitories by the station")
        val hotel = place("hotel", "Munich", "City Hotel", "Rooms")
        val both = VenueLookup.answer("any hostels or cheap hotels in Munich?", listOf(hostel, hotel))!!
        assertEquals(setOf(hostel, hotel), both.sources.toSet())
        assertEquals(listOf(hostel), VenueLookup.answer("hostels in Munich", listOf(hostel, hotel))?.sources)
        // A milk bar is a cheap canteen, listed under Eat; a bar question alone still asks for Drink.
        val milkBar = guide(18228, 20, "Kraków/South", "Eat", "Bar Mleczny Południowy", "Budget \"milk bar\" or soup kitchen, cheap & cheerful.")
        val cocktails = place("bar", "Kraków", "Night Bar", "Cocktails and vodka")
        val question = "where can i get cheap traditional polish food in kraków? staying in kazimierz and heard the milk bars are the thing"
        assertTrue(VenueLookup.isPlaceLookup(question))
        val milk = VenueLookup.answer(question, listOf(cocktails, milkBar))!!
        assertEquals(listOf(milkBar), milk.sources)
        // The words no listing carries are stated instead of emptying the city.
        assertTrue(milk.answer, milk.answer.contains("None of these saved listings mentions “polish”, “kazimierz”; they are the places in Kraków that fit the rest of the question."))
        assertEquals(listOf(cocktails), VenueLookup.answer("Which bars are in Kraków?", listOf(cocktails, milkBar))?.sources)
        assertEquals(setOf("Eat"), VenueLookup.nearbyCategories("sushi bars near me"))
        assertEquals(setOf("Sleep"), VenueLookup.nearbyCategories("hostels or hotels near me"))
    }

    @Test fun aKindLookupSearchesTheCityForTheKindAndListsItInTheGuidesOrder() {
        val flamenco = guide(32267, 20, "Seville", "Do", "La Casa del Flamenco", "A good spot to see real flamenco.")
        val cathedral = guide(32267, 1, "Seville", "See", "Cathedral", "The largest Gothic cathedral in the world.")
        val alcazar = guide(32267, 2, "Seville", "See", "Real Alcázar", "Royal palace.")
        val plaza = guide(32267, 8, "Seville", "See", "Plaza de España", "Built for the 1929 exhibition.")
        val tapas = guide(32267, 40, "Seville", "Eat", "Bodega", "Tapas since 1900.")
        val question = "I've only got one full day in Seville. Which sights are must-sees, and in what order should I do them?"
        assertTrue(VenueLookup.isPlaceLookup(question))
        // The kind search names the destination the first pass found and asks for its See listings
        // by category line; the destination alone is the question the relevance gate judges by.
        assertEquals(VenueLookup.KindQuery("Seville see", "Seville"), VenueLookup.kindQuery(question, listOf(flamenco)))
        assertEquals(VenueLookup.KindQuery("Madrid museum", "Madrid"), VenueLookup.kindQuery("Which museums in Madrid are worth it?", listOf(guide(1, 1, "Madrid", "See", "Prado", "Art."))))
        assertNull(VenueLookup.kindQuery("Which vegan restaurants should I try in Seville?", listOf(tapas)))
        // A city the first pass found nothing in is still named when the packs resolved it.
        assertEquals(VenueLookup.KindQuery("lyon market halles", "lyon"), VenueLookup.kindQuery("good food markets in lyon?", emptyList(), listOf("lyon")))
        // Candidates are kept to the destination and categories asked, lower ordinal first.
        assertEquals(listOf(cathedral, alcazar, plaza, flamenco),
            VenueLookup.kindListings(question, listOf(tapas, plaza, flamenco, alcazar, cathedral, guide(99, 1, "Granada", "See", "Alhambra", "Palace.")), listOf(flamenco)))
        val result = VenueLookup.answer(question, listOf(flamenco, plaza, alcazar, cathedral, tapas))!!
        assertEquals(listOf(cathedral, alcazar, plaza, flamenco), result.sources)
        assertTrue(result.answer, result.answer.startsWith("These Wikivoyage listings for Seville, in the guide's order:"))
        assertFalse(result.answer, result.answer.contains("None of these saved listings mentions"))
    }

    @Test fun indoorAndChildrenWordsRankSoftlyForAThingsToDoQuestion() {
        val aquarium = guide(8183, 1, "Copenhagen/Amager", "See", "Den Blå Planet", "Largest aquarium in Northern Europe with 20,000 fish.")
        val fortress = guide(8184, 4, "Copenhagen/Østerbro", "See", "Kastellet", "A star-shaped citadel; stroll the ramparts.")
        val museum = guide(8189, 12, "Copenhagen/Indre By", "See", "Nationalmuseet", "National history museum with a children's wing.")
        val park = guide(8189, 32, "Copenhagen/Indre By", "Do", "Kongens Have", "The most frequented park in the city.")
        val question = "We're in Copenhagen with two kids (6 and 9) and it's meant to rain all day tomorrow. What can we do indoors?"
        assertTrue(VenueLookup.isPlaceLookup(question))
        assertFalse("an activity question without a named place is not a lookup", VenueLookup.isPlaceLookup("We are lost in a forest and it is getting dark. What should we do?"))
        assertFalse(VenueLookup.isPlaceLookup("what can we do about a leaking gas heater in the cabin?"))
        val kind = VenueLookup.kindQuery(question, listOf(park))!!
        assertEquals("Copenhagen", kind.scope)
        assertTrue(kind.query, kind.query.startsWith("Copenhagen see ") && "aquarium" in kind.query && "children" in kind.query)
        // Both attributes beat one, one beats none; within a rank the guide's order holds.
        val result = VenueLookup.answer(question, listOf(park, fortress, museum, aquarium))!!
        assertEquals(listOf(museum, aquarium, fortress, park), result.sources)
        assertTrue(result.answer, result.answer.startsWith("These Wikivoyage listings for Copenhagen, in the guide's order, those described as indoor and children first:"))
        assertFalse(result.answer, result.answer.contains("None of these saved listings mentions"))
    }

    @Test fun marketsOpenOnANamedDayAreAnsweredFromTheRecordedHours() {
        val halles = guide(20606, 91, "Lyon", "Buy", "Halles de Lyon Paul Bocuse", "The very best food.", "Tu-Sa 07:00-12:00 and 15:00-19:00, Su 07:00-12:00")
        val croixRousse = guide(20606, 92, "Lyon", "Buy", "Croix-Rousse market", "Very popular market with local producers.", "Tu-Su 07:00-13:00")
        val mall = guide(20606, 84, "Lyon", "Buy", "La Part-Dieu", "A huge shopping mall.", "M-Sa 10:00-20:00")
        val wine = guide(20606, 95, "Lyon", "Buy", "Antic Wine", "Wines from all over the world.", "Tu-Su 11:00-20:00")
        val question = "good food markets in lyon? want to put together a picnic with cheese, bread and saucisson. is anything open on sunday morning?"
        assertTrue(VenueLookup.isPlaceLookup(question))
        val superHalle = Evidence("osm-place-n7", "osm-place-n7:0000", "Lyon — La Super Halle", "https://www.openstreetmap.org/node/7",
            "Destination: Lyon\nCategory: Buy\nPlace: La Super Halle\nType: supermarket\nHours in source: Mo-Sa 10:00-19:00\nLatitude: 45.76\nLongitude: 4.83", 0.0)
        val result = VenueLookup.answer(question, listOf(mall, wine, superHalle, halles, croixRousse))!!
        // Only the markets; the mall, the wine shop and a supermarket called Halle are not what was asked.
        assertEquals(listOf(halles, croixRousse), result.sources)
        assertTrue(result.answer, result.answer.startsWith("All 2 places below are open on Sunday morning by their recorded hours."))
        assertTrue(result.answer, result.answer.contains("**Halles de Lyon Paul Bocuse** — The very best food. **Open Sunday morning**. Hours in source: Tu-Sa 07:00-12:00 and 15:00-19:00, Su 07:00-12:00."))
        assertTrue(result.answer, result.answer.contains("None of these saved listings mentions “picnic”, “cheese”, “bread”, “saucisson”"))
        // A market closed that day says so, and a day question lists the open ones first.
        val sundayOff = guide(20606, 93, "Lyon", "Buy", "St Antoine Market", "The other major market.", "M-Sa 07:00-13:00")
        val mixed = VenueLookup.answer("which markets in Lyon are open on Sundays?", listOf(sundayOff, croixRousse))!!
        assertEquals(listOf(croixRousse, sundayOff), mixed.sources)
        assertTrue(mixed.answer, mixed.answer.startsWith("1 of the 2 places below is open on Sunday by their recorded hours; those come first."))
        assertTrue(mixed.answer, mixed.answer.contains("**Closed Sunday**."))
    }

    @Test fun guideHoursAreReadInTheMapsForm() {
        assertEquals("11:00-22:00", VenueLookup.guideHours("Daily 11:00-22:00"))
        assertEquals("Mo-Fr 07:00-18:00, Sa,Su 07:00-15:00", VenueLookup.guideHours("M-F 07:00-18:00, Sa Su 07:00-15:00"))
        assertEquals("Tu-Sa 07:00-12:00, 15:00-19:00, Su 07:00-12:00", VenueLookup.guideHours("Tu-Sa 07:00-12:00 and 15:00-19:00, Su 07:00-12:00"))
        assertEquals("07:00-15:30", VenueLookup.guideHours("7AM-3:30PM"))
        assertEquals("Mo-Fr 9:00-17:00", VenueLookup.guideHours("Mon-Fri 9:00-17:00"))
        assertEquals("Mo-Su 10:00-19:00", VenueLookup.guideHours("Mo-Su 10:00-19:00"))
    }

    @Test fun aDecisionBetweenWaysToPayIsNoPlaceLookup() {
        assertFalse(VenueLookup.isPlaceLookup("Should I bring euros to change in Morocco or just take out dirhams from an ATM when I land?"))
        assertTrue(VenueLookup.isPlaceLookup("Where can I find an ATM near the station in Fez?"))
        assertTrue(VenueLookup.isPlaceLookup("Find an ATM near me"))
    }

    @Test fun businessesAreTheLookupsAModelMayNotNameFromMemory() {
        // Sights and things to do are well known for years; a model's answer may name them.
        val iceland = "Im going to Iceland in December for a week and want to rent a car. What are the must-see things and what should I watch out for driving there in winter?"
        assertTrue(VenueLookup.isPlaceLookup(iceland))
        assertFalse(VenueLookup.asksForBusinesses(iceland))
        assertFalse(VenueLookup.asksForBusinesses("What are the best museums in Vienna?"))
        // Where to eat, sleep, drink or shop, and everyday essentials, change: only saved listings answer them.
        assertTrue(VenueLookup.asksForBusinesses("Any good vegan restaurants in Valencia?"))
        assertTrue(VenueLookup.asksForBusinesses("Which hostels in Porto are near the river?"))
        assertTrue(VenueLookup.asksForBusinesses("Where can I find an ATM near the station in Fez?"))
        assertTrue(VenueLookup.asksForBusinesses("Where is a good sushi bar in Lima?"))
        assertFalse(VenueLookup.asksForBusinesses("Why do onions make you cry?"))
    }
}
