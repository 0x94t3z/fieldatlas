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
    private val mutableState = MutableStateFlow(LlamaStateMapper.map(engine.state.value))
    override val state: StateFlow<InferenceState> = mutableState.asStateFlow()

    init {
        adapterScope.launch {
            engine.state.collect { upstream -> mutableState.value = LlamaStateMapper.map(upstream) }
        }
    }

    override suspend fun load(modelPath: String, systemPrompt: String) = lifecycleMutex.withLock {
        require(modelPath.isNotBlank()) { "modelPath must not be blank" }
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank" }
        check(state.value == InferenceState.Idle) { "Model load requires Idle state" }
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

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(maxTokens > 0) { "maxTokens must be positive" }
        lifecycleMutex.withLock {
            check(state.value == InferenceState.Ready) { "Generation requires Ready state" }
            try {
                engine.sendUserPrompt(prompt, maxTokens).collect { emit(it) }
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
            mutableState.value = LlamaStateMapper.map(engine.state.value)
        } catch (error: Exception) {
            mutableState.value = InferenceState.Failed(LlamaStateMapper.sanitize(error))
            throw IllegalStateException(LlamaStateMapper.sanitize(error), error)
        }
    }

    private companion object {
        const val NATIVE_INIT_TIMEOUT_MS = 60_000L
    }
}
