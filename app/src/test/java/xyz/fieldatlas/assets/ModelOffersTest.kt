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
            ModelOffers.gemma to "compact-gemma-4-e2b.example.json",
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

    @Test fun gemmaIsOfferedFromSixGigabytePhones() {
        // MemTotal as reported by a Redmi 13C (6 GB), where llama-bench ran Gemma 4 E2B at 2.4 GB resident,
        // and a typical 4 GB phone, which has not run it yet.
        val sixGb = 5_867_976L * 1024
        val fourGb = 3_790_000_000L
        assertTrue(ModelOffers.gemma.fits(sixGb))
        assertFalse(ModelOffers.gemma.fits(fourGb))
        assertTrue(ModelOffers.recommended.fits(fourGb))
        assertTrue(ModelOffers.recommended.fits(0))
    }

    @Test fun theTwoChoicesAreTheTwoSmallModels() {
        assertEquals(listOf("Qwen3.5 2B", "Gemma 4 E2B"), ModelOffers.all.map { it.name })
        // The 4B is no longer offered; a phone that already has it keeps it.
        assertNull(ModelOffers.byId("qwen3.5-4b-q4-k-m"))
    }

    @Test fun offersAreFoundById() {
        assertEquals(ModelOffers.gemma, ModelOffers.byId("gemma-4-e2b-q4-0"))
        assertNull(ModelOffers.byId("minicpm5-1b-q4-k-m"))
        assertEquals(ModelOffers.all.size, ModelOffers.all.map { it.id }.distinct().size)
    }
}
