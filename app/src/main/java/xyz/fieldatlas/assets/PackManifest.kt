package xyz.fieldatlas.assets

import kotlinx.serialization.Serializable

@Serializable
data class PackManifest(
    val schemaVersion: Int,
    val id: String,
    val version: String,
    val type: PackType,
    val title: String,
    val license: String,
    val sourceUrls: List<String>,
    val artifacts: List<PackArtifact>,
    val discovery: PackDiscovery? = null,
)

@Serializable
enum class PackType { MODEL, KNOWLEDGE }

@Serializable
data class PackArtifact(val path: String, val bytes: Long, val sha256: String)

@Serializable
data class PackDiscovery(
    val coverageSummary: String,
    val exampleQuestions: List<String>,
    val coverageLevel: CoverageLevel,
)

@Serializable
enum class CoverageLevel { DEMO, FOCUSED, BROAD }

@Serializable
data class InstalledAsset(
    val id: String,
    val version: String,
    val type: PackType,
    val title: String,
    val license: String,
    val installedBytes: Long,
    val manifestSha256: String,
    val rootPath: String,
    val discovery: PackDiscovery? = null,
)
