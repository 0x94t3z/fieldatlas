package xyz.fieldatlas.ui.proof

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.benchmark.BenchmarkResultState
import xyz.fieldatlas.benchmark.BenchmarkRun
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.theme.*

@Composable
fun BenchmarkScreen(
    state: BenchmarkUiState, inferenceState: InferenceState,
    onRunNext: () -> Unit, onStop: () -> Unit, onPrepareModel: () -> Unit,
    onExport: (BenchmarkRun) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val completed = state.run.results.size
    val fraction = if (state.totalQuestions == 0) 0f else completed.toFloat() / state.totalQuestions.toFloat()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar("Performance check", onBack = onBack)
            LazyColumn(Modifier.weight(1f).padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { FieldAtlasPageHeader("Measure on this phone.", "A local check, not a guarantee that every answer is correct.",
                    titleStyle = MaterialTheme.typography.headlineSmall) }
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Text("$completed of ${state.totalQuestions} recorded", style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        }
                        Text("One question at a time. You can save the results whenever you stop.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.nextQuestion?.let { next -> item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Text("Question ${completed + 1} of ${state.totalQuestions} · ${next.category.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(next.prompt, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp))
                    }
                } }
                if (state.running) item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Text("Working locally…", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                        if (state.currentAnswer.isNotBlank()) Text(state.currentAnswer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.error?.let { error -> item {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } }
                if (state.run.results.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { onExport(state.run) }, shape = FieldAtlasButtonShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(FieldAtlasIcons.Document, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export benchmark evidence", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item { Text("Recorded results", style = MaterialTheme.typography.titleSmall) }
                    items(state.run.results.size) { index ->
                        val result = state.run.results[index]
                        FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(result.questionId, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(resultStateLabel(result.state), style = MaterialTheme.typography.labelSmall,
                                    color = if (result.state == BenchmarkResultState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            Text("${result.evidenceChunkIds.size} evidence chunks", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else item {
                    Text("These checks use battery, memory and time. Your saved research is unchanged.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inferenceState == InferenceState.Loading && !state.running) Text("Preparing the model on this device…", style = MaterialTheme.typography.bodySmall)
                if (state.running) OutlinedButton(onClick = onStop, shape = FieldAtlasButtonShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Stop") }
                else if (inferenceState == InferenceState.Idle || inferenceState is InferenceState.Failed) FieldAtlasPrimaryButton(
                    if (inferenceState is InferenceState.Failed) "Try preparing model again" else "Prepare model", onPrepareModel, Modifier.fillMaxWidth())
                else FieldAtlasPrimaryButton("Run next", onRunNext, Modifier.fillMaxWidth(),
                    enabled = state.nextQuestion != null && inferenceState == InferenceState.Ready)
            }
        }
    }
}

private fun resultStateLabel(state: BenchmarkResultState): String = when (state) {
    BenchmarkResultState.COMPLETE -> "Complete"; BenchmarkResultState.CANCELLED -> "Cancelled"
    BenchmarkResultState.FAILED -> "Failed"; BenchmarkResultState.INSUFFICIENT -> "Insufficient evidence"
}
