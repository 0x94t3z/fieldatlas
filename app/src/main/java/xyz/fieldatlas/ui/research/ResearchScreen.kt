package xyz.fieldatlas.ui.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.inference.InferenceState
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
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
    onOpenAnswer: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "What are you investigating?",
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
        item {
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                label = { Text("Research question") },
                placeholder = { Text("Explain a topic, compare evidence, or examine a claim") },
                enabled = !state.isRunning,
                minLines = 4,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Research question" },
                shape = MaterialTheme.shapes.medium,
            )
        }
        if (suggestions.isNotEmpty() && !state.isRunning) {
            item {
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Text("Try an inquiry", style = MaterialTheme.typography.titleMedium)
                    suggestions.take(3).forEach { suggestion ->
                        TextButton(
                            onClick = { onQuestionChange(suggestion) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(suggestion, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
        item {
            ResearchAction(
                state = state,
                inferenceState = inferenceState,
                onSubmit = onSubmit,
                onStop = onStop,
                onPrepareModel = onPrepareModel,
            )
        }
        if (state.phase == ResearchPhase.Complete && state.answer.isNotBlank()) {
            item {
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    Text("Answer ready", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.sources.isEmpty()) {
                            "Written by the offline model without local citations."
                        } else {
                            "Written from ${state.sources.size} installed ${if (state.sources.size == 1) "source" else "sources"}."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FieldAtlasPrimaryButton(
                        text = "Read answer",
                        onClick = onOpenAnswer,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
private fun ResearchAction(
    state: ResearchUiState,
    inferenceState: InferenceState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
) {
    if (state.isRunning || inferenceState == InferenceState.Generating) {
        FieldAtlasCard(Modifier.fillMaxWidth()) {
            Text(researchActivityLabel(state.phase), fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Stop")
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
