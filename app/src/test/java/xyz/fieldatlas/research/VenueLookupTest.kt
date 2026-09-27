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
}
