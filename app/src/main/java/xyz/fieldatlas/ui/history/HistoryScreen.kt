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
    val days = remember(records) {
        records.groupBy { Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate() }.toList()
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this answer?") },
            text = { Text("It is removed from History on this phone. Your collections are not changed.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
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
