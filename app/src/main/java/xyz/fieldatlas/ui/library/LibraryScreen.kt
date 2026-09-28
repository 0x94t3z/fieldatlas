package xyz.fieldatlas.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.AssetCardModel
import xyz.fieldatlas.ui.presentation.toAssetCardModel
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile

@Composable
fun LibraryScreen(
    packs: List<InstalledAsset>,
    onImportPack: () -> Unit,
    onToggleResearch: (InstalledAsset, Boolean) -> Unit = { _, _ -> },
    onActivateModel: (InstalledAsset) -> Unit = {},
    onDeletePack: (InstalledAsset) -> Unit = {},
    importing: Boolean = false,
    importError: String? = null,
) {
    val models = packs.filter { it.type == PackType.MODEL }
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    val speech = packs.filter { it.type == PackType.AUDIO }
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
                FieldAtlasHeader(
                    title = "Library",
                    subtitle = "Models and knowledge saved on this phone.",
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            item {
                FieldAtlasPrimaryButton(
                    text = if (importing) "Verifying pack…" else "Import a pack",
                    onClick = onImportPack,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = FieldAtlasIcons.Import,
                )
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
            item { SectionLabel("Model") }
            if (models.isEmpty()) item { EmptyLibraryNote("No local model installed") }
            if (models.size > 1) {
                item {
                    Text(
                        "Choosing a model replaces the one in use; switching while a model is loaded releases it and loads the selection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(models.size, key = { "model:${models[it].id}:${models[it].version}" }) { index ->
                val asset = models[index]
                AssetCard(
                    model = asset.toAssetCardModel(),
                    selectedModel = asset == activeModel,
                    onActivateModel = if (asset == activeModel) null else ({ onActivateModel(asset) }),
                    onDelete = { onDeletePack(asset) },
                )
            }
            item { SectionLabel("Knowledge collections") }
            if (knowledge.isEmpty()) {
                item {
                    EmptyLibraryNote(
                        "No knowledge pack installed. You can still ask the offline model; add a pack for source-backed answers.",
                    )
                }
            }
            if (knowledge.isNotEmpty()) {
                item {
                    Text(
                        "Research draws evidence from every collection you leave on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(knowledge.size, key = { "knowledge:${knowledge[it].id}:${knowledge[it].version}" }) { index ->
                val asset = knowledge[index]
                AssetCard(
                    model = asset.toAssetCardModel(),
                    enabled = asset.enabled,
                    onToggleResearch = { enabled -> onToggleResearch(asset, enabled) },
                    onDelete = { onDeletePack(asset) },
                )
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
                    selectedModel = asset == activeSpeech,
                    onActivateModel = if (asset == activeSpeech) null else ({ onActivateModel(asset) }),
                    onDelete = { onDeletePack(asset) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AssetCard(
    model: AssetCardModel,
    enabled: Boolean = true,
    onToggleResearch: ((Boolean) -> Unit)? = null,
    selectedModel: Boolean = false,
    onActivateModel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    val icon = when {
        model.kind.equals("answer model", ignoreCase = true) -> FieldAtlasIcons.Archive
        model.kind.equals("audio model", ignoreCase = true) -> Icons.Outlined.Mic
        else -> FieldAtlasIcons.Document
    }
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldAtlasIconTile(icon, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FieldAtlasEditorial,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${model.size} · version ${model.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (onDelete != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        selectedModel -> Icon(FieldAtlasIcons.Check, contentDescription = "In use", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        onActivateModel != null -> TextButton(onClick = onActivateModel) { Text("Use model") }
                        onToggleResearch != null -> Switch(
                            checked = enabled,
                            onCheckedChange = onToggleResearch,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
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
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun EmptyLibraryNote(text: String) {
    FieldAtlasCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
