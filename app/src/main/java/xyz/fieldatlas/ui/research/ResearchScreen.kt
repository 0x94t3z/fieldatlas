package xyz.fieldatlas.ui.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
    onImportPack: () -> Unit = {},
    diagnosticsText: String = "",
    onClearDiagnostics: () -> Unit = {},
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
    onOpenAnswer: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val scope = rememberCoroutineScope()
    val hasReadyAnswer = state.phase == ResearchPhase.Complete && state.answer.isNotBlank()
    // Keep the first-run examples focused on the bounty's research task rather than
    // mirroring whichever pack happens to be installed on the device.
    val exampleQuestions = listOf(
        "Tell me the best vegan restaurants in Berlin",
        "What are the best museums to visit in Tokyo?",
        "Summarise my travel notes about Japan",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "What are you investigating today?",
                subtitle = "Ask across the knowledge saved on this phone.",
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
        if (collectionCount == 0) {
            item {
                Text(
                    "Answers can come from your offline model. Add a knowledge pack in Library for source-backed answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = onQuestionChange,
                    placeholder = { Text("Explain a topic, compare evidence, or examine a claim") },
                    enabled = !state.isRunning,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Research question" },
                    shape = MaterialTheme.shapes.medium,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 8.dp).size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    IconButton(
                        onClick = onMicClick,
                        enabled = !state.isRunning &&
                            (voiceState.phase == VoicePhase.Idle || voiceState.phase == VoicePhase.Recording),
                    ) {
                        when (voiceState.phase) {
                            VoicePhase.Starting, VoicePhase.Processing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            VoicePhase.Recording -> Icon(Icons.Filled.Stop, contentDescription = "Stop recording")
                            VoicePhase.Idle -> Icon(Icons.Outlined.Mic, contentDescription = "Speak question")
                        }
                    }
                }
                TextButton(
                    onClick = onImportPack,
                    enabled = !state.isRunning,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
                ) {
                    Icon(FieldAtlasIcons.AttachFile, contentDescription = null, modifier = Modifier.size(21.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    Text("Add files")
                }
            }
            when (voiceState.phase) {
                VoicePhase.Recording -> Column {
                    LinearProgressIndicator(
                        progress = { voiceState.level },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 4.dp),
                    )
                    Text(
                        "Listening — tap the stop icon when you finish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VoicePhase.Starting, VoicePhase.Processing -> Text(
                    if (voiceState.phase == VoicePhase.Starting) "Preparing offline voice…" else "Transcribing on this device…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
            voiceState.error?.let { error ->
                Text(
                    error,
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
                                    "Written by the offline model."
                                } else {
                                    "Written from $citedSourceCount cited local " +
                                        if (citedSourceCount == 1) "source." else "sources."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FieldAtlasStatusPill(
                            state = StatusTone.Neutral,
                            text = state.metrics?.totalMillis?.let(::formatElapsed) ?: "Just now",
                        )
                    }
                    val preview = answerCardPreview(state.answer)
                    if (preview.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                FieldAtlasIcons.Document,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(preview, style = MaterialTheme.typography.bodyMedium)
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
        }
        if (exampleQuestions.isNotEmpty() && !state.isRunning) {
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
        if (diagnosticsText.isNotBlank()) {
            item {
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Text("App notice", style = MaterialTheme.typography.titleMedium)
                    Text(
                        diagnosticsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onClearDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Dismiss")
                    }
                }
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
private fun ResearchAction(
    state: ResearchUiState,
    inferenceState: InferenceState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
) {
    if (state.isRunning || inferenceState == InferenceState.Generating) {
        FieldAtlasCard(Modifier.fillMaxWidth()) {
            Text(researchActivityLabel(state.phase, state.retrievalProgress, state.promptRead, state.tokensWritten, state.retrievalVectorMatches, state.sources.isNotEmpty()), fontWeight = FontWeight.SemiBold)
            if (state.keywords.isNotEmpty()) {
                Text(
                    "Searching for: ${state.keywords.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Stop")
            }
            val draft = draftAnswerPreview(state.answer)
            if (draft.isNotEmpty()) {
                Text("Answer in progress", style = MaterialTheme.typography.titleMedium)
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
