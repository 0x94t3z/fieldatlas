package xyz.fieldatlas.ui.research

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
    var questionFocused by remember { mutableStateOf(false) }
    val hasReadyAnswer = state.phase == ResearchPhase.Complete && state.answer.isNotBlank()
    var editReadyQuestion by rememberSaveable(hasReadyAnswer) { mutableStateOf(false) }
    // Keep the first-run examples focused on the bounty's research task rather than
    // mirroring whichever pack happens to be installed on the device.
    val exampleQuestions = listOf(
        "Tell me the best vegan restaurants in Berlin",
        "What are the best museums to visit in Tokyo?",
        "Tell me about Japan's history",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = when {
                    state.isRunning -> "Research in progress"
                    hasReadyAnswer -> "Your research"
                    else -> "What are you investigating today?"
                },
                subtitle = when {
                    state.isRunning -> "Working on this phone."
                    hasReadyAnswer -> "Read your answer or explore another question."
                    else -> "Ask across the knowledge saved on this phone."
                },
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        item {
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
        if (hasReadyAnswer && !editReadyQuestion) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.question, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { editReadyQuestion = true }) { Text("Edit") }
                }
            }
        } else if (state.isRunning) {
            item {
                Text(state.question, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
                    BasicTextField(
                        value = state.question,
                        onValueChange = onQuestionChange,
                        enabled = !state.isRunning,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)
                            .onFocusChanged { questionFocused = it.isFocused }
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
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
                                enabled = !state.isRunning,
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
                                expanded = attachmentMenuOpen,
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
                        Surface(
                            shape = CircleShape,
                            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                                lerp(MaterialTheme.colorScheme.primaryContainer, Color.White, 0.45f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            IconButton(
                                onClick = onMicClick,
                                enabled = !state.isRunning &&
                                    (voiceState.phase == VoicePhase.Idle || voiceState.phase == VoicePhase.Recording),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text(
                        "Listening… tap stop when finished.",
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
                val citedSourceCount = Regex("\\[(\\d+)]")
                    .findAll(state.answer)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .filter { it in 1..state.sources.size }
                    .distinct()
                    .count()
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(FieldAtlasIcons.Check, contentDescription = null, modifier = Modifier.padding(8.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Answer ready", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (citedSourceCount == 0) {
                                    "Model-generated · no sources cited"
                                } else {
                                    "Written from $citedSourceCount cited local " +
                                        if (citedSourceCount == 1) "source." else "sources."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.metrics?.totalMillis?.let { duration ->
                        Text("Completed in ${formatElapsed(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val preview = answerCardPreview(state.answer)
                    if (preview.isNotEmpty()) {
                        Text(preview, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
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
                TextButton(onClick = {
                    onAskAnotherQuestion()
                    scope.launch { listState.animateScrollToItem(0) }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ask another question")
                }
            }
        }
        if (exampleQuestions.isNotEmpty() && !state.isRunning && !hasReadyAnswer) {
            item {
                Text(
                    "Try an example",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(exampleQuestions.size) { index ->
                        val question = exampleQuestions[index]
                        Card(
                            onClick = {
                                onQuestionChange(question)
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            modifier = Modifier.width(148.dp).heightIn(min = 132.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val exampleIcon = when (index) {
                                    0 -> FieldAtlasIcons.Food
                                    1 -> FieldAtlasIcons.Place
                                    else -> FieldAtlasIcons.Document
                                }
                                val iconBackground = when (exampleIcon) {
                                    FieldAtlasIcons.Place -> Color(0xFFEAE3FA)
                                    FieldAtlasIcons.Document -> Color(0xFFFFE8C2)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                                val iconTint = when (exampleIcon) {
                                    FieldAtlasIcons.Place -> Color(0xFF6B4BA3)
                                    FieldAtlasIcons.Document -> Color(0xFF9A5A00)
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    color = iconBackground,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Icon(exampleIcon, contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.padding(8.dp))
                                }
                                Text(
                                    question,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
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

private fun formatElapsed(totalMillis: Long): String {
    if (totalMillis < 60_000) return "Just now"
    val seconds = totalMillis / 1_000
    return "${seconds / 60}m ${seconds % 60}s"
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
        FieldAtlasCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when (state.phase) {
                    ResearchPhase.Planning -> "Understanding your question…"
                    ResearchPhase.Searching -> "Searching saved sources…"
                    else -> "Writing your answer…"
                }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onStop) { Text("Stop") }
            }
            if (state.startedAtNanos != null) {
                Text("${formatElapsed(elapsedMillis)} elapsed", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == ResearchPhase.Generating && state.sources.isEmpty()) {
                Text("Model-generated · no supporting sources found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val draft = draftAnswerPreview(state.answer)
            if (draft.isNotEmpty()) {
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
    when (inferenceState) {
        InferenceState.Ready -> FieldAtlasPrimaryButton(
            text = "Start research",
            onClick = onSubmit,
            enabled = state.question.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            leadingIcon = FieldAtlasIcons.ResearchSparkles,
            leadingIconSize = 26.dp,
        )
        InferenceState.Loading -> FieldAtlasCard(Modifier.fillMaxWidth()) {
            Text("Preparing research tools")
            LinearProgressIndicator(Modifier.fillMaxWidth())
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

@Composable
private fun MessageCard(title: String, message: String) {
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
