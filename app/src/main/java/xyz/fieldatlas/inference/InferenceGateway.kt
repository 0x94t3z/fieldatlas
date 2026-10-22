package xyz.fieldatlas.inference

import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface InferenceGateway {
    val contextWindowTokens: Int get() = 8192
    val promptOverheadTokens: Int get() = 1024
    val state: StateFlow<InferenceState>

    /** Live prompt-prefill progress while a prompt is being read; null when idle. */
    val promptProgress: StateFlow<PromptProgress?> get() = NO_PROGRESS

    suspend fun load(modelPath: String, systemPrompt: String)

    /**
     * Generates an answer to [prompt]. Its first [savedPrefixChars] characters are fixed
     * instructions that many prompts share: an engine that can keep the model state after reading
     * them once restores it instead of reading them again; others read the whole prompt.
     */
    fun generate(prompt: String, maxTokens: Int, systemPrompt: String? = null, seed: Int = -1, savedPrefixChars: Int = 0): Flow<String>

    /** Evidence paraphrases must be allowed to repeat exact technical terms and quantities. */
    fun explainEvidence(prompt: String, maxTokens: Int, savedPrefixChars: Int = 0): Flow<String> =
        generate(prompt, maxTokens, savedPrefixChars = savedPrefixChars)

    /** Separate support-review turn. Production uses the same local engine;
     * test gateways can supply independent review outcomes. */
    fun reviewEvidence(prompt: String, savedPrefixChars: Int = 0): Flow<String> = generate(prompt, maxTokens = 32, savedPrefixChars = savedPrefixChars)

    /**
     * Tokens the loaded model may spend reasoning before an answer from its own knowledge; 0: it answers straight
     * away. Only a model chosen for it reasons (see ModelOffer.thinkingBudget).
     */
    val thinkingBudget: Int get() = 0

    /** Whether the loaded model answers ordinary questions from its own knowledge (see ModelOffer.knowledgeFirst). */
    val knowledgeFirst: Boolean get() = false

    /** Whether the loaded model writes first-aid answers from the official guide (see ModelOffer.writesFirstAid). */
    val writesFirstAid: Boolean get() = false

    /** Like [generate], with the model's thinking turn open: its reasoning streams first, between the template's marks. */
    fun think(prompt: String, maxTokens: Int, savedPrefixChars: Int = 0): Flow<String> =
        generate(prompt, maxTokens, savedPrefixChars = savedPrefixChars)

    /** Whether the last generation stopped because it used its whole token budget, not because the model finished. */
    val lastHitLimit: Boolean get() = false

    /** Prompt tokens the last generation restored instead of reading; 0 when it read them all. */
    val reusedPromptTokens: Int get() = 0

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
