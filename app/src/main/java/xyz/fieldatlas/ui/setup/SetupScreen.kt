package xyz.fieldatlas.ui.setup

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.assets.RecommendedModel
import xyz.fieldatlas.assets.PackImportInfo
import xyz.fieldatlas.ui.theme.*
import java.util.Locale

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
) {
    val model = packs.firstOrNull { it.type == PackType.MODEL }
    val busy = importing || downloading || knowledgeDownloadKey != null
    val blockedReason = when {
        importing || !online -> null // The single import/offline notice explains unavailable actions.
        busy -> "Another download is in progress."
        else -> null
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FieldAtlasHeader("Set up Field Atlas", "Download once. Research offline.")
                }
                if (!online) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(FieldAtlasIcons.Offline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("You’re offline. Import saved packs, or reconnect to download.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (importing && importingPack?.type !in setOf(PackType.MODEL, PackType.KNOWLEDGE)) item {
                    SetupImportCard(importingPack?.title ?: "Saved pack", importingName, "Import in progress")
                }
                item { SetupSection("Model", "Required") }
                item {
                    if (importing && importingPack?.type == PackType.MODEL) {
                        SetupImportCard(if (importingPack.id == RecommendedModel.id) "Qwen3.5 2B" else importingPack.title,
                            importingName, "Importing model", FieldAtlasIcons.Archive)
                    } else SetupDownloadRow(
                        title = if (model == null || model.id == RecommendedModel.id) "Qwen3.5 2B" else model.title,
                        icon = FieldAtlasIcons.Archive,
                        bytes = RecommendedModel.bytes, installed = model != null,
                        downloading = downloading, busy = busy || !online, downloadedBytes = downloadedBytes,
                        resumableBytes = resumableModelBytes,
                        details = "Recommended model · Apache 2.0", onDownload = onDownloadModel, onCancel = onCancelDownload,
                        subtitle = if (model == null || model.id == RecommendedModel.id) "Q4_K_M · Compact" else null,
                        blockedReason = blockedReason,
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SetupSection("Knowledge", "Optional") }
                        TextButton(onClick = onRefreshCatalog, enabled = online && !catalogRefreshing) {
                            Text(if (catalogRefreshing) "Refreshing…" else "Refresh")
                        }
                    }
                    if (online && catalogError != null) Text(catalogError,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (importing && importingPack?.type == PackType.KNOWLEDGE && availableKnowledge.none { it.id == importingPack.id }) item {
                    SetupImportCard(importingPack.title, importingName, "Importing knowledge")
                }
                items(availableKnowledge.size, key = { "${availableKnowledge[it].id}:${availableKnowledge[it].version}" }) { index ->
                    val pack = availableKnowledge[index]
                    if (importing && importingPack?.type == PackType.KNOWLEDGE && importingPack.id == pack.id) {
                        SetupImportCard(importingPack.title, importingName, "Importing knowledge")
                    } else SetupDownloadRow(pack.title, FieldAtlasIcons.Document, pack.bytes,
                        packs.any { it.type == PackType.KNOWLEDGE && it.id == pack.id && it.version == pack.version },
                        knowledgeDownloadKey == "${pack.id}:${pack.version}", busy || !online, knowledgeDownloadedBytes,
                        resumableKnowledgeBytes(pack),
                        "${pack.description}\n\nVersion ${pack.version} · ${pack.license}",
                        { onDownloadKnowledge(pack) }, onCancelKnowledgeDownload,
                        blockedReason = blockedReason)
                }
                item {
                    Text("Add knowledge now or later in Library.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!importing) {
                        OutlinedButton(onClick = onImportPack, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) {
                            Text("Import a saved pack")
                        }
                    }
                }
                (knowledgeError ?: error)?.let { message -> item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                        Text("Setup couldn’t finish", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error)
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!importing && !downloading && model == null) Text(
                    "Download or import a model to start.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!importing && model != null && knowledgeDownloadKey != null) Text(
                    "Your knowledge download will continue in the background.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val waitingMessage = when {
                    importing && importingPack?.type == PackType.MODEL -> "Your model is importing. Research will be ready when it finishes."
                    importing -> "Your pack is importing. Please wait before starting research."
                    downloading && downloadedBytes >= RecommendedModel.bytes -> "Your model is being checked and installed. Please wait."
                    downloading -> "Your model is downloading. Research will be ready after installation."
                    else -> null
                }
                waitingMessage?.let { Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FieldAtlasPrimaryButton("Start researching", onContinue, enabled = model != null && !importing && !downloading,
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
            filename?.let { Text(it, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer)
            Text("Please wait while this pack is checked and installed.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupSection(title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("· $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupDownloadRow(
    title: String, icon: ImageVector, bytes: Long, installed: Boolean,
    downloading: Boolean, busy: Boolean, downloadedBytes: Long, resumableBytes: Long, details: String,
    onDownload: () -> Unit, onCancel: () -> Unit,
    subtitle: String? = null, blockedReason: String? = null,
) {
    var confirm by rememberSaveable(title, bytes) { mutableStateOf(false) }
    val installing = downloading && downloadedBytes >= bytes
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(icon, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                val status = if (installed) "Installed" else if (!downloading && resumableBytes > 0)
                    "${downloadSize(resumableBytes)} saved · resume" else downloadSize(bytes)
                Text(listOfNotNull(subtitle, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!installed && !downloading && blockedReason != null) Text(blockedReason,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) Icon(FieldAtlasIcons.Check, "Installed", tint = MaterialTheme.colorScheme.primary)
            else if (!downloading) IconButton(onClick = { confirm = true }, enabled = !busy && blockedReason == null) {
                Icon(FieldAtlasIcons.Import, if (resumableBytes > 0) "Resume $title" else "Download $title", tint = if (busy || blockedReason != null)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary)
            }
        }
        if (downloading) {
            if (installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { (downloadedBytes.toFloat() / bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (installing) "Verifying and installing…" else "${downloadSize(downloadedBytes)} / ${downloadSize(bytes)}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (!installing) TextButton(onClick = onCancel) { Text("Pause") }
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
            "Allow about ${downloadSize(bytes * 2 + 512_000_000L)} free while installing. Works offline afterward.") },
        confirmButton = { TextButton(enabled = !busy && blockedReason == null, onClick = { confirm = false; onDownload() }) {
            Text(if (resumableBytes > 0) "Resume" else "Download") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not now") } })
}

private fun downloadSize(bytes: Long): String = if (bytes >= 1_000_000_000L)
    String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0) else "${bytes / 1_000_000} MB"
