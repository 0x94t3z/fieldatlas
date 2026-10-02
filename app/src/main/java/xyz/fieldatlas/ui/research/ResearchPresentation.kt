package xyz.fieldatlas.ui.research

import java.util.Locale
import xyz.fieldatlas.research.ResearchCompletion as DomainResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.research.AnswerText
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.markdown.plainText

typealias ResearchCompletion = DomainResearchCompletion

internal fun researchProgressHeading(phase: ResearchPhase, answer: String, hasSources: Boolean): String =
    when (phase) {
        ResearchPhase.Planning -> "Understanding your question…"
        ResearchPhase.Searching -> "Searching saved sources…"
        ResearchPhase.Generating -> when {
            answer.isNotBlank() -> "Writing your answer…"
            hasSources -> "Reading saved sources…"
            else -> "Preparing your answer…"
        }
        else -> "Writing your answer…"
    }

internal fun readyCitationCount(answer: String, sourceCount: Int): Int =
    buildAnswerPresentation(answer, sourceCount).availableCitations.size

internal fun formatResearchElapsed(totalMillis: Long): String {
    val seconds = totalMillis.coerceAtLeast(0) / 1_000
    if (seconds < 60) return "${seconds}s"
    return "${seconds / 60}m ${seconds % 60}s"
}

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

/** A short, non-interactive draft. Citation chips become actionable in the final answer. */
fun draftAnswerPreview(answer: String): String = AnswerText.visible(answer)
    .take(650)
    .replace(Regex("\\[S[0-9#]*]?", RegexOption.IGNORE_CASE), "")
    .replace(Regex("[ \\t]{2,}"), " ")
    .trim()

/** A real answer excerpt for the Research card, without raw Markdown or citation syntax. */
fun answerCardPreview(answer: String): String {
    val blocks = parseAnswerMarkdown(AnswerText.visible(answer))
    val firstBody = blocks.firstOrNull {
        it is MarkdownBlock.Paragraph || it is MarkdownBlock.ListBlock || it is MarkdownBlock.Quote
    } ?: blocks.firstOrNull()
    val text = firstBody?.plainText()
        ?.replace(Regex("\\[S\\d+]"), "")
        // Pronunciation guides ("Buoyancy (/ˈbɔɪənsi/)") crowd out the first words of a
        // two-line preview; the full answer keeps them verbatim.
        ?.replace(Regex("\\s*\\(/[^)]*\\)"), "")
        ?.replace(Regex("\\s+"), " ")
        ?.replace(Regex("\\s+([.,;:!?])"), "$1")
        ?.trim()
        .orEmpty()
    val labelled = if (answer.startsWith("## Model explanation—not verified against saved sources")) {
        "Model explanation (unverified): $text"
    } else text
    return if (labelled.length <= 170) labelled else labelled.take(169).trimEnd() + "…"
}

fun buildAnswerPresentation(answer: String, sourceCount: Int): AnswerPresentation {
    val blocks = parseAnswerMarkdown(answer)
    val citations = blocks.flatMap(MarkdownBlock::inlineContent)
        .flatMap(MarkdownInline::citationNumbers)
        .toSet()
    val lastAvailable = sourceCount.coerceAtLeast(0)
    return AnswerPresentation(
        blocks = blocks,
        availableCitations = citations.filterTo(linkedSetOf()) { it in 1..lastAvailable },
        unavailableCitations = citations.filterTo(linkedSetOf()) { it !in 1..lastAvailable },
    )
}

fun researchActivityLabel(
    phase: ResearchPhase,
    retrievalProgress: Double = 0.0,
    promptRead: Pair<Int, Int>? = null,
    tokensWritten: Int = 0,
    vectorMatches: Int = 0,
    hasSources: Boolean = true,
): String {
    val readSuffix = promptRead
        ?.takeIf { (read, total) -> total > 0 && read <= total && tokensWritten == 0 }
        ?.let { (read, total) -> " ($read/$total tokens read)" }
        .orEmpty()
    return when (phase) {
        ResearchPhase.Idle -> "Ready for a question"
        ResearchPhase.Planning -> "Generating search keywords$readSuffix"
        ResearchPhase.Searching -> if (retrievalProgress >= 0.02) {
            val found = if (vectorMatches > 0) "+$vectorMatches concept matches, " else ""
            "Searching your library ($found${(retrievalProgress * 100).toInt().coerceIn(1, 99)}%)"
        } else {
            "Searching your library"
        }
        ResearchPhase.Generating -> if (hasSources) when {
            tokensWritten > 0 -> "Writing an offline answer ($tokensWritten tokens written)"
            readSuffix.isNotEmpty() -> "Reading from sources$readSuffix"
            else -> "Writing an offline answer"
        } else when {
            tokensWritten > 0 -> "Writing an offline answer ($tokensWritten tokens written)"
            readSuffix.isNotEmpty() -> "Reading the question$readSuffix"
            else -> "Writing an offline answer"
        }
        ResearchPhase.Complete -> "Answer ready"
        ResearchPhase.Insufficient -> "More evidence needed"
        ResearchPhase.Error -> "Research needs attention"
    }
}

fun formatResearchMetrics(metrics: ResearchMetrics, sourceCount: Int): ResearchMetricsModel {
    val citedCount = metrics.citedSourceIds.size.coerceAtMost(sourceCount.coerceAtLeast(0))
    val rate = if (metrics.totalMillis > 0 && metrics.generatedTokenCount > 0) {
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
        tokenCount = if (metrics.generatedTokenCount == 0) "No model generation" else
            "${metrics.generatedTokenCount} generated tokens",
        tokenRate = rate,
        citationCoverage = if (sourceCount == 0) "No local sources cited" else "$citedCount of $sourceCount sources cited",
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
    is MarkdownBlock.Table -> headers.flatten() + rows.flatten().flatten()
    MarkdownBlock.ThematicBreak -> emptyList()
}

private fun MarkdownInline.citationNumbers(): List<Int> = when (this) {
    is MarkdownInline.Citation -> listOf(number)
    is MarkdownInline.Strong -> content.flatMap(MarkdownInline::citationNumbers)
    is MarkdownInline.Emphasis -> content.flatMap(MarkdownInline::citationNumbers)
    is MarkdownInline.Strikethrough -> content.flatMap(MarkdownInline::citationNumbers)
    is MarkdownInline.Link -> label.flatMap(MarkdownInline::citationNumbers)
    is MarkdownInline.Text, is MarkdownInline.Code -> emptyList()
}

internal enum class StepStatus { Done, Active, Pending }

internal data class ResearchStep(
    val title: String,
    val status: StepStatus,
    val detail: String? = null,
    /** 0..1 when the step can measure itself; null draws an indeterminate bar. */
    val progress: Float? = null,
)

/**
 * The three stages a question passes through on the phone. A source lead can appear before the
 * model writes, so "writing" starts with the first model token, not with the first answer text.
 */
internal fun researchSteps(state: ResearchUiState): List<ResearchStep> {
    val searched = state.phase == ResearchPhase.Generating || state.phase == ResearchPhase.Complete
    val writing = state.tokensWritten > 0
    val passages = state.sources.size
    val search = ResearchStep(
        title = "Search your collections",
        status = if (searched) StepStatus.Done else StepStatus.Active,
        detail = when {
            searched -> when (passages) {
                0 -> "No matching passages"
                1 -> "1 passage found"
                else -> "$passages passages found"
            }
            state.keywords.isNotEmpty() -> "Looking for ${state.keywords.take(4).joinToString(", ")}"
            else -> null
        },
    )
    val read = ResearchStep(
        title = if (searched && passages == 0) "Prepare the model" else "Read the sources",
        status = when {
            writing -> StepStatus.Done
            searched -> StepStatus.Active
            else -> StepStatus.Pending
        },
        detail = state.promptRead?.takeIf { searched && !writing && it.second > 0 }
            ?.let { (read, total) -> "${(read * 100L / total).coerceIn(0, 100)}% read" },
        progress = state.promptRead?.takeIf { it.second > 0 }
            ?.let { (read, total) -> (read.toFloat() / total).coerceIn(0f, 1f) },
    )
    val write = ResearchStep(
        title = "Write the answer",
        status = if (writing) StepStatus.Active else StepStatus.Pending,
        detail = if (writing) "${state.tokensWritten} tokens written" else null,
    )
    return listOf(search, read, write)
}
