package xyz.fieldatlas.ui.research

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.FollowUp
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.theme.FieldAtlasExpandIcon
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasReadingStyle

/**
 * The box under an answer. What is typed, dictated or attached here is read together with the answer above it;
 * the line over the box says which question it follows, and New topic starts afresh. It offers the same three ways
 * in as the question box: typing, the offline microphone, and + for a photo or file.
 */
@Composable
internal fun FollowUpComposer(
    follows: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onNewTopic: () -> Unit,
    voice: VoiceUiState = VoiceUiState(),
    onMic: () -> Unit = {},
    attachments: List<xyz.fieldatlas.attachments.AttachmentUiState> = emptyList(),
    canAddAttachment: Boolean = false,
    attachmentsReady: Boolean = true,
    onCamera: () -> Unit = {},
    onPhotos: () -> Unit = {},
    onFiles: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    attachmentNotice: String? = null,
    onLeave: () -> Unit = {},
) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose(onLeave) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val mine = voice.forFollowUp
    val recording = mine && voice.phase == VoicePhase.Recording
    val busy = mine && (voice.phase == VoicePhase.Starting || voice.phase == VoicePhase.Processing)
    val canSend = draft.isNotBlank() && attachmentsReady && !recording && !busy
    val send = { if (canSend) onSend(draft.trim()) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(FieldAtlasIcons.History, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildAnnotatedString {
                        append("Follows ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(follows) }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onNewTopic) { Text("New topic", style = MaterialTheme.typography.labelLarge) }
            }
            if (recording) Row(Modifier.padding(start = 4.dp, bottom = 2.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingDot()
                Text("Listening on this phone", style = MaterialTheme.typography.labelLarge)
                Text(if (voice.liveText.isBlank()) "· start speaking" else "· tap stop when done",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (draft.isBlank() && !recording) 1.dp else 1.5.dp,
                    if (draft.isBlank() && !recording) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Files used with this follow-up: the topic's earlier files stay attached, new ones join them.
                    if (attachments.isNotEmpty()) Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                        AttachmentRows(attachments, enabled = !recording, remove = onRemoveAttachment, retry = onRetryAttachment)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sheetOpen = true }, enabled = canAddAttachment && !recording) {
                            Icon(FieldAtlasIcons.Plus, "Add a file to the follow-up", Modifier.size(20.dp))
                        }
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            if (draft.isEmpty()) Text(if (recording) "Speak your follow-up" else "Ask a follow-up",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicTextField(
                                value = draft, onValueChange = onDraftChange,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                readOnly = recording,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Follow-up question" },
                            )
                        }
                        if (recording) VoiceLevelMeter(voice.level, Modifier.padding(end = 6.dp))
                        // Typed text turns the microphone into Send, as in a message box; while recording it is Stop.
                        val showSend = draft.isNotBlank() && !recording && !busy
                        IconButton(onClick = if (showSend) send else onMic,
                            enabled = if (showSend) canSend else voice.phase == VoicePhase.Idle || recording) {
                            Surface(Modifier.size(40.dp), shape = MaterialTheme.shapes.small,
                                color = when {
                                    recording || (showSend && canSend) -> MaterialTheme.colorScheme.primary
                                    showSend -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                contentColor = when {
                                    recording || (showSend && canSend) -> MaterialTheme.colorScheme.onPrimary
                                    showSend -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }) {
                                Box(contentAlignment = Alignment.Center) {
                                    when {
                                        busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        recording -> Icon(androidx.compose.material.icons.Icons.Filled.Stop, "Stop recording", Modifier.size(20.dp))
                                        showSend -> Icon(FieldAtlasIcons.Back, "Ask follow-up", Modifier.size(20.dp).rotate(90f))
                                        else -> Icon(FieldAtlasIcons.Mic, "Speak follow-up", Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (busy) Text(if (voice.phase == VoicePhase.Starting) "Preparing offline voice…" else "Transcribing on this device…",
                Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mine) voice.error?.let { Text(it, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error) }
            attachmentNotice?.let { Text(it, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (sheetOpen && canAddAttachment) {
        AttachmentSheet(
            onDismiss = { sheetOpen = false },
            onCamera = { sheetOpen = false; onCamera() },
            onPhotos = { sheetOpen = false; onPhotos() },
            onFiles = { sheetOpen = false; onFiles() },
            title = "Add to your follow-up",
        )
    }
}

/** An earlier question of the topic, folded to one line above the answer that followed it. */
@Composable
internal fun EarlierTurnCard(number: Int, turn: FollowUp.Turn) {
    var expanded by rememberSaveable(turn.readAs) { mutableStateOf(false) }
    val blocks = remember(turn.answer) { parseAnswerMarkdown(turn.answer) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(onClickLabel = if (expanded) "Fold answer" else "Show answer") { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(30.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.labelLarge) }
                }
                Column(Modifier.weight(1f)) {
                    Text(turn.asked, style = MaterialTheme.typography.titleSmall, maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(if (expanded) "Tap to fold" else "Tap to see this answer", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FieldAtlasExpandIcon(expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                // Citations stay plain here: their sources belong to that earlier answer, not this one.
                AnswerMarkdownRenderer(blocks = blocks, sourceCount = 0, onCitation = {}, modifier = Modifier.fillMaxWidth(),
                    bodyStyle = FieldAtlasReadingStyle)
            }
        }
    }
}

/** The small label over a follow-up's title: its place in the topic. */
@Composable
internal fun FollowUpLabel(number: Int) {
    Text("$number · Follow-up", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
    Spacer(Modifier.size(0.dp))
}
