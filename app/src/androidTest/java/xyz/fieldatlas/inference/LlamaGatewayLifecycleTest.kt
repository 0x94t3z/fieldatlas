package xyz.fieldatlas.inference

import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaGatewayLifecycleTest {
    @Test fun loadRequiresIdleAndSetsSystemPromptImmediately() {
        runBlocking {
            val engine = FakeEngine()
            val gateway = LlamaInferenceGateway(engine)
            gateway.load("/verified/model.gguf", "Ground every answer")
            assertEquals(listOf("load:/verified/model.gguf", "system:Ground every answer"), engine.calls)
            assertEquals(InferenceState.Ready, gateway.state.value)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { gateway.load("/verified/second.gguf", "system") }
            }
        }
    }

    @Test fun nonIdleEngineRejectsLoad() {
        val engine = FakeEngine(InferenceEngine.State.LoadingModel)
        val gateway = LlamaInferenceGateway(engine)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { gateway.load("/verified/model.gguf", "system") }
        }
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun streamsTokensWithoutAdapterBuffering() = runBlocking {
        val engine = FakeEngine(tokens = listOf("one", " two"))
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/verified/model.gguf", "system")
        assertEquals(listOf("one", " two"), gateway.generate("prompt", 8).toList())
        assertEquals(InferenceState.Ready, gateway.state.value)
    }

    @Test fun cancellationReturnsReadyAndUnloadReleases() = runBlocking {
        val engine = FakeEngine(waitAfterTokens = true)
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/verified/model.gguf", "system")
        assertEquals(listOf("one"), gateway.generate("prompt", 8).take(1).toList())
        assertEquals(InferenceState.Ready, gateway.state.value)
        gateway.unload()
        assertEquals(1, engine.cleanUpCalls)
        assertEquals(InferenceState.Idle, gateway.state.value)
    }

    private class FakeEngine(
        initialState: InferenceEngine.State = InferenceEngine.State.Initialized,
        private val tokens: List<String> = listOf("one"),
        private val waitAfterTokens: Boolean = false,
    ) : InferenceEngine {
        override val state = MutableStateFlow(initialState)
        val calls = mutableListOf<String>()
        var cleanUpCalls = 0

        override suspend fun loadModel(pathToModel: String) {
            calls += "load:$pathToModel"
            state.value = InferenceEngine.State.LoadingModel
            state.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {
            calls += "system:$systemPrompt"
            state.value = InferenceEngine.State.ProcessingSystemPrompt
            state.value = InferenceEngine.State.ModelReady
        }

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
            state.value = InferenceEngine.State.ProcessingUserPrompt
            state.value = InferenceEngine.State.Generating
            try {
                tokens.take(predictLength).forEach { emit(it) }
                if (waitAfterTokens) awaitCancellation()
            } finally {
                state.value = InferenceEngine.State.ModelReady
            }
        }

        override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = "unused"

        override fun cleanUp() {
            cleanUpCalls++
            state.value = InferenceEngine.State.UnloadingModel
            state.value = InferenceEngine.State.Initialized
        }

        override fun destroy() {
            state.value = InferenceEngine.State.Uninitialized
        }
    }
}
