package xyz.fieldatlas.ui.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.fieldatlas.assets.DownloadItem
import xyz.fieldatlas.speech.NaturalVoice
import xyz.fieldatlas.speech.ReadAloud
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasInfoSheet
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.TileTone

internal val voiceSize get() = formatAssetBytes(NaturalVoice.BYTES)
private const val VOICE_SOURCE = "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11"

/**
 * The step before the voice downloads, from wherever it is asked for (Library, the voice choice, More): the same
 * confirm sheet as any download. Confirming also makes it the voice for answers, since that is why it is fetched.
 */
@Composable
fun VoiceDownloadConfirm(onDone: () -> Unit, then: () -> Unit = {}) {
    val downloads = LocalDownloads.current ?: return
    val readAloud = xyz.fieldatlas.ui.research.LocalReadAloud.current
    val saved = readAloud?.natural?.savedBytes() ?: 0
    DownloadConfirmSheet(DownloadItem.Voice, "Download", "English · female voice · $voiceSize. Afterward every answer can be read aloud with no signal.",
        saved = saved, installMargin = 200_000_000L, sourceUrl = VOICE_SOURCE,
        onConfirm = {
            readAloud?.updateSettings { it.copy(voice = ReadAloud.Choice.Natural) }
            downloads.queue.enqueue(DownloadItem.Voice)
            then()
        }, onDismiss = onDone)
}

/** Discover's row for the voice: the size pill like every other download, and a sample to hear first. */
@Composable
internal fun VoiceDiscoverRow(readAloud: ReadAloud, onGet: () -> Unit, onDetails: () -> Unit) {
    val downloads = LocalDownloads.current ?: return
    val status by downloads.state.collectAsState()
    val voice by readAloud.natural.state.collectAsState()
    val reading by readAloud.state.collectAsState()
    DownloadRow(Icons.AutoMirrored.Outlined.VolumeUp, TileTone.Sage, DownloadItem.Voice.title,
        "English · female voice · reads answers offline",
        rowState(DownloadItem.Voice, status, installed = voice == NaturalVoice.State.Ready, saved = readAloud.natural.savedBytes()),
        voiceSize, rowActions(downloads, DownloadItem.Voice, onGet), onClick = onDetails) {
        SampleLine(readAloud, reading)
    }
}

/** On this phone: the voice is in use for answers (or can be), plays a sample, and can be removed. */
@Composable
internal fun VoiceInstalledRow(readAloud: ReadAloud) {
    val settings by readAloud.settings.collectAsState()
    val reading by readAloud.state.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val inUse = settings.voice == ReadAloud.Choice.Natural
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(Icons.AutoMirrored.Outlined.VolumeUp, size = 40.dp, tone = TileTone.Sage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(DownloadItem.Voice.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("$voiceSize · reads answers aloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (inUse) Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text("In use for answers", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            // The same overflow menu as every pack card: rounded, on the card's colour, delete last and in red.
            Box {
                androidx.compose.material3.IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(FieldAtlasIcons.MoreOptions, contentDescription = "More options for ${DownloadItem.Voice.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surface) {
                    val sampling = reading?.key == ReadAloud.SAMPLE_KEY
                    DropdownMenuItem(
                        text = { Text(if (sampling) "Stop the sample" else "Play a sample") },
                        leadingIcon = { Icon(if (sampling) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; if (sampling) readAloud.stop() else readAloud.playSample(ReadAloud.Choice.Natural) },
                    )
                    if (!inUse) {
                        DropdownMenuItem(
                            text = { Text("Use for answers") },
                            leadingIcon = { Icon(FieldAtlasIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menu = false; readAloud.updateSettings { it.copy(voice = ReadAloud.Choice.Natural) } },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    DropdownMenuItem(
                        text = { Text("Delete…", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(FieldAtlasIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirmRemove = true },
                    )
                }
            }
        }
    }
    if (confirmRemove) xyz.fieldatlas.ui.theme.FieldAtlasDeleteDialog(
        title = "Delete ${DownloadItem.Voice.title}?",
        message = "Frees $voiceSize. Answers are then read by the phone's voice; emergency guides keep their recordings. " +
            "You can download it again later.",
        onKeep = { confirmRemove = false },
        onDelete = {
            confirmRemove = false
            readAloud.stop()
            scope.launch { readAloud.natural.remove() }
        },
    )
}

/** Hear it before spending the space: the sample with its words marked as they are spoken, and what it changes. */
@Composable
internal fun VoiceDetailSheet(readAloud: ReadAloud, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val reading by readAloud.state.collectAsState()
    val voice by readAloud.natural.state.collectAsState()
    val playing = reading?.key == ReadAloud.SAMPLE_KEY
    val colors = MaterialTheme.colorScheme
    FieldAtlasInfoSheet(DownloadItem.Voice.title, onDismiss = { if (playing) readAloud.stop(); onDismiss() }) {
        Text("English · female voice · $voiceSize", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = if (playing) "Stop the sample" else "Play a sample") {
                    if (playing) readAloud.stop() else readAloud.playSample(ReadAloud.Choice.Natural)
                }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = colors.onPrimary)
                    }
                }
                SampleWave(playing && reading?.paused != true, Modifier.weight(1f))
                Text(seconds(reading?.takeIf { playing }?.positionMs ?: 0), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
            }
            val spoken = reading?.takeIf { playing }?.shown
            Text(buildAnnotatedString {
                ReadAloud.SAMPLE_TEXT.split(". ").forEachIndexed { index, part ->
                    val sentence = if (part.endsWith(".")) part else "$part."
                    if (index > 0) append(" ")
                    if (spoken == sentence) withStyle(SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer)) { append(sentence) }
                    else append(sentence)
                }
            }, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Point(true, "Every answer can be listened to, offline")
            Point(true, "Emergency guides already play in this voice")
            Point(false, "On older phones it pauses briefly between sentences")
        }
        if (voice != NaturalVoice.State.Ready) FieldAtlasPrimaryButton("Download · $voiceSize", onClick = { if (playing) readAloud.stop(); onDownload() },
            leadingIcon = FieldAtlasIcons.Download, modifier = Modifier.fillMaxWidth())
        Text("Supertonic 3 voice (OpenRAIL-M), run by sherpa-onnx. Licences are in More.", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
}

@Composable
private fun Point(good: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (good) FieldAtlasIcons.Check else FieldAtlasIcons.Info, null, Modifier.size(18.dp),
            tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SampleLine(readAloud: ReadAloud, reading: ReadAloud.Reading?) {
    val playing = reading?.key == ReadAloud.SAMPLE_KEY
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SampleButton(readAloud, reading)
        SampleWave(playing && reading?.paused != true, Modifier.width(120.dp))
        Text(if (playing) seconds(reading?.positionMs ?: 0) else "Sample", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A round play button for the natural voice's sample; it stops the sample while it plays. */
@Composable
private fun SampleButton(readAloud: ReadAloud, reading: ReadAloud.Reading?) {
    val playing = reading?.key == ReadAloud.SAMPLE_KEY
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = if (playing) "Stop the sample" else "Play a sample") {
        if (playing) readAloud.stop() else readAloud.playSample(ReadAloud.Choice.Natural)
    }.semantics { contentDescription = if (playing) "Stop the sample" else "Play a sample of the natural voice" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, colors.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(17.dp), tint = colors.primary)
        }
    }
}

/** Bars standing for the voice: still and pale until it plays, then moving; still when the phone asks for less motion. */
@Composable
private fun SampleWave(playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val heights = listOf(6, 12, 18, 10, 22, 14, 8, 16, 20, 9, 13, 6, 11, 17, 7)
    val phase by rememberInfiniteTransition(label = "sample").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "phase")
    val color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, h ->
            val scale = if (!playing || still) 1f else 0.5f + 0.5f * (if (index % 2 == 0) phase else 1f - phase)
            Box(Modifier.width(3.dp).height((h * scale).dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private fun seconds(ms: Long) = "0:%02d".format(ms / 1000)
