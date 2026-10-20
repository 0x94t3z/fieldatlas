package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import xyz.fieldatlas.speech.ReadAloud
import xyz.fieldatlas.ui.library.AboutFact
import xyz.fieldatlas.ui.library.AboutFacts
import xyz.fieldatlas.ui.library.AboutValue
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.automirrored.outlined.VolumeUp

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
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    var includeQuestion by rememberSaveable { mutableStateOf(false) }
    val model = packs.firstOrNull { it.type == PackType.MODEL && it.active } ?: packs.firstOrNull { it.type == PackType.MODEL }
    val readAloud = xyz.fieldatlas.ui.research.LocalReadAloud.current
    val readingSettings = readAloud?.settings?.collectAsState()?.value
    val naturalReady = readAloud?.natural?.state?.collectAsState()?.value == xyz.fieldatlas.speech.NaturalVoice.State.Ready
    val phoneVoice = readAloud?.phoneVoice?.collectAsState()?.value
    val busy = inferenceState == InferenceState.Loading || inferenceState == InferenceState.Generating
    val loaded = inferenceState == InferenceState.Ready || inferenceState == InferenceState.Generating
    val status = when (inferenceState) {
        InferenceState.Ready -> "Ready"; InferenceState.Loading -> "Loading…"
        InferenceState.Generating -> "Answering"; is InferenceState.Failed -> "Needs attention"
        InferenceState.Idle -> "Not loaded"
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { FieldAtlasPageHeader("More", "Settings and app details.") }
        item {
            // Settings rows, always visible: the model (opens Library) and the one setting it has.
            MoreGroup("Answer model") {
                if (model == null) SettingRow(FieldAtlasIcons.Model, "Add an answer model", "Needed to write answers. Get one in Library.",
                    onClick = onOpenLibrary, chevron = true)
                else {
                    // Tapping the model shows or hides its one setting; changing models stays in Library.
                    SettingRow(FieldAtlasIcons.Model, model.title.substringBefore(" — "),
                        "${formatAssetBytes(model.installedBytes)} · $status", expanded = modelExpanded,
                        onClick = { modelExpanded = !modelExpanded })
                    androidx.compose.animation.AnimatedVisibility(modelExpanded) {
                        Column {
                            MoreDivider()
                            FastStartRow(loaded, !busy, inferenceState == InferenceState.Loading, formatAssetBytes(model.installedBytes),
                                (inferenceState as? InferenceState.Failed)?.message) { if (it) onPrepareModel() else onReleaseModel() }
                        }
                    }
                }
            }
        }
        if (readAloud != null && readingSettings != null) item {
            MoreGroup("Reading aloud") {
                SettingRow(Icons.AutoMirrored.Outlined.VolumeUp, "Voice and speed", xyz.fieldatlas.ui.research.readingSummary(readingSettings, naturalReady),
                    onClick = { sheet = "reading" })
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
                SettingRow(FieldAtlasIcons.Storage, "Download sources", "Default servers and mirrors.", onClick = onOpenDownloadSources)
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
        "reading" -> readAloud?.let { xyz.fieldatlas.ui.research.ReadingAloudSheet(it) { sheet = null } }
        "device" -> DeviceInfoSheet(proof, packs, onTechnical = { sheet = "technical" }, onDismiss = { sheet = null })
        "technical" -> TechnicalDetailsSheet(proof, onBack = { sheet = "device" }, onDismiss = { sheet = null })
        "support" -> SupportSheet(diagnosticsText, includeQuestion, { includeQuestion = it }, onClearDiagnostics,
            { onExportDiagnostics(includeQuestion) }, { sheet = null })
        "readiness" -> FieldAtlasInfoSheet("Offline readiness", { sheet = null }) {
            // Looked at as the sheet opens, so the reading row can say whether the phone's voice works offline.
            LaunchedEffect(readAloud) { readAloud?.checkPhoneVoice() }
            val saved = packs.count { it.type == PackType.KNOWLEDGE }
            val collections = packs.count { it.type == PackType.KNOWLEDGE && it.enabled }
            // An installed model that stopped with an error (loading, answering or unloading) is not ready.
            val modelFailed = model != null && inferenceState is InferenceState.Failed
            ReadinessVerdict(ready = model != null && !modelFailed, modelFailed = modelFailed)
            // The pack sheets' facts card: each row's words say whether it is ready, so none needs a mark.
            AboutFacts {
                ReadinessFact("Answer model", when {
                    modelFailed -> "Stopped with an error"
                    model == null -> "Not downloaded"
                    else -> model.title.substringBefore(" — ")
                }, divider = false)
                ReadinessFact("Collections", when {
                    collections > 0 -> "$collections in research"
                    saved > 0 -> "None in research"
                    else -> "None downloaded"
                })
                ReadinessFact("Voice input", if (packs.any { it.type == PackType.AUDIO }) "Saved model" else "Built in")
                // Guides always have their recordings; answers use the natural voice once it is downloaded, else the phone's.
                ReadinessFact("Reading aloud", when {
                    naturalReady -> xyz.fieldatlas.speech.NaturalVoice.NAME
                    phoneVoice == ReadAloud.PhoneVoice.OnlineOnly -> "Phone voice needs the internet"
                    phoneVoice == ReadAloud.PhoneVoice.Missing -> "No voice on this phone"
                    else -> "Phone voice"
                })
            }
            MoreNote("Only downloads need internet.")
            OutlinedButton(onClick = { sheet = null; onOpenLibrary() }, shape = FieldAtlasButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (model == null) "Get a model in Library" else "Manage in Library")
            }
        }
        "privacy" -> FieldAtlasInfoSheet("Privacy & offline use", { sheet = null }) {
            MoreInfoBlock("On this phone", "Research, collection search and voice input run locally after setup. No account needed, and questions stay on this phone.", FieldAtlasIcons.Lock)
            MoreInfoBlock("When you connect", "Downloads contact their file hosts. External links open only when you choose them.", FieldAtlasIcons.Globe)
            MoreInfoBlock("You choose what to share", "Support reports only include your latest question if you choose to include it.", FieldAtlasIcons.Share)
        }
    }
}

/** The switch is named by what it gives (sooner answers) and says what it costs (memory). */
@Composable
private fun FastStartRow(loaded: Boolean, enabled: Boolean, loading: Boolean, modelSize: String, failure: String?,
                         onLoadedChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Start answers faster", style = MaterialTheme.typography.titleSmall)
                Text(if (loading) "Loading the model…" else "Keeps the model loaded so answers start sooner. Holds about $modelSize in memory.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FieldAtlasSwitch(checked = loaded, enabled = enabled, compact = true, onCheckedChange = onLoadedChange,
                // Named as the row reads, so "tap Start answers faster" finds it.
                modifier = Modifier.semantics { contentDescription = "Start answers faster" })
        }
        failure?.let {
            Text(it, Modifier.padding(start = 50.dp, end = 14.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String?, expanded: Boolean? = null, onClick: () -> Unit, status: String? = null,
                       chevron: Boolean = false) {
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
        if (chevron) Icon(FieldAtlasIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel(title)
        FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { Column(content = content) }
    }
}

/** The sheet's answer first: can the phone research offline right now? */
@Composable
private fun ReadinessVerdict(ready: Boolean, modelFailed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if (ready) colors.primaryContainer else colors.surfaceVariant, shape = FieldAtlasButtonShape) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // Ready shows the app's offline dino; not ready shows a neutral info mark. No badge behind it.
            Icon(if (ready) FieldAtlasIcons.Offline else FieldAtlasIcons.Info, null, Modifier.size(32.dp),
                tint = if (ready) colors.primary else colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (ready) "Ready to work offline" else "Not ready yet", style = MaterialTheme.typography.titleSmall,
                    color = if (ready) colors.onPrimaryContainer else colors.onSurface)
                Text(when {
                    ready -> "Questions, sources and voice all run on this phone."
                    modelFailed -> "The answer model stopped with an error. Load it again under Answer model."
                    else -> "Add an answer model to write answers."
                },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

/** One row of the readiness card; its labels run longer than the pack facts', so the label column is wider. */
@Composable
private fun ColumnScope.ReadinessFact(label: String, value: String, divider: Boolean = true) =
    AboutFact(label, divider = divider, labelWidth = 100.dp) { AboutValue(value) }
