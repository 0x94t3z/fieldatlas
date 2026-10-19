package xyz.fieldatlas.ui.history

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import xyz.fieldatlas.R
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.research.HistoryStorage
import xyz.fieldatlas.text.MarkdownLiteral
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel

/**
 * The "History" tab: a notebook of every topic, newest first and grouped by day. Tapping a topic opens it on the
 * answer page where it was left, ready for a follow-up. Pinned topics stay on top; the topics can be filtered by kind
 * and searched, and a search says where it matched. ⋯ pins, shares, copies or deletes; a swipe deletes too, with Undo
 * for a few seconds instead of a question. A long press selects several.
 */
@Composable
fun HistoryScreen(
    records: List<AnswerRecord>,
    onDelete: (Long) -> Unit = {},
    storage: HistoryStorage = HistoryStorage(),
    onContinue: (AnswerRecord) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val pinStore = remember(context) { HistoryPins(context) }
    var pins by remember { mutableStateOf(pinStore.load()) }
    fun setPinned(key: Long, on: Boolean) { pins = if (on) pins + key else pins - key; pinStore.save(pins) }
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    // Deleted but not yet gone: Undo brings these back; after a few seconds, or on leaving History, they are removed.
    var pending by remember { mutableStateOf(emptyList<HistoryTopic>()) }
    val currentDelete by rememberUpdatedState(onDelete)
    fun commit(topics: List<HistoryTopic>) {
        topics.forEach { topic -> topic.turns.forEach { currentDelete(it.createdAtEpochMs) }; if (topic.key in pins) setPinned(topic.key, false) }
    }
    fun delete(topics: List<HistoryTopic>) {
        commit(pending)
        pending = topics
        selected = emptySet()
    }
    // Someone who asked Android for more time to act gets it here too.
    val undoMs = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(UNDO_MS, containsIcons = true, containsText = true, containsControls = true) ?: UNDO_MS
    LaunchedEffect(pending) {
        val waiting = pending
        if (waiting.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(undoMs)
        if (pending === waiting) { commit(waiting); pending = emptyList() }
    }
    val latestPending by rememberUpdatedState(pending)
    DisposableEffect(Unit) { onDispose { commit(latestPending) } }

    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val pendingKeys = pending.map { it.key }.toSet()
    val allTopics = remember(records) { historyTopics(records, "") }.filter { it.key !in pendingKeys }
    val kinds = remember(records) { historyTopics(records, "").associate { it.key to topicKind(it) } }
    val matching = remember(records, query) { historyTopics(records, query) }.filter { it.key !in pendingKeys }
    val shown = matching.filter { topic -> kindFilter == null || kinds[topic.key]?.label == kindFilter }
    val pinned = if (query.isBlank()) shown.filter { it.key in pins } else emptyList()
    val days = remember(shown, pinned) {
        shown.filterNot { it in pinned }.groupBy { Instant.ofEpochMilli(it.last.createdAtEpochMs).atZone(zone).toLocalDate() }.toList()
    }
    fun conversation(topic: HistoryTopic) = topic.turns.joinToString("\n\n") { entry ->
        xyz.fieldatlas.ui.research.answerWithSources(entry.question, entry.answer, entry.evidence)
    }
    fun time(topic: HistoryTopic): String {
        val day = Instant.ofEpochMilli(topic.last.createdAtEpochMs).atZone(zone).toLocalDate()
        return if (day == today || day == today.minusDays(1)) timeFormatter.format(Date(topic.last.createdAtEpochMs))
        else formatter.format(Date(topic.last.createdAtEpochMs))
    }

    @Composable
    fun Entry(topic: HistoryTopic) {
        TopicEntry(
            topic = topic, kind = kinds[topic.key] ?: TopicKind.Knowledge, time = time(topic), pinned = topic.key in pins,
            match = if (query.isBlank()) null else historyMatch(topic, query),
            selecting = selecting, isSelected = topic.key in selected,
            onOpen = { onContinue(topic.last) },
            onToggleSelect = { selected = if (topic.key in selected) selected - topic.key else selected + topic.key },
            onPin = { setPinned(topic.key, topic.key !in pins) },
            onShare = { xyz.fieldatlas.ui.research.shareAnswer(context, topic.first.question, conversation(topic)) },
            onCopy = {
                clipboard.setText(AnnotatedString(conversation(topic)))
                Toast.makeText(context, "Copied as text", Toast.LENGTH_SHORT).show()
            },
            onDelete = { delete(listOf(topic)) },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = if (selecting || pending.isNotEmpty()) 96.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                if (selecting) SelectionBar(selected.size, onClose = { selected = emptySet() },
                    onSelectAll = { selected = shown.map { it.key }.toSet() })
                else NotebookHeader(
                    if (records.isEmpty()) "Past questions, kept on this phone."
                    else "${allTopics.size} topic${if (allTopics.size == 1) "" else "s"} · " +
                        "${records.size - pending.sumOf { it.turns.size }} answers · on this phone only",
                )
            }
            // History keeps every answer; when the phone runs short of space, say so before answers stop being saved.
            if (storage.low) item(key = "storage") { HistoryStorageWarning(storage) }
            // Search earns its space once History is longer than a screen.
            // Kept while a search is typed, or deleting matches could hide every answer with no way to clear it.
            if (records.size >= SEARCH_THRESHOLD || query.isNotEmpty()) item(key = "search") {
                HistorySearchField(query, onQueryChange = { query = it })
            }
            val kindCounts = allTopics.groupingBy { kinds[it.key] }.eachCount()
            if (allTopics.size >= SEARCH_THRESHOLD && kindCounts.size > 1) item(key = "kinds") {
                KindFilters(allTopics.size, TopicKind.entries.mapNotNull { kind -> kindCounts[kind]?.let { kind.label to it } },
                    kindFilter, onSelect = { kindFilter = it })
            }
            if (query.isNotBlank() && shown.isNotEmpty()) item(key = "matches") {
                Text("${shown.size} topic${if (shown.size == 1) "" else "s"} match", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isNotEmpty() && shown.isEmpty()) item(key = "no-match") {
                Text(if (query.isNotBlank()) "No saved answers match “${query.trim()}”." else "Nothing here yet.", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isEmpty()) item {
                FieldAtlasCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("No research yet", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinned") { PinnedLabel(pinned.size, Modifier.padding(top = 4.dp)) }
                pinned.forEach { topic -> item(key = "p:${topic.key}") { Entry(topic) } }
            }
            days.forEach { (day, dayTopics) ->
                item(key = "day:$day") {
                    FieldAtlasSectionLabel(
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> formatter.format(Date.from(day.atStartOfDay(zone).toInstant()))
                        },
                        Modifier.padding(top = 4.dp),
                    )
                }
                dayTopics.forEach { topic -> item(key = topic.key) { Entry(topic) } }
            }
        }
        if (selecting) SelectionActions(selected.size, Modifier.align(Alignment.BottomCenter),
            onShare = {
                val chosen = shown.filter { it.key in selected }
                xyz.fieldatlas.ui.research.shareAnswer(context, "Field Atlas notes", chosen.joinToString("\n\n———\n\n") { conversation(it) })
            },
            onDelete = { delete(shown.filter { it.key in selected }) })
        else if (pending.isNotEmpty()) UndoBar(pending, Modifier.align(Alignment.BottomCenter), onUndo = { pending = emptyList() })
    }
}

private const val UNDO_MS = 8_000L

/** The page title, and how much History holds with a lock, since none of it leaves the phone. */
@Composable
private fun NotebookHeader(summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Your notebook", style = xyz.fieldatlas.ui.theme.fieldAtlasPageTitle())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FieldAtlasIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinnedLabel(count: Int, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.material.icons.Icons.Outlined.PushPin, null, tint = muted, modifier = Modifier.size(15.dp))
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Pinned") }
            append(" · $count")
        }, style = MaterialTheme.typography.labelLarge, color = muted)
    }
}

/** One topic: its question, what kind and how much, where a search matched, and a Continue strip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicEntry(
    topic: HistoryTopic, kind: TopicKind, time: String, pinned: Boolean, match: HistoryMatch?,
    selecting: Boolean, isSelected: Boolean,
    onOpen: () -> Unit, onToggleSelect: () -> Unit, onPin: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit,
) {
    val card: @Composable () -> Unit = {
        TopicCard(topic, kind, time, pinned, match, selecting, isSelected, onOpen, onToggleSelect, onPin, onShare, onCopy, onDelete)
    }
    if (selecting) { card(); return }
    // A swipe to the left deletes, as in mail; Undo stays a few seconds, so a slip costs nothing.
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.error)
                .padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(FieldAtlasIcons.Trash, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(22.dp))
                    Text("Delete", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onError)
                }
            }
        },
    ) { card() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicCard(
    topic: HistoryTopic, kind: TopicKind, time: String, pinned: Boolean, match: HistoryMatch?,
    selecting: Boolean, isSelected: Boolean,
    onOpen: () -> Unit, onToggleSelect: () -> Unit, onPin: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) colors.primaryContainer.copy(alpha = 0.35f) else colors.surface,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .semantics { if (selecting) selected = isSelected }
            .combinedClickable(
                onClickLabel = if (selecting) (if (isSelected) "Unselect" else "Select") else "Continue this topic",
                onLongClickLabel = if (selecting) null else "Select",
                onLongClick = if (selecting) null else onToggleSelect,
                onClick = if (selecting) onToggleSelect else onOpen,
            ),
    ) {
        // The strip says what tapping does; a search result shows its match instead.
        val strip = !selecting && match == null
        Column {
            Row(Modifier.padding(start = if (selecting) 12.dp else 16.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selecting) SelectBox(isSelected, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(topic.first.question, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(buildAnnotatedString {
                        if (kind == TopicKind.FirstAid) {
                            withStyle(SpanStyle(color = colors.error, fontWeight = FontWeight.SemiBold)) { append("First aid") }
                            append(" · ")
                        }
                        append(historyMeta(time, topic, kind))
                        if (pinned) {
                            append(" · ")
                            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append("Pinned") }
                        }
                    }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    when {
                        match != null -> {
                            Text(match.where, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            Text(buildAnnotatedString {
                                match.lead?.let { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) { append(it) }; if (match.hit.isNotEmpty()) append(" · ") }
                                append(match.before)
                                withStyle(SpanStyle(background = Color(0xFFF1E2B9), color = Color(0xFF30250C))) { append(match.hit) }
                                append(match.after)
                            }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        !strip && topic.turns.size > 1 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("LATEST", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp), color = colors.onSurfaceVariant)
                            Text(topic.last.question, style = MaterialTheme.typography.bodySmall, color = colors.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!selecting) Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(FieldAtlasIcons.MoreOptions, contentDescription = "More for ${topic.first.question}",
                            tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(16.dp), containerColor = colors.surface) {
                        DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin") },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.PushPin, null, tint = colors.primary) },
                            onClick = { menuOpen = false; onPin() })
                        DropdownMenuItem(text = { Text("Share") },
                            leadingIcon = { Icon(FieldAtlasIcons.Share, null, tint = colors.primary) },
                            onClick = { menuOpen = false; onShare() })
                        DropdownMenuItem(text = { Text("Copy") },
                            leadingIcon = { Icon(FieldAtlasIcons.Copy, null, tint = colors.primary) },
                            onClick = { menuOpen = false; onCopy() })
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colors.outlineVariant.copy(alpha = 0.6f))
                        DropdownMenuItem(text = { Text("Delete", color = colors.error) },
                            leadingIcon = { Icon(FieldAtlasIcons.Trash, null, tint = colors.error) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            if (strip) ContinueStrip()
        }
    }
}

@Composable
private fun ContinueStrip() {
    val colors = MaterialTheme.colorScheme
    // The same warm fill as the collections chip on the Research home, so the strip sits with the card, not apart.
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainerHighest).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Continue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primary)
        Icon(FieldAtlasIcons.ChevronRight, null, tint = colors.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SelectBox(on: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
        .background(if (on) colors.primary else Color.Transparent)
        .border(1.5.dp, if (on) colors.primary else colors.outline, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        if (on) Icon(FieldAtlasIcons.Check, null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** First aid, Travel, Knowledge, Crypto: only the kinds History holds, with how many of each. */
@Composable
private fun KindFilters(total: Int, kinds: List<Pair<String, Int>>, selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterPill("All", total, selected == null) { onSelect(null) }
        kinds.forEach { (label, count) -> FilterPill(label, count, selected == label) { onSelect(if (selected == label) null else label) } }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab, onClick = onClick)
        .semantics { selected = on }, contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) colors.primary else colors.surface)
            .border(1.dp, if (on) colors.primary else colors.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) colors.onPrimary else colors.onSurface)
            Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = (if (on) colors.onPrimary else colors.onSurfaceVariant).copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(FieldAtlasIcons.Close, contentDescription = "Stop selecting") }
        Text("$count selected", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onSelectAll) { Text("Select all") }
    }
}

@Composable
private fun SelectionActions(count: Int, modifier: Modifier, onShare: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().background(colors.background).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)) {
            Icon(FieldAtlasIcons.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share $count")
        }
        androidx.compose.material3.Button(onClick = onDelete, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = colors.error, contentColor = colors.onError)) {
            Icon(FieldAtlasIcons.Trash, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Delete $count")
        }
    }
}

@Composable
private fun UndoBar(pending: List<HistoryTopic>, modifier: Modifier, onUndo: () -> Unit) {
    val answers = pending.sumOf { it.turns.size }
    val what = if (pending.size == 1) "“${pending.first().first.question}”" else "${pending.size} topics"
    Surface(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), shape = RoundedCornerShape(14.dp),
        color = Color(0xFF203D34), contentColor = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(FieldAtlasIcons.Trash, null, Modifier.size(18.dp))
            Text("Deleted $what · $answers answer${if (answers == 1) "" else "s"}", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onUndo) { Text("Undo", color = Color(0xFFBFE3CB), fontWeight = FontWeight.Bold) }
        }
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
                        if (query.isEmpty()) Text("Search questions and answers", style = MaterialTheme.typography.bodyLarge,
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
