package xyz.fieldatlas.research

import kotlinx.serialization.Serializable

@Serializable
enum class ResearchCompletion { Complete, Cancelled }

sealed interface ResearchEvent {
    data class Searching(val query: String) : ResearchEvent
    data class Sources(val evidence: List<Evidence>) : ResearchEvent
    data class Token(val text: String) : ResearchEvent
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

data class PromptSource(val citationId: String, val evidence: Evidence)

data class PackedPrompt(
    val prompt: String,
    val sources: List<PromptSource>,
)
