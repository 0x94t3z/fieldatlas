package xyz.fieldatlas.ui.research

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.TileTone

/** One listing of a place lookup, drawn as a card instead of a line of prose. */
internal data class PlaceCardModel(
    val sourceIndex: Int,
    val name: String,
    val badge: String?,
    val badgePositive: Boolean,
    val summary: String?,
    val hours: String,
    val checked: String,
    val distance: String?,
    val direction: String?,
    val eat: Boolean,
)

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
    val street = fields["Address"]?.substringBefore(',')?.trim()
    val distance = distanceText.find(line)
    return PlaceCardModel(
        sourceIndex = index,
        name = fields.getValue("Place"),
        badge = badge,
        badgePositive = vegan == "fully vegan",
        summary = listOfNotNull(kind, street).joinToString(" · ").takeIf(String::isNotBlank),
        hours = fields["Hours in source"] ?: fields["Hours"] ?: "Hours not recorded",
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

@Composable
internal fun PlaceCard(place: PlaceCardModel, onOpen: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${place.name}") { onOpen(place.sourceIndex) },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FieldAtlasIconTile(if (place.eat) FieldAtlasIcons.Food else FieldAtlasIcons.Place, size = 40.dp,
                    tone = if (place.eat) TileTone.Clay else TileTone.Gold)
                place.distance?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(place.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    place.badge?.let { FieldAtlasStatusPill(it, if (place.badgePositive) StatusTone.Positive else StatusTone.Neutral) }
                }
                val meta = listOfNotNull(place.summary, place.direction?.let { "to the $it" })
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(place.hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.checked, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(FieldAtlasIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically))
        }
    }
}
