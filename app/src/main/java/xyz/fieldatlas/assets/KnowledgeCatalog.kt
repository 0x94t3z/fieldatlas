package xyz.fieldatlas.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class KnowledgeCatalog(val schemaVersion: Int, val packs: List<KnowledgeCatalogEntry>) {
    companion object {
        fun parse(json: String): KnowledgeCatalog = Json.decodeFromString<KnowledgeCatalog>(json).also { catalog ->
            require(catalog.schemaVersion == 1) { "Unsupported knowledge catalog version" }
            require(catalog.packs.map { it.id to it.version }.distinct().size == catalog.packs.size) {
                "Knowledge catalog contains duplicate pack versions"
            }
            catalog.packs.forEach { pack ->
                require(pack.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid pack id" }
                require(pack.version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid pack version" }
                require(pack.title.isNotBlank() && pack.description.isNotBlank() && pack.license.isNotBlank()) {
                    "Pack description or license is missing"
                }
                require(pack.bytes in 1..StorageBudget.MAX_TOTAL_BYTES) { "Invalid pack size" }
                require(pack.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid pack checksum" }
                val uri = URI(pack.url)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null && uri.path.endsWith(".fapack")) {
                    "Knowledge downloads must use HTTPS fapack URLs"
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
)
