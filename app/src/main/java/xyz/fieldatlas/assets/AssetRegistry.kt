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
                    asset.discovery?.let { discovery ->
                        put("discovery", buildJsonObject {
                            put("coverageSummary", discovery.coverageSummary)
                            put("exampleQuestions", buildJsonArray {
                                discovery.exampleQuestions.forEach { add(JsonPrimitive(it)) }
                            })
                            put("coverageLevel", discovery.coverageLevel.name)
                        })
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
