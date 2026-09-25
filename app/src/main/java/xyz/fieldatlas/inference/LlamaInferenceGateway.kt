package xyz.fieldatlas.inference

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LlamaInferenceGateway internal constructor(
    private val engine: InferenceEngine,
    private val adapterScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InferenceGateway {
    constructor(context: Context) : this(AiChat.getInferenceEngine(context.applicationContext))

    private val lifecycleMutex = Mutex()
    private var loadedSystemPrompt: String? = null
    private val mutableState = MutableStateFlow(LlamaStateMapper.map(engine.state.value))

    /** Prefill progress straight from the native engine ("x/y tokens read"). */
    override val promptProgress get() = engine.promptProgress
    override val state: StateFlow<InferenceState> = mutableState.asStateFlow()

    init {
        adapterScope.launch {
            engine.state.collect {
                if (!lifecycleMutex.isLocked) {
                    mutableState.value = LlamaStateMapper.map(engine.state.value)
                }
            }
        }
    }

    override suspend fun load(modelPath: String, systemPrompt: String) = lifecycleMutex.withLock {
        require(modelPath.isNotBlank()) { "modelPath must not be blank" }
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank" }
        check(state.value == InferenceState.Idle) { "Model load requires Idle state" }
        mutableState.value = InferenceState.Loading
        try {
            when (val initialized = withTimeout(NATIVE_INIT_TIMEOUT_MS) {
                engine.state.first {
                    it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                }
            }) {
                is InferenceEngine.State.Error -> throw initialized.exception
                else -> Unit
            }
            withContext(NonCancellable) {
                engine.loadModel(modelPath)
                engine.setSystemPrompt(systemPrompt)
            }
            loadedSystemPrompt = systemPrompt
            check(engine.state.value is InferenceEngine.State.ModelReady) {
                "Native model did not reach ModelReady"
            }
            mutableState.value = InferenceState.Ready
        } catch (error: CancellationException) {
            mutableState.value = LlamaStateMapper.map(engine.state.value)
            throw error
        } catch (error: Exception) {
            mutableState.value = InferenceState.Failed(LlamaStateMapper.sanitize(error))
            throw IllegalStateException(LlamaStateMapper.sanitize(error), error)
        }
    }

    override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(maxTokens > 0) { "maxTokens must be positive" }
        lifecycleMutex.withLock {
            check(state.value == InferenceState.Ready) { "Generation requires Ready state" }
            mutableState.value = InferenceState.Generating
            try {
                // Field Atlas: every question is an isolated turn. The native wrapper otherwise
                // accumulates chat history across generate() calls, which would leak a keyword-
                // expansion turn into the real answer and bleed earlier questions into later
                // benchmark runs. A caller-supplied systemPrompt (e.g. the search-planner persona
                // for the keyword turn) replaces the research persona for this turn only.
                engine.resetConversation(systemPrompt ?: loadedSystemPrompt ?: DEFAULT_RESET_PROMPT)
                if (seed >= 0) engine.setSamplerSeed(seed) else engine.setSamplerSeed(-1)
                val generated = StringBuilder()
                try {
                    engine.sendUserPrompt(prompt, maxTokens).collect { token ->
                        emit(token)
                        generated.append(token)
                        if (generated.length >= MIN_LOOP_CHARS * 2 && hasDuplicatedTail(generated)) {
                            throw GenerationLoopStopped()
                        }
                    }
                } catch (stopped: GenerationLoopStopped) {
                    // Verbatim echo loop (seen with MiniCPM5-1B Q4): the second exact copy of
                    // a long stretch ended the turn; the stream so far is the whole answer.
                }
                mutableState.value = LlamaStateMapper.map(engine.state.value)
            } catch (error: CancellationException) {
                mutableState.value = if (engine.state.value is InferenceEngine.State.ModelReady) {
                    InferenceState.Ready
                } else {
                    LlamaStateMapper.map(engine.state.value)
                }
                throw error
            } catch (error: Exception) {
                mutableState.value = InferenceState.Failed(LlamaStateMapper.sanitize(error))
                throw IllegalStateException(LlamaStateMapper.sanitize(error), error)
            }
        }
    }

    override suspend fun unload() = lifecycleMutex.withLock {
        check(state.value is InferenceState.Ready || state.value is InferenceState.Failed) {
            "Unload requires a loaded model or recoverable error"
        }
        try {
            withContext(NonCancellable + Dispatchers.IO) { engine.cleanUp() }
            loadedSystemPrompt = null
            mutableState.value = LlamaStateMapper.map(engine.state.value)
        } catch (error: Exception) {
            mutableState.value = InferenceState.Failed(LlamaStateMapper.sanitize(error))
            throw IllegalStateException(LlamaStateMapper.sanitize(error), error)
        }
    }

    // The encoder lives outside the chat-model lifecycle mutex on purpose: switching knowledge
    // packs (and thus encoders) must never block or interrupt an in-flight answer.
    override suspend fun setEncoder(modelPath: String?) {
        withContext(Dispatchers.IO) {
            if (modelPath == null) engine.unloadEncoder() else engine.loadEncoder(modelPath)
        }
    }

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        runCatching { engine.embed(text) }.getOrNull()
    }

    /** True when the text ends with two consecutive identical copies of a >= MIN_LOOP_CHARS block. */
    private fun hasDuplicatedTail(text: CharSequence): Boolean {
        val n = text.length
        val maxPeriod = minOf(n / 2, LOOP_WINDOW_CHARS / 2)
        var period = MIN_LOOP_CHARS
        while (period <= maxPeriod) {
            var same = true
            var k = 0
            while (k < period && same) {
                val a = text[n - 2 * period + k]
                val b = text[n - period + k]
                // Echo loops of per-source refusals differ only in their citation numbers
                // ("S1 does not…", "S2 does not…"), so digit positions compare equal.
                if (a != b && !(a.isDigit() && b.isDigit())) same = false
                k++
            }
            if (same) return true
            period++
        }
        return false
    }

    private class GenerationLoopStopped : Exception()

    private companion object {
        const val MIN_LOOP_CHARS = 48
        const val LOOP_WINDOW_CHARS = 320
        const val NATIVE_INIT_TIMEOUT_MS = 60_000L
        const val DEFAULT_RESET_PROMPT = "You are a careful offline assistant."
    }
}
