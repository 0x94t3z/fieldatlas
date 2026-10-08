package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.proof.*
import xyz.fieldatlas.ui.theme.*

@Composable
internal fun MoreNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun MoreDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
}

@Composable
internal fun MoreAction(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldAtlasButtonShape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MoreInfoBlock(title: String, body: String, icon: ImageVector? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DeviceInfoSheet(proof: ProofModel, onAbout: () -> Unit, onDismiss: () -> Unit) {
    var technical by rememberSaveable { mutableStateOf(false) }
    FieldAtlasInfoSheet("App & device", onDismiss) {
        MoreInfoBlock("Field Atlas ${xyz.fieldatlas.BuildConfig.VERSION_NAME}", "Details reported by this phone.")
        if (proof.device.isNotEmpty()) FactGroup("Device", proof.device)
        if (proof.latestRun.isNotEmpty()) FactGroup("Latest research", proof.latestRun)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldAtlasButtonShape)
            .semantics { stateDescription = if (technical) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button) { technical = !technical }, verticalAlignment = Alignment.CenterVertically) {
            Text("Technical details", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            FieldAtlasExpandIcon(technical)
        }
        if (technical) {
            MoreNote("Manifest checks and verified pack metadata. These are observations, not a guarantee of answer quality.")
            proof.offline.forEach { fact ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(fact.label, style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(fact.value, style = MaterialTheme.typography.bodySmall) }
                    val state = when (fact.state) {
                        ProofState.Pass -> "Pass"; ProofState.Warning -> "Warning"
                        ProofState.Info -> "Info"; ProofState.Missing -> "Not measured"
                    }
                    val origin = when (fact.origin) {
                        ProofOrigin.ManifestAudit -> "Manifest audit"; ProofOrigin.OsReport -> "Android report"
                        ProofOrigin.MeasuredQuery -> "Measured query"
                    }
                    MoreNote("$state · $origin")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
        MoreAction("About Field Atlas", onAbout)
    }
}

@Composable
private fun FactGroup(title: String, facts: List<ProofFact>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Surface(color = MaterialTheme.colorScheme.background, shape = FieldAtlasButtonShape) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                facts.forEachIndexed { index, fact ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        val large = LocalDensity.current.fontScale > 1.3f || maxWidth < 260.dp
                        if (large) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MoreNote(fact.label)
                            Text(fact.value, style = MaterialTheme.typography.bodyMedium)
                        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(fact.label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fact.value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SupportSheet(
    diagnosticsText: String, includeQuestion: Boolean, onIncludeQuestion: (Boolean) -> Unit,
    onClear: () -> Unit, onExport: () -> Unit, onDismiss: () -> Unit,
) {
    var showIssues by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    FieldAtlasInfoSheet("Support report", onDismiss) {
        MoreInfoBlock("Choose what to include", "Save a file to review or share yourself. Nothing is sent automatically.", FieldAtlasIcons.Document)
        Surface(color = MaterialTheme.colorScheme.background, shape = FieldAtlasButtonShape) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Included in the report", style = MaterialTheme.typography.labelMedium)
                MoreNote("App and device details\nSaved-pack metadata\nLatest run measurements and recent issues")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Include latest question", style = MaterialTheme.typography.bodyMedium)
                MoreNote(if (includeQuestion) "Your question will be in this file." else "Off by default")
            }
            FieldAtlasSwitch(includeQuestion, onIncludeQuestion, Modifier.semantics { contentDescription = "Include latest question" })
        }
        FieldAtlasPrimaryButton("Save support report", onExport, Modifier.fillMaxWidth())
        if (diagnosticsText.isNotBlank()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldAtlasButtonShape)
                .semantics { stateDescription = if (showIssues) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button) { showIssues = !showIssues }, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent technical details", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                FieldAtlasExpandIcon(showIssues)
            }
            if (showIssues) {
                MoreNote("These may describe an earlier issue that has already been resolved.")
                SelectionContainer { Text(diagnosticsText, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { confirmClear = true }, shape = FieldAtlasButtonShape,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear details", color = MaterialTheme.colorScheme.error) }
            }
        } else MoreNote("No recent issues recorded.")
    }
    if (confirmClear) FieldAtlasDeleteDialog("Clear recent details?", "Only the recent issue log is cleared. Saved packs and answers stay on this phone.",
        onDelete = { confirmClear = false; onClear() }, onKeep = { confirmClear = false }, deleteLabel = "Clear")
}
