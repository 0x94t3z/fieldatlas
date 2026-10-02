package xyz.fieldatlas.ui.sources

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.attachments.AttachmentPolicy
import xyz.fieldatlas.attachments.TextAttachmentDecoder
import xyz.fieldatlas.attachments.TextFileTypes
import xyz.fieldatlas.ui.research.AttachmentImagePreview

private data class OriginalContent(val text: String? = null, val bitmap: Bitmap? = null,
    val pages: Int = 1, val page: Int = 1, val loading: Boolean = true, val failed: Boolean = false)

@Composable
internal fun OriginalSourcePreview(original: SourceOriginal, title: String, citedPage: Int, onClose: () -> Unit) {
    if (original.kind == AttachmentKind.IMAGE) {
        AttachmentImagePreview(original.file, false, onClose, {}, null, showRemove = false)
        return
    }
    var page by rememberSaveable(original.file.path, citedPage) { mutableIntStateOf(citedPage.coerceAtLeast(1)) }
    val content by produceState(OriginalContent(), original, page) {
        value = OriginalContent()
        value = withContext(Dispatchers.IO) {
            try {
                when (original.kind) {
                    AttachmentKind.TEXT -> OriginalContent(text = original.file.inputStream().use {
                        TextAttachmentDecoder.decode(AttachmentPolicy.readBounded(it))
                    }, loading = false)
                    AttachmentKind.PDF -> ParcelFileDescriptor.open(original.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            val selected = page.coerceIn(1, renderer.pageCount)
                            renderer.openPage(selected - 1).use { pdfPage ->
                                val ratio = minOf(2.0, 2048.0 / maxOf(pdfPage.width, pdfPage.height))
                                val bitmap = Bitmap.createBitmap((pdfPage.width * ratio).toInt().coerceAtLeast(1),
                                    (pdfPage.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                OriginalContent(bitmap = bitmap, pages = renderer.pageCount, page = selected, loading = false)
                            }
                        }
                    }
                    else -> OriginalContent(loading = false, failed = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { OriginalContent(loading = false, failed = true) }
        }
    }
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Original file", style = MaterialTheme.typography.titleLarge)
                        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close original file") }
                }
                when {
                    content.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    content.failed -> Box(Modifier.weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Couldn’t open the original file. Your saved source text is still available.")
                    }
                    content.text != null -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                        item {
                            TextButton(shape = FieldAtlasButtonShape, onClick = { clipboard.setText(AnnotatedString(content.text!!)) }) { Text("Copy original text") }
                            SourceTextContent(content.text!!, TextFileTypes.language(title))
                        }
                    }
                    content.bitmap != null -> {
                        PdfPageImage(content.bitmap!!, content.page, Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(shape = FieldAtlasButtonShape, onClick = { page = content.page - 1 }, enabled = content.page > 1) { Text("Previous") }
                            Text("Page ${content.page} of ${content.pages}", style = MaterialTheme.typography.bodySmall)
                            TextButton(shape = FieldAtlasButtonShape, onClick = { page = content.page + 1 }, enabled = content.page < content.pages) { Text("Next") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(bitmap: Bitmap, page: Int, modifier: Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun zoom(next: Float, pan: Offset = Offset.Zero) {
        scale = next.coerceIn(1f, 5f)
        offset = Offset((offset.x + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2),
            (offset.y + pan.y).coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2))
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { size = it }
            .pointerInput(bitmap) { detectTransformGestures { _, pan, factor, _ -> zoom(scale * factor, pan) } }) {
            Image(bitmap.asImageBitmap(), "Original PDF page $page", Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            }, contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(shape = FieldAtlasButtonShape, onClick = { zoom(scale / 1.5f) }, enabled = scale > 1f) { Text("Zoom out") }
            TextButton(shape = FieldAtlasButtonShape, onClick = { zoom(1f) }) { Text("Reset") }
            TextButton(shape = FieldAtlasButtonShape, onClick = { zoom(scale * 1.5f) }, enabled = scale < 5f) { Text("Zoom in") }
        }
    }
}
