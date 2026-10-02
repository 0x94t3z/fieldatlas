package xyz.fieldatlas.ui.library

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.AssetCardModel
import xyz.fieldatlas.ui.presentation.toAssetCardModel
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel
import xyz.fieldatlas.ui.theme.TileTone
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun LibraryScreen(
    packs: List<InstalledAsset>,
    onImportPack: () -> Unit,
    onToggleResearch: (InstalledAsset, Boolean) -> Unit = { _, _ -> },
    onActivateModel: (InstalledAsset) -> Unit = {},
    onDeletePack: (InstalledAsset) -> Unit = {},
    importing: Boolean = false,
    modelDownloading: Boolean = false,
    importError: String? = null,
    availableKnowledge: List<KnowledgeCatalogEntry> = emptyList(),
    catalogRefreshing: Boolean = false,
    catalogError: String? = null,
    onRefreshCatalog: () -> Unit = {},
    knowledgeDownloadKey: String? = null,
    knowledgeDownloadedBytes: Long = 0,
    knowledgeDownloadError: String? = null,
    onDownloadKnowledge: (KnowledgeCatalogEntry) -> Unit = {},
    onCancelKnowledgeDownload: () -> Unit = {},
    resumableKnowledgeBytes: (KnowledgeCatalogEntry) -> Long = { 0 },
) {
    val models = packs.filter { it.type == PackType.MODEL }
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    val speech = packs.filter { it.type == PackType.AUDIO }
    val available = availableKnowledge
        .filterNot { candidate -> knowledge.any { it.id == candidate.id && it.version == candidate.version } }
        .sortedWith(compareByDescending<KnowledgeCatalogEntry> { it.recommended }.thenBy { it.title })
    // The app always keeps exactly one answer model and one speech model in service; with no
    // explicit choice the first installed pack of the type serves (mirrors the runtime fallback).
    val activeModel = models.firstOrNull { it.active } ?: models.firstOrNull()
    val activeSpeech = speech.firstOrNull { it.active } ?: speech.firstOrNull()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldAtlasHeader(
                        title = "Library",
                        subtitle = "Models and knowledge on this phone.",
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(shape = FieldAtlasButtonShape, 
                        onClick = onImportPack,
                        enabled = !importing && !modelDownloading && knowledgeDownloadKey == null,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Import a pack" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(FieldAtlasIcons.Import, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (importing) "Verifying…" else "Import", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (importError != null) {
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text("This pack could not be imported.", color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                        Text(importError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (packs.isNotEmpty()) item { StorageSummary(models, knowledge, speech) }
            item { SectionLabel("Answer model") }
            if (models.isEmpty()) item { EmptyLibraryNote("No local model installed") }
            if (models.size > 1) {
                item {
                    Text(
                        "Choose one model to use for answers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(models.size, key = { "model:${models[it].id}:${models[it].version}" }) { index ->
                val asset = models[index]
                AssetCard(
                    model = asset.toAssetCardModel(),
                    visual = packVisual(asset),
                    selectedModel = asset == activeModel,
                    onActivateModel = if (asset == activeModel) null else ({ onActivateModel(asset) }),
                    onDelete = { onDeletePack(asset) },
                )
            }
            item { SectionLabel("Knowledge collections") }
            if (knowledge.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "No collection installed. Add one for local sources and citations; the model still works without it.",
                    )
                }
            }
            if (knowledge.isNotEmpty()) {
                item {
                    Text(
                        "Switched-on collections are searched for every question.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (knowledge.isNotEmpty()) item {
                // One grouped card, rows split by hairlines, as in the design.
                FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    Column {
                        knowledge.forEachIndexed { index, asset ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            AssetCard(
                                model = asset.toAssetCardModel(),
                                visual = packVisual(asset),
                                enabled = asset.enabled,
                                onToggleResearch = { enabled -> onToggleResearch(asset, enabled) },
                                onDelete = { onDeletePack(asset) },
                                inGroup = true,
                            )
                        }
                    }
                }
            }
            item { xyz.fieldatlas.ui.theme.CatalogRefresh(catalogRefreshing, catalogError, onRefreshCatalog) }
            if (available.isNotEmpty()) {
                item { SectionLabel("Available to download") }
                items(available.size, key = { "available:${available[it].id}:${available[it].version}" }) { index ->
                    val candidate = available[index]
                    KnowledgeDownloadCard(
                        pack = candidate,
                        downloading = knowledgeDownloadKey == "${candidate.id}:${candidate.version}",
                        busy = importing || modelDownloading || knowledgeDownloadKey != null,
                        downloadedBytes = knowledgeDownloadedBytes,
                        resumableBytes = resumableKnowledgeBytes(candidate),
                        onDownload = { onDownloadKnowledge(candidate) },
                        onCancel = onCancelKnowledgeDownload,
                    )
                }
            }
            if (knowledgeDownloadError != null) {
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text("Collection download did not finish", color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                        Text(knowledgeDownloadError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionLabel("Audio models") }
            if (speech.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "No dictation model installed - the compact built-in voice model is used.",
                    )
                }
            }
            items(speech.size, key = { "audio:${speech[it].id}:${speech[it].version}" }) { index ->
                val asset = speech[index]
                AssetCard(
                    model = asset.toAssetCardModel(),
                    visual = packVisual(asset),
                    selectedModel = asset == activeSpeech,
                    onActivateModel = if (asset == activeSpeech) null else ({ onActivateModel(asset) }),
                    onDelete = { onDeletePack(asset) },
                )
            }
        }
    }
}

@Composable
internal fun KnowledgeDownloadCard(
    pack: KnowledgeCatalogEntry,
    downloading: Boolean,
    busy: Boolean,
    downloadedBytes: Long,
    resumableBytes: Long = 0,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmDownload by rememberSaveable(pack.id, pack.version) { mutableStateOf(false) }
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(FieldAtlasIcons.Document, size = 44.dp, tone = TileTone.Paper)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pack.title, style = MaterialTheme.typography.titleMedium,
                    fontFamily = FieldAtlasEditorial,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatAssetBytes(pack.bytes)} · version ${pack.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(pack.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!downloading && resumableBytes > 0) {
            Text("${formatAssetBytes(resumableBytes)} saved · ready to resume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
        if (pack.recommended) {
            Text("Recommended", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        if (downloading) {
            val installing = downloadedBytes >= pack.bytes
            if (installing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { (downloadedBytes.toFloat() / pack.bytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (installing) "Verifying and installing in background" else
                    "${downloadedBytes / 1_000_000} / ${pack.bytes / 1_000_000} MB · continues in background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!installing) TextButton(shape = FieldAtlasButtonShape, onClick = onCancel) { Text("Pause") }
            }
        } else {
            FieldAtlasPrimaryButton(
                text = if (resumableBytes > 0) "Resume download" else "Download collection",
                onClick = { confirmDownload = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = FieldAtlasIcons.Import,
            )
        }
    }
    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("${if (resumableBytes > 0) "Resume" else "Download"} ${pack.title}?") },
            text = {
                Text((if (resumableBytes > 0)
                    "${formatAssetBytes(resumableBytes)} is saved. About ${formatAssetBytes(pack.bytes - resumableBytes.coerceIn(0, pack.bytes))} remains if the server supports resume; otherwise the full download may be needed. "
                    else "Uses ${formatAssetBytes(pack.bytes)} of internet data. ") +
                    "Installation needs about " +
                    "${sizeGb(pack.bytes * 2 + 512_000_000L)} GB free while installing. " +
                    "Afterward, the collection works offline.")
            },
            confirmButton = {
                TextButton(shape = FieldAtlasButtonShape, onClick = {
                    confirmDownload = false
                    onDownload()
                }) { Text(if (resumableBytes > 0) "Resume" else "Download") }
            },
            dismissButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { confirmDownload = false }) { Text("Not now") } },
        )
    }
}

private fun sizeGb(bytes: Long): String = String.format("%.1f", bytes / 1_000_000_000.0)

@Composable
private fun SectionLabel(text: String) {
    FieldAtlasSectionLabel(text, Modifier.padding(top = 6.dp))
}

/** How a pack is drawn: its subject decides the icon and a palette tint. */
internal data class PackVisual(val icon: ImageVector, val tone: TileTone)

internal fun packVisual(asset: InstalledAsset): PackVisual {
    val id = asset.id.lowercase()
    return when {
        asset.type == PackType.MODEL -> PackVisual(FieldAtlasIcons.Model, TileTone.Sage)
        asset.type == PackType.AUDIO -> PackVisual(Icons.Outlined.Mic, TileTone.Paper)
        listOf("voyage", "place", "osm", "travel").any { it in id } -> PackVisual(FieldAtlasIcons.Place, TileTone.Gold)
        listOf("biology", "science", "longevity", "health").any { it in id } -> PackVisual(FieldAtlasIcons.Knowledge, TileTone.Sage)
        else -> PackVisual(FieldAtlasIcons.Document, TileTone.Paper)
    }
}

@Composable
private fun StorageSummary(models: List<InstalledAsset>, knowledge: List<InstalledAsset>, speech: List<InstalledAsset>) {
    val parts = listOf(
        Triple("Models", models.sumOf { it.installedBytes }, MaterialTheme.colorScheme.primary),
        Triple("Knowledge", knowledge.sumOf { it.installedBytes }, Color(0xFF8DB59E)),
        Triple("Voice", speech.sumOf { it.installedBytes }, Color(0xFFC9A85C)),
    ).filter { it.second > 0 }
    val total = parts.sumOf { it.second }
    if (total <= 0) return
    val count = models.size + knowledge.size + speech.size
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatAssetBytes(total)} on this phone", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Text(if (count == 1) "1 pack" else "$count packs", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { (_, bytes, color) ->
                Box(Modifier.weight(bytes.toFloat().coerceAtLeast(total * 0.02f)).fillMaxHeight().background(color))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            parts.forEach { (label, bytes, color) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text("$label ${formatAssetBytes(bytes)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    model: AssetCardModel,
    visual: PackVisual,
    enabled: Boolean = true,
    onToggleResearch: ((Boolean) -> Unit)? = null,
    selectedModel: Boolean = false,
    onActivateModel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    inGroup: Boolean = false,
) {
    var confirmDelete by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // A switched-off collection stays readable but visibly inactive.
    val inactive = onToggleResearch != null && !enabled
    AssetSurface(inGroup) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldAtlasIconTile(visual.icon, size = 44.dp, tone = visual.tone,
                modifier = Modifier.alpha(if (inactive) 0.5f else 1f))
            Column(Modifier.weight(1f).alpha(if (inactive) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FieldAtlasEditorial,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${model.size} · v${model.version}" + if (inactive) " · not used" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                selectedModel -> FieldAtlasStatusPill("In use", StatusTone.Positive, icon = FieldAtlasIcons.Check)
                onActivateModel != null -> TextButton(shape = FieldAtlasButtonShape, onClick = onActivateModel) { Text("Use model") }
                onToggleResearch != null -> Switch(
                    checked = enabled,
                    onCheckedChange = onToggleResearch,
                    modifier = Modifier.semantics { contentDescription = "Use ${model.title} in research" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
            if (onDelete != null) {
                // Deleting is rare, so it lives in the overflow menu rather than beside the toggle.
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${model.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete…") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this pack?") },
            text = {
                Text(
                    "${model.title} ${model.version} will be removed from this phone. " +
                        "You can import it again later from its .fapack file.",
                )
            },
            confirmButton = {
                TextButton(shape = FieldAtlasButtonShape, 
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun AssetSurface(inGroup: Boolean, content: @Composable () -> Unit) {
    if (inGroup) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    else FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) { content() }
}

@Composable
private fun EmptyLibraryNote(text: String) {
    FieldAtlasCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
