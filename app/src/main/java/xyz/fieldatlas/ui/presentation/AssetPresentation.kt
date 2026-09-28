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
        "world-knowledge-biology" -> "Biology & longevity"
        "wikivoyage-places" -> "Travel places"
        else -> title
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

internal fun formatAssetBytes(bytes: Long): String {
    val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    return when {
        bytes >= 1_000_000_000 -> "${formatter.format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000 -> "${formatter.format(kotlin.math.ceil(bytes / 1_000_000.0))} MB"
        bytes >= 1_000 -> "${formatter.format(bytes / 1_000.0)} KB"
        else -> "$bytes B"
    }
}
