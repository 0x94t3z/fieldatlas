package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.FtsRetriever
import xyz.fieldatlas.research.KnowledgeDatabase
import xyz.fieldatlas.research.MultiKnowledgeRetriever
import xyz.fieldatlas.research.search

/** Uses desktop SQLite only; checks that a place pack is searched within the destinations a question names. */
class DestinationScopeSearchTest {
    @get:Rule val temp = TemporaryFolder()

    /** A place pack as the Wikivoyage and OpenStreetMap builders write it: (document id, title, listing text). */
    private fun pack(name: String, rows: List<Triple<String, String, String>>): File {
        val file = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(file.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            rows.forEach { (id, title, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, "$id:0000"); it.bindText(2, id); it.bindText(3, title)
                    it.bindText(4, "https://example.org/$id"); it.bindText(5, text); it.step()
                }
            }
        }
        return file
    }

    private fun listing(destination: String, category: String, place: String, description: String) =
        "Destination: $destination\nCategory: $category\nPlace: $place\nDescription: $description\nHours in source: Daily 09:00-18:00"

    private fun beaches() = pack("beaches", listOf(
        Triple("wv-place-1", "Cape Town — Spar", listing("Cape Town", "Buy", "Spar", "Supermarket on Beach Road.")),
        Triple("wv-place-2", "Cape Town — Tide", listing("Cape Town", "Eat", "Tide", "Seafood restaurant. Best fish in town.")),
        Triple("wv-place-3", "Cape Town — Swimming", listing("Cape Town", "Do", "Swimming",
            "Beaches on the False Bay side are the most popular with swimmers as the water is warmer. Clovelly and Fish Hoek beaches wrap around a sheltered bay.")),
        Triple("wv-place-4", "Cape Cod — Golf", listing("Cape Cod", "Do", "Golf", "Swim at the beaches after a round.")),
        Triple("wv-place-5", "Gouda — Swim", listing("Gouda", "Do", "Swim", "Outdoor pool.")),
        Triple("wv-place-6", "Doha — Beaches", listing("Doha", "Do", "Beaches", "Public beaches where you can swim.")),
        Triple("wv-place-7", "Cape Town/Southern Suburbs — Muizenberg Beach", listing("Cape Town/Southern Suburbs", "Do", "Muizenberg Beach", "Safe swimming beach with surf schools.")),
        Triple("wv-place-8", "Nice — Plage", listing("Nice", "Do", "Plage", "Pebble beach; swimming is fine.")),
    ) + fillers.mapIndexed { index, (place, category, description) ->
        Triple("wv-place-${10 + index}", "Cape Town — $place", listing("Cape Town", category, place, description))
    })

    // The rest of a city's listings, so that "beach" and "swim" are rare words with a bm25 weight, as in a real pack.
    private val fillers = listOf(
        Triple("Rhonda's Manor", "Sleep", "Guest house in Durbanville with a garden."), Triple("Wine Concepts", "Buy", "Wine shop in Kloof Street."),
        Triple("Willoughby's", "Eat", "Sushi bar in the V&A Waterfront."), Triple("Big Blue Backpackers", "Sleep", "Hostel near the station."),
        Triple("Radisson Blu", "Sleep", "Hotel at the waterfront."), Triple("Kirstenbosch", "See", "Botanical garden on the mountain slopes."),
        Triple("Castle of Good Hope", "See", "Fort from the colonial era."), Triple("Greenmarket Square", "Buy", "Craft market in the city centre."),
        Triple("Mama Africa", "Eat", "Live music and game meat."), Triple("Table Mountain Cableway", "Do", "Cable car to the summit."),
        Triple("Long Street", "Drink", "Bars and nightlife."), Triple("Company's Garden", "See", "Park with a rose garden."),
    )

    @Test fun aPackHoldsADestinationOnlyWhenATitleStartsWithIt() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val database = KnowledgeDatabase.open(beaches())
        try {
            assertTrue(database.isPlaceListing())
            assertTrue(database.holdsDestination("Cape Town"))
            assertTrue(database.holdsDestination("cape town"))
            assertTrue(database.holdsDestination("Nice"))
            // "Cape" and "Town" begin titles only as parts of "Cape Town"; "Beaches" and "Swim" are place names.
            for (name in listOf("Cape", "Town", "Beaches", "Swim", "Best")) assertFalse(name, database.holdsDestination(name))
        } finally {
            database.close()
        }
    }

    @Test fun aPlacePackIsSearchedWithinTheDestinationNamedAndWithoutTitleBoosts() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val database = KnowledgeDatabase.open(beaches())
        try {
            val question = "Best beaches in Cape Town where you can actually swim?"
            val scoped = FtsRetriever(database).search(question, 10, listOf("Cape Town")) { }
            // Only Cape Town and its districts; the listing about swimming at the beaches leads.
            assertEquals("Cape Town — Swimming", scoped.first().title)
            assertTrue(scoped.map { it.title }.toString(), scoped.all { it.title.startsWith("Cape Town") })
            assertTrue("Cape Town/Southern Suburbs — Muizenberg Beach" in scoped.map { it.title })
            // Without a destination the whole pack is ranked by bm25 alone: the shortest titles
            // holding a question word ("Cape Cod — Golf", "Gouda — Swim") no longer lead it.
            val unscoped = FtsRetriever(database).search(question, 10).take(3).map { it.title }
            assertTrue(unscoped.toString(), "Cape Town — Swimming" in unscoped)
            assertTrue(unscoped.toString(), unscoped.none { it == "Cape Cod — Golf" || it == "Gouda — Swim" })
            // Too few listings in the destination (Gouda has one): the whole pack is searched instead.
            val gouda = FtsRetriever(database).search("Best beaches in Gouda where you can swim?", 10, listOf("Gouda")) { }
            assertTrue(gouda.size > 1)
            assertTrue("Gouda — Swim" in gouda.map { it.title })
        } finally {
            database.close()
        }
    }

    @Test fun theMergedSearchScopesEachPlacePackToTheQuestionsDestination() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        fun osm(id: String, destination: String, place: String, vegan: String) = Triple(
            "osm-place-$id", "$destination — $place",
            "Destination: $destination\nCategory: Eat\nPlace: $place\nType: restaurant\nVegan: $vegan\nHours in source: Mo-Su 12:00-22:00",
        )
        val places = pack("vegan", listOf(
            osm("n1", "Berlin", "Kaya's Best Döner", "vegan options"),
            osm("n2", "Berlin", "Best Grill", "vegan options"),
            osm("n3", "Berlin", "Daizu", "fully vegan"),
            osm("n4", "Berlin", "Bluhms", "fully vegan"),
            osm("n5", "Berlin", "Lori", "vegan options"),
            osm("n6", "Brno", "Best Kebap", "fully vegan"),
        ) + listOf("Nemo", "Erdbeere", "Crisp", "Porta Café", "Cinnamon Waffel", "Restaurant Ehrlich", "Annarkali", "Tribeca")
            .mapIndexed { index, place -> osm("n${10 + index}", "Berlin", place, "vegan options") })
        val reference = pack("reference", listOf(
            Triple("gr-1-0000", "Berlin — Overview", "Berlin is the capital of Germany. Many restaurants in Berlin serve vegan food."),
        ))
        val retriever = MultiKnowledgeRetriever(databaseFiles = { listOf(places, reference) })
        val question = "Best vegan restaurants in Berlin"
        val found = retriever.searchForQuestion(question, question, 10, {}, placesOnly = true)
        assertTrue(found.isNotEmpty())
        assertTrue(found.map { it.title }.toString(), found.none { it.title.startsWith("Brno") })
        // The fully-vegan follow-up query is scoped the same way, and the fully vegan places lead it.
        val fully = retriever.searchForQuestion("fully vegan Berlin restaurant", question, 10, {}, placesOnly = true)
        assertEquals(setOf("Berlin — Daizu", "Berlin — Bluhms"), fully.take(2).map { it.title }.toSet())
        assertTrue(fully.all { it.title.startsWith("Berlin") })
    }
}
