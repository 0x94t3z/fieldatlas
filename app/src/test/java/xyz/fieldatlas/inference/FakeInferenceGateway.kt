package xyz.fieldatlas.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeInferenceGateway(private val tokens: List<String> = emptyList()) : InferenceGateway {
    private val mutableState = MutableStateFlow<InferenceState>(InferenceState.Ready)
    override val state: StateFlow<InferenceState> = mutableState.asStateFlow()
    var generateCalls: Int = 0
        private set

    override suspend fun load(modelPath: String, systemPrompt: String) {
        require(modelPath.isNotBlank()) { "modelPath must not be blank" }
        mutableState.value = InferenceState.Loading
        mutableState.value = InferenceState.Ready
    }

    override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(maxTokens > 0) { "maxTokens must be positive" }
        generateCalls++
        mutableState.value = InferenceState.Generating
        try {
            tokens.take(maxTokens).forEach { emit(it) }
        } finally {
            mutableState.value = InferenceState.Ready
        }
    }

    override suspend fun unload() {
        mutableState.value = InferenceState.Idle
    }
}
