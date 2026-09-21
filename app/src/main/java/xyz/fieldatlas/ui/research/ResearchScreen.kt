package xyz.fieldatlas.ui.research

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.StatusStrip

@Composable
fun ResearchScreen(
    state: ResearchUiState,
    assetsReady: Boolean,
    inferenceState: InferenceState,
    suggestions: List<String> = emptyList(),
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onSource: (Evidence) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FieldAtlasHeader(
                title = "Research",
                subtitle = "Search installed evidence, then synthesize locally.",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { ResearchStatus(state, inferenceState) }
        item {
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                label = { Text("Research question") },
                placeholder = { Text("Compare evidence, explain a topic, or test a claim") },
                enabled = !state.isRunning,
                minLines = 3,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Research question" },
            )
        }
        if (suggestions.isNotEmpty() && !state.isRunning) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("From installed knowledge", style = MaterialTheme.typography.labelLarge)
                    suggestions.forEach { suggestion ->
                        OutlinedButton(
                            onClick = { onQuestionChange(suggestion) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(suggestion) }
                    }
                }
            }
        }
        item {
            ResearchAction(
                state = state,
                assetsReady = assetsReady,
                inferenceState = inferenceState,
                onSubmit = onSubmit,
                onStop = onStop,
                onLoadModel = onLoadModel,
                onUnloadModel = onUnloadModel,
            )
        }
        if (inferenceState is InferenceState.Failed) item { MessageCard(inferenceState.message) }
        if (!assetsReady) item { Text("Install verified model and knowledge packs to enable research.") }
        if (state.error != null) item { MessageCard(state.error) }

        if (state.sources.isNotEmpty()) {
            item { Text("Evidence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.sources.size) { index ->
                val source = state.sources[index]
                Card(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { onSource(source) }
                        .semantics { contentDescription = "Open source S${index + 1}" },
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("S${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(source.title, fontWeight = FontWeight.SemiBold)
                            Text(source.source, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onSource(source) }) { Text("Read") }
                    }
                }
            }
        }
        if (state.answer.isNotBlank() && state.phase != ResearchPhase.Insufficient) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Synthesis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (state.completion == ResearchCompletion.Cancelled) Text("Stopped")
                    }
                    Text(state.answer, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        state.metrics?.let { metrics ->
            item { ResearchMetricsCard(formatResearchMetrics(metrics, state.sources.size)) }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
    }
}

@Composable
private fun ResearchStatus(state: ResearchUiState, inferenceState: InferenceState) {
    val activity = when {
        state.phase == ResearchPhase.Searching -> "Searching evidence"
        state.phase == ResearchPhase.Generating || inferenceState == InferenceState.Generating -> "Synthesizing locally"
        state.completion == ResearchCompletion.Cancelled -> "Stopped"
        state.completion == ResearchCompletion.Complete -> "Complete"
        else -> "Ready"
    }
    StatusStrip(listOf("Offline", activity))
}

@Composable
private fun ResearchAction(
    state: ResearchUiState,
    assetsReady: Boolean,
    inferenceState: InferenceState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
) {
    if (state.isRunning || inferenceState == InferenceState.Generating) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
        }
        return
    }
    when (inferenceState) {
        InferenceState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSubmit,
                enabled = assetsReady && state.question.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Research offline") }
            TextButton(onClick = onUnloadModel, modifier = Modifier.fillMaxWidth()) { Text("Unload model") }
        }
        InferenceState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Preparing local model…")
        }
        InferenceState.Idle -> Button(
            onClick = onLoadModel,
            enabled = assetsReady,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Load model") }
        is InferenceState.Failed -> Button(
            onClick = onLoadModel,
            enabled = assetsReady,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Retry model") }
        InferenceState.Generating -> Unit
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ResearchMetricsCard(model: ResearchMetricsModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Measured on this run", fontWeight = FontWeight.Bold)
            Text("Retrieval ${model.retrieval}")
            model.firstToken?.let { Text("First token $it") }
            Text("Total ${model.total} · ${model.tokenCount}")
            model.tokenRate?.let { Text(it) }
            Text(model.citationCoverage)
            if (model.hasUnmappedCitation) {
                Text("One or more citation markers did not map to evidence.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
