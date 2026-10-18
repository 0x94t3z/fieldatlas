package xyz.fieldatlas.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

/** What Discover offers, by kind, for its chips. */
enum class DiscoverType(val label: String) { All("All"), Models("Models"), Knowledge("Knowledge"), Voices("Voices") }

/**
 * Finding something in Discover once the catalogue holds more than a screen of models, collections and voices. A
 * query matches when every word of it appears in the name or the description, ignoring case and accents, so "espanol"
 * finds "español" and "spanish voice" finds the Spanish reading voice.
 */
object DiscoverSearch {
    /** Rows a kind shows before "See all", when nothing is typed and every kind is listed. */
    const val ROWS_BEFORE_SEE_ALL = 3

    /** A kind folds to [ROWS_BEFORE_SEE_ALL] rows only past this many offers: today's seven collections all show, so
     * a new edition is never behind "See all"; a catalogue that grows past a screen folds. */
    const val FOLD_OVER = 10

    fun folds(count: Int) = count > FOLD_OVER

    fun matches(query: String, vararg texts: String?): Boolean {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = texts.filterNotNull().joinToString(" ") { fold(it) }
        return words.all { it in haystack }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
}

@Composable
internal fun DiscoverSearchBar(query: String, onQuery: (String) -> Unit, type: DiscoverType, onType: (DiscoverType) -> Unit,
                               counts: Map<DiscoverType, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A slim field, the height of a row of text: the catalogue is the point of the screen, not the search.
        Surface(Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(FieldAtlasIcons.Research, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search models, collections, voices", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    BasicTextField(query, onQuery, Modifier.fillMaxWidth().semantics { contentDescription = "Search Discover" },
                        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                }
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, Modifier.size(40.dp)) {
                    Icon(FieldAtlasIcons.Close, "Clear search", Modifier.size(16.dp))
                }
            }
        }
        // The chips count what the current search finds, so they also say where the matches are.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DiscoverType.entries.forEach { kind ->
                val selected = kind == type
                Surface(
                    onClick = { onType(kind) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("${counts[kind] ?: 0}", style = MaterialTheme.typography.labelSmall,
                            color = (if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/** "See all 8" under a kind's first rows, or "Show fewer" once open. */
@Composable
internal fun DiscoverSeeAll(total: Int, open: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (open) "Show fewer" else "See all $total", Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
        Icon(FieldAtlasIcons.ChevronRight, null, Modifier.size(18.dp).rotate(if (open) -90f else 90f), tint = MaterialTheme.colorScheme.primary)
    }
}

/** Nothing in the catalogue matches: say what was searched, and offer the two ways in that need no catalogue. */
@Composable
internal fun DiscoverNothingFound(query: String, refreshing: Boolean, onRefresh: () -> Unit, onImport: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nothing called “${query.trim()}” yet", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text("Search covers what the catalogue offers. Refresh to check for new ones, or import a .fapack you already have.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh, enabled = !refreshing, shape = FieldAtlasButtonShape) {
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
                TextButton(onClick = onImport, shape = FieldAtlasButtonShape) {
                    Icon(FieldAtlasIcons.Plus, null, Modifier.size(18.dp)); Text("Import .fapack", Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
