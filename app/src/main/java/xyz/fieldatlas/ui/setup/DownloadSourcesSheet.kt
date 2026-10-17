package xyz.fieldatlas.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URI
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import xyz.fieldatlas.assets.AssetDownloadKind
import xyz.fieldatlas.assets.DownloadArtifact
import xyz.fieldatlas.assets.validatedDownloadUrl
import xyz.fieldatlas.ui.theme.*

/** Optional mirror settings; the first-run recommended flow does not depend on this sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSourcesSheet(
    artifacts: List<DownloadArtifact>, sources: Map<String, String>,
    onSave: (DownloadArtifact, String) -> String?, onReset: (DownloadArtifact) -> String?,
    onDismiss: () -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = artifacts.firstOrNull { it.key == selectedKey }
    var address by rememberSaveable(selectedKey) { mutableStateOf(selected?.let { sources[it.key] ?: it.defaultUrl }.orEmpty()) }
    var error by rememberSaveable(selectedKey) { mutableStateOf<String?>(null) }
    FieldAtlasInfoSheet("Download sources", onDismiss, closeDescription = "Close download sources",
        onBack = if (selected == null) null else ({ selectedKey = null }), backDescription = "All sources",
        contentKey = selectedKey ?: "list") {
            if (selected == null) {
                Text("Use the defaults, or choose a mirror for the same file.", style = MaterialTheme.typography.bodyMedium)
                Column { artifacts.forEach { artifact ->
                    Row(Modifier.fillMaxWidth().clip(FieldAtlasButtonShape).clickable {
                        selectedKey = artifact.key
                    }.heightIn(min = 64.dp).padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(when (artifact.kind) {
                            AssetDownloadKind.MODEL -> FieldAtlasIcons.Model
                            AssetDownloadKind.VOICE -> Icons.AutoMirrored.Outlined.VolumeUp
                            AssetDownloadKind.KNOWLEDGE -> FieldAtlasIcons.Database
                        },
                            null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(artifact.title, style = MaterialTheme.typography.bodyMedium)
                            val custom = sources[artifact.key]
                            val host = runCatching { URI(custom ?: artifact.defaultUrl).host }.getOrNull().orEmpty()
                            Text("${if (custom == null) "Default" else "Custom"} · $host",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                } }
            } else {
                Text(selected.title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = address, onValueChange = { address = it; error = null },
                    modifier = Modifier.fillMaxWidth(), label = { Text(if (selected.kind == AssetDownloadKind.VOICE) "HTTPS folder URL" else "HTTPS file URL") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = FieldAtlasButtonShape, minLines = 2, maxLines = 3, isError = error != null)
                Text((if (selected.kind == AssetDownloadKind.VOICE) "The folder must hold the voice's files, each matching its original size and checksum. "
                    else "The file must match the original size and checksum. ") + "Saving does not start a download or change one in progress.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val valid = runCatching { validatedDownloadUrl(address, selected.extension) }
                if (valid.isSuccess) {
                    Text("Downloads will contact ${URI(valid.getOrThrow()).host}. That server can see your IP address, not your questions.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                FieldAtlasPrimaryButton("Save source", {
                    error = valid.exceptionOrNull()?.message ?: onSave(selected, valid.getOrThrow())
                    if (error == null) selectedKey = null
                }, Modifier.fillMaxWidth())
                TextButton(shape = FieldAtlasButtonShape, onClick = {
                    error = onReset(selected)
                    if (error == null) selectedKey = null
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use default source", style = MaterialTheme.typography.labelMedium) }
            }
    }
}
