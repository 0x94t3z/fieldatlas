package xyz.fieldatlas.ui.research

import java.util.Locale
import xyz.fieldatlas.research.ResearchCompletion as DomainResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown

typealias ResearchCompletion = DomainResearchCompletion

data class ResearchMetricsModel(
    val retrieval: String,
    val firstToken: String?,
    val total: String,
    val tokenCount: String,
    val tokenRate: String?,
    val citationCoverage: String,
    val hasUnmappedCitation: Boolean,
)

data class AnswerPresentation(
    val blocks: List<MarkdownBlock>,
    val availableCitations: Set<Int>,
    val unavailableCitations: Set<Int>,
)

fun buildAnswerPresentation(answer: String, sourceCount: Int): AnswerPresentation {
    val blocks = parseAnswerMarkdown(answer)
    val citations = blocks.flatMap(MarkdownBlock::inlineContent)
        .filterIsInstance<MarkdownInline.Citation>()
        .map(MarkdownInline.Citation::number)
        .toSet()
    val lastAvailable = sourceCount.coerceAtLeast(0)
    return AnswerPresentation(
        blocks = blocks,
        availableCitations = citations.filterTo(linkedSetOf()) { it in 1..lastAvailable },
        unavailableCitations = citations.filterTo(linkedSetOf()) { it !in 1..lastAvailable },
    )
}

fun researchActivityLabel(phase: ResearchPhase): String = when (phase) {
    ResearchPhase.Idle -> "Ready for a question"
    ResearchPhase.Searching -> "Searching your library"
    ResearchPhase.Generating -> "Writing from sources"
    ResearchPhase.Complete -> "Answer ready"
    ResearchPhase.Insufficient -> "More evidence needed"
    ResearchPhase.Error -> "Research needs attention"
}

fun formatResearchMetrics(metrics: ResearchMetrics, sourceCount: Int): ResearchMetricsModel {
    val citedCount = metrics.citedSourceIds.size.coerceAtMost(sourceCount.coerceAtLeast(0))
    val rate = if (metrics.totalMillis > 0) {
        String.format(
            Locale.ROOT,
            "%.1f tok/s",
            metrics.generatedTokenCount * 1_000.0 / metrics.totalMillis,
        )
    } else {
        null
    }
    return ResearchMetricsModel(
        retrieval = formatDuration(metrics.retrievalMillis),
        firstToken = metrics.timeToFirstTokenMillis?.let(::formatDuration),
        total = formatDuration(metrics.totalMillis),
        tokenCount = "${metrics.generatedTokenCount} generated tokens",
        tokenRate = rate,
        citationCoverage = "$citedCount of $sourceCount sources cited",
        hasUnmappedCitation = metrics.hasUnmappedCitation,
    )
}

private fun formatDuration(millis: Long): String = if (millis < 1_000) {
    "$millis ms"
} else {
    String.format(Locale.ROOT, "%.2f s", millis / 1_000.0)
}

private fun MarkdownBlock.inlineContent(): List<MarkdownInline> = when (this) {
    is MarkdownBlock.Heading -> content
    is MarkdownBlock.Paragraph -> content
    is MarkdownBlock.ListBlock -> items.flatten()
    is MarkdownBlock.Quote -> content
    is MarkdownBlock.CodeBlock -> emptyList()
}
