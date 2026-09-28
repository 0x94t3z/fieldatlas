package xyz.fieldatlas.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import xyz.fieldatlas.R
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.markdown.plainText
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasPageHeader
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

/**
 * The "History" tab: every answer the app has produced, newest first. Tapping a card expands
 * the full answer and its source titles; the list itself stays scannable (question + preview).
 */
@Composable
fun HistoryScreen(records: List<AnswerRecord>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        FieldAtlasPageHeader(
            title = stringResource(R.string.history_title),
            subtitle = "Questions and answers saved on this device.",
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
        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(records, key = { record -> record.createdAtEpochMs }) { record ->
                var expanded by rememberSaveable(record.createdAtEpochMs) { mutableStateOf(false) }
                val answerBlocks = remember(record.answer) { parseAnswerMarkdown(record.answer) }
                val answerPreview = remember(answerBlocks) {
                    answerBlocks.joinToString("\n") { block -> block.plainText() }
                        .replace(Regex("\\[S\\d+]", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("[ \\t]+"), " ")
                        .trim()
                }
                FieldAtlasCard(Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    contentPadding = 14.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(record.question, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis)
                            Icon(
                                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (expanded) "Collapse answer" else "Expand answer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(FieldAtlasIcons.History, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 1.dp).size(14.dp))
                            Text(
                                formatter.format(Date(record.createdAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("· ${record.sources.size} sources", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (expanded) {
                            AnswerMarkdownRenderer(
                                blocks = answerBlocks,
                                sourceCount = 0,
                                onCitation = {},
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            Text(
                                answerPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (expanded && record.sources.isNotEmpty()) Text(
                            record.sources.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
