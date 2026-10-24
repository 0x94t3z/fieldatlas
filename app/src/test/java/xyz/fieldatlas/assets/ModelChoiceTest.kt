package xyz.fieldatlas.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {
    private val eightGb = 7_500_000_000L
    private val sixGb = 6_008_807_424L
    private val fourGb = 3_790_000_000L

    private fun model(id: String, active: Boolean = false, version: String = "1.0.0") = InstalledAsset(
        id = id, version = version, type = PackType.MODEL, title = "$id title", license = "Apache-2.0",
        installedBytes = 1, manifestSha256 = "a".repeat(64), rootPath = "/unused/$id", active = active,
    )

    // A larger model that needs a 6 GB phone; no such download is offered now, but one installed
    // from a file or an earlier version is still held to its memory floor.
    private val largerOffer = ModelOffers.recommended.copy(id = "larger-test", name = "Larger model",
        minimumMemoryBytes = 5_500_000_000L, minimumMemoryLabel = "6 GB")
    private fun offerFor(id: String) = if (id == largerOffer.id) largerOffer else ModelOffers.byId(id)
    private fun chooseModel(models: List<InstalledAsset>, memory: Long, unfinished: String?) =
        chooseModel(models, memory, unfinished, ::offerFor)

    private val compact = model(ModelOffers.recommended.id)
    private val larger = model(largerOffer.id, active = true)

    @Test fun theSelectedModelLoadsWhenThePhoneHasTheMemory() {
        val choice = chooseModel(listOf(compact, larger), eightGb, unfinishedLoad = null)
        assertSame(larger, choice.pack)
        assertNull(choice.notice)
    }

    @Test fun withNothingSelectedTheFirstModelLoads() {
        assertSame(compact, chooseModel(listOf(compact, larger.copy(active = false)), eightGb, null).pack)
    }

    @Test fun aPhoneWithoutTheMemoryGetsTheRecommendedModelAndIsTold() {
        val choice = chooseModel(listOf(larger, compact), fourGb, null)
        assertSame(compact, choice.pack)
        assertEquals("Larger model needs a phone with 6 GB of memory or more, so Qwen3.5 2B answers instead.", choice.notice)
    }

    @Test fun withoutTheRecommendedModelTheReasonIsTheError() {
        val error = assertThrows(IllegalStateException::class.java) { chooseModel(listOf(larger), fourGb, null) }
        assertTrue(error.message!!, error.message!!.startsWith("Larger model needs a phone with 6 GB of memory or more."))
        assertTrue(error.message!!, "Download Qwen3.5 2B in Library" in error.message!!)
    }

    @Test fun aLoadTheAppDidNotSurviveFallsBackOnce() {
        val choice = chooseModel(listOf(compact, larger), eightGb, modelLoadKey(larger))
        assertSame(compact, choice.pack)
        assertEquals("Field Atlas closed while loading Larger model last time, so Qwen3.5 2B answers instead. Choose Larger model here to try it again.", choice.notice)
    }

    @Test fun anUnfinishedLoadOfAnotherModelOrVersionIsIgnored() {
        assertSame(larger, chooseModel(listOf(compact, larger), eightGb, "${larger.id}:0.9.0").pack)
        assertSame(larger, chooseModel(listOf(compact, larger), eightGb, modelLoadKey(compact)).pack)
    }

    @Test fun theRecommendedModelNeverFallsBack() {
        val selected = compact.copy(active = true)
        val choice = chooseModel(listOf(selected, larger.copy(active = false)), eightGb, modelLoadKey(selected))
        assertSame(selected, choice.pack)
        assertNull(choice.notice)
    }

    @Test fun withNothingToFallBackToAnInterruptedModelIsTriedAgainQuietly() {
        val choice = chooseModel(listOf(larger), eightGb, modelLoadKey(larger))
        assertSame(larger, choice.pack)
        // Tried again, since it is all there is, with no note about the earlier load.
        assertNull(choice.notice)
    }

    @Test fun anImportedModelIsNamedByItsTitle() {
        val imported = model("minicpm5-1b-q4-k-m", active = true)
        val choice = chooseModel(listOf(compact, imported), sixGb, modelLoadKey(imported))
        assertSame(compact, choice.pack)
        assertTrue(choice.notice!!, "loading minicpm5-1b-q4-k-m title last time" in choice.notice!!)
    }

    @Test fun noModelIsAnError() {
        assertThrows(IllegalStateException::class.java) { chooseModel(emptyList(), eightGb, null) }
    }

    @Test fun aLargerModelThatFitsTakesOverOnceDownloaded() {
        assertTrue(takesOver(ModelOffers.larger, ModelOffers.recommended.id, eightGb))
        // Nothing in use yet: the download is the model.
        assertTrue(takesOver(ModelOffers.recommended, null, eightGb))
        // The 2B fetched as a fallback while the 4B answers waits in Library.
        assertFalse(takesOver(ModelOffers.recommended, ModelOffers.larger.id, eightGb))
        // The 4B fits a 6 GB phone but is not recommended there: downloaded, it waits in Library until chosen. A 4 GB
        // phone keeps the 2B. A model the app does not offer is left alone.
        assertFalse(takesOver(ModelOffers.larger, ModelOffers.recommended.id, sixGb))
        // The only model on the phone is the one used, whatever its size.
        assertTrue(takesOver(ModelOffers.larger, null, sixGb))
        assertFalse(takesOver(ModelOffers.larger, ModelOffers.recommended.id, fourGb))
        assertFalse(takesOver(ModelOffers.larger, "imported-model", eightGb))
    }
}
