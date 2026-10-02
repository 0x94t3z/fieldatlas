package xyz.fieldatlas.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial

/**
 * The "History" tab: every answer the app has produced, newest first. Tapping a card expands
 * the full answer and its source titles; the list itself stays scannable (question + preview).
 */
@Composable
fun HistoryScreen(records: List<AnswerRecord>) {
    var savedSource by remember { mutableStateOf<xyz.fieldatlas.research.Evidence?>(null) }
    savedSource?.let { evidence ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { savedSource = null },
            title = { Text(evidence.title) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(evidence.source, style = MaterialTheme.typography.labelLarge)
                    Text(evidence.text, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { savedSource = null }) { Text("Close") } },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        FieldAtlasPageHeader(
            title = stringResource(R.string.history_title),
            subtitle = "Your past questions and answers, saved on this phone.",
            modifier = Modifier.padding(top = 20.dp),
        )
        if (records.isEmpty()) {
            FieldAtlasCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text("No research yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        val today = LocalDate.now()
        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(records, key = { record -> record.createdAtEpochMs }) { record ->
                var expanded by rememberSaveable(record.createdAtEpochMs) { mutableStateOf(false) }
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
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(record.question, style = MaterialTheme.typography.titleMedium,
                                fontFamily = FieldAtlasEditorial,
                                modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis)
                            Icon(
                                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val date = Date(record.createdAtEpochMs)
                            val day = Instant.ofEpochMilli(record.createdAtEpochMs)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(
                                when (day) {
                                    today -> "Today · ${timeFormatter.format(date)}"
                                    today.minusDays(1) -> "Yesterday · ${timeFormatter.format(date)}"
                                    else -> formatter.format(date)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(when (record.sources.size) {
                                0 -> "No saved sources"
                                1 -> "1 saved source"
                                else -> "${record.sources.size} saved sources"
                            }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (expanded) {
                            AnswerMarkdownRenderer(
                                blocks = answerBlocks,
                                sourceCount = record.evidence.size,
                                onCitation = { savedSource = record.evidence.getOrNull(it) },
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
                                if (index < record.evidence.size) {
                                    androidx.compose.material3.TextButton(onClick = { savedSource = record.evidence[index] }) { Text("${index + 1}. $source") }
                                } else Text("• $source", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
