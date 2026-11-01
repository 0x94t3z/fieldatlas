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
    @Test fun evidenceSamplingDoesNotLeakIntoReviewOrOrdinaryTurns() = runBlocking {
        val engine = FakeEngine()
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/m.gguf", "sys")
        gateway.explainEvidence("evidence", 16).toList()
        gateway.reviewEvidence("review").toList()
        gateway.generate("ordinary", 16).toList()
        gateway.explainEvidence("second evidence", 16).toList()
        assertEquals(listOf(1.0f, 1.10f, 1.10f, 1.0f), engine.penalties)
    }
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

    @Test fun theRepackChoiceReachesTheEngineBeforeTheModelLoads() = runBlocking {
        val engine = FakeEngine()
        val gateway = LlamaInferenceGateway(engine)
        gateway.repackWeights = false
        gateway.load("/verified/model.gguf", "system")
        assertEquals(listOf(false), engine.repacks)
        assertTrue(engine.repackSetBeforeLoad)
        // The upstream default stays for every other model.
        val plain = FakeEngine()
        LlamaInferenceGateway(plain).load("/verified/model.gguf", "system")
        assertEquals(listOf(true), plain.repacks)
    }

    @Test fun aTurnWithNoProgressIsStoppedAndTheModelStaysUsable() = runBlocking {
        val engine = FakeEngine(readUntilStopped = true)
        val gateway = LlamaInferenceGateway(engine, stallMillis = 150, watchdogTickMillis = 20)
        gateway.load("/verified/model.gguf", "system")
        val error = assertThrows(LlamaInferenceGateway.GenerationStalledException::class.java) {
            runBlocking { gateway.generate("prompt", 8).toList() }
        }
        assertTrue(error.message!!.contains("stopped making progress"))
        assertTrue(engine.aborts >= 1)
        assertEquals(InferenceState.Ready, gateway.state.value)
        // The next question runs on the same loaded model.
        engine.readUntilStoppedOff()
        assertEquals(listOf("one"), gateway.generate("prompt", 8).toList())
    }

    @Test fun aSlowPromptThatKeepsReadingIsNotStopped() = runBlocking {
        // Six batches 60 ms apart against a 150 ms limit: slow, never stuck.
        val engine = FakeEngine(readUntilStopped = true, progressSteps = 6, stepMillis = 60, promptProgress = MutableStateFlow(null))
        val gateway = LlamaInferenceGateway(engine, stallMillis = 150, watchdogTickMillis = 20)
        gateway.load("/verified/model.gguf", "system")
        assertEquals(listOf("read"), gateway.generate("prompt", 8).toList())
        assertEquals(0, engine.aborts)
    }

    @Test fun stopAsksTheEngineToStopReading() = runBlocking {
        val engine = FakeEngine(waitAfterTokens = true)
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/verified/model.gguf", "system")
        gateway.generate("prompt", 8).take(1).toList()
        assertTrue(engine.aborts >= 1)
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
                "prefix:",
                "reset:planner persona",
                "seed:-1",
                "prefix:",
                "reset:sys",
                "seed:17",
                "prefix:",
            ),
            engine.calls,
        )
    }

    @Test fun theFixedOpeningOfAPromptReachesTheEngine() = runBlocking {
        val engine = FakeEngine()
        val gateway = LlamaInferenceGateway(engine)
        gateway.load("/m.gguf", "sys")
        gateway.generate("Rules. EVIDENCE: a QUESTION: b", 4, null, -1, "Rules.".length).toList()
        gateway.generate("short", 4, null, -1, 99).toList()
        assertEquals(listOf("prefix:Rules.", "prefix:short"), engine.calls.filter { it.startsWith("prefix:") })
    }

    @Test fun nativeLoadAndGenerationNeverRunOnTheCollectorThread() = runBlocking {
        val collectorThread = Thread.currentThread().id
        val engine = FakeEngine()
        val gateway = LlamaInferenceGateway(engine)

        gateway.load("/m.gguf", "sys")
        gateway.generate("question", 4).toList()

        assertTrue(engine.loadThread != collectorThread)
        assertTrue(engine.resetThread != collectorThread)
    }

    private class FakeEngine(
        override val promptProgress: kotlinx.coroutines.flow.StateFlow<com.arm.aichat.PromptProgress?> =
            kotlinx.coroutines.flow.MutableStateFlow(null),
        private val tokens: List<String> = listOf("one"),
        private val waitAfterTokens: Boolean = false,
        /** Reads the prompt until stopped, reporting [progressSteps] batches first; then ends with no tokens, as the engine does. */
        private var readUntilStopped: Boolean = false,
        private val progressSteps: Int = 0,
        private val stepMillis: Long = 0,
    ) : InferenceEngine {
        fun readUntilStoppedOff() { readUntilStopped = false }
        @Volatile var aborts = 0
        @Volatile private var abortRequested = false
        override fun abortGeneration() { aborts++; abortRequested = true }

        override val state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
        val calls = mutableListOf<String>()
        val penalties = mutableListOf<Float>()
        var cleanUpCalls = 0
        val repacks = mutableListOf<Boolean>()
        var repackSetBeforeLoad = false
        var loadThread = -1L
        var resetThread = -1L

        override suspend fun loadModel(pathToModel: String) {
            loadThread = Thread.currentThread().id
            calls += "load:$pathToModel"
            state.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {
            calls += "system:$systemPrompt"
            state.value = InferenceEngine.State.ProcessingSystemPrompt
            state.value = InferenceEngine.State.ModelReady
        }

        override fun sendUserPrompt(message: String, predictLength: Int, savedPrefix: String): Flow<String> = flow {
            calls += "prefix:$savedPrefix"
            state.value = InferenceEngine.State.Generating
            abortRequested = false
            if (readUntilStopped) {
                val started = System.currentTimeMillis()
                var step = 0
                while (!abortRequested) {
                    Thread.sleep(5)
                    if (step < progressSteps && System.currentTimeMillis() - started >= (step + 1) * stepMillis) {
                        step++
                        (promptProgress as MutableStateFlow).value = com.arm.aichat.PromptProgress(step * 256, progressSteps * 256)
                        if (step == progressSteps) break
                    }
                }
                state.value = InferenceEngine.State.ModelReady
                if (!abortRequested) emit("read")
                return@flow
            }
            try {
                tokens.take(predictLength).forEach { emit(it) }
                if (waitAfterTokens) awaitCancellation()
            } finally {
                state.value = InferenceEngine.State.ModelReady
            }
        }

        override fun resetConversation(systemPrompt: String) {
            resetThread = Thread.currentThread().id
            calls += "reset:$systemPrompt"
        }

        override fun setSamplerSeed(seed: Int) {
            calls += "seed:$seed"
        }

        override fun setRepack(enabled: Boolean) {
            repacks += enabled
            repackSetBeforeLoad = calls.none { it.startsWith("load:") }
        }

        override fun setSampler(seed: Int, repeatPenalty: Float) {
            penalties += repeatPenalty
            setSamplerSeed(seed)
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
