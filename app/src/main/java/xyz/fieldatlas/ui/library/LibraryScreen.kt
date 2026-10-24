package xyz.fieldatlas.ui.library

import xyz.fieldatlas.ui.theme.FieldAtlasProgressBar
import xyz.fieldatlas.assets.downloadFreeBytes
import xyz.fieldatlas.assets.DownloadItem
import androidx.compose.runtime.collectAsState

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
    /** A model this phone can load but answers slowly with, by id ([xyz.fieldatlas.assets.ModelOffer.slowOn]). */
    modelSlow: (String) -> Boolean = { false },
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
    val installedOrder = xyz.fieldatlas.ui.presentation.knowledgeOrder<InstalledAsset>(
        { knowledgeCategory(it.id, declared[it.id].orEmpty()) }, { it.toAssetCardModel().title })
    // One card per collection: the edition in use (or the catalog's, or the newest); older editions kept after an
    // update are offered back as space under it instead of looking like a second collection.
    val currentEdition = knowledge.groupBy { it.id }.mapValues { (_, editions) ->
        editions.firstOrNull { it.enabled } ?: editions.firstOrNull { edition ->
            availableKnowledge.any { it.id == edition.id && it.version == edition.version } } ?: editions.maxBy { it.version }
    }
    val shownKnowledge = knowledge.filter { currentEdition[it.id] == it }
    val oldEditions = knowledge.filterNot { currentEdition[it.id] == it }
    val knowledgeGroups = shownKnowledge.sortedWith(installedOrder)
        .groupBy { knowledgeCategory(it.id, declared[it.id].orEmpty()) }
        .toSortedMap(compareBy { it.ordinal })
    val offers = xyz.fieldatlas.assets.discoverOffers(availableKnowledge, knowledge)
    val order = xyz.fieldatlas.ui.presentation.knowledgeOrder<KnowledgeCatalogEntry>({ knowledgeCategory(it.id, it.category) }, { it.title })
    val fresh = offers.fresh.sortedWith(order)
    val updates = offers.updates.sortedWith(order)
    val available = fresh + updates
    // The app always keeps exactly one answer model and one speech model in service; with no
    // explicit choice the first installed pack of the type serves (mirrors the runtime fallback).
    val activeModel = models.firstOrNull { it.active } ?: models.firstOrNull()
    val activeSpeech = speech.firstOrNull { it.active } ?: speech.firstOrNull()
    val readAloud = xyz.fieldatlas.ui.research.LocalReadAloud.current
    val downloads = LocalDownloads.current
    val queue = downloads?.state?.collectAsState()?.value
    val naturalVoice = readAloud?.natural?.state?.collectAsState()?.value
    var confirming by remember { mutableStateOf<DownloadItem?>(null) }
    var voiceDetails by rememberSaveable { mutableStateOf(false) }
    var catalogDetails by remember { mutableStateOf<KnowledgeCatalogEntry?>(null) }
    var voiceConfirm by rememberSaveable { mutableStateOf(false) }
    // Search and kind chips over Discover, for a catalogue longer than a screen.
    var discoverQuery by rememberSaveable { mutableStateOf("") }
    var discoverType by rememberSaveable { mutableStateOf(DiscoverType.All) }
    var discoverOpen by rememberSaveable { mutableStateOf(setOf<String>().joinToString()) }
    val voiceOffered = readAloud != null && downloads != null && naturalVoice != xyz.fieldatlas.speech.NaturalVoice.State.Ready
    fun knowledgeMatches(entry: KnowledgeCatalogEntry) = DiscoverSearch.matches(discoverQuery, entry.title, entry.description,
        xyz.fieldatlas.ui.presentation.discoverLine(entry.id, entry.description), knowledgeCategory(entry.id, entry.category).label)
    val modelsFound = modelDownloads.filter { DiscoverSearch.matches(discoverQuery, it.name, it.line, "model") }
    val updatesFound = updates.filter(::knowledgeMatches)
    val freshFound = fresh.filter(::knowledgeMatches)
    val voiceFound = voiceOffered && DiscoverSearch.matches(discoverQuery, xyz.fieldatlas.speech.NaturalVoice.NAME, "Natural reading voice: an English voice that reads answers aloud")
    val discoverCounts = mapOf(DiscoverType.Models to modelsFound.size, DiscoverType.Knowledge to updatesFound.size + freshFound.size,
        DiscoverType.Voices to if (voiceFound) 1 else 0).let { it + (DiscoverType.All to it.values.sum()) }
    val discoverOffered = modelDownloads.size + updates.size + fresh.size + if (voiceOffered) 1 else 0
    fun shows(kind: DiscoverType) = discoverType == DiscoverType.All || discoverType == kind
    // With nothing typed and every kind listed, each kind shows its first rows and "See all"; a chip or a query shows all.
    fun folded(kind: DiscoverType) = discoverQuery.isBlank() && discoverType == DiscoverType.All && kind.name !in discoverOpen.split(", ")
    fun toggleOpen(kind: DiscoverType) {
        val open = discoverOpen.split(", ").filter { it.isNotEmpty() }.toSet()
        discoverOpen = (if (kind.name in open) open - kind.name else open + kind.name).joinToString()
    }
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
                        Text("This file wasn’t added.", color = MaterialTheme.colorScheme.error,
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
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                    // Updates waiting in Discover, counted where the person will look for them.
                                    if (index == 1 && updates.isNotEmpty()) Box(Modifier.padding(start = 6.dp).size(20.dp)
                                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                        .semantics { contentDescription = "${updates.size} update" + if (updates.size == 1) "" else "s" },
                                        contentAlignment = Alignment.Center) {
                                        Text("${updates.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
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
                    note = if (!fits) "needs more memory than this phone has" else if (modelSlow(asset.id)) "slow on this phone" else null,
                    onDelete = { onDeletePack(asset) },
                    asset = asset,
                    onModelOptions = { discovering = true },
                    onlyModel = models.size == 1,
                )
            }
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
            if (discoverOffered > 0) item(key = "discover-search") {
                DiscoverSearchBar(discoverQuery, { discoverQuery = it }, discoverType, { discoverType = it }, discoverCounts)
            }
            // Only beside the search bar, so a query left from before the last download can never strand this card.
            if (discoverOffered > 0 && discoverCounts[discoverType] == 0) item(key = "discover-nothing") {
                if (discoverQuery.isNotBlank()) DiscoverNothingFound(discoverQuery, catalogRefreshing, onRefreshCatalog, onImportPack)
                else EmptyLibraryNote("No ${discoverType.label.lowercase()} to download right now.")
            }
            }
            if (discovering && shows(DiscoverType.Models) && modelsFound.isNotEmpty()) {
            item { SectionLabel("Answer models") }
            if (downloads != null && queue != null) item(key = "model-offers") {
                val fold = folded(DiscoverType.Models) && DiscoverSearch.folds(modelsFound.size)
                Column {
                DownloadGroup((if (fold) modelsFound.take(DiscoverSearch.ROWS_BEFORE_SEE_ALL) else modelsFound).map { offer -> @Composable {
                    val item = DownloadItem.Model(offer)
                    // Two short lines like a collection in Discover; the download sheet says the rest.
                    DownloadRow(FieldAtlasIcons.Model, TileTone.Sage, offer.name,
                        "${formatAssetBytes(offer.bytes)} · " + (if (modelSlow(offer.id)) "Slow on this phone"
                            else offer.line.ifEmpty { xyz.fieldatlas.ui.presentation.displayLicence(offer.license) }),
                        rowState(item, queue, installed = false, saved = resumableModelBytes(offer)), formatAssetBytes(offer.bytes),
                        rowActions(downloads, item, onGet = { confirming = item },
                            discardSaved = onDiscardPausedDownload?.let { discard -> { discard(offer) } }))
                } })
                if (DiscoverSearch.folds(modelsFound.size) && discoverQuery.isBlank() && discoverType == DiscoverType.All)
                    DiscoverSeeAll(modelsFound.size, open = !fold) { toggleOpen(DiscoverType.Models) }
                }
            }
            }
            // One order on every screen: the models, then the voice, then the collections, as setup and Discover list them.
            if (!discovering) {
            item { SectionLabel("Voice") }
            if (readAloud != null && naturalVoice == xyz.fieldatlas.speech.NaturalVoice.State.Ready) {
                item(key = "reading-voice") { VoiceInstalledRow(readAloud) }
            }
            if (speech.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "Voice input uses the compact model built into the app.",
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetCard(
                            model = asset.toAssetCardModel(),
                            visual = packVisual(asset),
                            enabled = asset.enabled,
                            onToggleResearch = { enabled -> onToggleResearch(asset, enabled) },
                            onDelete = { onDeletePack(asset) },
                            asset = asset,
                            onAskExample = onAskExample,
                            catalogDescription = availableKnowledge.firstOrNull { it.id == asset.id }?.description,
                            category = knowledgeCategory(asset.id, declared[asset.id].orEmpty()).label,
                        )
                        oldEditions.filter { it.id == asset.id }.forEach { old ->
                            OldEditionNote(old, onRemove = { onDeletePack(old) },
                                onSwitchBack = { onToggleResearch(old, true); onToggleResearch(asset, false) })
                        }
                    }
                }
            }
            item { TextButton(shape = FieldAtlasButtonShape, onClick = { discovering = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Browse collections") } }
            }
            if (discovering) {
            if (shows(DiscoverType.Knowledge) && updatesFound.isNotEmpty() && downloads != null && queue != null) {
                item { SectionLabel("Updates") }
                item(key = "updates") {
                    DownloadGroup(updatesFound.map { candidate -> @Composable {
                        KnowledgeRow(candidate, knowledge.filter { it.id == candidate.id }.maxByOrNull { it.installedBytes }, downloads, queue,
                            resumableKnowledgeBytes(candidate), onGet = { confirming = it },
                            onDiscard = onDiscardPausedDownload?.let { discard -> { discard(candidate) } })
                    } })
                }
            }
            if (readAloud != null && downloads != null && shows(DiscoverType.Voices) && voiceFound) {
                item { SectionLabel("Voice") }
                item(key = "voice-offer") { DownloadGroup(listOf { VoiceDiscoverRow(readAloud, onGet = { voiceConfirm = true }, onDetails = { voiceDetails = true }) }) }
            }
            if (shows(DiscoverType.Knowledge) && freshFound.isNotEmpty() && downloads != null && queue != null) {
                item { SectionLabel("Knowledge collections") }
                val fold = folded(DiscoverType.Knowledge) && DiscoverSearch.folds(freshFound.size)
                // One card per category, its collections listed inside it.
                (if (fold) freshFound.take(DiscoverSearch.ROWS_BEFORE_SEE_ALL) else freshFound)
                    .groupBy { knowledgeCategory(it.id, it.category) }.forEach { (category, group) ->
                    item(key = "available:${category.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryLabel(category)
                            DownloadGroup(group.map { candidate -> @Composable {
                                KnowledgeRow(candidate, null, downloads, queue, resumableKnowledgeBytes(candidate), onGet = { confirming = it },
                                    onDiscard = onDiscardPausedDownload?.let { discard -> { discard(candidate) } },
                                    onDetails = { catalogDetails = candidate })
                            } })
                        }
                    }
                }
                if (DiscoverSearch.folds(freshFound.size) && discoverQuery.isBlank() && discoverType == DiscoverType.All)
                    item(key = "available-see-all") { DiscoverSeeAll(freshFound.size, open = !fold) { toggleOpen(DiscoverType.Knowledge) } }
            }
            if (available.isEmpty() && ((discoverQuery.isBlank() && discoverType == DiscoverType.All) || discoverOffered == 0) &&
                !catalogRefreshing && catalogError == null) item { EmptyLibraryNote("All collections are on this phone.") }
            }
        }
    }
    confirming?.let { item ->
        when (item) {
            is DownloadItem.Knowledge -> {
                val pack = item.pack
                val saved = resumableKnowledgeBytes(pack)
                val replaces = knowledge.filter { it.id == pack.id && it.version != pack.version }.maxByOrNull { it.installedBytes }
                DownloadConfirmSheet(item, if (replaces != null) "Update" else "Download",
                    if (replaces != null) "Newer edition · ${formatAssetBytes(pack.bytes)}" + (updateNote(pack, replaces)?.let { " · $it" } ?: "")
                    else pack.description,
                    saved = saved, installMargin = (pack.downloadFreeBytes(saved) - (pack.bytes - saved)).coerceAtLeast(0),
                    sourceUrl = knowledgeDownloadUrl(pack), replaces = replaces,
                    onConfirm = { downloads?.queue?.enqueue(item) }, onDismiss = { confirming = null })
            }
            is DownloadItem.Model -> DownloadConfirmSheet(item, "Download", item.offer.description,
                saved = resumableModelBytes(item.offer), installMargin = 512_000_000L, sourceUrl = modelDownloadUrl(item.offer),
                onConfirm = { downloads?.queue?.enqueue(item) }, onDismiss = { confirming = null })
            DownloadItem.Voice -> { confirming = null; voiceConfirm = true }
        }
    }
    catalogDetails?.let { pack ->
        CatalogPackSheet(pack, subjectVisual(pack.id) ?: categoryVisual(knowledgeCategory(pack.id, pack.category)),
            sourceUrl = knowledgeDownloadUrl(pack), onDownload = { confirming = DownloadItem.Knowledge(pack) },
            onDismiss = { catalogDetails = null })
    }
    if (voiceDetails && readAloud != null) VoiceDetailSheet(readAloud, onDownload = { voiceDetails = false; voiceConfirm = true },
        onDismiss = { voiceDetails = false })
    if (voiceConfirm) VoiceDownloadConfirm(onDone = { voiceConfirm = false })
}

/** What an update brings, in a few words: "Smaller · faster search". Null when nothing simple can be said. */
internal fun updateNote(pack: KnowledgeCatalogEntry, installed: InstalledAsset): String? = listOfNotNull(
    "Smaller".takeIf { pack.bytes < installed.installedBytes * 9 / 10 },
    "faster search".takeIf { pack.version.endsWith("-leviathan") && !installed.version.endsWith("-leviathan") },
).joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { null }

/** A collection in Discover: new, or a newer edition of one on the phone ([replaces]). */
@Composable
private fun KnowledgeRow(
    pack: KnowledgeCatalogEntry,
    replaces: InstalledAsset?,
    downloads: xyz.fieldatlas.assets.AppDownloads,
    queue: xyz.fieldatlas.assets.DownloadQueueState,
    saved: Long,
    onGet: (DownloadItem) -> Unit,
    onDiscard: (() -> Unit)?,
    onDetails: (() -> Unit)? = null,
) {
    val visual = subjectVisual(pack.id) ?: categoryVisual(knowledgeCategory(pack.id, pack.category))
    val item = DownloadItem.Knowledge(pack)
    val state = rowState(item, queue, installed = false, saved = saved)
    val chips = if (replaces != null) listOfNotNull(updateNote(pack, replaces)?.let { RowChip(it, positive = true) },
        RowChip("You have ${formatAssetBytes(replaces.installedBytes)}"))
    // Every listed collection is recommended, so the chip said nothing; the version and full text are in its sheet.
    else emptyList()
    DownloadRow(visual.icon, visual.tone, pack.title,
        if (replaces != null) "${formatAssetBytes(pack.bytes)} · newer edition"
        else "${formatAssetBytes(pack.bytes)} · ${xyz.fieldatlas.ui.presentation.discoverLine(pack.id, pack.description)}",
        state, if (replaces != null) "Update" else formatAssetBytes(pack.bytes),
        rowActions(downloads, item, onGet = { onGet(item) }, discardSaved = onDiscard), chips = chips,
        onClick = onDetails.takeIf { replaces == null })
}

/** Under a collection after an update: the old edition, kept switched off, whose space can be given back. */
@Composable
private fun OldEditionNote(old: InstalledAsset, onRemove: () -> Unit, onSwitchBack: () -> Unit) {
    Surface(shape = FieldAtlasButtonShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(FieldAtlasIcons.Trash, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (old.enabled) "Older edition, also searched" else "Old edition kept, switched off",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text("${formatAssetBytes(old.installedBytes)} you can free", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSwitchBack, shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use it") }
            TextButton(onClick = onRemove, shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    FieldAtlasSectionLabel(text, Modifier.padding(top = 6.dp))
}

/** How a pack is drawn: its subject decides the icon; every knowledge tile shares one background. */
internal data class PackVisual(val icon: ImageVector, val tone: TileTone)

/** The same icon a collection gets once installed, chosen from its catalog category. */
internal fun categoryVisual(category: KnowledgeCategory): PackVisual = when (category) {
    KnowledgeCategory.Places, KnowledgeCategory.Travel -> PackVisual(FieldAtlasIcons.CollectionPin, TileTone.Sage)
    KnowledgeCategory.Science -> PackVisual(FieldAtlasIcons.Knowledge, TileTone.Sage)
    else -> PackVisual(FieldAtlasIcons.Document, TileTone.Sage)
}

/**
 * A collection's own subject, so each one is recognisable at a glance in Library and Discover. The icons are the
 * website's (its Knowledge collections section), so a collection looks the same in both places.
 */
internal fun subjectVisual(packId: String): PackVisual? {
    val id = packId.lowercase()
    return when {
        "vegan" in id -> PackVisual(FieldAtlasIcons.CollectionBowl, TileTone.Sage)
        "essentials" in id -> PackVisual(FieldAtlasIcons.CollectionCross, TileTone.Sage)
        // The website shows travel guides and travel places as one card with a pin, so both rows carry it here.
        listOf("guides", "voyage", "place", "osm", "travel").any { it in id } -> PackVisual(FieldAtlasIcons.CollectionPin, TileTone.Sage)
        listOf("encyclopedia", "wikipedia").any { it in id } -> PackVisual(FieldAtlasIcons.CollectionBook, TileTone.Sage)
        listOf("crypto", "ethereum").any { it in id } -> PackVisual(FieldAtlasIcons.Key, TileTone.Sage)
        listOf("biology", "longevity", "health").any { it in id } -> PackVisual(FieldAtlasIcons.CollectionLeaf, TileTone.Sage)
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
    /** For a collection: the catalog's listing, shown in About when the pack has no summary of its own. */
    catalogDescription: String? = null,
    category: String? = null,
) {
    var confirmDelete by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    var showAbout by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showModelActions by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    val isAnswerModel = asset?.type == PackType.MODEL
    // The row shows a model's short name; tapping it shows the full one (quantisation included) in its sheets.
    val sheetTitle = if (isAnswerModel) asset?.title ?: model.title else model.title
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
                            text = { Text("About") },
                            leadingIcon = { Icon(FieldAtlasIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; showAbout = true },
                        )
                        // Using a collection in research is the switch on the card, so the menu does not repeat it.
                        if (asset != null) HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(FieldAtlasIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
    }
    if (showModelActions && asset != null) ModelActionsSheet(
        title = sheetTitle, detail = "${model.size} · v${model.version}", selected = selectedModel,
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
        PackAboutSheet(asset, sheetTitle, visual, onDismiss = { showAbout = false }, onAskExample = onAskExample,
            catalogDescription = catalogDescription, category = category)
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
