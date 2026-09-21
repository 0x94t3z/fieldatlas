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
import org.junit.Test

class LlamaGatewayJvmTest {
    @Test fun loadStreamsRejectsSecondLoadAndUnloads() = runBlocking {
        val engine = FakeEngine(tokens = listOf("one", " two"))
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/verified/model.gguf", "Ground every answer")
        assertEquals(listOf("load:/verified/model.gguf", "system:Ground every answer"), engine.calls)
        assertEquals(listOf("one", " two"), gateway.generate("prompt", 8).toList())
        assertThrows(IllegalStateException::class.java) {
            runBlocking { gateway.load("/verified/second.gguf", "system") }
        }
        gateway.unload()
        assertEquals(1, engine.cleanUpCalls)
        assertEquals(InferenceState.Idle, gateway.state.value)
    }

    @Test fun downstreamCancellationReturnsGatewayToReady() = runBlocking {
        val gateway = LlamaInferenceGateway(FakeEngine(waitAfterTokens = true))
        gateway.load("/verified/model.gguf", "system")
        assertEquals(listOf("one"), gateway.generate("prompt", 8).take(1).toList())
        assertEquals(InferenceState.Ready, gateway.state.value)
    }

    private class FakeEngine(
        private val tokens: List<String> = listOf("one"),
        private val waitAfterTokens: Boolean = false,
    ) : InferenceEngine {
        override val state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
        val calls = mutableListOf<String>()
        var cleanUpCalls = 0

        override suspend fun loadModel(pathToModel: String) {
            calls += "load:$pathToModel"
            state.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {
            calls += "system:$systemPrompt"
            state.value = InferenceEngine.State.ProcessingSystemPrompt
            state.value = InferenceEngine.State.ModelReady
        }

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
            state.value = InferenceEngine.State.Generating
            try {
                tokens.take(predictLength).forEach { emit(it) }
                if (waitAfterTokens) awaitCancellation()
            } finally {
                state.value = InferenceEngine.State.ModelReady
            }
        }

        override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int) = "unused"

        override fun cleanUp() {
            cleanUpCalls++
            state.value = InferenceEngine.State.Initialized
        }

        override fun destroy() {
            state.value = InferenceEngine.State.Uninitialized
        }
    }
}
