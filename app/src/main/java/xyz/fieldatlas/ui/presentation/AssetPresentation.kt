package xyz.fieldatlas.ui.presentation

import java.text.NumberFormat
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType

sealed interface SetupNextAction {
    data object ChooseModel : SetupNextAction
    data object OpenResearch : SetupNextAction
    data object Importing : SetupNextAction
}

data class AssetCardModel(
    val title: String,
    val kind: String,
    val size: String,
    val version: String,
    val license: String,
    val coverageLabel: String?,
    val coverageSummary: String?,
    val manifestSha256: String,
)

fun deriveSetupNextAction(packs: List<InstalledAsset>, importing: Boolean): SetupNextAction = when {
    importing -> SetupNextAction.Importing
    packs.none { it.type == PackType.MODEL } -> SetupNextAction.ChooseModel
    else -> SetupNextAction.OpenResearch
}

fun InstalledAsset.toAssetCardModel(): AssetCardModel = AssetCardModel(
    title = when (id) {
        "qwen3.5-2b-q4-k-m" -> "Qwen3.5 2B"
        "qwen3.5-4b-q4-k-m" -> "Qwen3.5 4B"
        "world-knowledge-biology" -> "Biology & longevity"
        "wikivoyage-places" -> "Travel places"
        // Short names, as in the download catalog; the source is in the collection's About sheet.
        // The earlier, smaller encyclopedia; "Encyclopedia" is the one that replaced it.
        "simplewiki" -> "Simple English encyclopedia"
        "wikivoyage-guides" -> "Travel guides"
        "osm-vegan-places" -> "Vegan places"
        "osm-essentials" -> "Essentials"
        "crypto-ethereum" -> "Cryptography & Ethereum"
        "wikipedia-en-vital" -> "Encyclopedia in depth"
        "wikipedia-encyclopedia" -> "Encyclopedia"
        else -> encyclopediaExpansionLabel(id)?.takeIf { type == PackType.KNOWLEDGE } ?: title
    },
    kind = when (type) {
        PackType.MODEL -> "Answer model"
        PackType.KNOWLEDGE -> "Knowledge data"
        PackType.AUDIO -> "Audio model"
    },
    size = formatAssetBytes(installedBytes),
    version = version,
    license = license,
    coverageLabel = null,
    coverageSummary = null,
    manifestSha256 = manifestSha256,
)

private val encyclopediaExpansionId = Regex("wikipedia-en-broad-s([0-9]{5})-([0-9]{4})")

/** Display-only identity: never rename the signed pack or imply subject-specific coverage. */
internal fun encyclopediaExpansionLabel(id: String): String? {
    val match = encyclopediaExpansionId.matchEntire(id) ?: return null
    val part = match.groupValues[2].toInt()
    if (part == 0) return null
    return "Encyclopedia expansion · ${match.groupValues[1].toInt() + 1}.$part"
}

internal fun InstalledAsset.collectionDescription(): String? =
    discovery?.coverageSummary?.takeIf(String::isNotBlank) ?: if (
        type == PackType.KNOWLEDGE && encyclopediaExpansionLabel(id) != null
    ) {
        "Additional English-language Wikipedia articles across mixed subjects. " +
            "This is one part of the encyclopedia expansion, not an English course or a subject-specific collection. " +
            "Only switched-on collections are searched."
    } else null

/** What About says a collection holds: the pack's own summary, else the catalog's listing without its sizes, which
 * the sheet already shows. Packs published before summaries existed (the encyclopedia) have only the listing. */
internal fun InstalledAsset.aboutDescription(catalogDescription: String?): String? =
    collectionDescription() ?: catalogDescription?.let(::catalogText)
        ?.takeIf(String::isNotBlank)

/** A catalogue description as people read it: the download sizes it repeats are already shown beside it. */
internal fun catalogText(description: String): String? =
    description.replace(catalogSizes, "").replace(Regex("\\s+"), " ").trim().takeIf(String::isNotBlank)

/**
 * The few words under a collection's name in Discover, after its size: what is inside, readable at a glance. The
 * catalogue's full text is one tap away in the collection's sheet. A collection without its own words gets the first
 * clause of its description.
 */
internal fun discoverLine(packId: String, description: String): String = when (packId.lowercase()) {
    "wikipedia-encyclopedia" -> "Wikipedia in English"
    "wikivoyage-places" -> "Food, hotels, sights"
    "osm-vegan-places" -> "Vegan food and shops"
    "osm-essentials" -> "Pharmacies, hospitals, ATMs"
    "wikivoyage-guides" -> "Getting around, money, safety"
    "world-knowledge-biology" -> "Biology, health, ageing"
    "crypto-ethereum" -> "Ethereum and cryptography"
    else -> catalogText(description).orEmpty().split(Regex("[.;:·]")).first().trim()
        .let { if (it.length > 40) it.take(39).trimEnd() + "…" else it }
}

private val catalogSizes = Regex(";?\\s*[0-9][0-9.,]*\\s*[KMGT]B download(?:,\\s*[0-9][0-9.,]*\\s*[KMGT]B installed)?")

/** Questions About offers to try: the pack's own, else a few the encyclopedia answers from its overviews. Each was
 * asked on a phone first; "What causes the seasons?" and "How does a volcano form?" read the wrong articles. */
internal fun InstalledAsset.aboutExamples(): List<String> =
    discovery?.exampleQuestions.orEmpty().ifEmpty {
        if (type == PackType.KNOWLEDGE && knowledgeCategory(id) == KnowledgeCategory.Encyclopedia) ENCYCLOPEDIA_EXAMPLES
        else emptyList()
    }

private val ENCYCLOPEDIA_EXAMPLES = listOf("Who was Ada Lovelace?", "Who was Marie Curie?", "What is the Great Barrier Reef?")

/** The line under a pack's name in About: its kind when that is not its name, then size and version. */
internal fun aboutSubtitle(title: String, category: String?, size: String, version: String): String =
    listOfNotNull(category?.takeUnless { it.equals(title, ignoreCase = true) }, size, "v${displayVersion(version)}")
        .joinToString(" · ")

internal fun knowledgeSelectionSummary(packs: List<InstalledAsset>): String? {
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    val enabled = knowledge.filter { it.enabled }
    if (enabled.size == knowledge.size) return null
    return "${enabled.size} of ${knowledge.size} collections enabled"
}

/** A version as people read it: "2026.10.12-native-compressed-preview" is shown as "2026.10.12".
 * Build suffixes stay in technical details; a version without a leading number is kept whole. */
internal fun displayVersion(version: String): String =
    Regex("^\\d+(?:\\.\\d+){1,3}").find(version.trim())?.value ?: version.trim()

/** A pack's SPDX licence expression as people read it: "CC-BY-4.0 AND LicenseRef-US-Government-Work"
 * is shown as "CC BY 4.0, US government work". Expressions that are not plain AND lists stay whole. */
/** The licence of a saved passage, from the site it was taken from; null when the site does not settle it. */
internal fun licenceOfSource(source: String): String? {
    val host = runCatching { java.net.URI(source).host?.lowercase() }.getOrNull() ?: return null
    return when {
        host == "wikipedia.org" || host.endsWith(".wikipedia.org") -> "CC BY-SA 4.0"
        host == "wikivoyage.org" || host.endsWith(".wikivoyage.org") -> "CC BY-SA 4.0"
        host == "openstreetmap.org" || host.endsWith(".openstreetmap.org") -> "ODbL 1.0"
        else -> null
    }
}

internal fun displayLicence(spdx: String): String {
    val parts = spdx.trim().split(" AND ")
    if (parts.any { it.isBlank() || it.contains(' ') }) return spdx.trim()
    return parts.joinToString(", ") { id ->
        KNOWN_LICENCES[id] ?: Regex("^CC-(BY(?:-SA|-NC|-ND)*)-(\\d\\.\\d)$").matchEntire(id)?.let {
            "CC ${it.groupValues[1]} ${it.groupValues[2]}"
        } ?: id.removePrefix("LicenseRef-").replace('-', ' ')
    }
}

private val KNOWN_LICENCES = mapOf(
    "CC0-1.0" to "CC0 1.0",
    "ODbL-1.0" to "ODbL 1.0",
    "Apache-2.0" to "Apache 2.0",
    "MIT" to "MIT",
    "LicenseRef-US-Government-Work" to "US government work",
)

internal fun formatAssetBytes(bytes: Long): String {
    val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    return when {
        bytes >= 1_000_000_000 -> "${formatter.format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000 -> "${formatter.format(kotlin.math.ceil(bytes / 1_000_000.0))} MB"
        bytes >= 1_000 -> "${formatter.format(bytes / 1_000.0)} KB"
        else -> "$bytes B"
    }
}

/** How knowledge packs are grouped in Library and setup, in display order. */
/** One order for collections in setup, Library and Discover: category, then the catalog's
 * recommended collections, then title. Installed packs look "recommended" up in the catalog. */
internal fun <T> knowledgeOrder(category: (T) -> KnowledgeCategory, recommended: (T) -> Boolean, title: (T) -> String): Comparator<T> =
    compareBy<T> { category(it).ordinal }.thenByDescending { recommended(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { title(it) }

enum class KnowledgeCategory(val label: String) {
    Encyclopedia("Encyclopedia"),
    Places("Places"),
    Travel("Travel"),
    Language("Language"),
    HowTo("How-to"),
    Science("Science"),
    Other("Other"),
}

/** A pack's category: the catalog's own label when it names one, else inferred from the pack id. */
fun knowledgeCategory(id: String, declared: String = ""): KnowledgeCategory =
    KnowledgeCategory.entries.firstOrNull { it.label.equals(declared.trim(), ignoreCase = true) } ?: when {
        id.startsWith("osm-") || id == "wikivoyage-places" || id == "wikivoyage-eat" -> KnowledgeCategory.Places
        id.startsWith("wikivoyage") -> KnowledgeCategory.Travel
        id.startsWith("wikipedia") || id.startsWith("simplewiki") || id.startsWith("everyday-reference") ||
            id.startsWith("general-reference") -> KnowledgeCategory.Encyclopedia
        "wiktionary" in id || "phrasebook" in id -> KnowledgeCategory.Language
        "wikibooks" in id || "how-to" in id -> KnowledgeCategory.HowTo
        "biology" in id || "science" in id || "crypto" in id || "ethereum" in id -> KnowledgeCategory.Science
        else -> KnowledgeCategory.Other
    }
