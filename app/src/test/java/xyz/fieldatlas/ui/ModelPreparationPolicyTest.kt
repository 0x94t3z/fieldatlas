package xyz.fieldatlas.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.InferenceState

class ModelPreparationPolicyTest {
    @Test
    fun preparesOnceWhenBothPacksArePresentAndModelIsIdle() {
        assertTrue(shouldAutoPrepareModel(assetsReady = true, inferenceState = InferenceState.Idle, started = false))
    }

    @Test
    fun doesNotPrepareAgainAfterAnExplicitMemoryRelease() {
        assertFalse(shouldAutoPrepareModel(assetsReady = true, inferenceState = InferenceState.Idle, started = true))
    }

    @Test
    fun waitsUntilBothPacksAreReady() {
        assertFalse(shouldAutoPrepareModel(assetsReady = false, inferenceState = InferenceState.Idle, started = false))
    }
}
