package xyz.fieldatlas.ui.more

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.theme.*

/** One tap to information; only the model's memory control expands inline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    proof: ProofModel, inferenceState: InferenceState,
    onPrepareModel: () -> Unit, onReleaseModel: () -> Unit,
    onExportDiagnostics: (Boolean) -> Unit, onOpenBenchmark: () -> Unit,
    diagnosticsText: String = "", onClearDiagnostics: () -> Unit = {},
    packs: List<InstalledAsset> = emptyList(), onOpenLibrary: () -> Unit = {},
    onOpenDownloadSources: () -> Unit = {},
) {
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    val model = packs.firstOrNull { it.type == PackType.MODEL && it.active } ?: packs.firstOrNull { it.type == PackType.MODEL }
    val busy = inferenceState == InferenceState.Loading || inferenceState == InferenceState.Generating
    val loaded = inferenceState == InferenceState.Ready || inferenceState == InferenceState.Generating
    val status = when (inferenceState) {
        InferenceState.Ready -> "Ready"; InferenceState.Loading -> "Preparing"
        InferenceState.Generating -> "Researching"; is InferenceState.Failed -> "Needs attention"
        InferenceState.Idle -> if (model == null) "Missing" else "Saved"
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { FieldAtlasPageHeader("More", "Settings and app details.", titleStyle = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight(650))) }
        item {
            FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Column {
                    SettingRow(FieldAtlasIcons.Model, "Answer model", model?.let {
                        "${it.title.substringBefore(" — ")} · ${formatAssetBytes(it.installedBytes)}"
                    }, expanded = modelExpanded, onClick = { modelExpanded = !modelExpanded }, status = status)
                    AnimatedVisibility(modelExpanded) {
                        Column {
                            MoreDivider()
                            if (model == null) MoreAction("Add model", onOpenLibrary)
                            else ModelMemoryControls(loaded, !busy,
                                { if (it) onPrepareModel() else onReleaseModel() }, { sheet = "model" })
                        }
                    }
                }
            }
        }
        item {
            MoreGroup("On this phone") {
                SettingRow(FieldAtlasIcons.Offline, "Offline readiness", "Model, collections and voice.", onClick = { sheet = "readiness" })
                MoreDivider()
                SettingRow(FieldAtlasIcons.Info, "App & device", "Version, phone and storage.", onClick = { sheet = "device" })
            }
        }
        item {
            MoreGroup("Tools") {
                SettingRow(FieldAtlasIcons.Globe, "Download sources", "Default servers and mirrors.", onClick = onOpenDownloadSources)
                MoreDivider()
                SettingRow(FieldAtlasIcons.Benchmark, "Performance check", "Measure speed and answers on this phone.", onClick = onOpenBenchmark)
                MoreDivider()
                SettingRow(FieldAtlasIcons.Support, "Support report", "Save a report if something goes wrong.", onClick = { sheet = "support" })
            }
        }
        item {
            MoreGroup("About") {
                SettingRow(FieldAtlasIcons.Privacy, "Privacy & offline use", "Questions stay on this phone.", onClick = { sheet = "privacy" })
                MoreDivider()
                SettingRow(FieldAtlasIcons.Document, "Licences", "Components, data and their licences.", onClick = { sheet = "licences" })
            }
        }
    }
    when (sheet) {
        "licences" -> LicenceSheet { sheet = null }
        "device" -> DeviceInfoSheet(proof, onAbout = { sheet = "about" }, onDismiss = { sheet = null })
        "support" -> SupportSheet(diagnosticsText, includeQuestion, { includeQuestion = it }, onClearDiagnostics,
            { onExportDiagnostics(includeQuestion) }, { sheet = null })
        "readiness" -> FieldAtlasInfoSheet("Offline readiness", { sheet = null }) {
            MoreNote("What is available without internet.")
            Column {
                ReadinessRow(FieldAtlasIcons.Model, "Model", model?.title?.substringBefore(" — ") ?: "Add one to generate answers.", if (model == null) "Missing" else "Saved", model != null)
                MoreDivider()
                val count = packs.count { it.type == PackType.KNOWLEDGE }
                ReadinessRow(FieldAtlasIcons.Database, "Knowledge", "Saved sources for cited answers.", if (count == 0) "Not added" else "$count saved", count > 0)
                MoreDivider()
                ReadinessRow(FieldAtlasIcons.Mic, "Voice", "Dictation works on this phone.", if (packs.any { it.type == PackType.AUDIO }) "Saved" else "Included", true)
            }
            MoreNote("Downloads need internet. Your saved files stay available offline.")
            MoreAction("Manage in Library", { sheet = null; onOpenLibrary() })
        }
        "model" -> FieldAtlasInfoSheet("Answer model", { sheet = null }) {
            model?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldAtlasIconTile(FieldAtlasIcons.Model, size = 36.dp, iconSize = 20.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(it.title.substringBefore(" — "), style = MaterialTheme.typography.titleSmall)
                        MoreNote("${formatAssetBytes(it.installedBytes)} · version ${it.version}")
                    }
                }
                MoreInfoBlock("Saved on this phone", "Freeing memory keeps the model file. Load it again when you need an answer.")
                if (inferenceState is InferenceState.Failed) Text(inferenceState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                MoreAction("Model details and options", { sheet = null; onOpenLibrary() })
            }
        }
        "about" -> FieldAtlasInfoSheet("About Field Atlas", { sheet = null }, onBack = { sheet = "device" }, backDescription = "Back to app and device") {
            Text("Field Atlas ${xyz.fieldatlas.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            MoreNote("Open-source research on this phone. No account needed.")
            MoreInfoBlock("Read the sources", "Check important answers against their saved sources. A generated answer is not a guarantee of accuracy.")
        }
        "privacy" -> FieldAtlasInfoSheet("Privacy & offline use", { sheet = null }) {
            MoreInfoBlock("On this phone", "Research, collection search and voice input run locally after setup. No account needed, and questions stay on this phone.", FieldAtlasIcons.Lock)
            MoreInfoBlock("When you connect", "Downloads contact their file hosts. External links open only when you choose them.", FieldAtlasIcons.Globe)
            MoreInfoBlock("You choose what to share", "Support reports only include your latest question if you choose to include it.", FieldAtlasIcons.Share)
        }
    }
}

/** Mockup alignment: memory control on the left, Details at the trailing edge. */
@Composable
private fun ModelMemoryControls(loaded: Boolean, enabled: Boolean, onLoadedChange: (Boolean) -> Unit, onDetails: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 6.dp)) {
        val stacked = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f
        val toggle: @Composable () -> Unit = {
            FieldAtlasSwitch(checked = loaded, enabled = enabled, compact = true,
                onCheckedChange = onLoadedChange,
                modifier = Modifier.semantics { contentDescription = "Model loaded" })
        }
        val details: @Composable () -> Unit = {
            TextButton(onClick = onDetails, shape = FieldAtlasButtonShape,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Details", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep in memory", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                toggle()
            }
            Box(Modifier.align(Alignment.End)) { details() }
        } else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keep in memory", style = MaterialTheme.typography.bodyMedium)
            toggle()
            Spacer(Modifier.weight(1f))
            details()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String?, expanded: Boolean? = null, onClick: () -> Unit, status: String? = null) {
    Row(Modifier.fillMaxWidth().semantics {
        if (expanded != null) stateDescription = if (expanded) "Expanded" else "Collapsed"
    }.clickable(role = Role.Button, onClick = onClick).heightIn(min = 72.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                status?.let { FieldAtlasStatusPill(it, if (it == "Ready" || it == "Saved") StatusTone.Positive else StatusTone.Neutral, compact = true) }
            }
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (expanded != null) FieldAtlasExpandIcon(expanded)
    }
}

@Composable
private fun MoreGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(650)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { Column(content = content) }
    }
}

@Composable
private fun ReadinessRow(icon: ImageVector, title: String, detail: String, value: String, available: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            MoreNote(detail)
        }
        FieldAtlasStatusPill(value, if (available) StatusTone.Positive else StatusTone.Neutral, compact = true)
    }
}
