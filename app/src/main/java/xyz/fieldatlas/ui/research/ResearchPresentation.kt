package xyz.fieldatlas.ui.research

import java.util.Locale
import xyz.fieldatlas.research.ResearchCompletion as DomainResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics

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
