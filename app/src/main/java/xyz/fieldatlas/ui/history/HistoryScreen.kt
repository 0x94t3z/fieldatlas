package xyz.fieldatlas.ui.history

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import xyz.fieldatlas.R
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.research.answerCardPreview
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasExpandIcon
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
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
    val days = remember(shown) {
        shown.groupBy { Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate() }.toList()
    }
    // The header scrolls with the list so long histories are not clipped under a fixed title.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FieldAtlasPageHeader(
                title = stringResource(R.string.history_title),
                subtitle = "Past questions and answers, kept on this phone.",
            )
        }
        // Search earns its space once History is longer than a screen.
        if (records.size >= SEARCH_THRESHOLD) item(key = "search") {
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
                    HistoryCard(record, timeFormatter.format(Date(record.createdAtEpochMs)), onAskAgain,
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
        .clickable(onClickLabel = if (expanded) "Collapse answer" else "Expand answer") { expanded = !expanded },
        contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                Text(record.question, style = MaterialTheme.typography.titleMedium,
                    fontFamily = FieldAtlasEditorial,
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
                )
            } else {
                Text(
                    answerPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded && record.sources.isNotEmpty()) {
                Text(if (record.evidence.isEmpty()) "Saved source titles" else "Saved sources", style = MaterialTheme.typography.titleSmall)
                record.sources.forEachIndexed { index, source ->
                    if (index < record.evidence.size) SavedSourceRow(index + 1, source) { onOpenSource(index) }
                    else Text("• $source", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(shape = FieldAtlasButtonShape,
                        onClick = { onAskAgain(record.question) },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Ask again", modifier = Modifier.padding(start = 6.dp))
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.material3.OutlinedIconButton(
                        onClick = {
                            xyz.fieldatlas.ui.research.shareAnswer(context, record.question,
                                xyz.fieldatlas.ui.research.answerWithSources(record.question, record.answer, record.evidence))
                        },
                        modifier = Modifier.size(44.dp),
                        shape = FieldAtlasButtonShape,
                    ) {
                        Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Share, contentDescription = "Share answer",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                    androidx.compose.material3.OutlinedIconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.size(44.dp),
                        shape = FieldAtlasButtonShape,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete from History",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
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

@Composable
private fun SavedSourceRow(number: Int, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Preview source $number", onClick = onClick)
            .heightIn(min = 44.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(Modifier.size(30.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontFamily = FieldAtlasEditorial,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    val haystack = (listOf(record.question, record.answer.replace(Regex("\\[S\\d+]"), "")) + record.sources)
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
