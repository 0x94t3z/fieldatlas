package xyz.fieldatlas.ui.research

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.fieldatlas.speech.NaturalVoice
import xyz.fieldatlas.speech.ReadAloud
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasInfoSheet
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasProgressBar
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasSwitch
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.TileTone

private val naturalSize get() = formatAssetBytes(NaturalVoice.BYTES)

/** Plays a few seconds of [choice]; while it plays, the same button stops it. */
@Composable
private fun SampleButton(readAloud: ReadAloud, reading: ReadAloud.Reading?, choice: ReadAloud.Choice, label: String = "Sample") {
    val playing = reading?.key == ReadAloud.SAMPLE_KEY && (reading.voice == ReadAloud.Voice.Phone) == (choice == ReadAloud.Choice.Phone)
    OutlinedButton(shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp),
        onClick = { if (playing) readAloud.stop() else readAloud.playSample(choice) }) {
        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
        Text(if (playing) "Stop" else label, Modifier.padding(start = 6.dp))
    }
}

/**
 * Asked the first time an answer is listened to: the natural voice (a download, once) or the phone's voice now.
 * An emergency guide never asks; it plays its recording at once.
 */
@Composable
fun ChooseVoiceSheet(readAloud: ReadAloud, onChosen: () -> Unit, onDismiss: () -> Unit) {
    val voice by readAloud.natural.state.collectAsState()
    val reading by readAloud.state.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    FieldAtlasInfoSheet("Read answers aloud", onDismiss = { if (reading?.key == ReadAloud.SAMPLE_KEY) readAloud.stop(); onDismiss() }) {
        Text("Pick a voice once. You can change it in More.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val queue = xyz.fieldatlas.ui.library.LocalDownloads.current?.state?.collectAsState()?.value
        val fetching = queue != null && (queue.isActive(xyz.fieldatlas.assets.DownloadItem.Voice) ||
            queue.queuePosition(xyz.fieldatlas.assets.DownloadItem.Voice) >= 0)
        VoiceOption(Icons.AutoMirrored.Outlined.VolumeUp, TileTone.Sage, "Natural voice", when {
            voice == NaturalVoice.State.Ready -> "Sounds like a person. On this phone; reads with no signal."
            fetching -> "Sounds like a person. Downloading now; the phone's voice reads until it is ready."
            else -> "Sounds like a person. $naturalSize, downloaded once, then works with no signal. The phone's voice reads until it is ready."
        }) {
            when {
                voice == NaturalVoice.State.Ready || fetching -> FieldAtlasPrimaryButton(if (fetching) "Use it when ready" else "Use natural voice", onClick = {
                    readAloud.updateSettings { it.copy(voice = ReadAloud.Choice.Natural) }; onChosen()
                })
                else -> FieldAtlasPrimaryButton("Download · $naturalSize", onClick = { confirm = true }, leadingIcon = FieldAtlasIcons.Download)
            }
            SampleButton(readAloud, reading, ReadAloud.Choice.Natural)
        }
        VoiceOption(Icons.Outlined.PhoneAndroid, TileTone.Paper, "Phone voice", "Ready now, no download. Sounds more synthetic.") {
            OutlinedButton(shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                readAloud.updateSettings { it.copy(voice = ReadAloud.Choice.Phone) }; onChosen()
            }) { Text("Use phone voice now") }
            SampleButton(readAloud, reading, ReadAloud.Choice.Phone)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FieldAtlasIcons.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("An emergency guide never waits for this: it plays its recording at once.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirm) xyz.fieldatlas.ui.library.VoiceDownloadConfirm(onDone = { confirm = false }, then = onChosen)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceOption(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: TileTone, title: String, detail: String,
                        actions: @Composable () -> Unit) {
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FieldAtlasIconTile(icon, size = 40.dp, tone = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** More › Reading aloud: the voice, its pace, and when the app reads without being asked. */
@Composable
fun ReadingAloudSheet(readAloud: ReadAloud, onDismiss: () -> Unit) {
    val settings by readAloud.settings.collectAsState()
    val voice by readAloud.natural.state.collectAsState()
    val reading by readAloud.state.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val natural = settings.voice == ReadAloud.Choice.Natural
    FieldAtlasInfoSheet("Reading aloud", onDismiss = { if (reading?.key == ReadAloud.SAMPLE_KEY) readAloud.stop(); onDismiss() }) {
        Label("Voice")
        Segments(listOf("Natural · $naturalSize", "Phone voice"), if (natural) 0 else if (settings.voice == ReadAloud.Choice.Phone) 1 else -1) { picked ->
            readAloud.updateSettings { it.copy(voice = if (picked == 0) ReadAloud.Choice.Natural else ReadAloud.Choice.Phone) }
        }
        when {
            natural && voice == NaturalVoice.State.Ready -> Note("Natural voice is on this phone. It reads with no signal.")
            natural -> {
                Note("Natural voice is not on this phone yet; the phone's voice reads until it is.")
                xyz.fieldatlas.ui.library.VoiceDiscoverRow(readAloud, onGet = { confirm = true }, onDetails = { confirm = true })
            }
            else -> Note("The phone's own voice: ready now, no download.")
        }
        Label("Speed")
        val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f)
        Segments(listOf("0.8×", "1×", "1.25×", "1.5×"), speeds.indexOfFirst { kotlin.math.abs(it - settings.speed) < 0.01f }) { picked ->
            readAloud.updateSettings { it.copy(speed = speeds[picked]) }
        }
        Label("When to read")
        FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            SwitchRow("Read the answer when I ask by voice", "Asked with the microphone, the answer is read back when it is done.",
                settings.readVoiceAnswers) { on -> readAloud.updateSettings { it.copy(readVoiceAnswers = on) } }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
            SwitchRow("Read emergency guides at once", "Starts reading as soon as a guide opens. Off: tap Read aloud.",
                settings.readGuidesAtOnce) { on -> readAloud.updateSettings { it.copy(readGuidesAtOnce = on) } }
        }
        SampleButton(readAloud, reading, settings.voice ?: ReadAloud.Choice.Phone, label = "Play a sample")
        Note("Emergency guides always play their own recording in the natural voice, already on the phone.")
    }
    if (confirm) xyz.fieldatlas.ui.library.VoiceDownloadConfirm(onDone = { confirm = false })
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(650)), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A row of choices that reads as one control; each part is a full-height 48dp target. */
@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup()) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .selectable(on, role = Role.RadioButton) { onSelect(index) }
                .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(option, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 72.dp).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FieldAtlasSwitch(checked = checked, onCheckedChange = onChange, compact = true, modifier = Modifier.semantics { contentDescription = title })
    }
}

/** What More shows under Reading aloud before it is opened. */
fun readingSummary(settings: ReadAloud.Settings, naturalReady: Boolean): String {
    val voice = when {
        settings.voice == ReadAloud.Choice.Natural && naturalReady -> "Natural voice"
        settings.voice == ReadAloud.Choice.Natural -> "Natural voice, not downloaded"
        else -> "Phone voice"
    }
    val speed = if (settings.speed == 1.0f) "normal speed" else "${settings.speed.toString().removeSuffix(".0")}×"
    return "$voice · $speed"
}
