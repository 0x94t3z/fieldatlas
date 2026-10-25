package xyz.fieldatlas.ui.research

import xyz.fieldatlas.ui.sharedQuestionBox
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel
import xyz.fieldatlas.ui.theme.TileTone
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.alpha

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ResearchScreen(
    state: ResearchUiState,
    inferenceState: InferenceState,
    collectionCount: Int,
    suggestions: List<String> = emptyList(),
    voiceState: VoiceUiState = VoiceUiState(),
    onMicClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onPhotosClick: () -> Unit = {},
    onFilesClick: () -> Unit = {},
    attachmentNotice: String? = null,
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPrepareModel: () -> Unit,
    onOpenAnswer: () -> Unit,
    onAskAnotherQuestion: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** True when the app answers this question itself (emergency guide, near-me lookup), with no model. */
    answersWithoutModel: (question: String, hasAttachments: Boolean) -> Boolean = { question, hasAttachments ->
        xyz.fieldatlas.research.QuestionRequirements.response(question, hasAttachments) != null
    },
    background: List<BackgroundAnswer> = emptyList(),
    onOpenBackground: (Long) -> Unit = {},
    onDismissBackground: (Long) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var attachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.canAddAttachment) {
        if (!state.canAddAttachment) attachmentMenuOpen = false
    }
    var questionFocused by remember { mutableStateOf(false) }
    // Research starters use saved reference articles; selecting one only fills the draft.
    val exampleQuestions = listOf(
        ExampleQuestion("Biology", "What role do chloroplasts play in photosynthesis?", FieldAtlasIcons.Knowledge, TileTone.Sage),
        ExampleQuestion("Compare", "How do earthquake P waves differ from S waves?", FieldAtlasIcons.Compare, TileTone.Clay),
        ExampleQuestion("Technology", "How does a heat pump work?", FieldAtlasIcons.Technology, TileTone.Gold),
        ExampleQuestion("History", "What was the Berlin Wall?", FieldAtlasIcons.Landmark, TileTone.Paper),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Answers kept off the screen, one row each: the only addition to this screen, and only while there are any.
        if (background.isNotEmpty()) item(key = "background") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                background.forEach { answer ->
                    androidx.compose.runtime.key(answer.id) {
                        BackgroundAnswerRow(answer, onOpen = { onOpenBackground(answer.id) },
                            onDismiss = { onDismissBackground(answer.id) })
                    }
                }
            }
        }
        item {
            FieldAtlasPageHeader(
                // A finished answer is read on its own page and waits here as a row, so this is always the asking view.
                title = "Ask the atlas.",
                subtitle = "Answers from what you carry.",
                modifier = Modifier.padding(top = 22.dp),
                titleStyle = MaterialTheme.typography.displaySmall,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reflects the real connection: research runs on the phone either way.
                val online = xyz.fieldatlas.ui.setup.rememberSetupConnectivity()
                FieldAtlasStatusPill(if (online) "Online" else "Offline", StatusTone.Positive, icon = connectionDino(online))
                FieldAtlasStatusPill(
                    if (collectionCount == 1) "1 collection" else "$collectionCount collections",
                    icon = FieldAtlasIcons.Database,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (collectionCount == 0) {
            item {
                // The same note shape as setup's offline line: a marker, then one short sentence.
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Icon(FieldAtlasIcons.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "No collections yet. Add one in Library for cited answers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Surface(
                // Start research carries this box down into the answer page's question box.
                modifier = Modifier.fillMaxWidth().sharedQuestionBox(),
                shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.CardShape,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (questionFocused || voiceState.phase == VoicePhase.Recording) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
                ),
            ) {
                Column {
                    AttachmentRows(state.activeAttachments, !state.isRunning, onRemoveAttachment, onRetryAttachment)
                    BasicTextField(
                        value = state.question,
                        onValueChange = onQuestionChange,
                        enabled = !state.isRunning,
                        // Spoken words stream into the box; typing at the same time would fight them.
                        readOnly = voiceState.phase == VoicePhase.Recording,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = if (state.activeAttachments.isNotEmpty()) 12.dp else 4.dp)
                            .heightIn(min = if (state.activeAttachments.isNotEmpty() && state.question.isNotBlank()) 48.dp else 96.dp, max = 220.dp)
                            .onFocusChanged { questionFocused = it.isFocused }
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
                            .semantics { contentDescription = "Research question" },
                        decorationBox = { innerTextField ->
                            Box {
                                if (state.question.isEmpty()) {
                                    Text(
                                        "Explain a topic, compare evidence, or examine a claim",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            TextButton(
                                shape = FieldAtlasButtonShape,
                                onClick = { attachmentMenuOpen = true },
                                enabled = state.canAddAttachment,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add a file" },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            ) {
                                Icon(
                                    FieldAtlasIcons.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("Add a file", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                            }
                            if (attachmentMenuOpen && state.canAddAttachment) {
                                AttachmentSheet(
                                    onDismiss = { attachmentMenuOpen = false },
                                    onCamera = { attachmentMenuOpen = false; onCameraClick() },
                                    onPhotos = { attachmentMenuOpen = false; onPhotosClick() },
                                    onFiles = { attachmentMenuOpen = false; onFilesClick() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.activeAttachments.isNotEmpty() && voiceState.phase != VoicePhase.Recording) {
                            Text(if (state.activeAttachments.size == 1) "1 file" else "${state.activeAttachments.size} files", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                        }
                        val recording = voiceState.phase == VoicePhase.Recording
                        if (recording) {
                            VoiceLevelMeter(voiceState.level, Modifier.padding(end = 10.dp))
                        }
                        // The button grows with the microphone level so speaking visibly registers.
                        val pulse by animateFloatAsState(
                            if (recording) 1f + voiceState.level.coerceIn(0f, 1f) * 0.18f else 1f,
                            label = "micLevel",
                        )
                        Surface(
                            shape = FieldAtlasButtonShape,
                            modifier = Modifier.scale(pulse),
                            color = when {
                                recording -> MaterialTheme.colorScheme.primary
                                MaterialTheme.colorScheme.background.luminance() > 0.5f ->
                                    lerp(MaterialTheme.colorScheme.primaryContainer, Color.White, 0.45f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            IconButton(
                                onClick = onMicClick,
                                enabled = !state.isRunning &&
                                    (voiceState.phase == VoicePhase.Idle || voiceState.phase == VoicePhase.Recording),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (recording) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                when (voiceState.phase) {
                                    VoicePhase.Starting, VoicePhase.Processing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    VoicePhase.Recording -> Icon(Icons.Filled.Stop, contentDescription = "Stop recording")
                                    VoicePhase.Idle -> Icon(FieldAtlasIcons.Mic, contentDescription = "Speak question")
                                }
                            }
                        }
                    }
                }
            }
            attachmentNotice?.let { notice ->
                Text(notice, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (voiceState.phase) {
                VoicePhase.Recording -> Row(
                    modifier = Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecordingDot()
                    Text("Listening on this phone", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (voiceState.liveText.isBlank()) "· start speaking" else "· tap stop when done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VoicePhase.Starting, VoicePhase.Processing -> Text(
                    if (voiceState.phase == VoicePhase.Starting) "Preparing offline voice…" else "Transcribing on this device…",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
            voiceState.error?.let { error ->
                Text(
                    error,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            ResearchAction(
                state = state,
                inferenceState = inferenceState,
                answersWithoutModel = answersWithoutModel,
                onSubmit = onSubmit,
                onPrepareModel = onPrepareModel,
            )
        }
        if (exampleQuestions.isNotEmpty() && !state.isRunning) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FieldAtlasSectionLabel("A few places to start", Modifier.weight(1f))
                    Text("Tap to try", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    exampleQuestions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { example ->
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 148.dp)
                                        .semantics { contentDescription = "Use example: ${example.question}" },
                                    onClick = { onQuestionChange(example.question); scope.launch { listState.animateScrollToItem(0) } },
                                    shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.CardShape, color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
                                ) {
                                    Column(Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            FieldAtlasIconTile(example.icon, size = 36.dp, tone = example.tone)
                                            Spacer(Modifier.weight(1f))
                                            Icon(FieldAtlasIcons.OpenExternal, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(example.label,
                                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(example.question, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.completion == ResearchCompletion.Cancelled) {
            item { MessageCard("Research stopped", "Your question is still here when you want to try again.") }
        }
        if (state.phase == ResearchPhase.Insufficient) {
            item {
                MessageCard(
                    "More evidence needed",
                    state.error ?: "Try a narrower question or add a relevant knowledge collection.",
                )
            }
        }
        if (state.phase == ResearchPhase.Error || inferenceState is InferenceState.Failed) {
            item {
                val detail = state.error ?: (inferenceState as? InferenceState.Failed)?.message
                MessageCard("Research needs attention", detail ?: "Try again after preparing the on-device model.")
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 18.dp)) }
    }
}

/** How a follow-up is searched, as one standalone question, with Edit to correct it. */
@Composable
private fun ResearchAction(
    state: ResearchUiState,
    inferenceState: InferenceState,
    answersWithoutModel: (String, Boolean) -> Boolean,
    onSubmit: () -> Unit,
    onPrepareModel: () -> Unit,
) {
    // Missing inputs, emergency guides and near-me lookups are answered without the language
    // model, so they must not wait for it to load (or fail to load on a low-memory phone).
    // Once per question, not on every redraw.
    val withoutModel = remember(state.question, state.activeAttachments.isNotEmpty()) { answersWithoutModel(state.question, state.activeAttachments.isNotEmpty()) }
    if (withoutModel) {
        FieldAtlasPrimaryButton(
            text = "Start research",
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            leadingIcon = FieldAtlasIcons.ResearchSparkles,
            leadingIconSize = 26.dp,
        )
        return
    }
    when (inferenceState) {
        // A model busy with an answer kept off the screen still takes a question: it waits its turn, and the home's
        // "Still answering" row shows the other one. The old progress card that stood here is gone (24 Oct).
        InferenceState.Ready, InferenceState.Generating -> FieldAtlasPrimaryButton(
            text = "Start research",
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            leadingIcon = FieldAtlasIcons.ResearchSparkles,
            leadingIconSize = 26.dp,
        )
        InferenceState.Loading -> FieldAtlasCard(Modifier.fillMaxWidth()) {
            Text("Loading your saved model")
            LinearProgressIndicator(Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer)
        }
        InferenceState.Idle -> FieldAtlasPrimaryButton(
            text = "Prepare for research",
            onClick = onPrepareModel,
            modifier = Modifier.fillMaxWidth(),
        )
        is InferenceState.Failed -> FieldAtlasPrimaryButton(
            text = "Try preparing again",
            onClick = onPrepareModel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Keeps the engine's prompt-reading reports with their arrival times and re-estimates progress
 * a few times a second, so the bar keeps moving between the engine's block reports.
 */
@Composable
internal fun rememberPrefillEstimate(state: ResearchUiState): PrefillEstimate? {
    val reading = state.phase == ResearchPhase.Generating && state.tokensWritten == 0
    val startedAt = remember(state.startedAtNanos, reading) { android.os.SystemClock.uptimeMillis() }
    val samples = remember(state.startedAtNanos) { mutableStateListOf<PrefillSample>() }
    LaunchedEffect(state.promptRead) {
        state.promptRead?.let { (read, total) ->
            if (samples.lastOrNull()?.read != read) samples += PrefillSample(read, total, android.os.SystemClock.uptimeMillis())
        }
    }
    var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(reading) {
        while (reading) {
            now = android.os.SystemClock.uptimeMillis()
            delay(200)
        }
    }
    if (!reading) return null
    return estimatePrefill(startedAt, samples, now)
}

private data class ExampleQuestion(val label: String, val question: String, val icon: ImageVector, val tone: TileTone)

@Composable
private fun MessageCard(title: String, message: String) {
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Five bars that follow the microphone level, newest on the right, so speech visibly registers. */
@Composable
internal fun VoiceLevelMeter(level: Float, modifier: Modifier = Modifier) {
    val history = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    LaunchedEffect(level) {
        history.removeAt(0)
        history.add(level.coerceIn(0f, 1f))
    }
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        history.forEach { value ->
            // Square root lifts quiet speech, which sits low on a linear loudness scale.
            val height by animateFloatAsState(6f + kotlin.math.sqrt(value) * 22f, label = "voiceBar")
            Box(Modifier.width(4.dp).height(height.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        }
    }
}

/** A softly pulsing red dot: the conventional "recording now" mark. */
@Composable
internal fun RecordingDot() {
    val pulse = rememberInfiniteTransition(label = "recordingDot")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recordingDotAlpha",
    )
    Box(Modifier.size(10.dp).alpha(alpha).background(MaterialTheme.colorScheme.error, CircleShape))
}

/**
 * The pixel dinosaur stands still offline and walks while the phone is online, two frames a step. With the
 * phone's animations turned off (an accessibility setting) it stays still.
 */
@Composable
internal fun connectionDino(online: Boolean): androidx.compose.ui.graphics.vector.ImageVector {
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val walking = online && !still
    val step by androidx.compose.runtime.produceState(0, walking) {
        while (walking) {
            kotlinx.coroutines.delay(DINO_STEP_MILLIS)
            value = 1 - value
        }
    }
    return when {
        !walking -> FieldAtlasIcons.Offline
        step == 0 -> FieldAtlasIcons.DinoWalkA
        else -> FieldAtlasIcons.DinoWalkB
    }
}

private const val DINO_STEP_MILLIS = 220L
