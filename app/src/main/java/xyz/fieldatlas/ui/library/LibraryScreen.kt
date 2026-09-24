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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.AssetCardModel
import xyz.fieldatlas.ui.presentation.toAssetCardModel
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction

@Composable
fun LibraryScreen(
    packs: List<InstalledAsset>,
    onImportPack: () -> Unit,
    onToggleResearch: (InstalledAsset, Boolean) -> Unit = { _, _ -> },
    onActivateModel: (InstalledAsset) -> Unit = {},
    onDeletePack: (InstalledAsset) -> Unit = {},
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
                    subtitle = "Installed knowledge sets the boundary for evidence-backed answers.",
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            item { SectionLabel("Answer models") }
            if (models.isEmpty()) item { EmptyLibraryNote("No local model installed") }
            if (activeModel != null) {
                item {
                    Text(
                        "In use: ${activeModel.title} · version ${activeModel.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            item { SectionLabel("Knowledge data") }
            if (knowledge.isEmpty()) item { EmptyLibraryNote("No knowledge pack installed") }
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
            if (speech.isNotEmpty() && activeSpeech != null) {
                item {
                    Text(
                        "In use for dictation: ${activeSpeech.title} · version ${activeSpeech.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            item {
                OutlinedButton(onClick = onImportPack, modifier = Modifier.fillMaxWidth()) {
                    Text("Import another pack")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val iconBackground = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else FieldAtlasColors.SageWash
    val icon = when {
        model.kind.equals("answer model", ignoreCase = true) -> Icons.Outlined.Inventory2
        model.kind.equals("audio model", ignoreCase = true) -> Icons.Outlined.Mic
        else -> Icons.Outlined.Description
    }
    // Family tint for the metadata lines: the Library sections already name the family, so
    // the kind label was dropped from the text and the colour alone carries it — green for
    // answer models, blue for knowledge data, purple for audio models.
    val familyTint = when {
        model.kind.equals("answer model", ignoreCase = true) -> androidx.compose.ui.graphics.Color(0xFF4C9A6A)
        model.kind.equals("audio model", ignoreCase = true) -> androidx.compose.ui.graphics.Color(0xFF8E6BB8)
        else -> androidx.compose.ui.graphics.Color(0xFF4A87B0)
    }
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(44.dp), color = iconBackground, shape = MaterialTheme.shapes.small) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    "${model.size} · version ${model.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = familyTint,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                // License plus the manifest hash on one unwrapped line (the tail is
                // ellipsised, never wrapped). Double-tap copies the FULL 64-character hash —
                // it replaced the old expandable "Verification details" section to keep each
                // row shorter; the family section already says what kind of pack this is.
                var justCopied by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
                Text(
                    if (justCopied) "SHA-256 copied: ${model.manifestSha256}"
                    else "${model.license} · SHA-256: ${model.manifestSha256}",
                    style = MaterialTheme.typography.labelSmall,
                    color = familyTint,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(model.manifestSha256) {
                            detectTapGestures(onDoubleTap = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(model.manifestSha256))
                                justCopied = true
                            })
                        },
                )
                if (justCopied) {
                    LaunchedEffect(model.title, model.version, Unit) {
                        kotlinx.coroutines.delay(1_500)
                        justCopied = false
                    }
                }
            }
            // Right column of the one-row card: control on top, Delete beneath, both
            // kept tight so this column never exceeds the icon/text columns — card height
            // stays content-driven.
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    onToggleResearch != null -> Switch(checked = enabled, onCheckedChange = onToggleResearch)
                    selectedModel -> Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Selected model",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    onActivateModel != null -> Button(
                        onClick = onActivateModel,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 2.dp,
                        ),
                    ) {
                        Text("Use")
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 0.dp,
                        ),
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
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
