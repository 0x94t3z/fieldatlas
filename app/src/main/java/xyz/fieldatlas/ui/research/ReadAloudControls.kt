package xyz.fieldatlas.ui.research

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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

/** One tap to hear an emergency guide: the first thing under the title, for someone who cannot read the screen now. */
@Composable
internal fun ReadAloudButton(seconds: Long?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClickLabel = "Read this guide aloud", onClick = onClick)
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

/** Shown pinned under the bar while a guide is read: Stop, and how far along it is. */
@Composable
internal fun ReadingControl(reading: ReadAloud.Reading, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button, onClickLabel = "Stop reading", onClick = onStop)
                .semantics { liveRegion = LiveRegionMode.Polite; stateDescription = "Sentence ${reading.index + 1} of ${reading.segments.size}" }
                .padding(start = 16.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoundBars(MaterialTheme.colorScheme.onPrimaryContainer, playing = !reading.paused)
            Text("Stop reading", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("${reading.index + 1} of ${reading.segments.size}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Icon(Icons.Filled.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Text("${voiceName(reading.voice)} · keeps reading with the screen off",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A quiet way to hear any answer, beside its sources and time. */
@Composable
internal fun ListenPill(onClick: () -> Unit) {
    // The pill is drawn small; its touch area is the full 48dp.
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Listen to this answer", onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(32.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).padding(start = 10.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Text("Listen", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The slim player above the question box while an answer is read. */
@Composable
internal fun ListenPlayer(reading: ReadAloud.Reading, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            .semantics { contentDescription = "Reading the answer aloud" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoundBars(MaterialTheme.colorScheme.primary, playing = !reading.paused && !reading.preparing)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(when {
                    reading.preparing -> "Getting the voice ready" + if (reading.prepared > 0f) " · ${(reading.prepared * 100).toInt()}%" else "…"
                    reading.paused -> "Paused"
                    else -> "Reading the answer"
                }, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text("${reading.index + 1} of ${reading.segments.size} · " + voiceName(reading.voice).lowercase(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { if (reading.preparing) reading.prepared else (reading.index + 1f) / reading.segments.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = if (reading.paused) "Resume reading" else "Pause reading",
                onClick = if (reading.paused) onResume else onPause), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(if (reading.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = "Stop reading", onClick = onStop), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Five bars that move while the voice speaks; still when paused or when the phone asks for less motion. */
@Composable
private fun SoundBars(color: Color, playing: Boolean) {
    val context = LocalContext.current
    val still = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val heights = listOf(8f, 16f, 11f, 19f, 9f)
    val motion = rememberInfiniteTransition(label = "voice")
    val phase by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "phase")
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEachIndexed { i, h ->
            val scale = if (!playing || still) 0.6f else 0.45f + 0.55f * (if (i % 2 == 0) phase else 1f - phase)
            Box(Modifier.width(3.dp).height((h * scale).dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private fun voiceName(voice: ReadAloud.Voice) = when (voice) {
    ReadAloud.Voice.Recorded -> "Recorded voice"
    ReadAloud.Voice.Natural -> "Natural voice"
    ReadAloud.Voice.Phone -> "Phone voice"
}

private fun roundedSeconds(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds < 60) "${(seconds + 4) / 5 * 5} s" else "${(seconds + 30) / 60} min"
}
