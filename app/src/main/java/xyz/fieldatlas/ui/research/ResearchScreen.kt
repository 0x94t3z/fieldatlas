package xyz.fieldatlas.ui.research

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
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
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
    onOpenAnswer: () -> Unit,
    onAskAnotherQuestion: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** True when the app answers this question itself (emergency guide, near-me lookup), with no model. */
    answersWithoutModel: (question: String, hasAttachments: Boolean) -> Boolean = { question, hasAttachments ->
        xyz.fieldatlas.research.QuestionRequirements.response(question, hasAttachments) != null
    },
) {
    val scope = rememberCoroutineScope()
    var attachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.canAddAttachment) {
        if (!state.canAddAttachment) attachmentMenuOpen = false
    }
    var questionFocused by remember { mutableStateOf(false) }
    val hasReadyAnswer = state.phase == ResearchPhase.Complete && state.answer.isNotBlank()
    var editReadyQuestion by rememberSaveable(hasReadyAnswer) { mutableStateOf(false) }
    // Research starters use saved reference articles; selecting one only fills the draft.
    val exampleQuestions = listOf(
        ExampleQuestion("Biology", "What role do chloroplasts play in photosynthesis?", FieldAtlasIcons.Knowledge, TileTone.Clay),
        ExampleQuestion("Compare", "How do earthquake P waves differ from S waves?", FieldAtlasIcons.Compare, TileTone.Sage),
        ExampleQuestion("Technology", "How does a heat pump work?", FieldAtlasIcons.Technology, TileTone.Gold),
        ExampleQuestion("History", "What was the Berlin Wall?", FieldAtlasIcons.Landmark, TileTone.Paper),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            if (state.isRunning) {
                Column(Modifier.padding(top = 8.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(top = 4.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text("Researching", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(650)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Looking into your question.", style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp, lineHeight = 33.6.sp, fontWeight = FontWeight(650), letterSpacing = (-0.025f).em))
                        Text("Working locally. Your question stays on this phone.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else FieldAtlasPageHeader(
                title = when {
                    hasReadyAnswer -> "Your research"
                    else -> "What would you\nlike to know?"
                },
                subtitle = when {
                    hasReadyAnswer -> "Your answer is ready to read."
                    else -> "Research with what’s saved on your phone."
                },
                modifier = Modifier.padding(top = if (hasReadyAnswer) 16.dp else 22.dp),
                titleStyle = if (hasReadyAnswer) MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight(650), letterSpacing = (-0.8).sp)
                    else MaterialTheme.typography.displaySmall,
            )
        }
        // The design drops the status chips while research runs, keeping focus on progress.
        if (!state.isRunning) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reflects the real connection: research runs on the phone either way.
                val online = xyz.fieldatlas.ui.setup.rememberSetupConnectivity()
                FieldAtlasStatusPill(if (online) "Works offline" else "Offline now", StatusTone.Positive,
                    icon = FieldAtlasIcons.Offline)
                FieldAtlasStatusPill(
                    if (collectionCount == 1) "1 collection" else "$collectionCount collections",
                    icon = FieldAtlasIcons.Database,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (collectionCount == 0 && !hasReadyAnswer) {
            item {
                Text(
                    "Answers can come from your offline model. Add a knowledge pack in Library for source-backed answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.isRunning || (hasReadyAnswer && !editReadyQuestion)) {
            item {
                ResearchQuestionPanel(
                    question = state.question,
                    onEdit = if (hasReadyAnswer && !state.isRunning) ({ editReadyQuestion = true }) else null,
                    running = state.isRunning,
                )
            }
        } else item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                    AttachmentRows(state.attachments, !state.isRunning, onRemoveAttachment, onRetryAttachment)
                    BasicTextField(
                        value = state.question,
                        onValueChange = onQuestionChange,
                        enabled = !state.isRunning,
                        // Spoken words stream into the box; typing at the same time would fight them.
                        readOnly = voiceState.phase == VoicePhase.Recording,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = if (state.attachments.isNotEmpty()) 12.dp else 4.dp)
                            .heightIn(min = if (state.attachments.isNotEmpty() && state.question.isNotBlank()) 48.dp else 96.dp, max = 220.dp)
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
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add attachment" },
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
                        if (state.attachments.isNotEmpty() && voiceState.phase != VoicePhase.Recording) {
                            Text(if (state.attachments.size == 1) "1 file" else "${state.attachments.size} files", style = MaterialTheme.typography.labelSmall,
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
        if (!hasReadyAnswer) {
            item {
                ResearchAction(
                    state = state,
                    inferenceState = inferenceState,
                    answersWithoutModel = answersWithoutModel,
                    onSubmit = onSubmit,
                    onStop = onStop,
                    onPrepareModel = onPrepareModel,
                )
            }
        }
        if (hasReadyAnswer) {
            item {
                val citedSourceCount = readyCitationCount(state.answer, state.sources.size)
                FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackTiming = maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                    val timing: @Composable () -> Unit = {
                        state.metrics?.totalMillis?.let { duration ->
                            Text("Took ${formatResearchElapsed(duration)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = "Completed in ${formatResearchElapsed(duration)}" })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FieldAtlasIcons.Document, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Answer ready", style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight(650), letterSpacing = (-0.3).sp))
                            Text(
                                if (citedSourceCount == 0 && state.metrics?.generatedTokenCount == 0) {
                                    "Lookup · no model generation"
                                } else if (citedSourceCount == 0) {
                                    "Model-generated · no sources cited"
                                } else {
                                    "$citedSourceCount local " +
                                        if (citedSourceCount == 1) "source cited." else "sources cited."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (stackTiming) timing()
                        }
                        if (!stackTiming) timing()
                    }
                    }
                    val preview = answerCardPreview(state.answer)
                    if (preview.isNotEmpty()) {
                        Text(preview, style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = xyz.fieldatlas.ui.theme.FieldAtlasEditorial, fontSize = 16.sp, lineHeight = 26.sp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    FieldAtlasPrimaryButton(
                        text = "Read answer",
                        onClick = onOpenAnswer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                TextButton(shape = FieldAtlasButtonShape, onClick = {
                    onAskAnotherQuestion()
                    scope.launch { listState.animateScrollToItem(0) }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ask another question")
                }
            }
        }
        if (exampleQuestions.isNotEmpty() && !state.isRunning && !hasReadyAnswer) {
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

@Composable
internal fun ResearchQuestionPanel(question: String, onEdit: (() -> Unit)?, running: Boolean = false) {
    if (running) {
        FieldAtlasCard(Modifier.fillMaxWidth()) {
            Text("YOUR QUESTION", style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp, fontWeight = FontWeight(650), letterSpacing = 0.6.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight(450)))
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your question", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(FieldAtlasIcons.Edit, contentDescription = "Edit question",
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(question, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge, fontFamily = xyz.fieldatlas.ui.theme.FieldAtlasSans,
                fontWeight = FontWeight.Medium)
        }
    }
}


@Composable
private fun ResearchAction(
    state: ResearchUiState,
    inferenceState: InferenceState,
    answersWithoutModel: (String, Boolean) -> Boolean,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
) {
    if (state.isRunning || inferenceState == InferenceState.Generating) {
        var elapsedMillis by remember(state.startedAtNanos) { mutableLongStateOf(0L) }
        LaunchedEffect(state.startedAtNanos) {
            val start = state.startedAtNanos ?: return@LaunchedEffect
            while (true) {
                elapsedMillis = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(0L)
                delay(1_000)
            }
        }
        val modelWriting = state.tokensWritten > 0
        FieldAtlasCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // A source lead arrives before the model writes; until then the model is still reading.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(researchProgressHeading(state.phase, if (modelWriting) state.answer else "", state.sources.isNotEmpty()),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp,
                            fontWeight = FontWeight(650)))
                    if (state.startedAtNanos != null) {
                        Text(formatResearchElapsed(elapsedMillis), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = "${formatResearchElapsed(elapsedMillis)} elapsed" })
                    }
                }
                FilledTonalButton(shape = FieldAtlasButtonShape, onClick = onStop, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )) { Text("Stop") }
            }
            ResearchSteps(researchSteps(state, rememberPrefillEstimate(state)))
            if (state.phase == ResearchPhase.Generating && state.sources.isEmpty()) {
                Text("Model-generated · no supporting sources found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Parsed once per answer change, not on every progress tick.
            val draftBlocks = remember(state.answer) { parseAnswerMarkdown(draftAnswerPreview(state.answer)) }
            if (draftBlocks.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AnswerMarkdownRenderer(
                    blocks = draftBlocks,
                    sourceCount = 0,
                    onCitation = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }
    // Missing inputs, emergency guides and near-me lookups are answered without the language
    // model, so they must not wait for it to load (or fail to load on a low-memory phone).
    if (answersWithoutModel(state.question, state.attachments.isNotEmpty())) {
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
        InferenceState.Ready -> FieldAtlasPrimaryButton(
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
        InferenceState.Generating -> Unit
    }
}

/**
 * Keeps the engine's prompt-reading reports with their arrival times and re-estimates progress
 * a few times a second, so the bar keeps moving between the engine's block reports.
 */
@Composable
private fun rememberPrefillEstimate(state: ResearchUiState): PrefillEstimate? {
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
private fun ResearchSteps(steps: List<ResearchStep>) {
    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StepMarker(index + 1, step.status)
                    if (index < steps.lastIndex) {
                        Box(Modifier.width(2.dp).weight(1f).heightIn(min = 18.dp)
                            .background(if (step.status == StepStatus.Done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(top = 1.dp, bottom = if (index < steps.lastIndex) 18.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(step.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (step.status == StepStatus.Pending) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface)
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (step.status == StepStatus.Active) {
                        val indicator = Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp)
                            .semantics { contentDescription = "Research progress" }
                        val progress = step.progress
                        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = indicator,
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        else LinearProgressIndicator(indicator,
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepMarker(number: Int, status: StepStatus) {
    val primary = MaterialTheme.colorScheme.primary
    val background = when (status) {
        StepStatus.Done -> primary
        StepStatus.Active, StepStatus.Pending -> MaterialTheme.colorScheme.surface
    }
    val foreground = when (status) {
        StepStatus.Done -> MaterialTheme.colorScheme.onPrimary
        StepStatus.Active, StepStatus.Pending -> primary
    }
    Surface(Modifier.size(24.dp).semantics {
        contentDescription = "Step $number"
        stateDescription = when (status) { StepStatus.Done -> "Completed"; StepStatus.Active -> "In progress"; StepStatus.Pending -> "Pending" }
    }, shape = CircleShape, color = background, contentColor = foreground,
        border = if (status == StepStatus.Done) null else BorderStroke(2.dp,
            if (status == StepStatus.Active) primary else MaterialTheme.colorScheme.outlineVariant)) {
        Box(contentAlignment = Alignment.Center) {
            when (status) {
                StepStatus.Done -> Icon(FieldAtlasIcons.Check, null, Modifier.size(15.dp))
                StepStatus.Active -> Box(Modifier.size(9.dp).background(primary, CircleShape))
                StepStatus.Pending -> Unit
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Five bars that follow the microphone level, newest on the right, so speech visibly registers. */
@Composable
private fun VoiceLevelMeter(level: Float, modifier: Modifier = Modifier) {
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
private fun RecordingDot() {
    val pulse = rememberInfiniteTransition(label = "recordingDot")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recordingDotAlpha",
    )
    Box(Modifier.size(10.dp).alpha(alpha).background(MaterialTheme.colorScheme.error, CircleShape))
}
