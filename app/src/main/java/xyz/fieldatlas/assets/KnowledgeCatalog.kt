package xyz.fieldatlas.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KnowledgeCatalog(val schemaVersion: Int, val packs: List<KnowledgeCatalogEntry>) {
    companion object {
        fun parse(json: String): KnowledgeCatalog = Json { ignoreUnknownKeys = true }.decodeFromString<KnowledgeCatalog>(json).also { catalog ->
            require(catalog.schemaVersion == 1) { "Unsupported knowledge catalog version" }
            require(catalog.packs.size in 1..500) { "Invalid catalog entry count" }
            require(catalog.packs.map { it.id to it.version }.distinct().size == catalog.packs.size) {
                "Knowledge catalog contains duplicate pack versions"
            }
            catalog.packs.forEach { pack ->
                require(pack.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid pack id" }
                require(pack.version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid pack version" }
                require(pack.replaces.size <= 50 && pack.replaces.distinct().size == pack.replaces.size &&
                    pack.replaces.all { it != pack.id && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) }) {
                    "Invalid superseded collection ids"
                }
                require(pack.title.isNotBlank() && pack.description.isNotBlank() && pack.license.isNotBlank()) {
                    "Pack description or license is missing"
                }
                require(pack.bytes in 1..StorageBudget.MAX_TOTAL_BYTES) { "Invalid pack size" }
                require(pack.installedBytes == null || pack.installedBytes in 1..StorageBudget.MAX_TOTAL_BYTES) { "Invalid installed pack size" }
                require(pack.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid pack checksum" }
                // Use exactly the transfer boundary's rules: don't save a catalog whose
                // default links the downloader will later reject (fragments/invalid ports).
                require(pack.url == validatedDownloadUrl(pack.url, ".fapack")) {
                    "Knowledge download URL must not contain surrounding whitespace"
                }
            }
        }
    }
}

@Serializable
data class KnowledgeCatalogEntry(
    val id: String,
    val version: String,
    val title: String,
    val description: String,
    val license: String,
    val bytes: Long,
    val sha256: String,
    val url: String,
    val recommended: Boolean = false,
    /** Library grouping, e.g. "Encyclopedia" or "Places"; older catalogs omit it. */
    val category: String = "",
    /** Required for compressed schema-2 packs; archive bytes are not extraction bytes. */
    val installedBytes: Long? = null,
    /** Superseded download listings only. Never deletes or disables installed data. */
    val replaces: List<String> = emptyList(),
)

/** Additional disk space for a staged download plus extraction, matching admission. */
fun KnowledgeCatalogEntry.downloadGrowthBytes(savedBytes: Long = 0): Long =
    Math.addExact(Math.addExact(bytes - savedBytes.coerceIn(0, bytes), installedBytes ?: bytes),
        StorageBudget.METADATA_RESERVE_BYTES)

fun KnowledgeCatalogEntry.downloadFreeBytes(savedBytes: Long = 0): Long =
    Math.addExact(downloadGrowthBytes(savedBytes), 512_000_000L)

/**
 * Orders two editions of one collection; null when nothing says which is later. The numbers decide first
 * ("2026.11.01" after "2026.10.12"). With equal numbers, a pre-release ("2026.10.12-native-compressed-preview")
 * comes before the plain edition, and a rebuild of the same data ("2026.09.1-leviathan", the same places with a
 * new search index) comes after it: only words that mean "not final yet" make a suffix earlier. Two different
 * rebuilds, or two different pre-releases, of the same numbers are not ordered.
 */
fun compareEditions(left: String, right: String): Int? {
    val edition = Regex("([0-9]+(?:\\.[0-9]+)*)(?:[-+_](.*))?")
    val l = edition.matchEntire(left) ?: return null
    val r = edition.matchEntire(right) ?: return null
    val a = l.groupValues[1].split('.').map { it.toBigInteger() }
    val b = r.groupValues[1].split('.').map { it.toBigInteger() }
    for (index in 0 until maxOf(a.size, b.size)) {
        val difference = (a.getOrNull(index) ?: java.math.BigInteger.ZERO)
            .compareTo(b.getOrNull(index) ?: java.math.BigInteger.ZERO)
        if (difference != 0) return difference
    }
    fun rank(suffix: String) = when {
        suffix.isEmpty() -> 1
        PRE_RELEASE.containsMatchIn(suffix) -> 0
        else -> 2
    }
    val (leftSuffix, rightSuffix) = l.groupValues[2] to r.groupValues[2]
    val difference = rank(leftSuffix).compareTo(rank(rightSuffix))
    return when {
        difference != 0 -> difference
        leftSuffix == rightSuffix -> 0
        else -> null
    }
}

private val PRE_RELEASE = Regex("(?i)(^|[-._])(preview|alpha|beta|rc[0-9]*|pre|dev|draft)([-._]|$)")

/** What Discover offers: collections not on the phone at all, and later editions of ones that are. */
data class DiscoverOffers(val fresh: List<KnowledgeCatalogEntry>, val updates: List<KnowledgeCatalogEntry>)

/**
 * Splits the catalog into what Discover may offer. A collection already on the phone is offered again only as an
 * update, and only when the catalog's edition is later than every edition installed. The same edition, an earlier
 * one, or one that cannot be ordered against it (an old catalog saved on the phone, say) is never offered: someone
 * who has the newest packs sees nothing to download.
 */
fun discoverOffers(catalog: List<KnowledgeCatalogEntry>, installed: List<InstalledAsset>): DiscoverOffers {
    val knowledge = installed.filter { it.type == PackType.KNOWLEDGE }
    val fresh = catalog.filter { candidate -> knowledge.none { it.id == candidate.id } }
    val updates = catalog.filter { candidate ->
        val editions = knowledge.filter { it.id == candidate.id }
        editions.isNotEmpty() && editions.all { (compareEditions(candidate.version, it.version) ?: 0) > 0 }
    }
    return DiscoverOffers(fresh, updates)
}
