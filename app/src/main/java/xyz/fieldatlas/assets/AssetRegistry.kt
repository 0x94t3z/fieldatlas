package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AssetRegistry(private val storageRoot: File) {
    private val mutex = Mutex()
    private val registryFile = File(storageRoot, "asset-registry.json")

    suspend fun list(): List<InstalledAsset> = mutex.withLock { readUnlocked() }

    suspend fun totalInstalledBytes(): Long = mutex.withLock {
        readUnlocked().fold(0L) { total, asset -> Math.addExact(total, asset.installedBytes) }
    }

    suspend fun add(asset: InstalledAsset) = mutex.withLock {
        val current = readUnlocked()
        require(current.none { it.id == asset.id && it.version == asset.version }) {
            "Asset version is already installed"
        }
        writeUnlocked((current + asset).sortedWith(compareBy(InstalledAsset::id, InstalledAsset::version)))
    }

    suspend fun remove(id: String, version: String): Boolean = mutex.withLock {
        val current = readUnlocked()
        val removed = current.firstOrNull { it.id == id && it.version == version } ?: return@withLock false
        val updated = current.filterNot { it.id == id && it.version == version }
        writeUnlocked(updated)
        File(removed.rootPath).deleteRecursively()
        true
    }

    suspend fun setEnabled(id: String, version: String, enabled: Boolean) = mutex.withLock {
        val current = readUnlocked()
        require(current.any { it.id == id && it.version == version }) { "Asset version is not installed" }
        writeUnlocked(current.map { asset ->
            if (asset.id == id && asset.version == version) asset.copy(enabled = enabled) else asset
        })
    }

    /** Marks one MODEL or AUDIO pack active; every other pack of the SAME type loses the flag. */
    suspend fun setActiveModel(id: String, version: String) = mutex.withLock {
        val current = readUnlocked()
        val target = current.firstOrNull { it.id == id && it.version == version }
        require(target != null && target.type != PackType.KNOWLEDGE) { "Selectable pack is not installed" }
        writeUnlocked(current.map { asset ->
            if (asset.type == target.type) {
                asset.copy(active = asset.id == id && asset.version == version)
            } else {
                asset
            }
        })
    }


    private fun readUnlocked(): List<InstalledAsset> {
        if (!registryFile.exists()) return emptyList()
        return Json.parseToJsonElement(registryFile.readText()).jsonArray.map { element ->
            val value = element.jsonObject
            val discovery = value["discovery"]?.jsonObject?.let { item ->
                PackDiscovery(
                    coverageSummary = item.getValue("coverageSummary").jsonPrimitive.content,
                    exampleQuestions = item.getValue("exampleQuestions").jsonArray.map { it.jsonPrimitive.content },
                    coverageLevel = CoverageLevel.valueOf(item.getValue("coverageLevel").jsonPrimitive.content),
                )
            }
            InstalledAsset(
                id = value.getValue("id").jsonPrimitive.content,
                version = value.getValue("version").jsonPrimitive.content,
                type = PackType.valueOf(value.getValue("type").jsonPrimitive.content),
                title = value.getValue("title").jsonPrimitive.content,
                license = value.getValue("license").jsonPrimitive.content,
                installedBytes = value.getValue("installedBytes").jsonPrimitive.content.toLong(),
                manifestSha256 = value.getValue("manifestSha256").jsonPrimitive.content,
                rootPath = value.getValue("rootPath").jsonPrimitive.content,
                discovery = discovery,
                enabled = value["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                active = value["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                embedding = value["embedding"]?.let { element ->
                    runCatching {
                        Json.decodeFromJsonElement(PackEmbedding.serializer(), element)
                    }.getOrNull()
                },
            )
        }
    }

    private fun writeUnlocked(assets: List<InstalledAsset>) {
        storageRoot.mkdirs()
        val json: JsonArray = buildJsonArray {
            assets.forEach { asset ->
                add(buildJsonObject {
                    put("id", asset.id)
                    put("version", asset.version)
                    put("type", asset.type.name)
                    put("title", asset.title)
                    put("license", asset.license)
                    put("installedBytes", asset.installedBytes)
                    put("manifestSha256", asset.manifestSha256)
                    put("rootPath", asset.rootPath)
                    put("enabled", asset.enabled)
                    put("active", asset.active)
                    asset.discovery?.let { discovery ->
                        put("discovery", buildJsonObject {
                            put("coverageSummary", discovery.coverageSummary)
                            put("exampleQuestions", buildJsonArray {
                                discovery.exampleQuestions.forEach { add(JsonPrimitive(it)) }
                            })
                            put("coverageLevel", discovery.coverageLevel.name)
                        })
                    }
                    asset.embedding?.let { embedding ->
                        put(
                            "embedding",
                            Json.encodeToJsonElement(PackEmbedding.serializer(), embedding),
                        )
                    }
                })
            }
        }
        val temp = File(storageRoot, "${registryFile.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(json.toString().encodeToByteArray())
            output.fd.sync()
        }
        Files.move(
            temp.toPath(),
            registryFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
