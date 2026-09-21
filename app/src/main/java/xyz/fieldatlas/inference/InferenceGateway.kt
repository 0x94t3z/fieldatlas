package xyz.fieldatlas.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InferenceGateway {
    val state: StateFlow<InferenceState>
    suspend fun load(modelPath: String, systemPrompt: String)
    fun generate(prompt: String, maxTokens: Int): Flow<String>
    suspend fun unload()
}

sealed interface InferenceState {
    data object Idle : InferenceState
    data object Loading : InferenceState
    data object Ready : InferenceState
    data object Generating : InferenceState
    data class Failed(val message: String) : InferenceState
}
