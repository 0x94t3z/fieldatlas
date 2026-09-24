package xyz.fieldatlas.ui.research

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.StatusTone

@Composable
fun AnswerScreen(
    state: ResearchUiState,
    onBack: () -> Unit,
    onAskAnother: () -> Unit,
    onCitation: (zeroBasedSourceIndex: Int) -> Unit,
) {
    BackHandler(onBack = onBack)
    val presentation = buildAnswerPresentation(state.answer, state.sources.size)
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    val sourceLabel = if (state.sources.size == 1) "1 source" else "${state.sources.size} sources"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar(title = "Research answer", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                Text(
                    text = state.question.ifBlank { "Research result" },
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Answer from $sourceLabel",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Generated on this device from your installed knowledge.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.keywords.isNotEmpty()) {
                        Text(
                            "Searched for: ${state.keywords.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FieldAtlasStatusPill("Offline", StatusTone.Positive)
                }
            }
            item {
                FieldAtlasCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    AnswerMarkdownRenderer(
                        blocks = presentation.blocks,
                        sourceCount = state.sources.size,
                        onCitation = onCitation,
                    )
                }
            }
            if (state.sources.isNotEmpty()) {
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text("Sources", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$sourceLabel available. Select a numbered citation in the answer, or a source below, to inspect its passage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Every source used for this answer, numbered exactly as the answer's
                        // [S#] markers reference them.
                        state.sources.forEachIndexed { index, evidence ->
                            Text(
                                "${index + 1}: ${evidence.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth()
                                    // Same passage inspector as the in-answer [S#] markers.
                                    .clickable { onCitation(index) },
                            )
                        }
                    }
                }
            }
            state.metrics?.let { metrics ->
                val model = formatResearchMetrics(metrics, state.sources.size)
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        FieldAtlasInformationAction(
                            label = if (showPerformance) "Hide performance details" else "Performance details",
                            onClick = { showPerformance = !showPerformance },
                            expanded = showPerformance,
                        )
                        if (showPerformance) {
                            Text("Retrieval ${model.retrieval}")
                            model.firstToken?.let { Text("First word $it") }
                            Text("Total ${model.total} · ${model.tokenCount}")
                            model.tokenRate?.let { Text(it) }
                            Text(model.citationCoverage)
                            if (model.hasUnmappedCitation) {
                                Text(
                                    "One citation could not be matched to an installed source.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAskAnother, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Ask another question")
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
