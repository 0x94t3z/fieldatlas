package xyz.fieldatlas.diagnostics

import kotlinx.serialization.Serializable
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.research.ResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics

@Serializable
data class InstalledAssetSummary(
    val id: String,
    val version: String,
    val type: PackType,
    val installedBytes: Long,
    val manifestSha256: String,
    /** Whether this knowledge pack participated in retrieval for the exported run. */
    val enabled: Boolean = true,
    /** Whether this was the selected model/audio pack for the exported run. */
    val active: Boolean = false,
)

@Serializable
data class DiagnosticsSnapshot(
    val schemaVersion: Int = 2,
    val appVersion: String,
    val device: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val deviceTotalMemoryBytes: Long,
    val memoryClassBytes: Long,
    val measuredPssBytes: Long,
    val measuredRssBytes: Long?,
    val totalInstalledBytes: Long,
    val networkPermissionPresent: Boolean,
    val cleartextTrafficPermitted: Boolean,
    val assets: List<InstalledAssetSummary>,
    val latestResearchMetrics: ResearchMetrics? = null,
    val latestResearchCompletion: ResearchCompletion? = null,
    val latestQuestion: String? = null,
    val notices: List<String> = emptyList(),
)

data class DiagnosticsInput(
    val appVersion: String,
    val device: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val deviceTotalMemoryBytes: Long,
    val memoryClassBytes: Long,
    val measuredPssBytes: Long,
    val measuredRssBytes: Long?,
    val networkPermissionPresent: Boolean,
    val cleartextTrafficPermitted: Boolean = false,
)
