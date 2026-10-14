package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in: FIELDATLAS_PLACE_PACK is a dual-search place pack built by index_pack_for_leviathan.py
 * (the OpenStreetMap vegan places), FIELDATLAS_TEXT_PACK a dual-search pack without the places
 * filter; the native library must be on java.library.path.
 */
class LeviathanPlaceFilterTest {
    private fun pack(name: String): File? = System.getenv(name)?.let(::File)?.takeIf(File::isFile)

    @Test fun placeSearchStaysInTheNamedCity() = runBlocking {
        val file = pack("FIELDATLAS_PLACE_PACK")
        assumeTrue(file != null && LeviathanNative.available)
        KnowledgeDatabase.open(file!!).use { db ->
            val lisbon = db.leviathanCandidates("bakery", 20, listOf("lisbon"))!!
            assertTrue(lisbon.isNotEmpty())
            assertTrue(lisbon.all { DestinationScope.scopes(it.title, listOf("Lisbon")) })
            val whole = db.leviathanCandidates("bakery", 20)!!
            assertTrue(whole.any { !DestinationScope.scopes(it.title, listOf("Lisbon")) })
        }
        MultiKnowledgeRetriever({ listOf(file) }).use { retriever ->
            val rows = retriever.searchForQuestion("vegan bakery lisbon", "Where can I get a vegan pastry in Lisbon?", 8, { }, placesOnly = true)
            assertTrue(rows.isNotEmpty())
            assertTrue(rows.all { it.matchedBy?.contains("Leviathan") == true })
            assertTrue(rows.all { DestinationScope.scopes(it.title, listOf("Lisbon")) })
        }
    }

    @Test fun packWithoutThePlaceFilterKeepsItsKeywordSearch() = runBlocking {
        val file = pack("FIELDATLAS_TEXT_PACK")
        assumeTrue(file != null && LeviathanNative.available)
        KnowledgeDatabase.open(file!!).use { db ->
            assertNull(db.leviathanCandidates("blob", 5, listOf("lisbon")))
            // Asking for a filter the pack lacks must not close its native search.
            assertEquals(5, db.leviathanCandidates("blob", 5)!!.size)
        }
    }
}
