package xyz.fieldatlas.ui.proof

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.benchmark.BenchmarkResultState
import xyz.fieldatlas.benchmark.BenchmarkRun
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.theme.FieldAtlasHeader

@Composable
fun BenchmarkScreen(
    state: BenchmarkUiState,
    inferenceState: InferenceState,
    onRunNext: () -> Unit,
    onStop: () -> Unit,
    onExport: (BenchmarkRun) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onBack) { Text("Back to proof") }
            }
            FieldAtlasHeader(
                title = "Device benchmark",
                subtitle = "Run one frozen question at a time and retain the raw local evidence.",
            )
        }
        item {
            Text(
                "${state.run.results.size} / ${state.totalQuestions} completed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        state.nextQuestion?.let { next ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Next · ${next.id}", style = MaterialTheme.typography.labelLarge)
                        Text(next.prompt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            if (state.running) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.currentAnswer.isNotBlank()) Text(state.currentAnswer)
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
                }
            } else {
                Button(
                    onClick = onRunNext,
                    enabled = state.nextQuestion != null && inferenceState == InferenceState.Ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Run next") }
            }
        }
        if (inferenceState != InferenceState.Ready && !state.running) {
            item { Text("Load the local model from Research before running the benchmark.") }
        }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (state.run.results.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { onExport(state.run) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export benchmark evidence") }
            }
            items(state.run.results.size) { index ->
                val result = state.run.results[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.questionId, fontWeight = FontWeight.SemiBold)
                        Text(resultStateLabel(result.state), style = MaterialTheme.typography.labelMedium)
                        Text("${result.evidenceChunkIds.size} evidence chunks")
                    }
                }
            }
        }
    }
}

private fun resultStateLabel(state: BenchmarkResultState): String = when (state) {
    BenchmarkResultState.COMPLETE -> "Complete"
    BenchmarkResultState.CANCELLED -> "Cancelled"
    BenchmarkResultState.FAILED -> "Failed"
    BenchmarkResultState.INSUFFICIENT -> "Insufficient evidence"
}
