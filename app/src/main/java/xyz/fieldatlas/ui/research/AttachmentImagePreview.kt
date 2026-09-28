package xyz.fieldatlas.ui.research

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

private data class PreviewImage(val bitmap: Bitmap? = null, val loading: Boolean = true)

/** Decode bounded local copies off the UI thread; no provider/network access here. */
@Composable
private fun localPreview(file: File, edge: Int): State<PreviewImage> = produceState(PreviewImage(), file, edge) {
    value = withContext(Dispatchers.IO) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val ratio = minOf(1.0, edge.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            PreviewImage(bitmap, false)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { PreviewImage(loading = false) }
    }
}

@Composable
internal fun AttachmentThumbnail(file: File, name: String, modifier: Modifier = Modifier.size(64.dp), onClick: () -> Unit) {
    val image by localPreview(file, 256)
    Box(modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClickLabel = "Preview image", onClick = onClick), contentAlignment = Alignment.Center) {
        image.bitmap?.let {
            Image(it.asImageBitmap(), "Preview $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: if (image.loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else Icon(FieldAtlasIcons.Photo, "Preview unavailable for $name")
    }
}

@Composable
internal fun AttachmentImagePreview(file: File, canRemove: Boolean, onClose: () -> Unit, onRemove: () -> Unit, onReadText: (() -> Unit)?) {
    val image by localPreview(file, 2048)
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset = Offset(
        value.x.coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2),
        value.y.coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2),
    )
    fun zoomTo(value: Float) { scale = value.coerceIn(1f, 5f); offset = clamp(offset, scale) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Image preview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close image preview") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)).onSizeChanged { size = it }
                    .pointerInput(file) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            offset = clamp(offset + pan, next)
                            scale = next
                        }
                    }.pointerInput(file) {
                        detectTapGestures(onDoubleTap = { zoomTo(if (scale > 1f) 1f else 2f) })
                    }, contentAlignment = Alignment.Center) {
                    image.bitmap?.let {
                        Image(it.asImageBitmap(), "Attached image", Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        }, contentScale = ContentScale.Fit)
                    } ?: if (image.loading) CircularProgressIndicator() else Text("This image preview is no longer available.", Modifier.padding(24.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { zoomTo(scale / 1.5f) }, enabled = image.bitmap != null && scale > 1f) { Icon(Icons.Default.ZoomOut, "Zoom out") }
                    TextButton(onClick = { zoomTo(1f) }, enabled = image.bitmap != null) { Text("Reset zoom") }
                    IconButton(onClick = { zoomTo(scale * 1.5f) }, enabled = image.bitmap != null && scale < 5f) { Icon(Icons.Default.ZoomIn, "Zoom in") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (onReadText != null) TextButton(onClick = onReadText) { Text("Recognized text") }
                    else Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRemove, enabled = canRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove image") }
                }
            }
        }
    }
}
