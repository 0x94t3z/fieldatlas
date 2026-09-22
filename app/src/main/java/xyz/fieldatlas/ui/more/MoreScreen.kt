package xyz.fieldatlas.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
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
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "More",
                subtitle = "Privacy, local research tools, and app details.",
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth()) {
                Text("Private by design", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Field Atlas works without an account, cloud service, or network permission.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldAtlasStatusPill("Offline", StatusTone.Positive)
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth()) {
                Text("On-device research", style = MaterialTheme.typography.titleMedium)
                Text(modelStatus(inferenceState), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (inferenceState == InferenceState.Idle || inferenceState is InferenceState.Failed) {
                    FieldAtlasPrimaryButton("Prepare model", onPrepareModel, Modifier.fillMaxWidth())
                } else if (inferenceState == InferenceState.Ready) {
                    OutlinedButton(onClick = onReleaseModel, modifier = Modifier.fillMaxWidth()) {
                        Text("Release model memory")
                    }
                }
            }
        }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth()) {
                Text("Research tools", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onOpenBenchmark, modifier = Modifier.fillMaxWidth()) {
                    Text("Open device benchmark")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
        item {
            FieldAtlasCard(Modifier.fillMaxWidth()) {
                FieldAtlasInformationAction(
                    label = if (showTechnical) "Hide technical details" else "Technical details",
                    onClick = { showTechnical = !showTechnical },
                    expanded = showTechnical,
                )
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
