package xyz.fieldatlas.ui.research

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.attachments.*
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

@Composable
fun AttachmentRows(items: List<AttachmentUiState>, enabled: Boolean, remove: (String) -> Unit, retry: (String) -> Unit) {
    var previewId by remember { mutableStateOf<String?>(null) }
    var imagePreviewId by remember { mutableStateOf<String?>(null) }
    var errorId by remember { mutableStateOf<String?>(null) }
    if (items.isNotEmpty()) LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val imageFile = item.previewFile.takeIf { item.kind == AttachmentKind.IMAGE }
            if (item.kind != AttachmentKind.IMAGE) {
                // Documents don't need a photo-sized tile. Keep the filename and read state in
                // one compact row so an attached file doesn't stretch the whole question box.
                Box(Modifier.width(232.dp).height(68.dp)) {
                    Surface(
                        onClick = { if (item.phase == AttachmentPhase.Error) errorId = item.id else previewId = item.id },
                        enabled = item.phase != AttachmentPhase.Reading,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(FieldAtlasIcons.Document, null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.displayName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (item.phase == AttachmentPhase.Error) "Read issue" else
                                        if (item.phase == AttachmentPhase.Reading) "Reading…" else
                                        if (item.extracted?.fromOcr == true || item.extracted?.coverageNote != null) "Check text" else "Ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.phase == AttachmentPhase.Error) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { remove(item.id) }, enabled = enabled,
                        modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                        Icon(Icons.Default.Close, "Remove ${item.displayName}", Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
                    }
                }
            } else {
                    Box(Modifier.size(112.dp)) {
                        if (imageFile != null) {
                            AttachmentThumbnail(imageFile, item.displayName, Modifier.fillMaxSize()) { imagePreviewId = item.id }
                        } else {
                            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(FieldAtlasIcons.Photo, "Image attachment", Modifier.size(28.dp))
                                }
                            }
                        }
                        IconButton(onClick = { remove(item.id) }, enabled = enabled,
                            modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.60f)) {
                                Icon(Icons.Default.Close, "Remove ${item.displayName}",
                                    Modifier.padding(5.dp).size(14.dp),
                                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
                            }
                        }
                        if (item.phase == AttachmentPhase.Error) {
                            IconButton(onClick = { errorId = item.id },
                                modifier = Modifier.align(Alignment.BottomStart).size(48.dp)) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                                    Icon(Icons.Default.Warning, "Read issue for ${item.displayName}",
                                        Modifier.padding(7.dp).size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        } else if (item.phase == AttachmentPhase.Reading) {
                            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                                shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)) {
                                CircularProgressIndicator(Modifier.padding(6.dp).size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
            }
        }
    }
    items.firstOrNull { it.id == errorId && it.phase == AttachmentPhase.Error }?.let { item ->
        AlertDialog(onDismissRequest = { errorId = null }, title = { Text("Couldn’t read attachment") },
            text = { Text(item.error ?: "Try another file saved on your device.") },
            confirmButton = { TextButton(onClick = { retry(item.id); errorId = null }, enabled = enabled) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { errorId = null }) { Text("Close") } })
    }
    items.firstOrNull { it.id == imagePreviewId }?.let { item ->
        item.previewFile?.let { file ->
            AttachmentImagePreview(file, enabled,
                onClose = { imagePreviewId = null },
                onRemove = { remove(item.id); imagePreviewId = null },
                onReadText = if (item.phase == AttachmentPhase.Ready) ({ imagePreviewId = null; previewId = item.id }) else null)
        }
    }
    items.firstOrNull { it.id == previewId }?.extracted?.let { extracted ->
        AlertDialog(
            onDismissRequest = { previewId = null },
            title = { Text(extracted.displayName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("Read locally. Answers use selected excerpts.", style = MaterialTheme.typography.bodySmall)
                if (extracted.fromOcr) Text("Text recognition currently supports printed English and may make mistakes. Check the original.", style = MaterialTheme.typography.bodySmall)
                extracted.coverageNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                extracted.pages.forEach { page ->
                    if (items.firstOrNull { it.id == previewId }?.kind == AttachmentKind.PDF) Text("Page ${page.number}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Text(page.text, style = MaterialTheme.typography.bodyMedium)
                }
            } },
            confirmButton = { TextButton(onClick = { previewId = null }) { Text("Close") } },
        )
    }
}
