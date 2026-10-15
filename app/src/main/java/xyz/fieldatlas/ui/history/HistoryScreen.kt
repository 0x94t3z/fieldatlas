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
    val shown = remember(records, query) { records.filter { historyMatches(it, query) } }
    // The first question of each topic, named above its follow-ups.
    val topicRoots = remember(records) {
        records.filter { it.topic != null }.groupBy { it.topic }.filterValues { it.size > 1 }
            .mapValues { (_, turns) -> turns.minBy(AnswerRecord::createdAtEpochMs) }
    }
    val days = remember(shown) {
        shown.groupBy { Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate() }.toList()
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
        days.forEach { (day, dayRecords) ->
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
            dayRecords.forEach { record ->
                item(key = record.createdAtEpochMs) {
                    val root = topicRoots[record.topic]?.takeIf { it.createdAtEpochMs != record.createdAtEpochMs }
                    HistoryCard(record, timeFormatter.format(Date(record.createdAtEpochMs)), onAskAgain, followUpOf = root?.question,
                        onDelete = { onDelete(record.createdAtEpochMs) }) { index -> savedSource = record to index }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    record: AnswerRecord,
    time: String,
    onAskAgain: (String) -> Unit,
    followUpOf: String? = null,
    onDelete: () -> Unit,
    onOpenSource: (Int) -> Unit,
) {
    var expanded by rememberSaveable(record.createdAtEpochMs) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(record.createdAtEpochMs) { mutableStateOf(false) }
    val answerBlocks = remember(record.answer) { parseAnswerMarkdown(record.answer) }
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
            followUpOf?.let {
                Text("Follow-up to · $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                Text(record.question, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
                FieldAtlasExpandIcon(expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$time · " + when (record.sources.size) {
                    0 -> "No sources"
                    1 -> "1 source"
                    else -> "${record.sources.size} sources"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                AnswerMarkdownRenderer(
                    blocks = answerBlocks,
                    sourceCount = record.evidence.size,
                    onCitation = { if (it in record.evidence.indices) onOpenSource(it) },
                    modifier = Modifier.fillMaxWidth(),
                    bodyStyle = FieldAtlasReadingStyle,
                    sourceTitles = record.evidence.map { it.title },
                )
            } else {
                Text(
                    answerPreview,
                    style = FieldAtlasReadingStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded && record.sources.isNotEmpty()) {
                Text(if (record.evidence.isEmpty()) "Saved source titles" else "Saved sources", style = MaterialTheme.typography.titleSmall)
                record.sources.forEachIndexed { index, source ->
                    if (index < record.evidence.size) SavedSourceRow(index + 1, source,
                        xyz.fieldatlas.ui.sourcePresentation(record.evidence[index]).isAttachment) { onOpenSource(index) }
                    else Text("• $source", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                val context = androidx.compose.ui.platform.LocalContext.current
                HistoryActions(onAskAgain = { onAskAgain(record.readAs ?: record.question) },
                    onShare = {
                        xyz.fieldatlas.ui.research.shareAnswer(context, record.question,
                            xyz.fieldatlas.ui.research.answerWithSources(record.question, record.answer, record.evidence))
                    }, onDelete = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        xyz.fieldatlas.ui.theme.FieldAtlasDeleteDialog(
            title = "Delete this answer?",
            message = "It is removed from History on this phone. Your collections are not changed.",
            onKeep = { confirmDelete = false },
            onDelete = { confirmDelete = false; onDelete() },
        )
    }
}

/** A clear repeat action plus quiet utilities; large text never shrinks the tap targets. */
@Composable
internal fun HistoryActions(onAskAgain: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f
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
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.End)) { utilities() }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
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
