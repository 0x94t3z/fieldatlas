package xyz.fieldatlas.research

import kotlinx.serialization.Serializable

@Serializable
enum class ResearchCompletion { Complete, Cancelled }

sealed interface ResearchEvent {
    data class Planning(val query: String) : ResearchEvent
    data class Keywords(val terms: List<String>) : ResearchEvent
    data class Searching(val query: String) : ResearchEvent
    data class Sources(val evidence: List<Evidence>) : ResearchEvent
    /**
     * Verbatim source text shown before model generation starts. It replaces any answer
     * text so far; later replace tokens repeat it. Consumers must not count it as a
     * generated token, or prefill progress would be hidden while the model is still reading.
     */
    data class Lead(val text: String) : ResearchEvent
    data class Token(val text: String, val replace: Boolean = false) : ResearchEvent
    data class Complete(val metrics: ResearchMetrics) : ResearchEvent
    data class InsufficientEvidence(val reason: String) : ResearchEvent
    data class Failed(val message: String) : ResearchEvent
}

@Serializable
data class ResearchMetrics(
    val retrievalMillis: Long,
    val timeToFirstTokenMillis: Long?,
    val totalMillis: Long,
    val generatedTokenCount: Int,
    val citedSourceIds: Set<String>,
    val hasUnmappedCitation: Boolean,
)

data class PromptSource(
    val citationId: String,
    val evidence: Evidence,
    /** Exact excerpt supplied to inference; evidence retains the original for source viewing. */
    val excerpt: String = evidence.text,
)

data class PackedPrompt(
    val prompt: String,
    val sources: List<PromptSource>,
    val mixedAnswer: Boolean = false,
)
