package xyz.fieldatlas.ui.research

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import xyz.fieldatlas.ui.theme.FieldAtlasChipShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.TileTone

/** One listing of a place lookup, drawn as a card instead of a line of prose. */
internal data class PlaceCardModel(
    val sourceIndex: Int,
    val name: String,
    val badge: String?,
    val badgePositive: Boolean,
    val summary: String?,
    val hours: String,
    /** "Open now until 22:00" or "Closed now" when the answer checked the hours against the clock. */
    val openNow: String? = null,
    val checked: String,
    val distance: String?,
    val direction: String?,
    val eat: Boolean,
)

private val openNowText = Regex("\\b(Open now(?: until \\d{2}:\\d{2})?|Closed now)\\b")
private val distanceText = Regex("(\\d[\\d.,]* (?:m|km)) ((?:north|south|east|west)(?:-(?:east|west))?) away")

/**
 * Cards for an app-written place answer: only when every bullet cites exactly one structured
 * map listing. Anything else (a model answer, a mixed list) keeps the written answer as is.
 */
internal fun placeCardModels(blocks: List<MarkdownBlock>, sources: List<Evidence>): List<PlaceCardModel>? {
    val lists = blocks.filterIsInstance<MarkdownBlock.ListBlock>()
    if (lists.size != 1 || sources.isEmpty()) return null
    return lists.single().items.map { item ->
        val numbers = item.flatMap(::citationsIn).distinct()
        val index = numbers.singleOrNull()?.minus(1) ?: return null
        val evidence = sources.getOrNull(index) ?: return null
        placeCardModel(index, evidence, plainText(item)) ?: return null
    }.takeIf { it.isNotEmpty() }
}

internal fun placeCardModel(index: Int, evidence: Evidence, line: String = ""): PlaceCardModel? {
    if (placeSheetContent(evidence.text) == null) return null
    val fields = evidence.text.lines().mapNotNull { row ->
        val split = row.indexOf(": ")
        if (split <= 0) null else row.substring(0, split).trim() to row.substring(split + 2).trim()
    }.toMap()
    val vegan = fields["Vegan"]
    val badge = when (vegan) {
        "fully vegan" -> "Fully vegan"
        "vegan options" -> "Vegan options"
        "limited vegan options" -> "Some vegan"
        else -> null
    }
    val kind = listOfNotNull(fields["Cuisine"]?.substringBefore(';')?.substringBefore(',')?.trim(), fields["Type"])
        .joinToString(" ").replaceFirstChar(Char::uppercaseChar).takeIf(String::isNotBlank)
        ?: fields["Description"]?.take(80)
    // "Hauptstraße 5, 10115 Berlin" shows its street; "1, 110-0005 台東区" (a Japanese block
    // number without a street) is only useful whole.
    val street = fields["Address"]?.let { address ->
        address.substringBefore(',').trim().takeIf { part -> part.any(Char::isLetter) } ?: address.trim()
    }
    val distance = distanceText.find(line)
    return PlaceCardModel(
        sourceIndex = index,
        name = fields.getValue("Place"),
        badge = badge,
        badgePositive = vegan == "fully vegan",
        summary = listOfNotNull(kind, street).joinToString(" · ").takeIf(String::isNotBlank),
        hours = fields["Hours in source"] ?: fields["Hours"] ?: "Hours not recorded",
        openNow = openNowText.find(line)?.value,
        checked = fields["Listing last checked"]?.let { "Checked $it" } ?: "No check date",
        distance = distance?.groupValues?.get(1),
        direction = distance?.groupValues?.get(2),
        eat = fields["Category"] in setOf("Eat", "Drink"),
    )
}

private fun citationsIn(inline: MarkdownInline): List<Int> = when (inline) {
    is MarkdownInline.Citation -> listOf(inline.number)
    is MarkdownInline.Strong -> inline.content.flatMap(::citationsIn)
    is MarkdownInline.Emphasis -> inline.content.flatMap(::citationsIn)
    is MarkdownInline.Strikethrough -> inline.content.flatMap(::citationsIn)
    is MarkdownInline.Link -> inline.label.flatMap(::citationsIn)
    else -> emptyList()
}

private fun plainText(content: List<MarkdownInline>): String = content.joinToString("") { inline ->
    when (inline) {
        is MarkdownInline.Text -> inline.value
        is MarkdownInline.Code -> inline.value
        is MarkdownInline.Strong -> plainText(inline.content)
        is MarkdownInline.Emphasis -> plainText(inline.content)
        is MarkdownInline.Strikethrough -> plainText(inline.content)
        is MarkdownInline.Link -> plainText(inline.label)
        else -> ""
    }
}

/**
 * A place answer opens with one result sentence and then its qualifications ("assumes the
 * phone is on Berlin time", "hours can be out of date"). The first is the headline; the rest
 * stay on screen in a quieter style rather than being dropped.
 */
internal fun placeIntroParts(intro: String): Pair<String, String?> {
    val text = intro.trim()
    // App-written place text has no exclamation marks, so a full stop or question mark ends a sentence.
    val cut = Regex("(?<=[.?])\\s+(?=[\\p{Lu}\\p{N}“\"])").find(text) ?: return text to null
    return text.substring(0, cut.range.first) to text.substring(cut.range.last + 1).trim().takeIf(String::isNotEmpty)
}

/** The text before an answer's first list item: the opening of an app-written place answer. */
internal fun placeIntroText(answer: String): String =
    answer.lines().takeWhile { !it.trimStart().startsWith("- ") }.joinToString("\n").trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaceCard(place: PlaceCardModel, onOpen: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${place.name}") { onOpen(place.sourceIndex) },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldAtlasIconTile(if (place.eat) FieldAtlasIcons.Food else FieldAtlasIcons.Place, size = 42.dp,
                tone = if (place.eat) TileTone.Clay else TileTone.Gold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                place.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // What decides whether to go, as scannable chips: open now, diet, how far.
                val distance = place.distance?.let { listOfNotNull(it, place.direction).joinToString(" ") }
                if (place.openNow != null || place.badge != null || distance != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        place.openNow?.let { OpenNowChip(it) }
                        place.badge?.let {
                            PlaceChip(it, if (place.badgePositive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant)
                        }
                        distance?.let { PlaceChip(it, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), FieldAtlasIcons.Place) }
                    }
                }
                Text(listOf(place.hours, place.checked).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(FieldAtlasIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun PlaceChip(text: String, container: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(color = container, contentColor = MaterialTheme.colorScheme.onSurface, shape = FieldAtlasChipShape) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(13.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Open or closed by recorded hours: a dot plus words, so the state never depends on colour alone. */
@Composable
private fun OpenNowChip(text: String) {
    val open = text.startsWith("Open")
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (open) colors.primaryContainer else colors.errorContainer.copy(alpha = 0.7f),
        contentColor = if (open) colors.onPrimaryContainer else colors.onErrorContainer,
        shape = FieldAtlasChipShape,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (open) colors.primary else colors.error, CircleShape))
            // "Open until 23:00" is enough on a chip; the answer text keeps the full wording.
            Text(text.replace("Open now until", "Open until"), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
