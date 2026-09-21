package xyz.fieldatlas.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.SetupNextAction
import xyz.fieldatlas.ui.presentation.deriveSetupNextAction
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.StatusStrip

@Composable
fun SetupScreen(
    packs: List<InstalledAsset>,
    importing: Boolean,
    error: String?,
    onImportPack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val hasModel = packs.any { it.type == PackType.MODEL }
    val hasKnowledge = packs.any { it.type == PackType.KNOWLEDGE }
    val nextAction = deriveSetupNextAction(packs, importing)
    var showErrorDetails by rememberSaveable(error) { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            FieldAtlasHeader(
                title = "Pack your atlas",
                subtitle = "Two verified files make research available without a connection.",
            )
        }
        item { StatusStrip(listOf("No account", "No cloud", "No Google services")) }
        item { PackRequirementCard("1", "Local model", hasModel, "Runs synthesis on this device") }
        item { PackRequirementCard("2", "Knowledge", hasKnowledge, "Provides searchable, attributable evidence") }
        item {
            Button(
                onClick = onImportPack,
                enabled = nextAction != SetupNextAction.Importing && nextAction != SetupNextAction.OpenResearch,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (nextAction == SetupNextAction.Importing) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(actionLabel(nextAction))
                }
            }
        }
        if (error != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "This pack could not be imported. Check the file and try again.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = { showErrorDetails = !showErrorDetails }) {
                            Text(if (showErrorDetails) "Hide technical details" else "Show technical details")
                        }
                        if (showErrorDetails) Text(error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PackRequirementCard(number: String, title: String, ready: Boolean, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (ready) "✓" else number, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(if (ready) "Ready and verified" else detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun actionLabel(action: SetupNextAction): String = when (action) {
    SetupNextAction.ChooseModel -> "Choose model pack"
    SetupNextAction.ChooseKnowledge -> "Choose knowledge pack"
    SetupNextAction.OpenResearch -> "Open research"
    SetupNextAction.Importing -> "Verifying pack"
}
