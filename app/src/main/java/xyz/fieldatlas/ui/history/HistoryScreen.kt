package xyz.fieldatlas.ui.history

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import xyz.fieldatlas.R
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.research.HistoryStorage
import xyz.fieldatlas.text.MarkdownLiteral
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.research.answerCardPreview
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasExpandIcon
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasReadingStyle
import xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel

/**
 * The "History" tab: every answer the app has produced, newest first and grouped by day.
 * Tapping a card expands the full answer and its sources; the list itself stays scannable.
 */
@Composable
fun HistoryScreen(
    records: List<AnswerRecord>,
    onAskAgain: (String) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    storage: HistoryStorage = HistoryStorage(),
    onContinue: (AnswerRecord) -> Unit = {},
) {
    // The same quick look as on an answer, over the passages saved with this record.
    var savedSource by remember { mutableStateOf<Pair<AnswerRecord, Int>?>(null) }
    savedSource?.let { (record, index) ->
        val cited = remember(record.answer) {
            Regex("\\[S(\\d+)]").findAll(record.answer).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
        }
        xyz.fieldatlas.ui.research.SourcePreviewSheet(
            sources = record.evidence,
            index = index,
            cited = index + 1 in cited,
            nextIndex = (index + 1).takeIf { it < record.evidence.size },
            onDismiss = { savedSource = null },
            onOpenFull = null,
            onNext = { savedSource = record to it },
        )
    }
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    var query by rememberSaveable { mutableStateOf("") }
    // One card per conversation: a question and its follow-ups, filed under the day of its latest turn.
    val shown = remember(records, query) { historyTopics(records, query) }
    val days = remember(shown) {
        shown.groupBy { Instant.ofEpochMilli(it.last.createdAtEpochMs).atZone(zone).toLocalDate() }.toList()
    }
    // The header scrolls with the list so long histories are not clipped under a fixed title.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = "Your notebook",
                subtitle = "Past questions, kept on this phone.",
            )
        }
        // History keeps every answer; when the phone runs short of space, say so before answers stop being saved.
        if (storage.low) item(key = "storage") { HistoryStorageWarning(storage) }
        // Search earns its space once History is longer than a screen.
        // Kept while a search is typed, or deleting matches could hide every answer with no way to clear it.
        if (records.size >= SEARCH_THRESHOLD || query.isNotEmpty()) item(key = "search") {
            HistorySearchField(query, onQueryChange = { query = it })
        }
        if (records.isNotEmpty() && shown.isEmpty()) item(key = "no-match") {
            Text("No saved answers match “${query.trim()}”.", Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (records.isEmpty()) {
            item {
                FieldAtlasCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("No research yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        days.forEach { (day, dayTopics) ->
            item(key = "day:$day") {
                FieldAtlasSectionLabel(
                    when (day) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> formatter.format(Date.from(day.atStartOfDay(zone).toInstant()))
                    },
                    Modifier.padding(top = 6.dp),
                )
            }
            dayTopics.forEach { topic ->
                item(key = topic.key) {
                    HistoryCard(topic.turns, timeFormatter.format(Date(topic.last.createdAtEpochMs)), onAskAgain,
                        onContinue = { onContinue(topic.last) },
                        onDelete = { topic.turns.forEach { onDelete(it.createdAtEpochMs) } }) { record, index -> savedSource = record to index }
                }
            }
        }
    }
}

/** A question and its follow-ups, oldest first: one card in History. */
internal class HistoryTopic(val turns: List<AnswerRecord>) {
    val first: AnswerRecord get() = turns.first()
    val last: AnswerRecord get() = turns.last()
    val key: Long get() = first.topicKey
}

/** Every conversation with a turn matching [query], the one with the latest turn first. */
internal fun historyTopics(records: List<AnswerRecord>, query: String): List<HistoryTopic> =
    records.groupBy { it.topicKey }.values
        .map { turns -> HistoryTopic(turns.sortedBy(AnswerRecord::createdAtEpochMs)) }
        .filter { topic -> topic.turns.any { historyMatches(it, query) } }
        .sortedByDescending { it.last.createdAtEpochMs }

/** The meta line under a card's question: its follow-ups, or the sources of a single answer. */
internal fun historyMeta(time: String, turns: List<AnswerRecord>): String {
    val followUps = turns.size - 1
    return "$time · " + when {
        followUps == 1 -> "1 follow-up"
        followUps > 1 -> "$followUps follow-ups"
        else -> when (turns.first().sources.size) {
            0 -> "No sources"
            1 -> "1 source"
            else -> "${turns.first().sources.size} sources"
        }
    }
}

@Composable
private fun HistoryCard(
    turns: List<AnswerRecord>,
    time: String,
    onAskAgain: (String) -> Unit,
    onContinue: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onOpenSource: (AnswerRecord, Int) -> Unit,
) {
    val record = turns.first()
    var expanded by rememberSaveable(record.topicKey) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(record.topicKey) { mutableStateOf(false) }
    // Same excerpt as the Research card: section labels are not content, and an
    // unverified model answer keeps its label even when collapsed.
    val answerPreview = remember(record.answer) { answerCardPreview(record.answer) }
    FieldAtlasCard(Modifier.fillMaxWidth()
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        // The click indication is outside Card's own surface; clip it before clickable
        // so pressed/focused feedback follows the same corners in both states.
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClickLabel = if (expanded) "Collapse answer" else "Expand answer") { expanded = !expanded },
        contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                Text(record.question, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
                FieldAtlasExpandIcon(expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                historyMeta(time, turns),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                // Every turn in the order it was asked, each with its own answer and sources.
                turns.forEachIndexed { turn, entry ->
                    if (turn > 0) {
                        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text("Follow-up", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.question, style = MaterialTheme.typography.titleSmall)
                    }
                    HistoryTurn(entry) { index -> onOpenSource(entry, index) }
                }
            } else {
                Text(
                    answerPreview,
                    style = FieldAtlasReadingStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                val context = androidx.compose.ui.platform.LocalContext.current
                HistoryActions(onAskAgain = { onAskAgain(record.readAs ?: record.question) }, onContinue = onContinue,
                    onShare = {
                        // The whole conversation, each answer with the sources it cites.
                        xyz.fieldatlas.ui.research.shareAnswer(context, record.question, turns.joinToString("\n\n") { entry ->
                            xyz.fieldatlas.ui.research.answerWithSources(entry.question, entry.answer, entry.evidence)
                        })
                    }, onDelete = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        val conversation = turns.size > 1
        xyz.fieldatlas.ui.theme.FieldAtlasDeleteDialog(
            title = if (conversation) "Delete this conversation?" else "Delete this answer?",
            message = if (conversation) "Its ${turns.size} answers are removed from History on this phone. Your collections are not changed."
                else "It is removed from History on this phone. Your collections are not changed.",
            onKeep = { confirmDelete = false },
            onDelete = { confirmDelete = false; onDelete() },
        )
    }
}

/** One answer of a conversation, expanded: the answer itself, then the sources saved with it. */
@Composable
private fun HistoryTurn(record: AnswerRecord, onOpenSource: (Int) -> Unit) {
    val answerBlocks = remember(record.answer) { parseAnswerMarkdown(record.answer) }
    AnswerMarkdownRenderer(
        blocks = answerBlocks,
        sourceCount = record.evidence.size,
        onCitation = { if (it in record.evidence.indices) onOpenSource(it) },
        modifier = Modifier.fillMaxWidth(),
        bodyStyle = FieldAtlasReadingStyle,
        sourceTitles = record.evidence.map { it.title },
    )
    if (record.sources.isNotEmpty()) {
        Text(if (record.evidence.isEmpty()) "Saved source titles" else "Saved sources", style = MaterialTheme.typography.titleSmall)
        record.sources.forEachIndexed { index, source ->
            if (index < record.evidence.size) SavedSourceRow(index + 1, source,
                xyz.fieldatlas.ui.sourcePresentation(record.evidence[index]).isAttachment) { onOpenSource(index) }
            else Text("• $source", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A clear repeat action plus quiet utilities; large text never shrinks the tap targets. */
@Composable
internal fun HistoryActions(onAskAgain: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onContinue: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f
        // Continue picks the topic up where it ended, follow-up box ready; Ask again runs the question afresh.
        val resume: @Composable (Modifier) -> Unit = { modifier ->
            if (onContinue != null) androidx.compose.material3.Button(onClick = onContinue,
                modifier = modifier.heightIn(min = 48.dp), shape = FieldAtlasButtonShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.History, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continue", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        val repeat: @Composable (Modifier) -> Unit = { modifier ->
            androidx.compose.material3.OutlinedButton(onClick = onAskAgain,
                modifier = modifier.heightIn(min = 48.dp), shape = FieldAtlasButtonShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask again", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        val utilities: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryUtilityButton(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Share, "Share answer", onShare)
                HistoryUtilityButton(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Trash, "Delete from History", onDelete)
            }
        }
        // Continue, Ask again, Share and Delete need about 360dp in one line; a 360dp phone's card has 280dp, where
        // Delete was pushed out of reach. There the two actions share a line and the utilities take the next.
        val twoLines = onContinue != null && maxWidth < 360.dp
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            resume(Modifier.fillMaxWidth())
            repeat(Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.End)) { utilities() }
        } else if (twoLines) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resume(Modifier.weight(1f))
                repeat(Modifier.weight(1f))
            }
            Box(Modifier.align(Alignment.End)) { utilities() }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            resume(Modifier)
            repeat(Modifier)
            Spacer(Modifier.weight(1f))
            utilities()
        }
    }
}

@Composable
private fun HistoryUtilityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton(onClick, label, icon,
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SavedSourceRow(number: Int, title: String, isAttachment: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Preview source $number", onClick = onClick)
            .heightIn(min = 52.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(Modifier.size(24.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isAttachment) 1 else 2,
            overflow = if (isAttachment) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis)
        Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

private const val SEARCH_THRESHOLD = 4

/**
 * Every word of the query must appear in the question, the answer or a source title, in any
 * order and case: "snake bite" finds "I got bitten by a snake". Citation marks are ignored.
 */
internal fun historyMatches(record: AnswerRecord, query: String): Boolean {
    val words = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return true
    // Remove genuine app citations while encoded native source markers are still literal.
    // Search is a plain-text destination; otherwise visible words such as "Standard"
    // stored with a protected S could not be found in a quoted answer.
    val answer = MarkdownLiteral.decode(record.answer.replace(Regex("\\[S\\d+]"), ""))
    val haystack = (listOf(record.question, answer) + record.sources)
        .joinToString("\n").lowercase()
    return words.all { it in haystack }
}

@Composable
private fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (focused) 2.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp).heightIn(min = 50.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Research, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Search History" },
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box {
                        if (query.isEmpty()) Text("Search past questions and answers", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onQueryChange("") }) {
                    Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Close, contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Shown at the top of History when free space is low or the last answer could not be saved. */
@Composable
internal fun HistoryStorageWarning(storage: HistoryStorage) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (storage.lastSaveFailed) "The last answer wasn't saved" else "Phone storage is almost full",
                style = MaterialTheme.typography.titleSmall)
            Text(storageWarningText(storage), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun storageWarningText(storage: HistoryStorage): String {
    val free = storage.freeBytes.takeIf { it != Long.MAX_VALUE }?.let { formatFreeSpace(it) }
    return when {
        storage.lastSaveFailed -> "There wasn't room to save it to History. It stays on screen until you close the app. " +
            "Free up space on the phone or delete answers you no longer need."
        free != null -> "$free free. New answers may not be saved to History. Free up space on the phone or delete answers you no longer need."
        else -> "New answers may not be saved to History. Free up space on the phone or delete answers you no longer need."
    }
}

internal fun formatFreeSpace(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024) else "${mb.toInt()} MB"
}
