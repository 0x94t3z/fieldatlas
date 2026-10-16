package xyz.fieldatlas.ui.research

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.speech.ReadAloud

/** The app's reader, for any page that offers to read itself aloud. */
val LocalReadAloud = compositionLocalOf<ReadAloud?> { null }

/**
 * One tap to hear the page, the first thing under the title: an emergency guide for someone who cannot read the screen
 * now, and any answer the same way.
 */
@Composable
internal fun ReadAloudButton(seconds: Long?, label: String = "Read this guide aloud", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 16.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        Text("Read aloud", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary)
        seconds?.let { Text("about ${roundedSeconds(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)) }
    }
}

/**
 * Pinned under the bar while a page (a guide or an answer) is read. Tapping it pauses and resumes; Stop is its own
 * button beside pause. It says how far along the reading is (sentences for a guide; time for an answer,
 * [showTime]) and, while the natural voice prepares, fills as it gets ready.
 */
@Composable
internal fun ReadingControl(reading: ReadAloud.Reading, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit,
                            showTime: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val toggle = if (reading.paused) onResume else onPause
    Column(
        Modifier.fillMaxWidth().background(colors.background).padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                .background(colors.primaryContainer)
                .clickable(role = Role.Button, onClickLabel = if (reading.paused) "Resume reading" else "Pause reading", onClick = toggle)
                .semantics { liveRegion = LiveRegionMode.Polite; stateDescription = if (reading.paused) "Paused" else "Reading" }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoundBars(colors.onPrimaryContainer, playing = !reading.paused && !reading.preparing)
            Text(when {
                    reading.preparing -> "Getting ready"
                    reading.paused -> "Paused"
                    else -> "Reading aloud"
                },
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer, maxLines = 1)
            Text(
                when {
                    reading.preparing -> "${(reading.prepared * 100).toInt()}%"
                    showTime && reading.durationMs > 0 -> "${clock(reading.positionMs)} of ${clock(reading.durationMs)}"
                    else -> "${reading.index + 1} of ${reading.segments.size}"
                },
                style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = if (reading.paused) "Resume reading" else "Pause reading", onClick = toggle),
                contentAlignment = Alignment.Center) {
                Icon(if (reading.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null,
                    tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Stop reading", onClick = onStop)
                .semantics { contentDescription = "Stop reading" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Stop, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        if (reading.preparing) xyz.fieldatlas.ui.theme.FieldAtlasProgressBar(progress = { reading.prepared },
            modifier = Modifier.fillMaxWidth().height(3.dp))
        Text(if (reading.preparing) "${voiceName(reading.voice)} · prepares first, then reads without pauses"
            else "${voiceName(reading.voice)} · keeps reading with the screen off",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** Shows [content] with the reading of page [key] while there is one, following its clock without the page around it. */
@Composable
internal fun LiveReading(readAloud: ReadAloud, key: String, content: @Composable (ReadAloud.Reading) -> Unit) {
    val reading by readAloud.state.collectAsState()
    reading?.takeIf { it.key == key }?.let { content(it) }
}

/**
 * Five bars that move while the voice speaks; still when paused or when the phone asks for less motion. They move at
 * draw time only, so a reading does not lay the player out again sixty times a second.
 */
@Composable
private fun SoundBars(color: Color, playing: Boolean) {
    val context = LocalContext.current
    val still = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val heights = listOf(8f, 16f, 11f, 19f, 9f)
    // Eight steps a second look the same at this size as sixty frames, and redraw the screen a fraction as often.
    val phase = androidx.compose.runtime.produceState<Float?>(null, playing, still) {
        if (!playing || still) { value = null; return@produceState }
        var step = 0
        while (true) { value = BAR_STEPS[step % BAR_STEPS.size]; step++; kotlinx.coroutines.delay(125) }
    }
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEachIndexed { i, h ->
            Box(Modifier.width(3.dp).height(h.dp).graphicsLayer {
                val at = phase?.value
                scaleY = if (at == null) 0.6f else 0.45f + 0.55f * (if (i % 2 == 0) at else 1f - at)
            }.clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private val BAR_STEPS = listOf(0f, 0.35f, 0.7f, 1f, 0.7f, 0.35f)

/** "0:12", "1:05". */
private fun clock(ms: Long): String = (ms.coerceAtLeast(0) / 1000).let { "%d:%02d".format(it / 60, it % 60) }

private fun voiceName(voice: ReadAloud.Voice) = when (voice) {
    ReadAloud.Voice.Recorded -> "Recorded voice"
    ReadAloud.Voice.Natural -> "Natural voice"
    ReadAloud.Voice.Phone -> "Phone voice"
}

private fun roundedSeconds(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds < 60) "${(seconds + 4) / 5 * 5} s" else "${(seconds + 30) / 60} min"
}
