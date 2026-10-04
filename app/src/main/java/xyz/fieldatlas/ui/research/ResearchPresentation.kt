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
            // Short enough to stay on one line beside a "1m 12s" timer and the Stop button.
            hasSources -> "Reading sources…"
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
    /** "1,214 tokens in 66.0 s (18.4 tok/s)": what the wait before the first word was spent on. */
    val promptReading: String? = null,
    val citationCoverage: String,
    val hasUnmappedCitation: Boolean,
)

data class AnswerPresentation(
    val blocks: List<MarkdownBlock>,
    val availableCitations: Set<Int>,
    val unavailableCitations: Set<Int>,
)

/** A short, non-interactive draft. Citation chips become actionable in the final answer. */
/**
 * The live draft while the model writes. It follows the newest text: showing only the opening
 * froze the card once an answer outgrew it, although words were still arriving. A long draft
 * starts at a line break (or a word) so the visible part never opens mid-word.
 */
fun draftAnswerPreview(answer: String, maxChars: Int = 650): String {
    val text = AnswerText.visible(answer)
        .replace(Regex("\\[S[0-9#]*]?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
    if (text.length <= maxChars) return text
    val tail = text.substring(text.length - maxChars)
    val lineStart = tail.indexOf('\n').takeIf { it in 0 until maxChars / 2 }
    val cut = lineStart?.let { tail.substring(it + 1) } ?: tail.substringAfter(' ', tail)
    return "…" + cut.trimStart()
}

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
    // Writing speed counts the writing only; dividing by the whole wait (search and prompt
    // reading included) showed 1.6 tok/s for a model writing at 4.
    val writingMillis = metrics.totalMillis - (metrics.timeToFirstTokenMillis ?: 0L)
    val rate = if (writingMillis > 0 && metrics.generatedTokenCount > 0) {
        String.format(
            Locale.ROOT,
            "%.1f tok/s",
            metrics.generatedTokenCount * 1_000.0 / writingMillis,
        )
    } else {
        null
    }
    val reading = metrics.promptTokens?.let { tokens ->
        metrics.promptMillis?.takeIf { it > 0 }?.let { millis ->
            String.format(Locale.ROOT, "%,d tokens in %.1f s (%.1f tok/s)", tokens, millis / 1_000.0, tokens * 1_000.0 / millis)
        }
    }
    return ResearchMetricsModel(
        retrieval = formatDuration(metrics.retrievalMillis),
        firstToken = metrics.timeToFirstTokenMillis?.let(::formatDuration),
        total = formatDuration(metrics.totalMillis),
        tokenCount = if (metrics.generatedTokenCount == 0) "No model generation" else
            "${metrics.generatedTokenCount} generated tokens",
        tokenRate = rate,
        promptReading = reading,
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

/** One engine report while the model reads its prompt: [read] of [total] tokens at [atMillis]. */
internal data class PrefillSample(val read: Int, val total: Int, val atMillis: Long)

internal data class PrefillEstimate(val fraction: Float, val secondsLeft: Long?)

/**
 * The engine reports prompt reading once per block (256 tokens), several seconds apart on a slow
 * phone, so the bar sat still and then jumped. Between reports this moves it at the speed measured
 * since reading began, but never past the end of the block being read, so it cannot run ahead of
 * the engine. Null until the first report: the bar stays indeterminate until there is a speed.
 */
internal fun estimatePrefill(startedAtMillis: Long, samples: List<PrefillSample>, nowMillis: Long): PrefillEstimate? {
    val last = samples.lastOrNull()?.takeIf { it.total > 0 && it.read > 0 } ?: return null
    val elapsed = (last.atMillis - startedAtMillis).coerceAtLeast(1)
    val tokensPerMilli = last.read.toDouble() / elapsed
    val block = (last.read - (samples.getOrNull(samples.size - 2)?.read ?: 0)).coerceAtLeast(1)
    // Stop just short of the next report, which is the only proof that block was read.
    val ceiling = minOf(last.total.toDouble(), last.read + block * 0.95)
    val estimate = minOf(ceiling, last.read + tokensPerMilli * (nowMillis - last.atMillis).coerceAtLeast(0))
    val secondsLeft = if (estimate >= last.total) null
        else ((last.total - estimate) / tokensPerMilli / 1_000).toLong().coerceAtLeast(1)
    return PrefillEstimate((estimate / last.total).toFloat().coerceIn(0f, 1f), secondsLeft)
}

/** "about 20 s left" in steps that do not flicker every frame. */
internal fun formatTimeLeft(seconds: Long): String = when {
    seconds <= 3 -> "almost done"
    seconds < 60 -> "about ${((seconds + 4) / 5 * 5)} s left"
    else -> "about ${(seconds + 30) / 60} min left"
}

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
internal fun researchSteps(state: ResearchUiState, readEstimate: PrefillEstimate? = null): List<ResearchStep> {
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
        detail = if (readEstimate != null && searched && !writing) {
            listOfNotNull("${(readEstimate.fraction * 100).toInt().coerceIn(0, 100)}% read",
                readEstimate.secondsLeft?.let(::formatTimeLeft)).joinToString(" · ")
        } else state.promptRead?.takeIf { searched && !writing && it.second > 0 }
            ?.let { (read, total) -> "${(read * 100L / total).coerceIn(0, 100)}% read" },
        progress = readEstimate?.fraction ?: state.promptRead?.takeIf { it.second > 0 }
            ?.let { (read, total) -> (read.toFloat() / total).coerceIn(0f, 1f) },
    )
    val write = ResearchStep(
        title = "Write the answer",
        status = if (writing) StepStatus.Active else StepStatus.Pending,
        detail = if (writing) "${state.tokensWritten} tokens written" else null,
    )
    return listOf(search, read, write)
}
