package xyz.fieldatlas.inference

import com.arm.aichat.InferenceEngine

internal object LlamaStateMapper {
    fun map(state: InferenceEngine.State): InferenceState = when (state) {
        InferenceEngine.State.Uninitialized,
        InferenceEngine.State.Initializing,
        InferenceEngine.State.LoadingModel,
        InferenceEngine.State.UnloadingModel,
        InferenceEngine.State.ProcessingSystemPrompt,
        -> InferenceState.Loading

        InferenceEngine.State.Initialized -> InferenceState.Idle
        InferenceEngine.State.ModelReady -> InferenceState.Ready
        InferenceEngine.State.Benchmarking,
        InferenceEngine.State.ProcessingUserPrompt,
        InferenceEngine.State.Generating,
        -> InferenceState.Generating

        is InferenceEngine.State.Error -> InferenceState.Failed(sanitize(state.exception))
    }

    fun sanitize(error: Throwable): String {
        val type = error::class.simpleName ?: "NativeError"
        val detail = error.message.orEmpty()
            .replace(PRIVATE_PATH, "<private-path>")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_ERROR_CHARS)
        return if (detail.isBlank()) type else "$type: $detail"
    }

    private val PRIVATE_PATH = Regex("(?:(?:[A-Za-z]:)?[/\\\\])(?:[^\\s:]+[/\\\\])*[^\\s:]+")
    private const val MAX_ERROR_CHARS = 240
}
