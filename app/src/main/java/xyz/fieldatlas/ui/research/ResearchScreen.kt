package xyz.fieldatlas.ui.research

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.theme.FieldAtlasCard
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
    val exampleQuestions = suggestions.filter(String::isNotBlank).distinct().take(3)
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
                FieldAtlasStatusPill("Offline", StatusTone.Positive)
                FieldAtlasStatusPill(
                    if (collectionCount == 1) "1 collection" else "$collectionCount collections",
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
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                label = { Text("Research question") },
                placeholder = {
                    Text(
                        suggestions.firstOrNull()?.let { "e.g. $it" }
                            ?: "Explain a topic, compare evidence, or examine a claim",
                    )
                },
                enabled = !state.isRunning,
                minLines = 3,
                trailingIcon = {
                    IconButton(
                        onClick = onMicClick,
                        enabled = !state.isRunning &&
                            (voiceState.phase == VoicePhase.Idle || voiceState.phase == VoicePhase.Recording),
                    ) {
                        when (voiceState.phase) {
                            VoicePhase.Starting, VoicePhase.Processing -> CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            VoicePhase.Recording -> Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop recording",
                            )
                            VoicePhase.Idle -> Icon(
                                Icons.Outlined.Mic,
                                contentDescription = "Speak question",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Research question" },
                shape = MaterialTheme.shapes.medium,
            )
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
                val citedSourceCount = buildAnswerPresentation(state.answer, state.sources.size)
                    .availableCitations.size
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text("Answer ready", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        if (citedSourceCount == 0) {
                            "Written by the offline model without local citations."
                        } else {
                            "Written from $citedSourceCount cited local " +
                                if (citedSourceCount == 1) "source." else "sources."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val preview = answerCardPreview(state.answer)
                    if (preview.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Description,
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
                    )
                }
            }
        }
        if (exampleQuestions.isNotEmpty() && !state.isRunning) {
            item {
                Text(
                    "Try an example from your library",
                    style = MaterialTheme.typography.titleMedium,
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
                            modifier = Modifier.width(190.dp).heightIn(min = 110.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Text(
                                question,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
            modifier = Modifier.fillMaxWidth(),
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
