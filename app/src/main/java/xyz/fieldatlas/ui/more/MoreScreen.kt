package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
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
) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "More",
                subtitle = "Privacy, local research tools, and app details.",
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Lock)
                    Column(Modifier.weight(1f)) {
                        Text("Privacy & offline use", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "No account, cloud service, or network permission.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(Modifier.fillMaxWidth().clickable { showModel = !showModel },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Model)
                    Column(Modifier.weight(1f)) {
                        Text("Local model", style = MaterialTheme.typography.titleMedium)
                        Text(modelStatus(inferenceState), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
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
                            Text("Release model memory")
                        }
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth().clickable(onClick = onOpenBenchmark),
                contentPadding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Benchmark)
                    Column(Modifier.weight(1f)) {
                        Text("Device benchmark", style = MaterialTheme.typography.titleMedium)
                        Text("Test the frozen 18 questions on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight, contentDescription = null)
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(Modifier.fillMaxWidth().clickable { showDiagnostics = !showDiagnostics },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Diagnostics)
                    Column(Modifier.weight(1f)) {
                        Text("Export diagnostics", style = MaterialTheme.typography.titleMedium)
                        Text("Save a device record without sharing it automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
                        contentDescription = if (showDiagnostics) "Hide export options" else "Show export options")
                }
                if (showDiagnostics) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Include latest question")
                            Text("Off by default", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = includeQuestion, onCheckedChange = { includeQuestion = it })
                    }
                    TextButton(onClick = { onExportDiagnostics(includeQuestion) }) {
                        Text("Export diagnostics")
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(Modifier.fillMaxWidth().clickable { showTechnical = !showTechnical },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Info)
                    Column(Modifier.weight(1f)) {
                        Text("App and device details", style = MaterialTheme.typography.titleMedium)
                        Text("Offline and device checks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(FieldAtlasIcons.ChevronRight,
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
    InferenceState.Idle -> "The model is installed and can be prepared when needed."
    InferenceState.Loading -> "Preparing the local model…"
    InferenceState.Ready -> "The local model is ready."
    InferenceState.Generating -> "Research is running on this phone."
    is InferenceState.Failed -> "The local model needs attention."
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
