package xyz.fieldatlas.assets

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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

    @Test fun theRecommendedModelRunsOnEveryPhone() {
        assertTrue(ModelOffers.recommended.fits(3_790_000_000L))
        assertTrue(ModelOffers.recommended.fits(0))
    }

    @Test fun theFourBillionModelIsOfferedOnlyWhereItFits() {
        assertEquals(listOf("Qwen3.5 2B", "Qwen3.5 4B"), ModelOffers.all.map { it.name })
        assertNull(ModelOffers.byId("gemma-4-e2b-q4-0"))
        // A Pixel 8a (8 GB) reports about 7.5e9 bytes: the 4B first, the 2B as the faster choice.
        assertEquals(listOf(ModelOffers.larger, ModelOffers.recommended), ModelOffers.forDevice(7_500_000_000L))
        // A 6 GB Redmi 13C reports under 6.1e9: the 2B only.
        assertEquals(listOf(ModelOffers.recommended), ModelOffers.forDevice(5_870_000_000L))
        assertEquals(listOf(ModelOffers.recommended), ModelOffers.forDevice(6_999_999_999L))
    }

    @Test fun offersAreFoundById() {
        assertEquals(ModelOffers.recommended, ModelOffers.byId("qwen3.5-2b-q4-k-m"))
        assertNull(ModelOffers.byId("minicpm5-1b-q4-k-m"))
        assertEquals(ModelOffers.all.size, ModelOffers.all.map { it.id }.distinct().size)
    }
}
