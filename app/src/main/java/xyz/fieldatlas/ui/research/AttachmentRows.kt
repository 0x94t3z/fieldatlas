package xyz.fieldatlas.ui.research

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val imageFile = item.previewFile.takeIf { item.kind == AttachmentKind.IMAGE }
            if (item.kind != AttachmentKind.IMAGE) {
                // Documents don't need a photo-sized tile. Keep the filename and read state in
                // one compact row so an attached file doesn't stretch the whole question box.
                Box(Modifier.width(228.dp)) {
                    Surface(
                        onClick = { if (item.phase == AttachmentPhase.Error) errorId = item.id else previewId = item.id },
                        enabled = item.phase != AttachmentPhase.Reading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.background,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 40.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(Modifier.size(36.dp), color = MaterialTheme.colorScheme.primaryContainer,
                                shape = FieldAtlasButtonShape) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(when {
                                        item.kind == AttachmentKind.PDF -> FieldAtlasIcons.Pdf
                                        item.kind == AttachmentKind.TEXT && TextFileTypes.language(item.displayName) != null -> FieldAtlasIcons.Code
                                        else -> FieldAtlasIcons.Document
                                    },
                                        null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.displayName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold)
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
                        Icon(FieldAtlasIcons.Close, "Remove ${item.displayName}", Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
                    }
                }
            } else {
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(132.dp)) {
                        if (imageFile != null) {
                            AttachmentThumbnail(imageFile, item.displayName, Modifier.fillMaxSize()) { imagePreviewId = item.id }
                        } else {
                            Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(FieldAtlasIcons.Photo, "Image attachment", Modifier.size(28.dp))
                                }
                            }
                        }
                        IconButton(onClick = { remove(item.id) }, enabled = enabled,
                            modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                            Surface(shape = CircleShape, color = Color(0xFF24392D).copy(alpha = 0.8f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))) {
                                Icon(FieldAtlasIcons.Close, "Remove ${item.displayName}",
                                    Modifier.padding(5.dp).size(12.dp),
                                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
                            }
                        }
                        if (item.phase == AttachmentPhase.Error) {
                            IconButton(onClick = { errorId = item.id },
                                modifier = Modifier.align(Alignment.BottomStart).size(48.dp)) {
                                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                    Icon(FieldAtlasIcons.Warning, "Read issue for ${item.displayName}",
                                        Modifier.padding(7.dp).size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        } else if (item.phase == AttachmentPhase.Reading) {
                            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)) {
                                CircularProgressIndicator(Modifier.padding(6.dp).size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    Text(item.displayName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 5.dp))
                    Text(when (item.phase) {
                        AttachmentPhase.Reading -> "Reading…"
                        AttachmentPhase.Error -> "Read issue"
                        AttachmentPhase.Ready -> "Photo · OCR"
                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 3.dp))
                }
            }
        }
    }
    // Answers lead with the files; the switch adds the downloaded collections behind them, to explain terms or add
    // context. "Only my files" or "use my library" in the question still wins either way.
    val library = LocalLibraryWithFiles.current
    if (items.isNotEmpty() && library != null) {
        val on by library.state.collectAsState()
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = on, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch) { library.set(it) }
            .padding(start = 16.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Also use my collections", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(if (on) "Your files first, then what you downloaded" else "Answers use your files only",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = on, onCheckedChange = null, enabled = enabled)
        }
    }
    items.firstOrNull { it.id == errorId && it.phase == AttachmentPhase.Error }?.let { item ->
        AlertDialog(onDismissRequest = { errorId = null }, title = { Text("Couldn’t read attachment") },
            text = { Text(item.error ?: "Try another file saved on your device.") },
            confirmButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { retry(item.id); errorId = null }, enabled = enabled) { Text("Retry") } },
            dismissButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { errorId = null }) { Text("Close") } })
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
            text = {
                // Up to 300,000 characters: render paragraph by paragraph, only what is on screen.
                val blocks = remember(extracted) { previewBlocks(extracted) }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item {
                        Text("Read on this phone. Answers use the parts that fit the question.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                        if (extracted.fromOcr) Text("Text recognition currently supports printed English and may make mistakes. Check the original.", style = MaterialTheme.typography.bodySmall)
                        extracted.coverageNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(blocks.size) { index ->
                        val (heading, text) = blocks[index]
                        if (heading != null) Text(heading, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        if (text.isNotEmpty()) Text(text, style =
                            if (extracted.kind == AttachmentKind.TEXT && TextFileTypes.language(extracted.displayName) != null)
                                MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp, lineHeight = 21.sp)
                            else MaterialTheme.typography.bodyLarge.copy(fontFamily = xyz.fieldatlas.ui.theme.FieldAtlasEditorial,
                                lineHeight = 29.sp))
                    }
                }
            },
            confirmButton = { TextButton(shape = FieldAtlasButtonShape, onClick = { previewId = null }) { Text("Close") } },
        )
    }
}

/** Page headings (for paged formats) and paragraph-sized text blocks for a lazy preview. */
internal fun previewBlocks(extracted: xyz.fieldatlas.attachments.ExtractedAttachment): List<Pair<String?, String>> {
    val noun = when (extracted.kind) {
        AttachmentKind.PDF -> "Page"
        AttachmentKind.DOCUMENT -> xyz.fieldatlas.attachments.DocumentTextReader.pageNoun(extracted.displayName)
            .takeIf { it != "part" }?.replaceFirstChar(Char::uppercaseChar)
        else -> null
    }
    return extracted.pages.flatMap { page ->
        val paragraphs = page.text.split(Regex("\\n\\s*\\n")).flatMap { it.chunked(2_000) }.filter(String::isNotBlank)
        paragraphs.mapIndexed { index, text -> (if (index == 0 && noun != null) "$noun ${page.number}" else null) to text.trim() }
    }
}
