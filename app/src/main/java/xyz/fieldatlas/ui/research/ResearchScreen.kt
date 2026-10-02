package xyz.fieldatlas.ui.research

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
) {
    val scope = rememberCoroutineScope()
    var attachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.canAddAttachment) {
        if (!state.canAddAttachment) attachmentMenuOpen = false
    }
    var questionFocused by remember { mutableStateOf(false) }
    val hasReadyAnswer = state.phase == ResearchPhase.Complete && state.answer.isNotBlank()
    var editReadyQuestion by rememberSaveable(hasReadyAnswer) { mutableStateOf(false) }
    // Keep the first-run examples focused on the bounty's research task rather than
    // mirroring whichever pack happens to be installed on the device.
    val exampleQuestions = listOf(
        ExampleQuestion("Tell me the best vegan restaurants in Berlin", FieldAtlasIcons.Food, TileTone.Sage),
        ExampleQuestion("Best vegan restaurants near me", FieldAtlasIcons.Place, TileTone.Gold),
        ExampleQuestion("Tell me about Japan's history", FieldAtlasIcons.Document, TileTone.Paper),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = when {
                    state.isRunning -> "Researching"
                    hasReadyAnswer -> "Your research"
                    else -> "What are you investigating today?"
                },
                subtitle = when {
                    state.isRunning -> "Working on this phone. Nothing leaves it."
                    hasReadyAnswer -> "Your answer is ready to read."
                    else -> "Ask across the knowledge saved on this phone."
                },
                modifier = Modifier.padding(top = if (state.isRunning || hasReadyAnswer) 16.dp else 22.dp),
            )
        }
        // The design drops the status chips while research runs, keeping focus on progress.
        if (!state.isRunning) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldAtlasStatusPill("Offline", StatusTone.Positive,
                    icon = FieldAtlasIcons.Offline)
                FieldAtlasStatusPill(
                    if (collectionCount == 1) "1 collection" else "$collectionCount collections",
                    icon = FieldAtlasIcons.Database,
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
                )
            }
        } else item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    if (questionFocused) 2.dp else 1.dp,
                    if (questionFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column {
                    AttachmentRows(state.attachments, !state.isRunning, onRemoveAttachment, onRetryAttachment)
                    BasicTextField(
                        value = state.question,
                        onValueChange = onQuestionChange,
                        enabled = !state.isRunning,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 76.dp)
                            .onFocusChanged { questionFocused = it.isFocused }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            IconButton(
                                onClick = { attachmentMenuOpen = true },
                                enabled = state.canAddAttachment,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Icon(
                                    FieldAtlasIcons.AttachFile,
                                    contentDescription = "Add attachment",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = attachmentMenuOpen && state.canAddAttachment,
                                onDismissRequest = { attachmentMenuOpen = false },
                                modifier = Modifier.width(120.dp),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 6.dp,
                            ) {
                                AttachmentMenuItem("Camera", FieldAtlasIcons.Camera) {
                                    attachmentMenuOpen = false
                                    onCameraClick()
                                }
                                AttachmentMenuItem("Photos", FieldAtlasIcons.Photo) {
                                    attachmentMenuOpen = false
                                    onPhotosClick()
                                }
                                AttachmentMenuItem("Files", FieldAtlasIcons.Document) {
                                    attachmentMenuOpen = false
                                    onFilesClick()
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        val recording = voiceState.phase == VoicePhase.Recording
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
                                    VoicePhase.Idle -> Icon(Icons.Outlined.Mic, contentDescription = "Speak question")
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
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldAtlasStatusPill("Listening", StatusTone.Positive)
                    Text(
                        "Tap stop when you finish.",
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
                    onSubmit = onSubmit,
                    onStop = onStop,
                    onPrepareModel = onPrepareModel,
                )
            }
        }
        if (hasReadyAnswer) {
            item {
                val citedSourceCount = readyCitationCount(state.answer, state.sources.size)
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(FieldAtlasIcons.Check, contentDescription = null, modifier = Modifier.padding(8.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Answer ready", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (citedSourceCount == 0 && state.metrics?.generatedTokenCount == 0) {
                                    "Lookup · no model generation"
                                } else if (citedSourceCount == 0) {
                                    "Model-generated · no sources cited"
                                } else {
                                    "$citedSourceCount local " +
                                        if (citedSourceCount == 1) "source cited." else "sources cited."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.metrics?.totalMillis?.let { duration ->
                            FieldAtlasStatusPill(
                                text = "Took ${formatResearchElapsed(duration)}",
                                state = StatusTone.Neutral,
                                modifier = Modifier.semantics { contentDescription = "Completed in ${formatResearchElapsed(duration)}" },
                            )
                        }
                    }
                    val preview = answerCardPreview(state.answer)
                    if (preview.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(FieldAtlasIcons.Document, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text(preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FieldAtlasPrimaryButton(
                        text = "Read answer",
                        onClick = onOpenAnswer,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = FieldAtlasIcons.ChevronRight,
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
            item { FieldAtlasSectionLabel("Try an example", Modifier.padding(top = 4.dp)) }
            item {
                FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    Column {
                        exampleQuestions.forEachIndexed { index, example ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                                    .clickable(onClickLabel = "Use this example") {
                                        onQuestionChange(example.question)
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FieldAtlasIconTile(example.icon, size = 36.dp, tone = example.tone)
                                Text(example.question, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Icon(FieldAtlasIcons.ChevronRight, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
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
internal fun ResearchQuestionPanel(question: String, onEdit: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your question", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit question",
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(question, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AttachmentMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
private fun ResearchAction(
    state: ResearchUiState,
    inferenceState: InferenceState,
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
                Text(researchProgressHeading(state.phase, if (modelWriting) state.answer else "", state.sources.isNotEmpty()),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (state.startedAtNanos != null) {
                    Text(formatResearchElapsed(elapsedMillis), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = "${formatResearchElapsed(elapsedMillis)} elapsed" })
                }
                FilledTonalButton(shape = FieldAtlasButtonShape, onClick = onStop, modifier = Modifier.heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )) { Text("Stop") }
            }
            ResearchSteps(researchSteps(state))
            if (state.phase == ResearchPhase.Generating && state.sources.isEmpty()) {
                Text("Model-generated · no supporting sources found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val draft = draftAnswerPreview(state.answer)
            if (draft.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AnswerMarkdownRenderer(
                    blocks = parseAnswerMarkdown(draft),
                    sourceCount = 0,
                    onCitation = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }
    // Missing inputs can be explained without loading or running the language model.
    if (xyz.fieldatlas.research.QuestionRequirements.response(state.question, state.attachments.isNotEmpty()) != null) {
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

private data class ExampleQuestion(val question: String, val icon: ImageVector, val tone: TileTone)

@Composable
private fun ResearchSteps(steps: List<ResearchStep>) {
    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StepMarker(step.status)
                    if (index < steps.lastIndex) {
                        Box(Modifier.width(2.dp).heightIn(min = 18.dp).height(if (step.status == StepStatus.Active) 34.dp else 20.dp)
                            .background(if (step.status == StepStatus.Done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(top = 1.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(step.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (step.status == StepStatus.Pending) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (step.status == StepStatus.Pending) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface)
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (step.status == StepStatus.Active) {
                        val progress = step.progress
                        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepMarker(status: StepStatus) {
    val primary = MaterialTheme.colorScheme.primary
    when (status) {
        StepStatus.Done -> Surface(Modifier.size(22.dp), shape = CircleShape, color = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary) {
            Icon(FieldAtlasIcons.Check, contentDescription = "Done", modifier = Modifier.padding(4.dp))
        }
        StepStatus.Active -> Box(Modifier.size(22.dp).border(2.dp, primary, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).background(primary, CircleShape))
        }
        StepStatus.Pending -> Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
