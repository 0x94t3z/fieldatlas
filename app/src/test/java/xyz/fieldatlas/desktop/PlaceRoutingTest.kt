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

/** Uses desktop SQLite only; checks place routing and that parallel pack search merges as before. */
class PlaceRoutingTest {
    @get:Rule val temp = TemporaryFolder()

    private fun pack(name: String, rows: List<Pair<String, String>>): File {
        val file = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(file.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            rows.forEach { (id, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, "$id:0000"); it.bindText(2, id); it.bindText(3, if (id.startsWith("osm-")) "Berlin — " + text.lines()[2].removePrefix("Place: ") else text.substringBefore('.'))
                    it.bindText(4, "https://example.org/$id"); it.bindText(5, text); it.step()
                }
            }
        }
        return file
    }

    private fun packs(): List<File> = listOf(
        pack("places", listOf(
            "osm-place-n1" to "Destination: Berlin\nCategory: Eat\nPlace: Leaf Bowl\nType: restaurant\nVegan: fully vegan",
            "osm-place-n2" to "Destination: Berlin\nCategory: Eat\nPlace: Corner Curry\nType: restaurant\nVegan: vegan options",
        )),
        pack("reference", listOf(
            "gr-1-0000" to "Berlin is the capital of Germany. Many restaurants in Berlin serve vegan food.",
            "gr-2-0000" to "A restaurant is a business that prepares and serves food.",
        )),
        pack("science", listOf(
            "bio-1" to "Vegan diets and restaurant meals were compared in a Berlin cohort study.",
        )),
    )

    @Test fun placePacksAreRecognisedFromTheirRows() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val (places, reference, science) = packs().map(KnowledgeDatabase::open)
        try {
            assertTrue(places.isPlaceListing())
            assertFalse(reference.isPlaceListing())
            assertFalse(science.isPlaceListing())
        } finally { listOf(places, reference, science).forEach { it.close() } }
    }

    @Test fun placeLookupsSearchOnlyPlacePacks() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val files = packs()
        val retriever = MultiKnowledgeRetriever(databaseFiles = { files })
        val question = "best vegan restaurants in Berlin"
        val places = retriever.searchForQuestion(question, question, 10, {}, placesOnly = true)
        val everything = retriever.searchForQuestion(question, question, 10, {})
        assertTrue(places.isNotEmpty())
        assertTrue(places.all { it.documentId.startsWith("osm-place-") })
        assertTrue(everything.any { !it.documentId.startsWith("osm-place-") })
    }

    @Test fun withoutAPlacePackEveryPackIsStillSearched() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val files = packs().drop(1)
        val retriever = MultiKnowledgeRetriever(databaseFiles = { files })
        val question = "best vegan restaurants in Berlin"
        assertTrue(retriever.searchForQuestion(question, question, 10, {}, placesOnly = true).isNotEmpty())
    }

    @Test fun parallelSearchMergesExactlyLikeSearchingEachPackInTurn() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val files = packs()
        val progress = mutableListOf<Double>()
        for (question in listOf("best vegan restaurants in Berlin", "restaurant food", "Berlin capital", "cohort study")) {
            val expected = MultiKnowledgeRetriever.mergeRelevantEvidence(
                files.map { file -> KnowledgeDatabase.open(file).use { db -> FtsRetriever(db).search(question, 10) {} } },
                question, question, 10,
            )
            progress.clear()
            val actual = MultiKnowledgeRetriever(databaseFiles = { files })
                .searchForQuestion(question, question, 10, { progress += it.fraction })
            assertEquals(question, expected.map { it.chunkId }, actual.map { it.chunkId })
            assertEquals(question, progress.sorted(), progress)
            assertEquals(1.0, progress.last(), 1e-9)
        }
    }
}
