package xyz.fieldatlas.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import xyz.fieldatlas.ui.theme.FieldAtlasCard

/**
 * The "History" tab: every answer the app has produced, newest first. Tapping a card expands
 * the full answer and its source titles; the list itself stays scannable (question + preview).
 */
@Composable
fun HistoryScreen(records: List<AnswerRecord>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        if (records.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
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
                FieldAtlasCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Column {
                        Text(record.question, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatter.format(Date(record.createdAtEpochMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            record.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            if (expanded && record.sources.isNotEmpty()) {
                                "${record.sources.size} sources: " + record.sources.joinToString(" · ")
                            } else {
                                "${record.sources.size} sources"
                            },
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
