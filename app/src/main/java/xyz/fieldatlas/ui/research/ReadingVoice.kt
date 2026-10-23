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
import androidx.compose.material3.minimumInteractiveComponentSize
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

/** The rounded pill buttons of the reading-aloud board: tonal for the main choice, outlined for the rest. */
@Composable
private fun BoardPill(text: String, onClick: () -> Unit, tonal: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.minimumInteractiveComponentSize().heightIn(min = 40.dp).clip(shape)
            .then(if (tonal) Modifier.background(colors.primaryContainer) else Modifier.border(1.dp, colors.outline, shape))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = if (tonal) colors.onPrimaryContainer else colors.primary) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (tonal) colors.onPrimaryContainer else colors.primary)
    }
}

/** Plays a few seconds of [choice]; while it plays, the same pill stops it. */
@Composable
private fun SampleButton(readAloud: ReadAloud, reading: ReadAloud.Reading?, choice: ReadAloud.Choice, label: String = "Sample") {
    val playing = reading?.key == ReadAloud.SAMPLE_KEY && (reading.voice == ReadAloud.Voice.Phone) == (choice == ReadAloud.Choice.Phone)
    BoardPill(if (playing) "Stop" else label, onClick = { if (playing) readAloud.stop() else readAloud.playSample(choice) },
        icon = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow)
}

/**
 * Asked the first time an answer is listened to: the natural voice (a download, once) or the phone's voice now.
 * An emergency guide never asks; it plays its recording at once.
 */
@Composable
fun ChooseVoiceSheet(readAloud: ReadAloud, onChosen: () -> Unit, onDismiss: () -> Unit) {
    val voice by readAloud.natural.state.collectAsState()
    val reading by readAloud.state.collectAsState()
    val phone by readAloud.phoneVoice.collectAsState()
    // Looked at before anything is played, so the choice says at once whether the phone's voice works offline.
    androidx.compose.runtime.LaunchedEffect(Unit) { readAloud.checkPhoneVoice() }
    var confirm by rememberSaveable { mutableStateOf(false) }
    FieldAtlasInfoSheet("Read answers aloud", onDismiss = { if (reading?.key == ReadAloud.SAMPLE_KEY) readAloud.stop(); onDismiss() }) {
        Text("Pick a voice once. You can change it in More.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val queue = xyz.fieldatlas.ui.library.LocalDownloads.current?.state?.collectAsState()?.value
        val fetching = queue != null && (queue.isActive(xyz.fieldatlas.assets.DownloadItem.Voice) ||
            queue.queuePosition(xyz.fieldatlas.assets.DownloadItem.Voice) >= 0)
        VoiceOption(Icons.AutoMirrored.Outlined.VolumeUp, TileTone.Sage, NaturalVoice.NAME, selected = true, detail = when {
            voice == NaturalVoice.State.Ready -> "Sounds like a person. On this phone; reads with no signal."
            fetching -> "Sounds like a person. Downloading now." + meanwhile(phone)
            else -> "Sounds like a person. $naturalSize, downloaded once, then works with no signal." + meanwhile(phone)
        }) {
            when {
                voice == NaturalVoice.State.Ready || fetching -> BoardPill(if (fetching) "Use it when ready" else "Use ${NaturalVoice.NAME}", tonal = true, onClick = {
                    readAloud.updateSettings { it.copy(voice = ReadAloud.Choice.Natural) }; onChosen()
                })
                else -> BoardPill("Download · $naturalSize", tonal = true, icon = FieldAtlasIcons.Download, onClick = { confirm = true })
            }
            SampleButton(readAloud, reading, ReadAloud.Choice.Natural)
        }
        // Offered only once the phone's voice is known to work offline; otherwise the option says why not.
        VoiceOption(FieldAtlasIcons.Model, TileTone.Paper, "Phone voice", detail = phoneVoiceChoice(phone)) {
            if (phone is ReadAloud.PhoneVoice.Offline) {
                BoardPill("Use phone voice now", onClick = { readAloud.updateSettings { it.copy(voice = ReadAloud.Choice.Phone) }; onChosen() })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FieldAtlasIcons.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("An emergency guide never waits for this: it reads with the best voice already on the phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirm) xyz.fieldatlas.ui.library.VoiceDownloadConfirm(onDone = { confirm = false }, then = onChosen)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceOption(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: TileTone, title: String, selected: Boolean = false,
                        detail: String, actions: @Composable () -> Unit) {
    // As on the board: the suggested choice outlined in the accent colour; the buttons sit under the text, beside each other.
    androidx.compose.material3.Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top) {
            FieldAtlasIconTile(icon, size = 40.dp, tone = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
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
    val phone by readAloud.phoneVoice.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { readAloud.checkPhoneVoice() }
    FieldAtlasInfoSheet("Reading aloud", onDismiss = { if (reading?.key == ReadAloud.SAMPLE_KEY) readAloud.stop(); onDismiss() }) {
        Label("Voice")
        Segments(listOf(NaturalVoice.NAME, "Phone voice"), if (natural) 0 else if (settings.voice == ReadAloud.Choice.Phone) 1 else -1) { picked ->
            readAloud.updateSettings { it.copy(voice = if (picked == 0) ReadAloud.Choice.Natural else ReadAloud.Choice.Phone) }
        }
        when {
            natural && voice == NaturalVoice.State.Ready -> Note("${NaturalVoice.NAME} is on this phone. It reads with no signal.")
            natural -> {
                Note(untilNaturalNote(phone))
                xyz.fieldatlas.ui.library.DownloadGroup(listOf { xyz.fieldatlas.ui.library.VoiceDiscoverRow(readAloud, onGet = { confirm = true }, onDetails = { confirm = true }) })
            }
            else -> {
                Note(phoneVoiceNote(phone))
                // A phone voice that cannot read offline leaves Supertonic 3 as the way to listen: offered right here.
                if ((phone == ReadAloud.PhoneVoice.OnlineOnly || phone == ReadAloud.PhoneVoice.Missing) && voice != NaturalVoice.State.Ready)
                    xyz.fieldatlas.ui.library.DownloadGroup(listOf { xyz.fieldatlas.ui.library.VoiceDiscoverRow(readAloud, onGet = { confirm = true }, onDetails = { confirm = true }) })
            }
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
        // Supertonic 3's sample is a recording in the app, a preview before the download; the phone's voice has nothing
        // to play with until an offline engine is there, so its sample is not offered.
        val sampled = settings.voice ?: ReadAloud.Choice.Phone
        if (sampleAvailable(sampled, phone)) SampleButton(readAloud, reading, sampled, label = "Play a sample")
    }
    if (confirm) xyz.fieldatlas.ui.library.VoiceDownloadConfirm(onDone = { confirm = false })
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A row of choices that reads as one control; each part is a full-height 48dp target. */
@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // As on the board (and Library's tabs): a soft track, the chosen part raised on the card colour.
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
        .selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            androidx.compose.material3.Surface(
                selected = on, onClick = { onSelect(index) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(11.dp),
                color = if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                shadowElevation = if (on) 1.dp else 0.dp,
            ) {
                Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(option, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
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
        settings.voice == ReadAloud.Choice.Natural && naturalReady -> NaturalVoice.NAME
        settings.voice == ReadAloud.Choice.Natural -> "${NaturalVoice.NAME}, not downloaded"
        else -> "Phone voice"
    }
    val speed = if (settings.speed == 1.0f) "normal speed" else "${settings.speed.toString().removeSuffix(".0")}×"
    return "$voice · $speed"
}

/** Whether a sample of [choice] can play: the natural voice's is bundled, the phone's needs an offline engine. */
internal fun sampleAvailable(choice: ReadAloud.Choice, phone: ReadAloud.PhoneVoice?): Boolean =
    choice == ReadAloud.Choice.Natural || phone is ReadAloud.PhoneVoice.Offline

/** The phone voice in the reading settings, as its engine was found: offline, needing the internet, missing, or still being looked at. */
internal fun phoneVoiceNote(phone: ReadAloud.PhoneVoice?): String = when (phone) {
    is ReadAloud.PhoneVoice.Offline -> "The phone's own voice, installed on this phone. It reads with no signal."
    ReadAloud.PhoneVoice.OnlineOnly -> "This phone's speech voice needs the internet, so answers are not read with it. " +
        "Download ${NaturalVoice.NAME}, or install an offline English voice in Android settings (Text-to-speech output)."
    ReadAloud.PhoneVoice.Missing -> "This phone has no speech voice. Add one in Android settings (Text-to-speech output), or download ${NaturalVoice.NAME}."
    null -> "Checking whether the phone's voice works offline…"
}

/** The phone voice as a choice the first time an answer is listened to. */
internal fun phoneVoiceChoice(phone: ReadAloud.PhoneVoice?): String = when (phone) {
    is ReadAloud.PhoneVoice.Offline -> "Ready now, no download: a voice installed on this phone. Sounds more synthetic."
    ReadAloud.PhoneVoice.OnlineOnly -> "This phone's speech voice needs the internet, so Field Atlas does not use it. " +
        "Use ${NaturalVoice.NAME}, or install an offline English voice in Android settings (Text-to-speech output)."
    ReadAloud.PhoneVoice.Missing -> "This phone has no speech voice. Add one in Android settings (Text-to-speech output), or use ${NaturalVoice.NAME}."
    null -> "Checking whether this phone's voice works offline…"
}

/** Until Supertonic 3 is on the phone: who reads meanwhile, if anyone. */
internal fun untilNaturalNote(phone: ReadAloud.PhoneVoice?): String = when (phone) {
    ReadAloud.PhoneVoice.OnlineOnly -> "${NaturalVoice.NAME} is not on this phone yet, and the phone's voice needs the internet, " +
        "so answers are read aloud once it is downloaded."
    ReadAloud.PhoneVoice.Missing -> "${NaturalVoice.NAME} is not on this phone yet, and the phone has no speech voice, " +
        "so answers are read aloud once it is downloaded."
    else -> "${NaturalVoice.NAME} is not on this phone yet; the phone's own voice reads until it is."
}

/** The sentence after the download offer: the phone's voice fills in only where it works offline. */
private fun meanwhile(phone: ReadAloud.PhoneVoice?): String = when (phone) {
    ReadAloud.PhoneVoice.OnlineOnly, ReadAloud.PhoneVoice.Missing -> ""
    else -> " The phone's voice reads until it is ready."
}
