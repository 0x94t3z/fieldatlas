package xyz.fieldatlas.inference

import com.arm.aichat.InferenceEngine
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaStateMapperTest {
    @Test fun mapsEveryUpstreamState() {
        val cases = listOf(
            InferenceEngine.State.Uninitialized to InferenceState.Loading,
            InferenceEngine.State.Initializing to InferenceState.Loading,
            InferenceEngine.State.Initialized to InferenceState.Idle,
            InferenceEngine.State.LoadingModel to InferenceState.Loading,
            InferenceEngine.State.UnloadingModel to InferenceState.Loading,
            InferenceEngine.State.ModelReady to InferenceState.Ready,
            InferenceEngine.State.Benchmarking to InferenceState.Generating,
            InferenceEngine.State.ProcessingSystemPrompt to InferenceState.Loading,
            InferenceEngine.State.ProcessingUserPrompt to InferenceState.Generating,
            InferenceEngine.State.Generating to InferenceState.Generating,
        )
        cases.forEach { (upstream, expected) -> assertEquals(expected, LlamaStateMapper.map(upstream)) }
    }

    @Test fun sanitizesNativeErrorsWithoutPrivatePaths() {
        val state = LlamaStateMapper.map(
            InferenceEngine.State.Error(IOException("Failed /data/user/0/xyz.fieldatlas/files/packs/model.gguf: bad header")),
        ) as InferenceState.Failed
        assertFalse(state.message.contains("/data/"))
        assertFalse(state.message.contains("model.gguf"))
        assertTrue(state.message.contains("bad header"))
    }
}
