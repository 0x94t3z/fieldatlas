package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofFact
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.proof.ProofState
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone

@Composable
fun MoreScreen(
    proof: ProofModel,
    inferenceState: InferenceState,
    onPrepareModel: () -> Unit,
    onReleaseModel: () -> Unit,
    onExportDiagnostics: (Boolean) -> Unit,
    onOpenBenchmark: () -> Unit,
    diagnosticsText: String = "",
    onClearDiagnostics: () -> Unit = {},
) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "More",
                subtitle = "Settings and app details.",
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                contentPadding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(FieldAtlasIcons.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MoreCardTitle("Privacy & offline use")
                        Text(
                            "Download online. Research offline. No account needed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showModel = !showModel },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Archive, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MoreCardTitle("Answer model")
                        Text(modelStatus(inferenceState), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
                        modifier = Modifier.size(20.dp).rotate(if (showModel) 90f else 0f),
                        contentDescription = if (showModel) "Hide model actions" else "Show model actions")
                }
                if (showModel) {
                    FieldAtlasStatusPill(
                    when (inferenceState) {
                        InferenceState.Ready -> "Ready"
                        InferenceState.Loading -> "Preparing"
                        InferenceState.Generating -> "Researching"
                        InferenceState.Idle -> "Not loaded"
                        is InferenceState.Failed -> "Needs attention"
                    },
                    if (inferenceState == InferenceState.Ready) StatusTone.Positive else StatusTone.Neutral,
                    )
                    if (inferenceState == InferenceState.Idle || inferenceState is InferenceState.Failed) {
                        FieldAtlasPrimaryButton("Prepare model", onPrepareModel, Modifier.fillMaxWidth())
                    } else if (inferenceState == InferenceState.Ready) {
                        OutlinedButton(onClick = onReleaseModel, modifier = Modifier.fillMaxWidth()) {
                            Text("Free up memory")
                        }
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth().clickable(onClick = onOpenBenchmark),
                contentPadding = 12.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Benchmark, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MoreCardTitle("Performance check")
                        Text("Check answer quality and speed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showDiagnostics = !showDiagnostics },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Diagnostics, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MoreCardTitle("Troubleshooting")
                        Text("Recent issues and support reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
                        modifier = Modifier.size(20.dp).rotate(if (showDiagnostics) 90f else 0f),
                        contentDescription = if (showDiagnostics) "Hide export options" else "Show export options")
                }
                if (showDiagnostics) {
                    if (diagnosticsText.isNotBlank()) {
                        Text("Recent technical details", style = MaterialTheme.typography.titleSmall)
                        Text("These may describe an earlier issue that has already been resolved.",
                            style = MaterialTheme.typography.bodySmall)
                        Text(diagnosticsText, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onClearDiagnostics) { Text("Clear details") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Include latest question")
                            Text("Off by default", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = includeQuestion, onCheckedChange = { includeQuestion = it })
                    }
                    TextButton(onClick = { onExportDiagnostics(includeQuestion) }) {
                        Text("Save support report")
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showTechnical = !showTechnical },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Info, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MoreCardTitle("App & device")
                        Text("Storage, memory, and offline checks.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
                        modifier = Modifier.size(20.dp).rotate(if (showTechnical) 90f else 0f),
                        contentDescription = if (showTechnical) "Hide details" else "Show details")
                }
                if (showTechnical) {
                    (proof.offline + proof.device + proof.latestRun).forEach { fact -> ProofRow(fact) }
                }
            }
        }
    }
}

@Composable
private fun MoreCardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ProofRow(fact: ProofFact) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(fact.label, fontWeight = FontWeight.SemiBold)
        Text(fact.value)
        Text(
            "${stateLabel(fact.state)} · ${originLabel(fact)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun modelStatus(state: InferenceState): String = when (state) {
    InferenceState.Idle -> "Saved on this phone. Ready to load."
    InferenceState.Loading -> "Preparing your model…"
    InferenceState.Ready -> "Ready for offline research."
    InferenceState.Generating -> "Writing your answer…"
    is InferenceState.Failed -> "Couldn't load. Tap to try again."
}

private fun stateLabel(state: ProofState): String = when (state) {
    ProofState.Pass -> "Pass"
    ProofState.Info -> "Info"
    ProofState.Missing -> "Not measured"
    ProofState.Warning -> "Warning"
}

private fun originLabel(fact: ProofFact): String = when (fact.origin) {
    xyz.fieldatlas.proof.ProofOrigin.ManifestAudit -> "Manifest audit"
    xyz.fieldatlas.proof.ProofOrigin.OsReport -> "Android report"
    xyz.fieldatlas.proof.ProofOrigin.MeasuredQuery -> "Measured query"
}
