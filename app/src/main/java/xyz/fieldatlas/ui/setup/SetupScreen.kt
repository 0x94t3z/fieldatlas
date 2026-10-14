package xyz.fieldatlas.ui.setup

import xyz.fieldatlas.ui.theme.FieldAtlasProgressBar
import xyz.fieldatlas.assets.downloadFreeBytes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.ui.presentation.knowledgeCategory
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.assets.PackImportInfo
import xyz.fieldatlas.ui.theme.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    packs: List<InstalledAsset>, importing: Boolean, downloading: Boolean,
    downloadedBytes: Long, error: String?, onImportPack: () -> Unit,
    onDownloadModel: () -> Unit, onCancelDownload: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    availableKnowledge: List<KnowledgeCatalogEntry> = emptyList(),
    catalogRefreshing: Boolean = false, catalogError: String? = null,
    onRefreshCatalog: () -> Unit = {},
    knowledgeDownloadKey: String? = null, knowledgeDownloadedBytes: Long = 0,
    knowledgeError: String? = null, onDownloadKnowledge: (KnowledgeCatalogEntry) -> Unit = {},
    onCancelKnowledgeDownload: () -> Unit = {}, onContinue: () -> Unit = {},
    resumableModelBytes: Long = 0,
    resumableKnowledgeBytes: (KnowledgeCatalogEntry) -> Long = { 0 },
    importingName: String? = null,
    online: Boolean = rememberSetupConnectivity(),
    importingPack: PackImportInfo? = null,
    /** A model other than the recommended one is downloading (started from Library). */
    otherModelDownloading: Boolean = false,
    onOpenDownloadSources: () -> Unit = {},
    modelDownloadUrl: String = ModelOffers.recommended.url,
    knowledgeDownloadUrl: (KnowledgeCatalogEntry) -> String = { it.url },
    modelDownloadError: String? = null,
) {
    val recommended = ModelOffers.recommended
    val model = packs.firstOrNull { it.type == PackType.MODEL }
    val busy = importing || downloading || otherModelDownloading || knowledgeDownloadKey != null
    val blockedReason = when {
        importing || !online -> null // The single import/offline notice explains unavailable actions.
        busy -> "Another download is in progress."
        else -> null
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text("Field Atlas", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        TextButton(shape = FieldAtlasButtonShape, onClick = onOpenDownloadSources,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(start = 8.dp, end = 0.dp)) {
                            Icon(FieldAtlasIcons.Storage, null, Modifier.size(18.dp))
                            Text("Download sources", Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(650)))
                        }
                    }
                }
                item {
                    FieldAtlasPageHeader("A little setup.\nThen, offline.",
                        "Choose what lives on your phone. You can change it later.",
                        titleStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.9).sp))
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldAtlasStatusPill("Offline", StatusTone.Positive, icon = FieldAtlasIcons.Offline)
                        FieldAtlasStatusPill("Cited", StatusTone.Attention, icon = FieldAtlasIcons.Citation)
                        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
                        FieldAtlasStatusPill("Private", icon = FieldAtlasIcons.Lock,
                            containerColor = if (light) Color(0xFFF2DED2) else Color(0xFF51372C),
                            contentColor = if (light) Color(0xFF754333) else Color(0xFFEFC7B2))
                    }
                }
                if (!importing) item {
                    TextButton(shape = FieldAtlasButtonShape, onClick = onImportPack, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(FieldAtlasIcons.Import, null, Modifier.size(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Import .fapack", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(650)))
                                Text("Model or knowledge pack", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Explain unavailable downloads before their controls, not at the end of the list.
                if (!online) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top) {
                        Icon(FieldAtlasIcons.Offline, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("You’re offline. Import saved packs, or reconnect to download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (importing && importingPack?.type !in setOf(PackType.MODEL, PackType.KNOWLEDGE)) item {
                    SetupImportCard(importingPack?.title ?: "Saved pack", importingName, "Import in progress")
                }
                item { SetupSection(1, "Answer model", "Required") }
                item {
                    if (importing && importingPack?.type == PackType.MODEL) {
                        SetupImportCard(if (importingPack.id == recommended.id) recommended.name else importingPack.title,
                            importingName, "Importing model", FieldAtlasIcons.Model)
                    } else SetupDownloadRow(
                        title = if (model == null || model.id == recommended.id) recommended.name else model.title,
                        icon = FieldAtlasIcons.Model,
                        bytes = recommended.bytes, installed = model != null,
                        downloading = downloading, busy = busy || !online, downloadedBytes = downloadedBytes,
                        resumableBytes = resumableModelBytes,
                        details = "Recommended model · Apache 2.0", onDownload = onDownloadModel, onCancel = onCancelDownload,
                        subtitle = if (model == null || model.id == recommended.id) "Q4_K_M" else null,
                        blockedReason = blockedReason,
                        // The verified model is moved into place, not unpacked from a copy.
                        freeBytesNeeded = recommended.bytes - resumableModelBytes.coerceIn(0, recommended.bytes) + 512_000_000L,
                        downloadUrl = modelDownloadUrl,
                        downloadError = modelDownloadError,
                        online = online,
                    )
                }
                item {
                    val refresh: @Composable () -> Unit = {
                        TextButton(shape = FieldAtlasButtonShape, onClick = onRefreshCatalog, enabled = online && !catalogRefreshing) {
                            Text(if (catalogRefreshing) "Refreshing…" else "Refresh")
                        }
                    }
                    if (LocalDensity.current.fontScale > 1.3f) Column(Modifier.fillMaxWidth()) {
                        SetupSection(2, "Knowledge", "Optional · add later in Library")
                        Box(Modifier.align(Alignment.End)) { refresh() }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SetupSection(2, "Knowledge", "Optional · add later in Library") }
                        refresh()
                    }
                    if (online && catalogError != null) Text(catalogError,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (importing && importingPack?.type == PackType.KNOWLEDGE && availableKnowledge.none { it.id == importingPack.id }) item {
                    SetupImportCard(importingPack.title, importingName, "Importing knowledge")
                }
                availableKnowledge
                    .groupBy { knowledgeCategory(it.id, it.category) }
                    .toSortedMap(compareBy { it.ordinal })
                    .forEach { (category, group) -> item(key = "setup-category:${category.name}") { Column {
                    // One grouped card per category of optional collections, as in the design.
                    Text(category.label, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        Column {
                            group.sortedWith(xyz.fieldatlas.ui.presentation.knowledgeOrder({ category }, { it.recommended }, { it.title }))
                                .forEachIndexed { index, pack ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                // The same subject icon and tint the collection gets in Library.
                                val visual = xyz.fieldatlas.ui.library.subjectVisual(pack.id)
                                if (importing && importingPack?.type == PackType.KNOWLEDGE && importingPack.id == pack.id) {
                                    SetupImportCard(importingPack.title, importingName, "Importing knowledge", visual?.icon ?: FieldAtlasIcons.Document)
                                } else SetupDownloadRow(pack.title, visual?.icon ?: FieldAtlasIcons.Document, pack.bytes,
                                    packs.any { it.type == PackType.KNOWLEDGE && it.id == pack.id && it.version == pack.version },
                                    knowledgeDownloadKey == "${pack.id}:${pack.version}", busy || !online, knowledgeDownloadedBytes,
                                    resumableKnowledgeBytes(pack),
                                    "${pack.description}\n\nVersion ${pack.version} · ${xyz.fieldatlas.ui.presentation.displayLicence(pack.license)}",
                                    { onDownloadKnowledge(pack) }, onCancelKnowledgeDownload,
                                    blockedReason = blockedReason, inGroup = true, tone = visual?.tone,
                                    freeBytesNeeded = pack.downloadFreeBytes(resumableKnowledgeBytes(pack)),
                                    downloadUrl = knowledgeDownloadUrl(pack))
                            }
                        }
                    }
                } } }
                (knowledgeError ?: error?.takeUnless { it == modelDownloadError })?.let { message -> item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                        Text("Setup couldn’t finish", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error)
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!importing && !downloading && !otherModelDownloading && model == null) Text(
                    "Add a model to start. Knowledge can come later.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!importing && model != null && knowledgeDownloadKey != null) Text(
                    "Your knowledge download will continue in the background.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val waitingMessage = when {
                    importing && importingPack?.type == PackType.MODEL -> "Your model is importing. Research will be ready when it finishes."
                    importing -> "Your pack is importing. Please wait before starting research."
                    downloading && downloadedBytes >= recommended.bytes -> "Your model is being checked and installed. Please wait."
                    downloading || otherModelDownloading -> "Your model is downloading. Research will be ready after installation."
                    else -> null
                }
                waitingMessage?.let { Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FieldAtlasPrimaryButton("Start researching", onContinue,
                    enabled = model != null && !importing && !downloading && !otherModelDownloading,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SetupImportCard(title: String, filename: String?, status: String, icon: ImageVector = FieldAtlasIcons.Document) {
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(icon, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            filename?.let { Text(it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer)
            Text("Please wait while this pack is checked and installed.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupSection(step: Int, title: String, hint: String) {
    if (hint == "Required") {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text("0$step / $title", style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("0$step / $title", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text(hint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupDownloadRow(
    title: String, icon: ImageVector, bytes: Long, installed: Boolean,
    downloading: Boolean, busy: Boolean, downloadedBytes: Long, resumableBytes: Long, details: String,
    onDownload: () -> Unit, onCancel: () -> Unit,
    subtitle: String? = null, blockedReason: String? = null, inGroup: Boolean = false, tone: TileTone? = null,
    freeBytesNeeded: Long = bytes * 2 + 512_000_000L,
    downloadUrl: String,
    downloadError: String? = null,
    online: Boolean = true,
) {
    var confirm by rememberSaveable(title, bytes) { mutableStateOf(false) }
    val installing = downloading && downloadedBytes >= bytes
    // Keep full names readable: with large text, the action gets its own line instead of
    // competing with the name. Installed rows need only one textual status, not two pills.
    val stackAction = LocalDensity.current.fontScale > 1.3f
    val downloadAction: @Composable () -> Unit = {
        if (!installed && !downloading && downloadError == null) {
            FilledTonalIconButton(shape = FieldAtlasButtonShape, onClick = { confirm = true },
                enabled = !busy && blockedReason == null,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (resumableBytes > 0) "Resume $title" else "Download $title"
                }, colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Icon(FieldAtlasIcons.Download, null, Modifier.size(20.dp))
            }
        } else {
        if (!installed && !downloading) FilledTonalButton(shape = FieldAtlasButtonShape,
            onClick = { confirm = true },
            enabled = !busy && blockedReason == null,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = if (downloadError != null) "Retry download $title"
                    else if (resumableBytes > 0) "Resume $title" else "Download $title"
            },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(if (downloadError != null) "Retry" else if (resumableBytes > 0) "Resume" else "Get",
                style = MaterialTheme.typography.labelMedium)
        }
        }
    }
    SetupRowSurface(inGroup) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(icon, size = 36.dp, tone = tone ?: TileTone.Sage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = if (inGroup) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium)
                val status = if (installed) "Installed" else if (!downloading && resumableBytes > 0)
                    "${downloadSize(resumableBytes)} saved · resume" else downloadSize(bytes)
                Text(listOfNotNull(subtitle, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!installed && !downloading && blockedReason != null) Text(blockedReason,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) Icon(FieldAtlasIcons.Check, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            else if (!stackAction) downloadAction()
        }
        if (stackAction && !installed && !downloading) Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End) { downloadAction() }
        if (downloadError != null && !installed && !downloading) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(FieldAtlasIcons.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Download interrupted", style = MaterialTheme.typography.labelMedium)
                    // Keep the actual failure visible; a checksum failure must not be called a connection issue.
                    Text(downloadError, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (resumableBytes > 0) Text("${downloadSize(resumableBytes)} saved. Your progress is kept.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!online) Text("Go online to retry.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (downloading) {
            if (installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else FieldAtlasProgressBar(progress = { (downloadedBytes.toFloat() / bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (installing) "Verifying and installing…" else "${downloadSize(downloadedBytes)} / ${downloadSize(bytes)}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (!installing) TextButton(shape = FieldAtlasButtonShape, onClick = onCancel) { Text("Pause") }
            }
            Text("You can switch apps while this finishes. Paused downloads can be resumed later.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("${if (resumableBytes > 0) "Resume" else "Download"} $title?") },
        text = { Text("$details\n\n" + (if (resumableBytes > 0)
            "${downloadSize(resumableBytes)} is saved. About ${downloadSize(bytes - resumableBytes.coerceIn(0, bytes))} remains if the server supports resume; otherwise the full download may be needed. "
            else "Uses ${downloadSize(bytes)} of internet data. ") +
            "Allow about ${downloadSize(freeBytesNeeded)} free while installing. Works offline afterward.\n\n" +
            xyz.fieldatlas.ui.presentation.downloadSourceDisclosure(downloadUrl)) },
        confirmButton = { TextButton(shape = FieldAtlasButtonShape, enabled = !busy && blockedReason == null, onClick = { confirm = false; onDownload() }) {
            Text(if (resumableBytes > 0) "Resume" else "Download") } },
        dismissButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { confirm = false }) { Text("Not now") } })
}

@Composable
private fun SetupRowSurface(inGroup: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (inGroup) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    else FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp, content = content)
}

// One size format across the app, so a download reads the same in setup and in Library.
private fun downloadSize(bytes: Long): String = xyz.fieldatlas.ui.presentation.formatAssetBytes(bytes)
