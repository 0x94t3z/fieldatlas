package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Network access is confined to this explicit, user-started provisioning action. */
class KnowledgePackDownload(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val importer: AssetImporter,
    private val footprint: StorageFootprint = StorageFootprint { listOf(storageRoot) },
) {
    fun resumableBytes(pack: KnowledgeCatalogEntry): Long =
        File(storageRoot, "knowledge-downloads/${pack.id}-${pack.version}.part")
            .length().coerceIn(0, pack.bytes)

    suspend fun install(pack: KnowledgeCatalogEntry, onProgress: (Long) -> Unit): InstalledAsset =
        withContext(Dispatchers.IO) {
            val downloadDir = File(storageRoot, "knowledge-downloads").apply { mkdirs() }
            val partial = File(downloadDir, "${pack.id}-${pack.version}.part")
            registry.list().firstOrNull { it.id == pack.id && it.version == pack.version }?.let { installed ->
                partial.delete() // A process may have died after registration but before cleanup.
                return@withContext installed
            }
            val remaining = pack.bytes - partial.length().coerceAtMost(pack.bytes)
            // STORED-only archives: reserving archive size for extraction is conservative.
            val peakGrowth = Math.addExact(Math.addExact(remaining, pack.bytes), StorageBudget.METADATA_RESERVE_BYTES)
            check(StorageBudget.evaluate(footprint.bytes(), peakGrowth, Long.MAX_VALUE) == BudgetDecision.Allowed) {
                "Collection would exceed the app storage budget"
            }
            check(storageRoot.usableSpace - 512_000_000L >= peakGrowth) {
                "Not enough free space to download and install this collection"
            }
            VerifiedResumableDownload().download(pack.url, pack.bytes, pack.sha256, partial, onProgress)
            // A killed process can leave an extracted pack without a registry entry.
            val orphan = File(storageRoot, "packs/${pack.id}/${pack.version}")
            if (orphan.exists()) check(orphan.deleteRecursively()) { "Could not clear interrupted collection install" }
            val installed = importer.importArchive(partial, pack.id, pack.version, PackType.KNOWLEDGE)
            partial.delete() // Only remove the verified archive once installation is registered.
            installed
        }
}
