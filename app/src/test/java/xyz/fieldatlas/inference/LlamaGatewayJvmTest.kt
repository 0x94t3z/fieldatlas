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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LlamaGatewayJvmTest {
    @Test fun verbatimEchoLoopEndsTheTurnEarly() = runBlocking {
        val sentence = "The source S3 does not contain the requested information about the topic.\n"
        val engine = FakeEngine(tokens = List(80) { sentence })
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/m.gguf", "sys")
        val out = gateway.generate("answer", 80).toList()
        // The guard cuts the stream right after the second exact copy of the sentence.
        assertEquals(2, out.size)
        assertEquals(InferenceState.Ready, gateway.state.value)
    }

    @Test fun citationCountingEchoLoopIsAlsoCut() = runBlocking {
        val engine = FakeEngine(tokens = List<String>(80) { index ->
            "Source S${index % 7 + 1} does not contain the answer to the research question here.\n"
        })
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/m.gguf", "sys")
        val out = gateway.generate("answer", 80).toList()
        assertTrue("expected early stop, got ${out.size} tokens", out.size <= 3)
    }

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

    @Test fun everyGenerationStartsWithConversationReset() = runBlocking {
        val engine = FakeEngine()
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/m.gguf", "sys")
        gateway.generate("first", 4).toList()
        gateway.generate("second", 4, systemPrompt = "planner persona").toList()
        gateway.generate("third", 4, seed = 17).toList()
        assertEquals(
            listOf(
                "load:/m.gguf",
                "system:sys",
                "reset:sys",
                "seed:-1",
                "reset:planner persona",
                "seed:-1",
                "reset:sys",
                "seed:17",
            ),
            engine.calls,
        )
    }

    private class FakeEngine(
        override val promptProgress: kotlinx.coroutines.flow.StateFlow<com.arm.aichat.PromptProgress?> =
            kotlinx.coroutines.flow.MutableStateFlow(null),
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

        override fun resetConversation(systemPrompt: String) {
            calls += "reset:$systemPrompt"
        }

        override fun setSamplerSeed(seed: Int) {
            calls += "seed:$seed"
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
