package xyz.fieldatlas.ui.proof

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.benchmark.BenchmarkResultState
import xyz.fieldatlas.benchmark.BenchmarkRun
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.StatusTone

@Composable
fun BenchmarkScreen(
    state: BenchmarkUiState,
    inferenceState: InferenceState,
    onRunNext: () -> Unit,
    onStop: () -> Unit,
    onPrepareModel: () -> Unit,
    onExport: (BenchmarkRun) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val completedCount = state.run.results.size
    val fraction = if (state.totalQuestions == 0) 0f else
        completedCount.toFloat() / state.totalQuestions.toFloat()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar(title = "Performance check", onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Text("Question ${state.nextQuestion?.let { completedCount + 1 } ?: completedCount} of ${state.totalQuestions}",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "One question at a time. Export your answers when finished.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$completedCount of ${state.totalQuestions} recorded",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)) {
                        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
            state.nextQuestion?.let { next ->
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                        Text("Next · ${next.category.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(next.prompt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                if (state.running) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.currentAnswer.isNotBlank()) Text(state.currentAnswer)
                        OutlinedButton(shape = FieldAtlasButtonShape, onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Stop")
                        }
                    }
                } else if (inferenceState == InferenceState.Idle || inferenceState is InferenceState.Failed) {
                    FieldAtlasPrimaryButton(
                        text = if (inferenceState is InferenceState.Failed) "Try preparing model again" else "Prepare model",
                        onClick = onPrepareModel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    FieldAtlasPrimaryButton(
                        text = "Run next",
                        onClick = onRunNext,
                        enabled = state.nextQuestion != null && inferenceState == InferenceState.Ready,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = FieldAtlasIcons.ChevronRight,
                    )
                }
            }
            if (inferenceState == InferenceState.Loading && !state.running) {
                item { Text("Preparing the model on this device…", style = MaterialTheme.typography.bodySmall) }
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.run.results.isNotEmpty()) {
                item {
                    OutlinedButton(shape = FieldAtlasButtonShape, 
                        onClick = { onExport(state.run) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(FieldAtlasIcons.Document, contentDescription = null)
                        Text("Export benchmark evidence", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                items(state.run.results.size) { index ->
                    val result = state.run.results[index]
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text(result.questionId, fontWeight = FontWeight.SemiBold)
                        Text(resultStateLabel(result.state), style = MaterialTheme.typography.labelMedium)
                        Text("${result.evidenceChunkIds.size} evidence chunks")
                    }
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
