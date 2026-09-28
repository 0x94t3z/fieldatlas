package xyz.fieldatlas.ui.setup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.assets.RecommendedModel
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton

@Composable
fun SetupScreen(
    packs: List<InstalledAsset>,
    importing: Boolean,
    downloading: Boolean,
    downloadedBytes: Long,
    error: String?,
    onImportPack: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val hasModel = packs.any { it.type == PackType.MODEL }
    val hasKnowledge = packs.any { it.type == PackType.KNOWLEDGE }
    val fieldPanelColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        FieldAtlasColors.SageWash
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                SetupHeader()
            }
            item {
                ModelSetupCard(
                    ready = hasModel,
                    importing = importing,
                    downloading = downloading,
                    downloadedBytes = downloadedBytes,
                    onImportPack = onImportPack,
                    onDownloadModel = onDownloadModel,
                    onCancelDownload = onCancelDownload,
                )
            }
            item {
                KnowledgeNote(ready = hasKnowledge)
            }
            item { OfflineByDesignNote(fieldPanelColor) }
            if (error != null) {
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Couldn't finish setup",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SetupHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "FIELD ATLAS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 4.sp,
        )
        FieldAtlasHeader(
            title = "Set up offline research",
            subtitle = "Choose how to add a model to this phone.",
        )
    }
}

@Composable
private fun ModelSetupCard(
    ready: Boolean,
    importing: Boolean,
    downloading: Boolean,
    downloadedBytes: Long,
    onImportPack: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(FieldAtlasIcons.Archive, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text("Qwen3.5 2B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (ready) "Installed and verified" else "Recommended · 1.4 GB · Apache 2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!ready) {
                FieldAtlasPrimaryButton(
                    text = if (downloading) "Downloading model…" else "Download model",
                    onClick = onDownloadModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing && !downloading,
                    leadingIcon = FieldAtlasIcons.Import,
                )
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { (downloadedBytes.toFloat() / RecommendedModel.bytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${downloadedBytes / 1_000_000} / ${RecommendedModel.bytes / 1_000_000} MB · keep app open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancelDownload) { Text("Cancel") }
                    }
                }
                OutlinedButton(
                    onClick = onImportPack,
                    enabled = !importing && !downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (importing) "Verifying pack…" else "Import a model pack instead") }
            }
        }
    }
}

@Composable
private fun KnowledgeNote(ready: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldAtlasIconTile(FieldAtlasIcons.Document, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Knowledge collections", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(if (ready) "Collection ready" else "Optional · add in Library for cited answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OfflineByDesignNote(background: androidx.compose.ui.graphics.Color) {
    FieldAtlasCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = background,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FieldAtlasIcons.Offline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Works offline after setup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Internet is needed to download. After setup, research works with Wi-Fi and mobile data off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
