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

/** The kind of place, shown by its tile: one colour per kind, and red kept for health. */
internal enum class PlaceKind { Cafe, Food, Health, Stay, Other }

/** How the card's status line reads: open, closed, a plain fact, or something the record does not say. */
internal enum class PlaceStatusTone { Open, Closed, Fact, Unknown }

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
    val kind: PlaceKind = PlaceKind.Other,
    /** What it is, once: "Coffee shop", not "Coffee shop cafe". */
    val kindLabel: String? = null,
    val street: String? = null,
    /** The travel guide's own words about it. */
    val quote: String? = null,
    /** Whether the map records an emergency department: "yes", "no", or null when not recorded. */
    val emergency: String? = null,
    val hospital: Boolean = false,
    /** The year the guide last checked the listing, when that is long enough ago to matter. */
    val listedYear: Int? = null,
    val phone: String? = null,
    /** Listed by the travel guide, which gives a description rather than map tags. */
    val guide: Boolean = false,
) {
    /** What it is and where, once each: "Coffee shop · 1-20-12 Jinnan". A guide listing without tags says it is one. */
    val kindLine: String?
        get() = listOfNotNull(kindLabel ?: if (guide) "Travel guide listing" else null, street).joinToString(" · ").takeIf(String::isNotBlank)

    /** The one status line under the name: open now, a hospital's emergency department, or the recorded hours. */
    val status: Pair<String, PlaceStatusTone>
        get() {
            val (text, tone) = when {
                openNow != null -> openNow.replace("Open now until", "Open now · until") to
                    (if (openNow.startsWith("Open")) PlaceStatusTone.Open else PlaceStatusTone.Closed)
                hospital -> when (emergency) {
                    "yes" -> "Emergency department" to PlaceStatusTone.Open
                    "no" -> "No emergency department" to PlaceStatusTone.Fact
                    else -> "Emergency department not recorded" to PlaceStatusTone.Unknown
                }
                hours == "Hours not recorded" -> hours to PlaceStatusTone.Unknown
                else -> hours to PlaceStatusTone.Fact
            }
            return (listedYear?.let { "$text · listed in $it" } ?: text) to tone
        }
}

private val openNowText = Regex("\\b(Open now(?: until \\d{2}:\\d{2})?|Closed now)\\b")
// "290 m north-west away" (near me) and "1.2 km to the north" (where am I).
// The app writes "1.2 km to the north", "220 m west away" or "300 m away"; a guide's own "200 m north of
// the cathedral" is in the quoted description and is not the distance from the phone.
private const val COMPASS = "((?:north|south|east|west)(?:-(?:east|west))?)"
private val distanceText = Regex("(\\d[\\d.,]* (?:m|km)) (?:to the $COMPASS\\b|(?:$COMPASS )?away\\b)")

private val dayNames = mapOf("Mo" to "Mon", "Tu" to "Tue", "We" to "Wed", "Th" to "Thu", "Fr" to "Fri", "Sa" to "Sat", "Su" to "Sun")

/**
 * OpenStreetMap hours as people read them: "Mo-Su 12:00-23:00" becomes "Daily 12:00–23:00",
 * "Mo-Fr 09:00-19:00; Sa 09:00-14:00" becomes "Mon–Fri 09:00–19:00 · Sat 09:00–14:00".
 * Only day names, ranges and separators change; an unfamiliar rule is still shown in full.
 */
private const val OSM_DAYS = "(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)(?:\\s*[-,]\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su|PH))*"
private const val OSM_TIMES = "(?:\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}\\+?(?:\\s*,\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}\\+?)*|off|closed)"
// Only a rule in OpenStreetMap's syntax is rewritten. Guide text ("We open at 9", "50% off
// drinks") is shown as written: its "We" is not Wednesday and its "off" is not closed.
private val osmRule = Regex("^(?:$OSM_DAYS\\s*)?$OSM_TIMES(?:\\s*,\\s*$OSM_DAYS\\s*$OSM_TIMES)*$")

internal fun readableHours(raw: String): String {
    if (raw.trim() == "24/7") return "Open 24 hours"
    return raw.trim().split(Regex("\\s*;\\s*")).filter(String::isNotBlank).joinToString(" · ") { rule ->
        if (!osmRule.matches(rule)) return@joinToString rule.replace(Regex("(?<=\\d{2}:\\d{2})-(?=\\d{2}:\\d{2})"), "–")
        rule.replace(Regex("\\bMo-Su\\b"), "Daily")
            .replace(Regex("\\b(Mo|Tu|We|Th|Fr|Sa|Su)\\b")) { dayNames.getValue(it.value) }
            .replace(Regex("\\bPH\\b"), "holidays")
            .replace(Regex("\\boff\\b"), "closed")
            .replace(Regex("(?<=[A-Za-z]{3})-(?=[A-Z][a-z]{2})"), "–")
            .replace(Regex("(?<=\\d{2}:\\d{2})-(?=\\d{2}:\\d{2})"), "–")
            .replace(",", ", ")
            .replace(Regex("\\s{2,}"), " ")
    }
}

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

// A kind word in the cuisine already says what the place is: "coffee shop" needs no "cafe" after it.
private val placeNoun = Regex("(?i)\\b(?:shop|caf[eé]|bar|baker(?:y|ies)|house|restaurant|pub|parlou?r|stand|stall|kitchen|diner|grill)s?\\b")
// A guide listing checked this many years ago or more says so: a café listed in 2015 may have closed.
private const val OLD_LISTING_YEARS = 3

internal fun placeCardModel(index: Int, evidence: Evidence, line: String = "", today: java.time.LocalDate = java.time.LocalDate.now()): PlaceCardModel? {
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
    val cuisine = fields["Cuisine"]?.substringBefore(';')?.substringBefore(',')?.replace('_', ' ')?.trim()?.takeIf(String::isNotBlank)
    val type = fields["Type"]?.replace('_', ' ')?.trim()?.takeIf(String::isNotBlank)
    val kindLabel = listOfNotNull(cuisine, type?.takeUnless { cuisine != null && placeNoun.containsMatchIn(cuisine) })
        .joinToString(" ").replaceFirstChar(Char::uppercaseChar).takeIf(String::isNotBlank)
    // "Hauptstraße 5, 10115 Berlin" shows its street; "1, 110-0005 台東区" (a Japanese block number without a
    // street) drops the lone number, which means nothing without the block it belongs to.
    val street = fields["Address"]?.let { address ->
        val first = address.substringBefore(',').trim()
        when {
            first.any(Char::isLetter) -> first
            ',' in address && first.all { it.isDigit() || it == '-' } -> address.substringAfter(',').trim()
            else -> address.trim()
        }.replace(Regex(" (\\d+[A-Za-z]?)$"), "\u00A0$1")  // "Rotondo 6" never leaves its 6 on a line alone
    }
    val category = fields["Category"]
    val coffee = type?.contains(Regex("(?i)caf[eé]|coffee")) == true || cuisine?.contains(Regex("(?i)coffee|caf[eé]|tea")) == true
    val kind = when {
        category == "Health" -> PlaceKind.Health
        category in setOf("Eat", "Drink") && (coffee || category == "Drink" && type == null) -> PlaceKind.Cafe
        category in setOf("Eat", "Drink") -> PlaceKind.Food
        category == "Sleep" -> PlaceKind.Stay
        else -> PlaceKind.Other
    }
    val checkedDate = fields["Listing last checked"]
    val listedYear = checkedDate?.take(4)?.toIntOrNull()?.takeIf { it <= today.year - OLD_LISTING_YEARS }
    val distance = distanceText.find(line.substringBefore('“'))
    return PlaceCardModel(
        sourceIndex = index,
        name = fields.getValue("Place"),
        badge = badge,
        badgePositive = vegan == "fully vegan",
        summary = listOfNotNull(kindLabel ?: fields["Description"]?.take(80), street).joinToString(" · ").takeIf(String::isNotBlank),
        hours = (fields["Hours in source"] ?: fields["Hours"])?.let(::readableHours) ?: "Hours not recorded",
        openNow = openNowText.find(line)?.value,
        checked = checkedDate?.let { "Checked $it" } ?: "No check date",
        distance = distance?.groupValues?.get(1),
        direction = distance?.let { d -> d.groupValues[2].ifEmpty { d.groupValues[3] } }?.takeIf(String::isNotEmpty),
        eat = category in setOf("Eat", "Drink"),
        kind = kind,
        kindLabel = kindLabel,
        street = street,
        quote = fields["Description"]?.takeIf(String::isNotBlank),
        guide = evidence.documentId.startsWith("wv"),
        emergency = fields["Emergency department"]?.lowercase(),
        hospital = type.equals("hospital", ignoreCase = true),
        listedYear = listedYear,
        phone = fields["Phone"],
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

private val nearestLead = Regex("^Nearest saved places to (.+?)(?: \\(map data as of ([\\d-]+)\\))?:$")

/**
 * The opening sentence a place list shows: what was found, counted, and where it is measured from ("**2 coffee shops**
 * near Shibuya Crossing in Tokyo, nearest first."). The map's date moves to the footnote. Any other opening (open-now
 * counts, a vegan qualification) is shown as written.
 */
internal fun placeLead(lead: String, question: String, count: Int): String {
    val match = nearestLead.matchEntire(lead.trim()) ?: return lead
    val where = match.groupValues[1]
    if (" that " in where) return lead
    val counted = xyz.fieldatlas.research.VenueLookup.placeCount(question, count) ?: "$count ${if (count == 1) "place" else "places"}"
    val near = if (where == "your current location") "near you" else "near $where"
    return "**$counted** $near, nearest first."
}

/** "2026-10-02" from the opening's "(map data as of 2026-10-02)", shown as "2 Oct 2026" in the footnote. */
internal fun placeMapDate(intro: String): String? = Regex("\\(map data as of (\\d{4})-(\\d{2})-(\\d{2})\\)").find(intro)?.let { m ->
    val (y, mo, d) = m.destructured
    val month = java.time.Month.of(mo.toInt()).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    "${d.toInt()} $month $y"
}

/**
 * What the note above a list of hospitals adds: the nearest one recorded with an emergency department, which may be
 * last in a list ordered by distance (its card's source, so the line opens it), or that none of them is.
 */
internal fun hospitalEmergencyLine(cards: List<PlaceCardModel>): Pair<String, Int?>? {
    if (cards.isEmpty() || !cards.all { it.hospital }) return null
    val nearest = cards.firstOrNull { it.emergency == "yes" }
        ?: return "None of these is recorded with an emergency department." to null
    val where = listOfNotNull(nearest.distance, nearest.direction).joinToString(" ")
    return "Nearest emergency department: ${nearest.name}" + (if (where.isNotBlank()) ", $where" else "") to nearest.sourceIndex
}

/** The health line the lookup writes after a list of health places; shown as a note above the list instead. */
internal const val PLACE_EMERGENCY_LINE = "In an emergency, call the local emergency number first."

/**
 * The footnote under a place list, in one short line: where distances are measured from and how old the map is. The
 * lookup's full wording stays one tap away ("About these places").
 */
internal fun placeFootnote(outro: String, mapDate: String?): String? {
    val from = Regex("Distances are straight-line from (.+?), by").find(outro)?.groupValues?.get(1)
        ?: Regex("Distances are straight-line from (this phone's location)").find(outro)?.groupValues?.get(1)
        ?: return null
    val origin = if (from == "this phone's location") "your location" else from
    return "Straight-line distances from $origin. " + (mapDate?.let { "Map data $it; hours" } ?: "Hours") + " can be out of date."
}

/** The text after a place list: the lookup's notes on distances, hours and data credit. */
internal fun placeOutroText(answer: String): String =
    answer.lines().let { lines -> lines.drop(lines.indexOfLast { it.trimStart().startsWith("- ") } + 1) }.joinToString("\n").trim()

/** The text before an answer's first list item: the opening of an app-written place answer. */
internal fun placeIntroText(answer: String): String =
    answer.lines().takeWhile { !it.trimStart().startsWith("- ") }.joinToString("\n").trim()

/** The tile for a kind of place: gold for food and drink, a red tint for health, sage for places to stay. */
internal fun placeTile(kind: PlaceKind, focused: Boolean = false): Pair<androidx.compose.ui.graphics.vector.ImageVector, TileTone> = when (kind) {
    PlaceKind.Cafe -> FieldAtlasIcons.Cup to TileTone.Gold
    PlaceKind.Food -> FieldAtlasIcons.Food to TileTone.Gold
    PlaceKind.Health -> FieldAtlasIcons.Medical to if (focused) TileTone.HealthSolid else TileTone.Health
    PlaceKind.Stay -> FieldAtlasIcons.Bed to TileTone.Sage
    PlaceKind.Other -> FieldAtlasIcons.Place to TileTone.Paper
}

/** A diet the question asks about: only then does a card show its vegan tag. */
internal val dietAsked = Regex("(?i)\\b(vegan|vegetarian|plant[- ]based|halal|kosher|gluten)\\b")

/**
 * One place: name, with how far and which way at the right; what it is and where; one status line; the guide's words.
 * The whole card opens the place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaceCard(place: PlaceCardModel, onOpen: (Int) -> Unit, showDiet: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${place.name}") { onOpen(place.sourceIndex) },
        color = colors.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val (icon, tone) = placeTile(place.kind)
                FieldAtlasIconTile(icon, size = 40.dp, tone = tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    place.kindLine?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                // How far and which way belong together, where the eye ends the line.
                place.distance?.let { distance ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(distance, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        place.direction?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1) }
                    }
                }
            }
            Column(Modifier.padding(start = 52.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val (text, tone) = place.status
                PlaceStatusLine(text, tone)
                if (showDiet) place.badge?.let {
                    PlaceChip(it, if (place.badgePositive) colors.primaryContainer else colors.surfaceVariant)
                }
                place.quote?.let {
                    Text("“$it”", style = MaterialTheme.typography.bodySmall.copy(fontFamily = xyz.fieldatlas.ui.theme.FieldAtlasEditorial,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A dot and words, so the state never depends on colour alone. Red is not used here: it means health. */
@Composable
private fun PlaceStatusLine(text: String, tone: PlaceStatusTone) {
    val colors = MaterialTheme.colorScheme
    val (dot, ink) = when (tone) {
        PlaceStatusTone.Open -> colors.primary to colors.onPrimaryContainer
        PlaceStatusTone.Closed -> colors.secondary to colors.onSurface
        PlaceStatusTone.Fact -> colors.outline to colors.onSurface
        PlaceStatusTone.Unknown -> colors.outlineVariant to colors.onSurfaceVariant
    }
    // The dot sits by the first line when the hours run to two.
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
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
