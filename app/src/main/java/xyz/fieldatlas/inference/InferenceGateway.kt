package xyz.fieldatlas.inference

import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface InferenceGateway {
    val state: StateFlow<InferenceState>

    /** Live prompt-prefill progress while a prompt is being read; null when idle. */
    val promptProgress: StateFlow<PromptProgress?> get() = NO_PROGRESS

    suspend fun load(modelPath: String, systemPrompt: String)
    fun generate(prompt: String, maxTokens: Int, systemPrompt: String? = null, seed: Int = -1): Flow<String>
    suspend fun unload()

    /**
     * Loads (or clears) the embedding encoder GGUF shipped inside a vector knowledge pack.
     * Independent of the answer model — the encoder can be swapped as packs change while a
     * chat model stays loaded. Default no-ops keep fakes and non-native backends unchanged.
     */
    suspend fun setEncoder(modelPath: String?) {}

    /** Encode [text] with the loaded encoder; null when no encoder is available. */
    suspend fun embed(text: String): FloatArray? = null

    companion object {
        private val NO_PROGRESS = MutableStateFlow<PromptProgress?>(null)
    }
}

sealed interface InferenceState {
    data object Idle : InferenceState
    data object Loading : InferenceState
    data object Ready : InferenceState
    data object Generating : InferenceState
    data class Failed(val message: String) : InferenceState
}
