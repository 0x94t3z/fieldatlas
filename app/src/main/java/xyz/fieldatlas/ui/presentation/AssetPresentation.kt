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
        // Short names, as in the download catalog; the source is in About this collection.
        "simplewiki" -> "Encyclopedia"
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

internal fun knowledgeSelectionSummary(packs: List<InstalledAsset>): String? {
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    val enabled = knowledge.filter { it.enabled }
    if (enabled.size == knowledge.size) return null
    return "${enabled.size} of ${knowledge.size} collections enabled"
}

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
