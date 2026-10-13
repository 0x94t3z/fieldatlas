package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
internal fun DeviceInfoSheet(proof: ProofModel, packs: List<xyz.fieldatlas.assets.InstalledAsset>, onTechnical: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Read once per opening: free space changes as packs download. The app's own share counts
    // everything it holds (APK, partial downloads, staging, attachments, caches), not only packs.
    val space = remember { runCatching { android.os.StatFs(context.filesDir.absolutePath).let { it.totalBytes to it.availableBytes } }.getOrNull() }
    val appBytes by produceState<Long?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { xyz.fieldatlas.assets.StorageFootprint.forApp(context).bytes() }.getOrNull()
        }
    }
    FieldAtlasInfoSheet("App & device", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(FieldAtlasIcons.Offline, size = 44.dp, iconSize = 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Field Atlas ${xyz.fieldatlas.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                MoreNote("Open-source research on this phone. No account.")
            }
        }
        space?.let { (total, free) -> StorageSummary(appBytes ?: packs.sumOf { it.installedBytes }, packs.sumOf { it.installedBytes }, free, total, measured = appBytes != null) }
        val (phone, _) = phoneFacts(proof.device)
        if (phone.isNotEmpty()) FactGroup("Phone", phone)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(FieldAtlasButtonShape)
            .clickable(role = Role.Button, onClick = onTechnical), verticalAlignment = Alignment.CenterVertically) {
            Text("Technical details", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(FieldAtlasIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How much of the phone Field Atlas holds, against what is left free. */
@Composable
private fun StorageSummary(appBytes: Long, packBytes: Long, freeBytes: Long, totalBytes: Long, measured: Boolean) {
    val colors = MaterialTheme.colorScheme
    fun fmt(bytes: Long) = xyz.fieldatlas.ui.presentation.formatAssetBytes(bytes)
    val other = (totalBytes - freeBytes - appBytes).coerceAtLeast(0)
    val appLabel = if (measured) "Field Atlas ${fmt(appBytes)}" else "Installed models and collections ${fmt(appBytes)}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Storage", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().height(8.dp).clip(FieldAtlasButtonShape).background(colors.surfaceContainerHighest)
            .semantics { contentDescription = "$appLabel, ${fmt(freeBytes)} free of ${fmt(totalBytes)}" }) {
            if (totalBytes > 0) {
                if (appBytes > 0) Box(Modifier.fillMaxHeight().weight(appBytes.toFloat()).background(colors.primary))
                if (other > 0) Box(Modifier.fillMaxHeight().weight(other.toFloat()).background(colors.outlineVariant))
                if (freeBytes > 0) Box(Modifier.fillMaxHeight().weight(freeBytes.toFloat()))
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(colors.primary, appLabel)
            LegendItem(colors.outlineVariant, "Everything else on the phone")
            LegendItem(colors.surfaceContainerHighest, "Free ${fmt(freeBytes)}")
        }
        if (measured && appBytes > packBytes) MoreNote("Installed models and collections: ${fmt(packBytes)}. " +
            "The rest is the app itself, unfinished downloads, attachments and caches.")
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
        MoreNote(label)
    }
}

/** Measurements and manifest checks for support, one level below App & device. */
@Composable
internal fun TechnicalDetailsSheet(proof: ProofModel, onBack: () -> Unit, onDismiss: () -> Unit) {
    FieldAtlasInfoSheet("Technical details", onDismiss, closeDescription = "Close App & device",
        onBack = onBack, backDescription = "Back to app and device") {
        MoreNote("Manifest checks and verified pack metadata. These are observations, not a guarantee of answer quality.")
        val (_, internals) = phoneFacts(proof.device)
        if (internals.isNotEmpty()) FactGroup("Memory and processor", internals)
        if (proof.latestRun.any { it.state != ProofState.Missing }) FactGroup("Latest research", proof.latestRun)
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
}

/** What a person reads about their phone, and the measurements only support needs. */
internal fun phoneFacts(device: List<ProofFact>): Pair<List<ProofFact>, List<ProofFact>> {
    val everyday = device.mapNotNull { fact ->
        when (fact.label) {
            "Device" -> fact
            "Physical memory" -> fact.copy(label = "Memory")
            // API 33 is Android 13; later levels follow one-to-one so far.
            "Android API" -> fact.value.toIntOrNull()?.takeIf { it >= 33 }
                ?.let { fact.copy(label = "Android", value = "${it - 20}") } ?: fact
            else -> null
        }
    }
    val internals = device.filter { it.label !in setOf("Device", "Physical memory", "Android API") && it.state != ProofState.Missing }
    return everyday to internals
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
