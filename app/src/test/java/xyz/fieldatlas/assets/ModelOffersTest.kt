package xyz.fieldatlas.assets

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOffersTest {
    /** Each download is the exact file its registry definition pins for the pack builder. */
    @Test fun everyOfferMatchesItsRegistryFile() {
        for ((offer, file) in listOf(
            ModelOffers.recommended to "compact-qwen3.5-2b.example.json",
            ModelOffers.larger to "compact-qwen3.5-4b.example.json",
        )) {
            val spec = Json.parseToJsonElement(File("../models/$file").readText()).jsonObject
            val text = { key: String -> spec.getValue(key).jsonPrimitive.content }
            assertEquals(text("id"), offer.id)
            assertEquals(text("version"), offer.version)
            assertEquals(text("title"), offer.title)
            assertEquals(text("license"), offer.license)
            assertEquals(text("expectedSha256"), offer.sha256)
            val pinned = spec.getValue("sourceUrls").jsonArray.map { it.jsonPrimitive.content }.single { "/blob/" in it }
            assertEquals(pinned.replace("/blob/", "/resolve/"), offer.url)
        }
    }

    @Test fun theLargerModelIsOfferedFromEightGigabytePhones() {
        // MemTotal as reported by a Redmi 13C (6 GB), where Android killed the app just after the
        // 4B loaded, and typical 8 GB and 4 GB phones.
        val sixGb = 5_867_976L * 1024
        val eightGb = 7_500_000_000L
        val fourGb = 3_790_000_000L
        assertTrue(ModelOffers.larger.fits(eightGb))
        assertFalse(ModelOffers.larger.fits(sixGb))
        assertFalse(ModelOffers.larger.fits(fourGb))
        assertTrue(ModelOffers.recommended.fits(fourGb))
        assertTrue(ModelOffers.recommended.fits(0))
    }

    @Test fun offersAreFoundById() {
        assertEquals(ModelOffers.larger, ModelOffers.byId("qwen3.5-4b-q4-k-m"))
        assertNull(ModelOffers.byId("minicpm5-1b-q4-k-m"))
        assertEquals(ModelOffers.all.size, ModelOffers.all.map { it.id }.distinct().size)
    }
}
