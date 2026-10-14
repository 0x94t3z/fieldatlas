package xyz.fieldatlas.ui.library

import xyz.fieldatlas.ui.theme.FieldAtlasProgressBar
import xyz.fieldatlas.assets.downloadFreeBytes

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
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.ModelOffer
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.AssetCardModel
import xyz.fieldatlas.ui.presentation.toAssetCardModel
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.presentation.KnowledgeCategory
import xyz.fieldatlas.ui.presentation.knowledgeCategory
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasDeleteDialog
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected

@Composable
fun LibraryScreen(
    packs: List<InstalledAsset>,
    onImportPack: () -> Unit,
    onToggleResearch: (InstalledAsset, Boolean) -> Unit = { _, _ -> },
    onActivateModel: (InstalledAsset) -> Unit = {},
    onDeletePack: (InstalledAsset) -> Unit = {},
    onAskExample: ((String) -> Unit)? = null,
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
    /** Models this phone has the memory for; the ones not installed get a download card. */
    modelOffers: List<ModelOffer> = emptyList(),
    downloadingModelId: String? = null,
    modelDownloadedBytes: Long = 0,
    modelDownloadError: String? = null,
    modelDownloadErrorId: String? = null,
    onDownloadModel: (ModelOffer) -> Unit = {},
    onCancelModelDownload: () -> Unit = {},
    resumableModelBytes: (ModelOffer) -> Long = { 0 },
    /** Discards a paused download of a model or collection. */
    onDiscardPausedDownload: ((Any) -> Unit)? = null,
    /** A model is loading or answering; switching then would wait for it, so it is offered after. */
    modelBusy: Boolean = false,
    modelFits: (InstalledAsset) -> Boolean = { true },
    modelNotice: String? = null,
    modelDownloadUrl: (ModelOffer) -> String = { it.url },
    knowledgeDownloadUrl: (KnowledgeCatalogEntry) -> String = { it.url },
    onOpenDownloadSources: () -> Unit = {},
) {
    var discovering by rememberSaveable { mutableStateOf(false) }
    val models = packs.filter { it.type == PackType.MODEL }
    val modelDownloads = modelOffers.filterNot { offer -> models.any { it.id == offer.id && it.version == offer.version } }
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    val speech = packs.filter { it.type == PackType.AUDIO }
    // Installed packs carry no category of their own; the catalog's label wins when it lists the pack.
    val declared = availableKnowledge.associate { it.id to it.category }
    val recommended = availableKnowledge.filter { it.recommended }.map { it.id }.toSet()
    val installedOrder = xyz.fieldatlas.ui.presentation.knowledgeOrder<InstalledAsset>(
        { knowledgeCategory(it.id, declared[it.id].orEmpty()) }, { it.id in recommended }, { it.toAssetCardModel().title })
    val knowledgeGroups = knowledge.sortedWith(installedOrder)
        .groupBy { knowledgeCategory(it.id, declared[it.id].orEmpty()) }
        .toSortedMap(compareBy { it.ordinal })
    val available = availableKnowledge
        .filterNot { candidate -> knowledge.any { it.id == candidate.id && it.version == candidate.version } }
        .sortedWith(xyz.fieldatlas.ui.presentation.knowledgeOrder({ knowledgeCategory(it.id, it.category) }, { it.recommended }, { it.title }))
    // The app always keeps exactly one answer model and one speech model in service; with no
    // explicit choice the first installed pack of the type serves (mirrors the runtime fallback).
    val activeModel = models.firstOrNull { it.active } ?: models.firstOrNull()
    val activeSpeech = speech.firstOrNull { it.active } ?: speech.firstOrNull()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    xyz.fieldatlas.ui.theme.FieldAtlasPageHeader(
                        title = "Your library",
                        subtitle = "Choose what you carry offline.",
                        modifier = Modifier.weight(1f),
                        titleStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp,
                            lineHeight = 36.sp, fontWeight = FontWeight(650), letterSpacing = (-0.8).sp),
                    )
                    xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton(
                        onClick = onImportPack,
                        description = "Import a pack",
                        icon = FieldAtlasIcons.Plus,
                        enabled = !importing && !modelDownloading && knowledgeDownloadKey == null,
                    )
                }
            }
            if (importing) item { FieldAtlasStatusPill("Verifying…") }
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
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("On this phone", "Discover").forEachIndexed { index, label ->
                            val selected = discovering == (index == 1)
                            Surface(onClick = { discovering = index == 1 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { role = Role.Tab; this.selected = selected },
                                shape = FieldAtlasButtonShape,
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent) {
                                Box(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            if (!discovering && (modelDownloading || knowledgeDownloadKey != null)) item {
                EmptyLibraryNote("Your download continues in the background. View its progress in Discover.")
            }
            if (!discovering) {
            item { SectionLabel("Answer model") }
            if (modelNotice != null) item { EmptyLibraryNote(modelNotice) }
            if (models.isEmpty()) item { EmptyLibraryNote("No local model installed") }
            if (models.count(modelFits) > 1) {
                item {
                    Text(
                        if (modelBusy) "Choose one model to use for answers once the current one finishes loading or answering."
                        else "Choose one model to use for answers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(models.size, key = { "model:${models[it].id}:${models[it].version}" }) { index ->
                val asset = models[index]
                val fits = modelFits(asset)
                AssetCard(
                    model = asset.toAssetCardModel(),
                    visual = packVisual(asset),
                    selectedModel = asset == activeModel && fits,
                    onActivateModel = if (asset == activeModel || !fits) null else ({ onActivateModel(asset) }),
                    activateEnabled = !modelBusy,
                    note = if (fits) null else "needs more memory than this phone has",
                    onDelete = { onDeletePack(asset) },
                    asset = asset,
                    onModelOptions = { discovering = true },
                    onlyModel = models.size == 1,
                )
            }
            }
            if (discovering && modelDownloads.isNotEmpty()) {
            item { SectionLabel("Answer models") }
            items(modelDownloads.size, key = { "model-offer:${modelDownloads[it].id}:${modelDownloads[it].version}" }) { index ->
                val offer = modelDownloads[index]
                ModelDownloadCard(
                    offer = offer,
                    downloading = downloadingModelId == offer.id,
                    busy = importing || modelDownloading || knowledgeDownloadKey != null,
                    downloadedBytes = modelDownloadedBytes,
                    resumableBytes = resumableModelBytes(offer),
                    error = modelDownloadError?.takeIf { modelDownloadErrorId == offer.id },
                    onDownload = { onDownloadModel(offer) },
                    onCancel = onCancelModelDownload,
                    onDiscard = onDiscardPausedDownload?.let { discard -> { discard(offer) } },
                    downloadUrl = modelDownloadUrl(offer),
                )
            }
            }
            if (!discovering) {
            item { SectionLabel("Knowledge collections") }
            if (knowledge.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "No collection installed. Add one for local sources and citations; the model still works without it.",
                    )
                }
            }
            xyz.fieldatlas.ui.presentation.knowledgeSelectionSummary(knowledge)?.let { summary ->
                item {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            knowledgeGroups.forEach { (category, group) ->
                item(key = "category:${category.name}") { CategoryLabel(category) }
                // Each installed bundle is one collection. Do not pretend separate legacy
                // archives are one installed bundle or hide their search/delete controls.
                items(group.size, key = { "knowledge:${group[it].id}:${group[it].version}" }) { index ->
                    val asset = group[index]
                    AssetCard(
                        model = asset.toAssetCardModel(),
                        visual = packVisual(asset),
                        enabled = asset.enabled,
                        onToggleResearch = { enabled -> onToggleResearch(asset, enabled) },
                        onDelete = { onDeletePack(asset) },
                        asset = asset,
                        onAskExample = onAskExample,
                    )
                }
            }
            item { TextButton(shape = FieldAtlasButtonShape, onClick = { discovering = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Browse collections") } }
            }
            if (discovering) {
            item {
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenDownloadSources, shape = FieldAtlasButtonShape,
                        modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                        Icon(FieldAtlasIcons.Storage, null, Modifier.size(18.dp))
                        Text("Download sources", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onRefreshCatalog, enabled = !catalogRefreshing, shape = FieldAtlasButtonShape,
                        modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(start = 12.dp)) {
                        Text(if (catalogRefreshing) "Refreshing…" else "Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }
                catalogError?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error) }
            }
            if (available.isNotEmpty()) {
                item { SectionLabel("Available to download") }
                items(available.size, key = { "available:${available[it].id}:${available[it].version}" }) { index ->
                    val candidate = available[index]
                    val category = knowledgeCategory(candidate.id, candidate.category)
                    Column {
                        if (index == 0 || knowledgeCategory(available[index - 1].id, available[index - 1].category) != category) {
                            CategoryLabel(category, Modifier.padding(bottom = 8.dp))
                        }
                        KnowledgeDownloadCard(
                            pack = candidate,
                            downloading = knowledgeDownloadKey == "${candidate.id}:${candidate.version}",
                            busy = importing || modelDownloading || knowledgeDownloadKey != null,
                            downloadedBytes = knowledgeDownloadedBytes,
                            resumableBytes = resumableKnowledgeBytes(candidate),
                            onDownload = { onDownloadKnowledge(candidate) },
                            onCancel = onCancelKnowledgeDownload,
                            onDiscard = onDiscardPausedDownload?.let { discard -> { discard(candidate) } },
                            downloadUrl = knowledgeDownloadUrl(candidate),
                        )
                    }
                }
            }
            if (available.isEmpty() && !catalogRefreshing && catalogError == null) item { EmptyLibraryNote("All collections are on this phone.") }
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
            if (!discovering) {
            item { SectionLabel("Voice") }
            if (speech.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "Voice input uses the compact model built into the app. Nothing else to download.",
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
    /** Deletes a paused download's saved part; null hides the choice. */
    onDiscard: (() -> Unit)? = null,
    downloadUrl: String = pack.url,
) {
    var confirmDownload by rememberSaveable(pack.id, pack.version) { mutableStateOf(false) }
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val visual = subjectVisual(pack.id) ?: categoryVisual(knowledgeCategory(pack.id, pack.category))
            FieldAtlasIconTile(visual.icon, size = 44.dp, tone = visual.tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pack.title, style = PackTitleStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatAssetBytes(pack.bytes)} · version ${xyz.fieldatlas.ui.presentation.displayVersion(pack.version)}",
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
                FieldAtlasProgressBar(
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
                leadingIcon = FieldAtlasIcons.Download,
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
                    "${sizeGb(pack.downloadFreeBytes(resumableBytes))} GB free while installing. " +
                    "Afterward, the collection works offline.\n\n" +
                    xyz.fieldatlas.ui.presentation.downloadSourceDisclosure(downloadUrl))
            },
            confirmButton = {
                TextButton(shape = FieldAtlasButtonShape, onClick = {
                    confirmDownload = false
                    onDownload()
                }) { Text(if (resumableBytes > 0) "Resume" else "Download") }
            },
            dismissButton = {
                Row {
                    // A paused download holds its saved part until it finishes or is discarded here.
                    if (resumableBytes > 0 && onDiscard != null) TextButton(shape = FieldAtlasButtonShape, enabled = !busy,
                        onClick = { confirmDownload = false; onDiscard() }) { Text("Discard saved part") }
                    TextButton(shape = FieldAtlasButtonShape, onClick = { confirmDownload = false }) { Text("Not now") }
                }
            },
        )
    }
}

/** A model this phone can run but has not installed: download, progress, pause and resume. */
@Composable
internal fun ModelDownloadCard(
    offer: ModelOffer,
    downloading: Boolean,
    busy: Boolean,
    downloadedBytes: Long,
    resumableBytes: Long = 0,
    error: String? = null,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    /** Deletes a paused download's saved part; null hides the choice. */
    onDiscard: (() -> Unit)? = null,
    downloadUrl: String = offer.url,
) {
    var confirmDownload by rememberSaveable(offer.id, offer.version) { mutableStateOf(false) }
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(FieldAtlasIcons.Archive, size = 44.dp, tone = TileTone.Sage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(offer.name, style = PackTitleStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatAssetBytes(offer.bytes)} · ${xyz.fieldatlas.ui.presentation.displayLicence(offer.license)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            offer.description + if (offer.minimumMemoryLabel.isEmpty()) "" else
                " For phones with ${offer.minimumMemoryLabel} of memory or more.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!downloading && resumableBytes > 0) {
            Text("${formatAssetBytes(resumableBytes)} saved · ready to resume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
        if (downloading) {
            val installing = downloadedBytes >= offer.bytes
            if (installing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                FieldAtlasProgressBar(
                    progress = { (downloadedBytes.toFloat() / offer.bytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (installing) "Verifying and installing in background" else
                    "${downloadedBytes / 1_000_000} / ${offer.bytes / 1_000_000} MB · continues in background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!installing) TextButton(shape = FieldAtlasButtonShape, onClick = onCancel) { Text("Pause") }
            }
        } else {
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FieldAtlasPrimaryButton(
                text = if (resumableBytes > 0) "Resume download" else "Download model",
                onClick = { confirmDownload = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().semantics {
                    // The spoken name starts with the visible label, so "tap Download model" finds it.
                    contentDescription = "${if (resumableBytes > 0) "Resume download" else "Download model"}, ${offer.name}"
                },
                leadingIcon = FieldAtlasIcons.Download,
            )
        }
    }
    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("${if (resumableBytes > 0) "Resume" else "Download"} ${offer.name}?") },
            text = {
                Text((if (resumableBytes > 0)
                    "${formatAssetBytes(resumableBytes)} is saved. About ${formatAssetBytes(offer.bytes - resumableBytes.coerceIn(0, offer.bytes))} remains if the server supports resume; otherwise the full download may be needed. "
                    else "Uses ${formatAssetBytes(offer.bytes)} of internet data. ") +
                    // The verified file is moved into place, not copied: what is left to fetch plus
                    // Android's margin.
                    "Installation needs about ${sizeGb(offer.bytes - resumableBytes.coerceIn(0, offer.bytes) + 512_000_000L)} GB free. " +
                    "Afterward it works offline, and you can switch between models here.\n\n" +
                    xyz.fieldatlas.ui.presentation.downloadSourceDisclosure(downloadUrl))
            },
            confirmButton = {
                TextButton(shape = FieldAtlasButtonShape, enabled = !busy, onClick = {
                    confirmDownload = false
                    onDownload()
                }) { Text(if (resumableBytes > 0) "Resume" else "Download") }
            },
            dismissButton = {
                Row {
                    // A paused download holds its saved part until it finishes or is discarded here.
                    if (resumableBytes > 0 && onDiscard != null) TextButton(shape = FieldAtlasButtonShape, enabled = !busy,
                        onClick = { confirmDownload = false; onDiscard() }) { Text("Discard saved part") }
                    TextButton(shape = FieldAtlasButtonShape, onClick = { confirmDownload = false }) { Text("Not now") }
                }
            },
        )
    }
}

private fun sizeGb(bytes: Long): String = String.format("%.1f", bytes / 1_000_000_000.0)

@Composable
private fun SectionLabel(text: String) {
    FieldAtlasSectionLabel(text, Modifier.padding(top = 6.dp))
}

/** How a pack is drawn: its subject decides the icon; every knowledge tile shares one background. */
internal data class PackVisual(val icon: ImageVector, val tone: TileTone)

/** The same icon a collection gets once installed, chosen from its catalog category. */
internal fun categoryVisual(category: KnowledgeCategory): PackVisual = when (category) {
    KnowledgeCategory.Places -> PackVisual(FieldAtlasIcons.Place, TileTone.Sage)
    KnowledgeCategory.Travel -> PackVisual(FieldAtlasIcons.Collection, TileTone.Sage)
    KnowledgeCategory.Science -> PackVisual(FieldAtlasIcons.Knowledge, TileTone.Sage)
    else -> PackVisual(FieldAtlasIcons.Document, TileTone.Sage)
}

/** A collection's own subject, so each one is recognisable at a glance in Library and Discover. */
internal fun subjectVisual(packId: String): PackVisual? {
    val id = packId.lowercase()
    return when {
        "vegan" in id -> PackVisual(FieldAtlasIcons.Leaf, TileTone.Sage)
        "essentials" in id -> PackVisual(FieldAtlasIcons.Storefront, TileTone.Sage)
        "guides" in id -> PackVisual(FieldAtlasIcons.Compass, TileTone.Sage)
        listOf("voyage", "place", "osm", "travel").any { it in id } -> PackVisual(FieldAtlasIcons.Place, TileTone.Sage)
        listOf("encyclopedia", "wikipedia").any { it in id } -> PackVisual(FieldAtlasIcons.Encyclopedia, TileTone.Sage)
        listOf("crypto", "ethereum").any { it in id } -> PackVisual(FieldAtlasIcons.Key, TileTone.Sage)
        listOf("biology", "longevity", "health").any { it in id } -> PackVisual(FieldAtlasIcons.Microscope, TileTone.Sage)
        "science" in id -> PackVisual(FieldAtlasIcons.Knowledge, TileTone.Sage)
        else -> null
    }
}

internal fun packVisual(asset: InstalledAsset): PackVisual = when {
    asset.type == PackType.MODEL -> PackVisual(FieldAtlasIcons.Model, TileTone.Sage)
    asset.type == PackType.AUDIO -> PackVisual(FieldAtlasIcons.Mic, TileTone.Paper)
    else -> subjectVisual(asset.id) ?: PackVisual(FieldAtlasIcons.Document, TileTone.Sage)
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun StorageSummary(models: List<InstalledAsset>, knowledge: List<InstalledAsset>, speech: List<InstalledAsset>) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val parts = listOf(
        Triple("Models", models.sumOf { it.installedBytes }, MaterialTheme.colorScheme.primary),
        Triple("Knowledge", knowledge.sumOf { it.installedBytes }, Color(0xFF8DB59E)),
        Triple("Voice", speech.sumOf { it.installedBytes }, Color(0xFFC9A85C)),
    ).filter { it.second > 0 }
    val total = parts.sumOf { it.second }
    if (total <= 0) return
    val count = models.size + knowledge.size + speech.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (count == 1) "1 pack saved" else "$count packs saved", Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(650)))
        TextButton(onClick = { showDetails = true }, shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(FieldAtlasIcons.Storage, null, Modifier.size(18.dp))
            Text("Storage", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showDetails) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { showDetails = false },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Storage", Modifier.weight(1f), style = xyz.fieldatlas.ui.theme.FieldAtlasSheetTitleStyle)
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton({ showDetails = false }, "Close Storage")
            }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatAssetBytes(total)} on this phone", style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
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
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { (label, bytes, color) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text("$label ${formatAssetBytes(bytes)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Downloaded packs only. App files and saved answers are not included.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    activateEnabled: Boolean = true,
    /** Shown after the size and version, for a reason the pack cannot be used. */
    note: String? = null,
    onDelete: (() -> Unit)? = null,
    inGroup: Boolean = false,
    asset: InstalledAsset? = null,
    onAskExample: ((String) -> Unit)? = null,
    onModelOptions: (() -> Unit)? = null,
    /** The phone's only answer model: deleting it returns the app to setup. */
    onlyModel: Boolean = false,
) {
    var confirmDelete by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    var showAbout by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showModelActions by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    val isAnswerModel = asset?.type == PackType.MODEL
    // A switched-off collection stays readable but visibly inactive.
    val inactive = onToggleResearch != null && !enabled
    AssetSurface(inGroup, onClick = if (isAnswerModel) ({ showModelActions = true }) else null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (inGroup) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inGroup) Box(Modifier.size(width = 32.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                Icon(visual.icon, null, Modifier.size(20.dp).alpha(if (inactive) 0.5f else 1f),
                    tint = MaterialTheme.colorScheme.primary)
            }
            else FieldAtlasIconTile(visual.icon, size = 36.dp, tone = visual.tone,
                modifier = Modifier.alpha(if (inactive) 0.5f else 1f))
            Column(Modifier.weight(1f).alpha(if (inactive) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(model.title, style = PackTitleStyle().copy(fontSize = if (inGroup) 14.sp else 16.sp,
                    fontWeight = if (inGroup) FontWeight.SemiBold else FontWeight(650)),
                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    "${model.size} · v${xyz.fieldatlas.ui.presentation.displayVersion(model.version)}" + (if (inactive) " · not used" else "") + (note?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedModel) Text("In use", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            when {
                selectedModel -> Unit
                onActivateModel != null && !isAnswerModel -> TextButton(shape = FieldAtlasButtonShape, onClick = onActivateModel,
                    enabled = activateEnabled) { Text("Use model") }
                onToggleResearch != null -> xyz.fieldatlas.ui.theme.FieldAtlasSwitch(
                    checked = enabled,
                    onCheckedChange = onToggleResearch,
                    modifier = Modifier.semantics { contentDescription = "Use ${model.title} in research" },
                )
            }
            if (onDelete != null) {
                // Deleting is rare, so it lives in the overflow menu rather than beside the toggle.
                Box {
                    IconButton(onClick = { if (isAnswerModel) showModelActions = true else menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(FieldAtlasIcons.MoreOptions, contentDescription = "More options for ${model.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surface) {
                        if (asset != null) DropdownMenuItem(
                            text = { Text(if (asset.type == xyz.fieldatlas.assets.PackType.KNOWLEDGE) "About this collection" else "About this pack") },
                            leadingIcon = { Icon(FieldAtlasIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; showAbout = true },
                        )
                        if (onToggleResearch != null) DropdownMenuItem(
                            text = { Text(if (enabled) "Don't use in research" else "Use in research") },
                            leadingIcon = { Icon(if (enabled) FieldAtlasIcons.Close else FieldAtlasIcons.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onToggleResearch(!enabled) },
                        )
                        if (asset != null || onToggleResearch != null) HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DropdownMenuItem(
                            text = { Text("Delete…", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(FieldAtlasIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
    }
    if (showModelActions && asset != null) ModelActionsSheet(
        title = model.title, detail = "${model.size} · v${model.version}", selected = selectedModel,
        notice = note, useEnabled = activateEnabled, onUse = onActivateModel,
        onDetails = { showModelActions = false; showAbout = true },
        onOptions = onModelOptions?.let { { showModelActions = false; it() } },
        onDelete = { showModelActions = false; confirmDelete = true },
        // Deleting the model while it answers would end the answer with an internal error.
        deleteEnabled = activateEnabled,
        onDismiss = { showModelActions = false },
    )
    if (confirmDelete) {
        FieldAtlasDeleteDialog(
            title = "Delete ${model.title}?",
            message = "Frees ${model.size}. Answers in History keep the passages saved with them. " +
                "You can download or import it again later." +
                if (onlyModel) " It is the only answer model, so Field Atlas returns to setup until you add one." else "",
            onKeep = { confirmDelete = false },
            onDelete = { confirmDelete = false; onDelete?.invoke() },
        )
    }
    if (showAbout && asset != null) {
        PackAboutSheet(asset, model.title, visual, onDismiss = { showAbout = false }, onAskExample = onAskExample)
    }
}

/** Pack names use the interface face; serif is reserved for research reading. */
@Composable
private fun PackTitleStyle() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

@Composable
private fun AssetSurface(inGroup: Boolean, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (inGroup) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) { content() }
    else FieldAtlasCard(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else
        Modifier.clip(xyz.fieldatlas.ui.theme.FieldAtlasLayout.CardShape)
            .clickable(role = Role.Button, onClickLabel = "Model options", onClick = onClick)), contentPadding = 16.dp) { content() }
}

@Composable
private fun EmptyLibraryNote(text: String) {
    FieldAtlasCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryLabel(category: KnowledgeCategory, modifier: Modifier = Modifier) {
    Text(
        category.label,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
