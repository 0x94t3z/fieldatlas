package xyz.fieldatlas.ui.research

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
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
internal fun ReadAloudButton(seconds: Long?, label: String = "Read this guide aloud", compact: Boolean = false, onClick: () -> Unit) {
    // A guide keeps the large button, for someone who cannot read the screen now; an answer's is smaller, so the answer leads.
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 56.dp).clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp))
        Text("Read aloud", Modifier.weight(1f), style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
            .copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimary)
        seconds?.let { Text("about ${roundedSeconds(it)}", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)) }
    }
}

/**
 * An answer's Read aloud bar once tapped: the same dark bar becomes the player, in place. It fills as the voice gets
 * ready and then as it reads; back one sentence, pause or play, and stop each have their own button.
 */
@Composable
internal fun AnswerPlayerBar(reading: ReadAloud.Reading, onBack: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit,
                             modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val toggle = if (reading.paused) onResume else onPause
    val fraction = when {
        reading.preparing -> reading.prepared
        reading.durationMs > 0 -> reading.positionMs.toFloat() / reading.durationMs
        reading.segments.isNotEmpty() -> (reading.index + 1).toFloat() / reading.segments.size
        else -> 0f
    }.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary)
        .semantics { liveRegion = LiveRegionMode.Polite; stateDescription = when {
            reading.preparing -> "Getting ready"; reading.paused -> "Paused"; else -> "Reading" } }) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(colors.onPrimary.copy(alpha = 0.13f)))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (reading.preparing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                }
            } else {
                PlayerIconButton("Back one sentence", onBack) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
                }
                // 48 dp, as an emergency guide is read with this player too: hands may be shaking or gloved.
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClickLabel = if (reading.paused) "Resume reading" else "Pause reading", onClick = toggle)
                    .semantics { contentDescription = if (reading.paused) "Resume reading" else "Pause reading" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(colors.onPrimary), contentAlignment = Alignment.Center) {
                        Icon(if (reading.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null,
                            tint = colors.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)
                .clickable(enabled = !reading.preparing, role = Role.Button,
                    onClickLabel = if (reading.paused) "Resume reading" else "Pause reading", onClick = toggle)) {
                Text(when {
                        reading.preparing -> "Getting ready · ${(reading.prepared * 100).toInt()}%"
                        reading.paused -> "Paused"
                        else -> "Reading aloud"
                    }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onPrimary, maxLines = 1)
                Text(if (reading.preparing) "${voiceName(reading.voice)} · then reads without pauses"
                    else (if (reading.durationMs > 0) "${clock(reading.positionMs)} of ${clock(reading.durationMs)}"
                        else "${reading.index + 1} of ${reading.segments.size}") + " · " + voiceName(reading.voice).lowercase(),
                    style = MaterialTheme.typography.labelSmall, color = colors.onPrimary.copy(alpha = 0.8f), maxLines = 1)
            }
            PlayerIconButton(if (reading.preparing) "Cancel reading" else "Stop reading", onStop) {
                if (reading.preparing) Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                else Icon(Icons.Filled.Stop, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PlayerIconButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { icon() }
}

/** Shows [content] with the reading of page [key] while there is one, following its clock without the page around it. */
@Composable
internal fun LiveReading(readAloud: ReadAloud, key: String, content: @Composable (ReadAloud.Reading) -> Unit) {
    val reading by readAloud.state.collectAsState()
    reading?.takeIf { it.key == key }?.let { content(it) }
}

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
