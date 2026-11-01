package xyz.fieldatlas.inference

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LlamaInferenceGateway internal constructor(
    private val engine: InferenceEngine,
    private val adapterScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** How long a turn may go with no prompt batch read and no word written before it is stopped. */
    private val stallMillis: Long = STALL_MILLIS,
    private val watchdogTickMillis: Long = WATCHDOG_TICK_MS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : InferenceGateway {
    constructor(context: Context) : this(AiChat.getInferenceEngine(context.applicationContext))

    private val lifecycleMutex = Mutex()
    private var loadedSystemPrompt: String? = null
    override var contextWindowTokens: Int = 2048
        private set
    override val promptOverheadTokens: Int get() = (loadedSystemPrompt?.toByteArray()?.size ?: 512) + 512
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
            // Native model loading and system-prompt prefill are CPU-heavy synchronous calls.
            // Keep them off the UI thread even when load() is launched from a ViewModel scope.
            withContext(NonCancellable + Dispatchers.Default) {
                engine.setRepack(repackWeights)
                engine.loadModel(modelPath)
                contextWindowTokens = runCatching {
                    java.io.File(modelPath).inputStream().use {
                        com.arm.aichat.gguf.GgufMetadataReader.create().readStructuredMetadata(it).dimensions?.contextLength
                    }?.coerceIn(1, 8192) ?: 2048
                }.getOrDefault(2048)
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

    override val reusedPromptTokens: Int get() = engine.reusedPromptTokens()

    /** Set by the app for the loaded model (see ModelOffer.thinkingBudget). */
    override var thinkingBudget: Int = 0

    @Volatile override var lastHitLimit: Boolean = false
        private set

    /** Set by the app for the loaded model (see ModelOffer.knowledgeFirst). */
    override var knowledgeFirst: Boolean = false

    /** Set by the app for the loaded model (see ModelOffer.writesFirstAid). */
    override var writesFirstAid: Boolean = false

    /** Set by the app before a load (see ModelOffer.repackWeights). */
    var repackWeights: Boolean = true

    override fun think(prompt: String, maxTokens: Int, savedPrefixChars: Int): Flow<String> =
        generateWithSampling(prompt, maxTokens, null, -1, savedPrefixChars, 1.10f, thinking = true)

    override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> =
        generateWithSampling(prompt, maxTokens, systemPrompt, seed, savedPrefixChars, 1.10f)

    override fun explainEvidence(prompt: String, maxTokens: Int, savedPrefixChars: Int): Flow<String> =
        generateWithSampling(prompt, maxTokens, null, -1, savedPrefixChars, 1.0f)

    private fun generateWithSampling(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int,
        savedPrefixChars: Int, repeatPenalty: Float, thinking: Boolean = false): Flow<String> = flow {
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
                // Set on every turn, before the reset renders the system turn, so no turn inherits another's.
                engine.setThinking(thinking)
                engine.resetConversation(systemPrompt ?: loadedSystemPrompt ?: DEFAULT_RESET_PROMPT)
                engine.setSampler(if (seed >= 0) seed else -1, repeatPenalty)
                val generated = StringBuilder()
                var pieces = 0
                lastHitLimit = false
                // Stall guard: a batch of the prompt read or a word written is progress. On 1 Nov a model gave no answer in
                // over 50 minutes on a 16 GB phone with nothing on screen to say so. A 256-token batch takes about 90 s
                // on a 6 GB phone with the 4B, so only far longer than that with neither counts as stuck.
                val lastProgressAt = AtomicLong(nowMillis())
                val stalled = AtomicBoolean(false)
                val watchdog = adapterScope.launch {
                    launch { engine.promptProgress.collect { lastProgressAt.set(nowMillis()) } }
                    while (true) {
                        delay(watchdogTickMillis)
                        // Asked again on every tick: a stop sent just before the engine starts the prompt is not lost.
                        if (stalled.get() || nowMillis() - lastProgressAt.get() >= stallMillis) {
                            stalled.set(true)
                            engine.abortGeneration()
                        }
                    }
                }
                try {
                    val safe = withoutControlTokens(prompt)
                    engine.sendUserPrompt(safe, maxTokens, safe.take(savedPrefixChars.coerceIn(0, safe.length))).collect { token ->
                        lastProgressAt.set(nowMillis())
                        emit(token)
                        generated.append(token)
                        // The engine streams one piece per sampled token, so reaching the budget means it was cut off.
                        if (++pieces >= maxTokens) lastHitLimit = true
                        if (generated.length >= MIN_LOOP_CHARS * 2 && hasDuplicatedTail(generated)) {
                            throw GenerationLoopStopped()
                        }
                    }
                } catch (stopped: GenerationLoopStopped) {
                    // Verbatim echo loop (seen with MiniCPM5-1B Q4): the second exact copy of
                    // a long stretch ended the turn; the stream so far is the whole answer.
                } finally {
                    watchdog.cancel()
                }
                mutableState.value = LlamaStateMapper.map(engine.state.value)
                if (stalled.get()) throw GenerationStalledException()
            } catch (error: GenerationStalledException) {
                // The model is still loaded and the next question can use it; only this answer stops.
                mutableState.value = LlamaStateMapper.map(engine.state.value)
                throw error
            } catch (error: CancellationException) {
                // Stop: the engine would otherwise read the rest of the prompt before noticing, holding up the next question.
                engine.abortGeneration()
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
    }.flowOn(Dispatchers.Default)

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
        runCatching { engine.embed(withoutControlTokens(text)) }.getOrNull()
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

    /** The turn made no progress for [stallMillis]; its message is what the person reads. */
    class GenerationStalledException : IllegalStateException(STALLED_MESSAGE)

    private companion object {
        const val MIN_LOOP_CHARS = 48
        const val LOOP_WINDOW_CHARS = 320
        const val NATIVE_INIT_TIMEOUT_MS = 60_000L
        const val STALL_MILLIS = 180_000L
        const val WATCHDOG_TICK_MS = 1_000L
        const val STALLED_MESSAGE = "The model stopped making progress for 3 minutes, so this answer was stopped. " +
            "Try again; if it happens again, choose a smaller model in Library."
        const val DEFAULT_RESET_PROMPT = "You are a careful offline assistant."
    }
}

/**
 * The native side reads the chat template's control tokens in the prompt text ("<|im_end|>",
 * "<|im_start|>system", "<think>"), so text from an attached file, a saved passage or the question
 * could end the user's turn and write instructions of its own. The app's own prompts contain none;
 * any that arrive are given a look-alike first character of the same length, so cached prompt offsets
 * still line up and the text is read as text.
 */
internal fun withoutControlTokens(prompt: String): String =
    // ChatML and Qwen ("<|im_end|>", "<think>", "<tool_call>"), Gemma ("<start_of_turn>"), Llama and Mistral
    // ("<s>", "</s>", "[INST]", "<<SYS>>"): the shapes chat models give their control tokens.
    prompt.replace(Regex("<(?=\\|[^\\s<>|]{1,40}\\|>)|<(?=/?(?:think|tools?|s)>)|<(?=/?[A-Za-z]+_[A-Za-z_]+>)|<(?=<SYS>>)|<(?=</SYS>>)"), "\u2039")
        .replace(Regex("\\[(?=/?INST])"), "\u27E6")
