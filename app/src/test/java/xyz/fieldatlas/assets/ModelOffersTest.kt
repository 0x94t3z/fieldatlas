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

    @Test fun theRecommendedModelRunsOnEveryPhone() {
        assertTrue(ModelOffers.recommended.fits(3_790_000_000L))
        assertTrue(ModelOffers.recommended.fits(0))
    }

    @Test fun theFourBillionModelIsOfferedOnlyWhereItFits() {
        assertEquals(listOf("Qwen3.5 2B", "Qwen3.5 4B", "Gemma 4 26B"), ModelOffers.all.map { it.name })
        assertNull(ModelOffers.byId("gemma-4-e2b-q4-0"))
        // A Pixel 8a (8 GB) reports about 7.5e9 bytes: since 1.4.7 the 2B first and the 4B after it, slow there; a 12 GB
        // phone (about 11.8e9) is offered the 4B first. Contributors timed the 4B at up to 5.5 min on a 12 GB phone.
        assertEquals(listOf(ModelOffers.recommended, ModelOffers.larger), ModelOffers.forDevice(7_500_000_000L))
        assertEquals(listOf(ModelOffers.larger, ModelOffers.recommended), ModelOffers.forDevice(11_760_000_000L))
        // A 6 GB Redmi 13C reports under 6.1e9: the 2B is recommended there and the 4B follows, slow on that memory
        // (24 Oct: 3-6 minutes an answer, one of 47 minutes with other apps open); a 4 GB phone, the 2B only.
        assertEquals(listOf(ModelOffers.recommended, ModelOffers.larger), ModelOffers.forDevice(5_870_000_000L))
        assertTrue(ModelOffers.larger.slowOn(5_870_000_000L))
        assertTrue(ModelOffers.larger.slowOn(7_500_000_000L))
        assertFalse(ModelOffers.larger.slowOn(11_760_000_000L))
        assertFalse(ModelOffers.recommended.slowOn(3_790_000_000L))
        assertEquals(listOf(ModelOffers.recommended), ModelOffers.forDevice(3_790_000_000L))
        assertFalse(ModelOffers.larger.repacksOn(5_870_000_000L))
        assertTrue(ModelOffers.larger.repacksOn(7_500_000_000L))
        assertTrue(ModelOffers.recommended.repacksOn(3_790_000_000L))
        assertFalse(ModelOffers.largest.repacksOn(11_500_000_000L))
    }

    @Test fun gemmaIsNoLongerOfferedButStillRecognised() {
        // Since 1.4.3 a 12 GB phone (about 11.5e9 bytes) is offered the 4B first: Gemma gave no answer in 50 minutes on a
        // 16 GB phone. A copy already installed or imported is still recognised, so it loads mapped, not copied.
        assertEquals(listOf(ModelOffers.larger, ModelOffers.recommended), ModelOffers.forDevice(11_500_000_000L))
        assertEquals(listOf(ModelOffers.larger, ModelOffers.recommended), ModelOffers.forDevice(16_000_000_000L))
        assertEquals(listOf(ModelOffers.recommended, ModelOffers.larger), ModelOffers.forDevice(7_900_000_000L))
        assertFalse(ModelOffers.largest in ModelOffers.offered)
        assertEquals(ModelOffers.largest, ModelOffers.byId("gemma-4-26b-a4b-ud-q2-k-xl"))
        assertTrue(ModelOffers.largest.line.startsWith("No longer offered"))
        // Only the model chosen for it reasons before answering.
        assertEquals(2048, ModelOffers.largest.thinkingBudget)
        assertEquals(0, ModelOffers.larger.thinkingBudget)
        assertEquals(0, ModelOffers.recommended.thinkingBudget)
        // The 2B answers from the passages found; the 4B and Gemma know enough to answer from their own knowledge.
        assertFalse(ModelOffers.recommended.knowledgeFirst)
        assertTrue(ModelOffers.larger.knowledgeFirst)
        assertTrue(ModelOffers.largest.knowledgeFirst)
        // Gemma's weights stay mapped from the file, which Android can reclaim; the small models keep the faster copy.
        assertFalse(ModelOffers.largest.repackWeights)
        assertTrue(ModelOffers.larger.repackWeights)
        assertTrue(ModelOffers.recommended.repackWeights)
    }

    @Test fun offersAreFoundById() {
        assertEquals(ModelOffers.recommended, ModelOffers.byId("qwen3.5-2b-q4-k-m"))
        assertNull(ModelOffers.byId("minicpm5-1b-q4-k-m"))
        assertEquals(ModelOffers.all.size, ModelOffers.all.map { it.id }.distinct().size)
    }
}
