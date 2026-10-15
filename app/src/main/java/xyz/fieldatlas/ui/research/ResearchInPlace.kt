package xyz.fieldatlas.ui.research

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

/*
 * While research runs, the answer page is already shaped like the answer: the pixel dino walks the Search, Read,
 * Write trail, one line says what is happening, a placeholder fills as the model writes, and the passages being read
 * are listed as the sources the answer will cite. Nothing here is a made-up figure: the dino's place is the step plus
 * what the app measures (search progress, prompt reading), and the placeholder only shows that words are arriving.
 */

/** Where the stages sit along the trail, as fractions of its width: Search, Read, Write, and the answer. */
private val STOPS = floatArrayOf(0.06f, 0.37f, 0.68f, 0.96f)
private val STAGES = listOf("Search", "Read", "Write")

/** 0 searching, 1 reading the passages, 2 writing (and checking) the answer. */
internal fun researchStage(state: ResearchUiState): Int = when {
    state.tokensWritten > 0 || state.checking -> 2
    state.phase == ResearchPhase.Generating -> 1
    else -> 0
}

/**
 * The dino's place along the trail, 0..1: its stage plus what that stage measures. Searching moves with the packs
 * searched, reading with the prompt read; writing has no known length, so the dino stays at Write, and moves on
 * towards the answer only once the draft is being checked.
 */
internal fun trailPosition(state: ResearchUiState, readFraction: Float?): Float {
    if (state.waitingFor != null) return STOPS[0]
    val stage = researchStage(state)
    val within = when (stage) {
        0 -> state.retrievalProgress.toFloat()
        1 -> readFraction ?: 0f
        else -> if (state.checking) 1f else 0f
    }.coerceIn(0f, 1f)
    // Short of the next flag: reaching it is the next stage's news.
    return STOPS[stage] + (STOPS[stage + 1] - STOPS[stage]) * within * 0.85f
}

/** What the status line says is happening, in words a reader uses. */
internal fun researchStatusTitle(state: ResearchUiState): String = if (state.waitingFor != null) "Waiting for your last question" else when (researchStage(state)) {
    0 -> if (state.phase == ResearchPhase.Planning) "Finding better search words"
        else if (state.attachments.isNotEmpty()) "Searching your files" else "Searching your collections"
    1 -> when (state.sources.size) {
        0 -> "Preparing the model"
        1 -> "Reading 1 passage"
        else -> "Reading ${state.sources.size} passages"
    }
    else -> if (state.checking) "Checking the answer" else "Writing the answer"
}

/** Roughly how many words the model has written: three words to four tokens, in tens. */
internal fun wordsWritten(tokens: Int): Int = (tokens * 3 / 4) / 10 * 10

/**
 * How long this phone's recent answers took: the median of the last five researched answers, once there are three.
 * Instant replies (guides, sums, lookups) say nothing about a model's answer and are left out.
 */
internal fun typicalAnswerMillis(records: List<xyz.fieldatlas.research.AnswerRecord>): Long? {
    val recent = records.sortedByDescending { it.createdAtEpochMs }.mapNotNull { it.durationMs }
        .filter { it >= MIN_RESEARCHED_MILLIS }.take(5)
    if (recent.size < 3) return null
    return recent.sorted()[recent.size / 2]
}

private const val MIN_RESEARCHED_MILLIS = 15_000L
private const val TOKENS_PER_LINE = 40f
private const val DINO_STEP = 220L

@Composable
private fun stillMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The trail, the status line and what to expect. */
@Composable
internal fun ResearchTrailStatus(state: ResearchUiState, typicalMillis: Long?) {
    val estimate = rememberPrefillEstimate(state)
    val readFraction = estimate?.fraction ?: state.promptRead?.takeIf { it.second > 0 }
        ?.let { (read, total) -> (read.toFloat() / total).coerceIn(0f, 1f) }
    val still = stillMotion()
    val position by animateFloatAsState(trailPosition(state, readFraction),
        animationSpec = tween(if (still) 0 else 700), label = "Trail")
    val stage = researchStage(state)
    var elapsedMillis by remember(state.startedAtNanos) { mutableLongStateOf(0L) }
    LaunchedEffect(state.startedAtNanos) {
        val start = state.startedAtNanos ?: return@LaunchedEffect
        while (true) {
            elapsedMillis = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    // The dino walks while work is done for this question, and stands while it waits its turn.
    val walking = !still && state.waitingFor == null
    val step by produceState(0, walking) {
        while (walking) { delay(DINO_STEP); value = 1 - value }
    }
    val title = researchStatusTitle(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Trail(stage, position, if (!walking || step == 0) FieldAtlasIcons.DinoWalkA else FieldAtlasIcons.DinoWalkB,
            Modifier.semantics {
                contentDescription = "Research progress: step ${stage + 1} of 3, ${STAGES[stage]}"
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
            })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // One line that wraps as text: the step, then the time so far. Announced when the step changes, not
            // every second, so the time is left out of what is read aloud.
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(title)
                    if (state.startedAtNanos != null) {
                        append("  ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = muted, fontWeight = FontWeight.Normal, fontSize = 13.sp))
                        append(formatResearchElapsed(elapsedMillis))
                        pop()
                    }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight(700)),
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = title
                    liveRegion = LiveRegionMode.Polite
                },
            )
            val right = when {
                stage == 1 -> estimate?.secondsLeft?.let(::formatTimeLeft)
                stage == 2 && !state.checking && state.tokensWritten > 0 -> wordsWritten(state.tokensWritten)
                    .takeIf { it >= 10 }?.let { "about $it words" }
                else -> null
            }
            right?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            state.waitingFor?.let { "One answer at a time on this phone: this starts when “$it” is saved to History." }
                ?: typicalMillis?.let { "Usually about ${((it + 30_000) / 60_000).coerceAtLeast(1)} min on this phone · keeps going if you switch apps." }
                ?: "Keeps going if you switch apps. On slower phones an answer takes 2 to 5 minutes.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Trail(stage: Int, position: Float, dino: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier.fillMaxWidth().height(72.dp)) {
        val inset = 10.dp
        val usable = maxWidth - inset * 2
        val lineY = 44.dp
        fun x(fraction: Float) = inset + usable * fraction
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val y = lineY.toPx()
            val start = inset.toPx()
            val end = start + usable.toPx()
            drawLine(track, Offset(start, y), Offset(end, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            drawLine(primary, Offset(start, y), Offset(start + usable.toPx() * position, y), strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round)
        }
        STAGES.forEachIndexed { index, name ->
            val done = index < stage
            Surface(
                modifier = Modifier.offset(x = x(STOPS[index]) - 11.dp, y = lineY - 11.dp).size(22.dp),
                shape = CircleShape,
                color = if (done) primary else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = if (done) null else BorderStroke(2.dp, if (index == stage) primary else track),
            ) {
                if (done) Box(contentAlignment = Alignment.Center) { Icon(FieldAtlasIcons.Check, null, Modifier.size(13.dp)) }
            }
            Text(name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp,
                fontWeight = if (index <= stage) FontWeight(650) else FontWeight.Medium),
                color = if (index <= stage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = x(STOPS[index]) - 24.dp, y = lineY + 13.dp).width(48.dp)
                    .clearAndSetSemantics {},
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        }
        // Where the answer will be.
        Surface(
            modifier = Modifier.offset(x = x(STOPS[3]) - 11.dp, y = lineY - 11.dp).size(22.dp),
            shape = CircleShape, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, track),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(FieldAtlasIcons.Document, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(dino, contentDescription = null, tint = primary,
            modifier = Modifier.offset(x = x(position) - 16.dp, y = 0.dp).size(32.dp))
    }
}

/**
 * The answer's place: lines that fill as the model writes (no draft is shown before it is checked), or the draft
 * itself on the few paths that stream it. The caption says why the text waits.
 */
@Composable
internal fun ForthcomingAnswer(state: ResearchUiState) {
    val stage = researchStage(state)
    val still = stillMotion()
    val written by animateFloatAsState(if (stage == 2) state.tokensWritten / TOKENS_PER_LINE else 0f,
        animationSpec = tween(if (still) 0 else 600), label = "Written")
    val draftBlocks = remember(state.answer) { parseAnswerMarkdown(draftAnswerPreview(state.answer)) }
    val sources = state.sources.size
    val caption = when {
        stage == 0 -> "Your answer will appear here, with the sources it cites."
        sources == 0 -> "No saved passage matched: this answer comes from the model alone."
        stage == 1 -> "Written next, from ${if (sources == 1) "this passage" else "these $sources passages"} only."
        else -> "Shown once it is checked against your ${if (sources == 1) "source" else "$sources sources"}."
    }
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        if (draftBlocks.isNotEmpty()) {
            AnswerMarkdownRenderer(blocks = draftBlocks, sourceCount = 0, onCitation = {}, modifier = Modifier.fillMaxWidth())
        } else {
            val empty = MaterialTheme.colorScheme.surfaceVariant
            val filled = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(11.dp)) {
                LINE_WIDTHS.forEachIndexed { index, width ->
                    if (index == PARAGRAPH_BREAK) Spacer(Modifier.height(4.dp))
                    val fill = (written - index).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(width).height(11.dp).background(empty, RoundedCornerShape(6.dp))) {
                        if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(11.dp).background(filled, RoundedCornerShape(6.dp)))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FieldAtlasIcons.Citation, null, Modifier.padding(top = 2.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LINE_WIDTHS = listOf(0.96f, 1f, 0.88f, 1f, 0.62f, 1f, 0.94f, 1f, 0.71f)
private const val PARAGRAPH_BREAK = 5

/** The passages being read, as the source cards the answer will cite; before they are found, where they will be. */
@Composable
internal fun SourcesSoFar(state: ResearchUiState) {
    val stage = researchStage(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (stage == 1) "Being read" else "Sources", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(650)))
        if (stage == 0 || state.sources.isEmpty()) {
            Text(if (stage == 0) "Found passages appear here as the search finds them."
                else "No saved passage matched this question.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else state.sources.take(SOURCE_ROWS).forEachIndexed { index, evidence -> SourceSoFarRow(index + 1, evidence) }
        if (stage > 0 && state.sources.size > SOURCE_ROWS) Text("and ${state.sources.size - SOURCE_ROWS} more",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val SOURCE_ROWS = 5

@Composable
private fun SourceSoFarRow(number: Int, evidence: Evidence) {
    val tile = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant
        else xyz.fieldatlas.ui.theme.FieldAtlasColors.SageWash
    val presentation = xyz.fieldatlas.ui.sourcePresentation(evidence)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.size(36.dp), shape = MaterialTheme.shapes.small, color = tile) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "Source $number" })
                }
            }
            Column(Modifier.weight(1f)) {
                Text(evidence.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (presentation.isAttachment) presentation.metadata else xyz.fieldatlas.ui.sourceDisplayName(evidence.source),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * What the run reads besides the title, as (label, text): a follow-up's standalone reading, or the extra search words
 * the app chose when the first search found too little. Null when the question is searched just as it was typed.
 */
internal fun researchReading(state: ResearchUiState): Pair<String, String>? = when {
    state.asked.isNotBlank() -> "Read as" to state.question
    state.keywords.isNotEmpty() -> "Also searching for" to state.keywords.take(6).joinToString(", ")
    else -> null
}

/** One quiet line under the title: a search icon, the label, the reading in two lines at most, and Edit if offered. */
@Composable
internal fun ReadingLine(label: String, reading: String, onEdit: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(FieldAtlasIcons.Research, null, Modifier.padding(top = 3.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("$label ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold))
                append(reading)
                pop()
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 1.dp),
        )
        // The touch area stays 48dp though the word is small; it overlaps the line's padding, not the trail.
        if (onEdit != null) androidx.compose.material3.TextButton(
            onClick = onEdit,
            modifier = Modifier.offset(y = (-12).dp).heightIn(min = 48.dp)
                .semantics { contentDescription = "Edit how this follow-up is read" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) { Text("Edit", style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * One line on the Research home for the answer New topic left running: "Still answering …" with the time so far, which
 * reopens its page, then "Saved to History …" or "No answer for …" once it ends.
 */
@Composable
internal fun BackgroundAnswerRow(answer: BackgroundAnswer, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var elapsedMillis by remember(answer.startedAtNanos) { mutableLongStateOf(0L) }
    LaunchedEffect(answer.startedAtNanos, answer.running) {
        val start = answer.startedAtNanos ?: return@LaunchedEffect
        while (answer.running) {
            elapsedMillis = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    val still = stillMotion()
    val step by produceState(0, answer.running && !still) {
        while (answer.running && !still) { delay(DINO_STEP); value = 1 - value }
    }
    val label = when {
        answer.running -> "Still answering"
        answer.done -> "Saved to History"
        else -> "No answer for"
    }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                when {
                    !answer.running -> if (answer.done) FieldAtlasIcons.Check else FieldAtlasIcons.Info
                    step == 0 -> FieldAtlasIcons.DinoWalkA
                    else -> FieldAtlasIcons.DinoWalkB
                },
                contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("$label ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(answer.question)
                    pop()
                    if (answer.running && answer.startedAtNanos != null) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        append(" · " + formatResearchElapsed(elapsedMillis))
                        pop()
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (answer.running) Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(FieldAtlasIcons.Close, "Dismiss", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
