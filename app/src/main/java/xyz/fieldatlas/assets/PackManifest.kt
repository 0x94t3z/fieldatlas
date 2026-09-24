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
    /** Embedding vectors shipped inside the pack (KNOWLEDGE packs only); null = keyword-only. */
    val embedding: PackEmbedding? = null,
)

/**
 * Vector-search metadata for packs whose content.sqlite carries a chunk_vectors table of
 * int8-quantized BGE embeddings plus an encoder GGUF artifact for on-device query encoding.
 * The app falls back to plain keyword search whenever the encoder cannot be loaded or a query
 * encodes below [rejectBelow].
 */
@Serializable
data class PackEmbedding(
    val model: String,
    val dim: Int,
    val quant: String,
    val normalized: Boolean,
    val table: String,
    val count: Long,
    val rejectBelow: Double,
    val queryPrefix: String,
    val encoderPath: String,
    val encoderSha256: String,
    val encoderSourceUrl: String,
)

@Serializable
enum class PackType { MODEL, KNOWLEDGE, AUDIO }

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
    val enabled: Boolean = true,
    /** True for the one MODEL pack the app should load; at most one model pack is active. */
    val active: Boolean = false,
    /** Embedding metadata carried from the manifest at import time (KNOWLEDGE packs). */
    val embedding: PackEmbedding? = null,
)
