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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.proof.ProofFact
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.proof.ProofOrigin
import xyz.fieldatlas.proof.ProofState
import xyz.fieldatlas.ui.theme.FieldAtlasHeader

@Composable
fun ProofScreen(
    model: ProofModel,
    onExport: (Boolean) -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
) {
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .semantics { contentDescription = "Offline proof" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FieldAtlasHeader(
                title = "Proof",
                subtitle = "Inspect what the app guarantees, what Android reports, and what this run measured.",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { ProofSectionHeader("Offline contract") }
        items(model.offline.size) { ProofFactCard(model.offline[it]) }
        item { ProofSectionHeader("Device") }
        items(model.device.size) { ProofFactCard(model.device[it]) }
        item { ProofSectionHeader("Latest run") }
        items(model.latestRun.size) { ProofFactCard(model.latestRun[it]) }
        item {
            Button(onClick = onOpenBenchmark, modifier = Modifier.fillMaxWidth()) {
                Text("Open device benchmark")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include latest question")
                    Text("Off by default", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = includeQuestion, onCheckedChange = { includeQuestion = it })
            }
        }
        item {
            Button(onClick = { onExport(includeQuestion) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export redacted proof")
            }
        }
    }
}

@Composable
private fun ProofSectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ProofFactCard(fact: ProofFact) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stateGlyph(fact.state), fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(fact.label, fontWeight = FontWeight.SemiBold)
                Text(fact.value)
                Text(
                    "${stateLabel(fact.state)} · ${originLabel(fact.origin)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun stateGlyph(state: ProofState): String = when (state) {
    ProofState.Pass -> "✓"
    ProofState.Info -> "i"
    ProofState.Missing -> "—"
    ProofState.Warning -> "△"
}

private fun stateLabel(state: ProofState): String = when (state) {
    ProofState.Pass -> "Pass"
    ProofState.Info -> "Info"
    ProofState.Missing -> "Not measured"
    ProofState.Warning -> "Warning"
}

private fun originLabel(origin: ProofOrigin): String = when (origin) {
    ProofOrigin.ManifestAudit -> "Manifest audit"
    ProofOrigin.OsReport -> "Android report"
    ProofOrigin.MeasuredQuery -> "Measured query"
}
