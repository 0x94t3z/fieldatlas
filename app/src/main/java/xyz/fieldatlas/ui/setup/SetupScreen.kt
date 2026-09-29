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
) {
    val model = packs.firstOrNull { it.type == PackType.MODEL }
    val busy = importing || downloading || knowledgeDownloadKey != null
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FieldAtlasHeader("Set up Field Atlas", "Download once. Research offline.")
                }
                item { SetupSection("Model", "Required") }
                item {
                    SetupDownloadRow(
                        title = model?.title ?: "Qwen3.5 2B", icon = FieldAtlasIcons.Archive,
                        bytes = RecommendedModel.bytes, installed = model != null,
                        downloading = downloading, busy = busy, downloadedBytes = downloadedBytes,
                        details = "Recommended model · Apache 2.0", onDownload = onDownloadModel, onCancel = onCancelDownload,
                    )
                }
                item { SetupSection("Knowledge", "Optional") }
                item { CatalogRefresh(catalogRefreshing, catalogError, onRefreshCatalog) }
                items(availableKnowledge.size, key = { "${availableKnowledge[it].id}:${availableKnowledge[it].version}" }) { index ->
                    val pack = availableKnowledge[index]
                    SetupDownloadRow(pack.title, FieldAtlasIcons.Document, pack.bytes,
                        packs.any { it.type == PackType.KNOWLEDGE && it.id == pack.id && it.version == pack.version },
                        knowledgeDownloadKey == "${pack.id}:${pack.version}", busy, knowledgeDownloadedBytes,
                        "${pack.description}\n\nVersion ${pack.version} · ${pack.license}",
                        { onDownloadKnowledge(pack) }, onCancelKnowledgeDownload)
                }
                item {
                    Text("Add knowledge now or later in Library.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (importing) {
                        FieldAtlasCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            contentPadding = 14.dp,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Importing your pack…", style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            }
                        }
                    } else {
                        OutlinedButton(onClick = onImportPack, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) {
                            Text("Import a saved pack")
                        }
                    }
                }
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        contentPadding = 14.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(FieldAtlasIcons.Offline, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Works offline after setup", style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text("Internet is needed to download. After setup, research works with Wi-Fi and mobile data off.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                if (importing || model == null) Text(
                    if (importing) "Finish importing to continue." else "Download or import a model to start.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FieldAtlasPrimaryButton("Start researching", onContinue, enabled = model != null && !busy,
                    modifier = Modifier.fillMaxWidth())
            }
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
    downloading: Boolean, busy: Boolean, downloadedBytes: Long, details: String,
    onDownload: () -> Unit, onCancel: () -> Unit,
) {
    var confirm by rememberSaveable(title, bytes) { mutableStateOf(false) }
    val installing = downloading && downloadedBytes >= bytes
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(icon, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(if (installed) "Installed" else downloadSize(bytes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) Icon(FieldAtlasIcons.Check, "Installed", tint = MaterialTheme.colorScheme.primary)
            else if (!downloading) IconButton(onClick = { confirm = true }, enabled = !busy) {
                Icon(FieldAtlasIcons.Import, "Download $title", tint = if (busy)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary)
            }
        }
        if (downloading) {
            if (installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { (downloadedBytes.toFloat() / bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (installing) "Verifying and installing…" else "${downloadSize(downloadedBytes)} / ${downloadSize(bytes)}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (!installing) TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Text("Keep the app open.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Download $title?") },
        text = { Text("$details\n\nUses ${downloadSize(bytes)} of internet data. Allow about ${downloadSize(bytes * 2 + 512_000_000L)} free while installing. Works offline afterward.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirm = false; onDownload() }) { Text("Download") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not now") } })
}

private fun downloadSize(bytes: Long): String = if (bytes >= 1_000_000_000L)
    String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0) else "${bytes / 1_000_000} MB"
