package xyz.fieldatlas.ui.research

import xyz.fieldatlas.ui.sharedQuestionBox
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
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.FollowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
    /** An answer is being researched on this page: the button is Stop, and a follow-up can be drafted but not sent. */
    running: Boolean = false,
    onStop: () -> Unit = {},
) {
    // Turning the phone rebuilds the screen around the same answer; only really leaving it drops a follow-up recording.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (context.findActivity()?.isChangingConfigurations != true) onLeave() }
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val mine = voice.forFollowUp
    val recording = mine && voice.phase == VoicePhase.Recording
    val busy = mine && (voice.phase == VoicePhase.Starting || voice.phase == VoicePhase.Processing)
    val canSend = draft.isNotBlank() && attachmentsReady && !recording && !busy && !running
    val send = { if (canSend) onSend(draft.trim()) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(FieldAtlasIcons.History, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (running) buildAnnotatedString {
                        append(if (draft.isBlank()) "Working on this answer · Stop ends it" else "Send opens when this answer is ready")
                    } else buildAnnotatedString {
                        append("Follows ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(follows) }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                NewTopicChip(onNewTopic)
            }
            if (recording) Row(Modifier.padding(start = 4.dp, bottom = 2.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingDot()
                Text("Listening on this phone", style = MaterialTheme.typography.labelLarge)
                // At large text the hint takes what width is left and wraps by words, not letter by letter.
                Text(if (voice.liveText.isBlank()) "· start speaking" else "· tap stop when done",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false))
            }
            Surface(
                modifier = Modifier.sharedQuestionBox(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (draft.isBlank() && !recording) 1.dp else 1.5.dp,
                    if (draft.isBlank() && !recording) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Only newly added files belong in the draft. Sent files are shown with their question.
                    if (attachments.isNotEmpty()) Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                        AttachmentRows(attachments, enabled = !recording && !running, remove = onRemoveAttachment, retry = onRetryAttachment)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sheetOpen = true }, enabled = canAddAttachment && !recording) {
                            Icon(FieldAtlasIcons.Plus, "Add a file to the follow-up", Modifier.size(20.dp))
                        }
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            // A slim box under the answer: medium text, so the placeholder does not compete with the answer.
                            if (draft.isEmpty()) Text(when {
                                recording -> "Speak your follow-up"
                                running -> "Type your follow-up now"
                                else -> "Ask a follow-up"
                            },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicTextField(
                                value = draft, onValueChange = onDraftChange,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                readOnly = recording,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Follow-up question" },
                            )
                        }
                        if (recording) VoiceLevelMeter(voice.level, Modifier.padding(end = 6.dp))
                        // While an answer is researched the button stops it; the microphone is hidden so it means one thing.
                        if (running) IconButton(onClick = onStop) {
                            Surface(Modifier.size(40.dp), shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(androidx.compose.material.icons.Icons.Filled.Stop, "Stop research", Modifier.size(20.dp))
                                }
                            }
                        }
                        // Typed text turns the microphone into Send, as in a message box; while recording it is Stop.
                        val showSend = draft.isNotBlank() && !recording && !busy
                        if (!running) IconButton(onClick = if (showSend) send else onMic,
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
                                        busy -> androidx.compose.material3.CircularProgressIndicator(
                                            Modifier.size(20.dp).semantics {
                                                contentDescription = if (voice.phase == VoicePhase.Starting) "Preparing offline voice" else "Transcribing"
                                            }, strokeWidth = 2.dp)
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

/**
 * The earlier questions of the topic, in one card above the answer that followed them: a short row each, opened in
 * place to its answer and its source. Past three, the middle ones fold into one row, so the newest answer stays in view.
 */
@Composable
internal fun EarlierTopicList(turns: List<FollowUp.Turn>, onOpenSource: (List<Evidence>, Int) -> Unit) {
    if (turns.isEmpty()) return
    var open by rememberSaveable(turns.size) { mutableStateOf(-1) }
    var showMiddle by rememberSaveable(turns.size) { mutableStateOf(false) }
    val folded = turns.size > MAX_EARLIER_ROWS && !showMiddle
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(FieldAtlasIcons.History, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Earlier in this topic · ${turns.size}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
                val shown = if (folded) listOf(0, turns.lastIndex) else turns.indices.toList()
                shown.forEachIndexed { position, index ->
                    if (position > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    if (folded && position == 1) {
                        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Show the earlier questions") { showMiddle = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).heightIn(min = 44.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            FieldAtlasExpandIcon(false, tint = MaterialTheme.colorScheme.primary)
                            Text("${turns.size - 2} more earlier question" + (if (turns.size - 2 == 1) "" else "s"),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    EarlierTurnRow(index + 1, turns[index], open == index, onToggle = { open = if (open == index) -1 else index }, onOpenSource)
                }
            }
        }
    }
}

private const val MAX_EARLIER_ROWS = 3
/** Past this many characters an opened earlier answer is shown in part, with Show all. */
private const val LONG_EARLIER_ANSWER = 320

@Composable
private fun EarlierTurnRow(number: Int, turn: FollowUp.Turn, expanded: Boolean, onToggle: () -> Unit,
                           onOpenSource: (List<Evidence>, Int) -> Unit) {
    val blocks = remember(turn.answer) { parseAnswerMarkdown(turn.answer) }
    val cited = remember(turn.answer, turn.sources) {
        xyz.fieldatlas.research.AnswerText.citationAudit(turn.answer, turn.sources.size).citedSourceIds
            .mapNotNull { it.removePrefix("S").toIntOrNull() }.sorted()
    }
    val meta = listOfNotNull(
        cited.size.takeIf { it > 0 }?.let { if (it == 1) "1 cited source" else "$it cited sources" },
        turn.millis?.let { if (it < 1_000) "under 1s" else formatResearchElapsed(it) },
    ).joinToString(" · ").ifBlank { if (expanded) "" else "Tap to see this answer" }
    var showAll by rememberSaveable(turn.readAs) { mutableStateOf(false) }
    val long = turn.answer.length > LONG_EARLIER_ANSWER
    Column(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Fold answer" else "Show answer", onClick = onToggle)
            .heightIn(min = 52.dp).padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(28.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.labelMedium) }
            }
            Column(Modifier.weight(1f)) {
                Text(turn.asked, style = MaterialTheme.typography.titleSmall, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FieldAtlasExpandIcon(expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = 48.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val capped = long && !showAll
            Box(Modifier.fillMaxWidth().then(if (capped) Modifier.heightIn(max = 140.dp).clip(RoundedCornerShape(0.dp)) else Modifier)) {
                // Citations stay plain here: the source line below says where this earlier answer came from.
                AnswerMarkdownRenderer(blocks = blocks, sourceCount = 0, onCitation = {}, modifier = Modifier.fillMaxWidth(),
                    bodyStyle = FieldAtlasReadingStyle)
                if (capped) Box(Modifier.matchParentSize()) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp).background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                }
            }
            if (long) Text(if (showAll) "Show less" else "Show all", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { showAll = !showAll }
                    .padding(vertical = 4.dp))
            cited.firstOrNull()?.let { number -> turn.sources.getOrNull(number - 1)?.let { source ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open source") { onOpenSource(turn.sources, number - 1) }
                    .heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(20.dp), shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.labelSmall) }
                    }
                    Text(source.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Icon(FieldAtlasIcons.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            } }
        }
    }
}

/** Starts a topic of its own: small, beside what the next question follows. */
@Composable
private fun NewTopicChip(onClick: () -> Unit) {
    Box(Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "New topic", onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = 30.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp)).padding(start = 8.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("New topic", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
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

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
