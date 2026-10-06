package xyz.fieldatlas.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {
    private val eightGb = 7_500_000_000L
    private val sixGb = 6_008_807_424L

    private fun model(id: String, active: Boolean = false, version: String = "1.0.0") = InstalledAsset(
        id = id, version = version, type = PackType.MODEL, title = "$id title", license = "Apache-2.0",
        installedBytes = 1, manifestSha256 = "a".repeat(64), rootPath = "/unused/$id", active = active,
    )

    private val compact = model(ModelOffers.recommended.id)
    private val larger = model(ModelOffers.larger.id, active = true)

    @Test fun theSelectedModelLoadsWhenThePhoneHasTheMemory() {
        val choice = chooseModel(listOf(compact, larger), eightGb, unfinishedLoad = null)
        assertSame(larger, choice.pack)
        assertNull(choice.notice)
    }

    @Test fun withNothingSelectedTheFirstModelLoads() {
        assertSame(compact, chooseModel(listOf(compact, larger.copy(active = false)), eightGb, null).pack)
    }

    @Test fun aPhoneWithoutTheMemoryGetsTheRecommendedModelAndIsTold() {
        val choice = chooseModel(listOf(larger, compact), sixGb, null)
        assertSame(compact, choice.pack)
        assertEquals("Qwen3.5 4B needs a phone with 8 GB of memory or more, so Qwen3.5 2B answers instead.", choice.notice)
    }

    @Test fun withoutTheRecommendedModelTheReasonIsTheError() {
        val error = assertThrows(IllegalStateException::class.java) { chooseModel(listOf(larger), sixGb, null) }
        assertTrue(error.message!!, error.message!!.startsWith("Qwen3.5 4B needs a phone with 8 GB of memory or more."))
        assertTrue(error.message!!, "Download Qwen3.5 2B in Library" in error.message!!)
    }

    @Test fun aLoadTheAppDidNotSurviveFallsBackOnce() {
        val choice = chooseModel(listOf(compact, larger), eightGb, modelLoadKey(larger))
        assertSame(compact, choice.pack)
        assertEquals("Field Atlas closed while loading Qwen3.5 4B last time, so Qwen3.5 2B answers instead.", choice.notice)
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

    @Test fun withNothingToFallBackToAnInterruptedModelIsTriedAgain() {
        val choice = chooseModel(listOf(larger), eightGb, modelLoadKey(larger))
        assertSame(larger, choice.pack)
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
}
