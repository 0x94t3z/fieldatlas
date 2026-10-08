package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Network access is confined to this explicit, user-started provisioning action. */
class KnowledgePackDownload internal constructor(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val importer: AssetImporter,
    private val footprint: StorageFootprint = StorageFootprint { listOf(storageRoot) },
    private val downloader: VerifiedResumableDownload = VerifiedResumableDownload(),
) {
    fun resumableBytes(pack: KnowledgeCatalogEntry): Long =
        File(storageRoot, "knowledge-downloads/${pack.id}-${pack.version}.part")
            .length().coerceIn(0, pack.bytes)

    /**
     * Frees the space a download holds that can no longer finish: [catalog] no longer offers its
     * archive at the same id, version, size and checksum. An entry that changed only its wording,
     * category, recommended flag or address still offers those bytes, so they stay to resume (a
     * server that cannot continue them restarts the download); a new size or checksum under the
     * same version means they would never verify.
     */
    fun discardPartial(pack: KnowledgeCatalogEntry, catalog: List<KnowledgeCatalogEntry>): Boolean =
        catalog.none {
            it.id == pack.id && it.version == pack.version &&
                it.bytes == pack.bytes && it.sha256 == pack.sha256
        } && File(storageRoot, "knowledge-downloads/${pack.id}-${pack.version}.part").delete()

    /**
     * Removes paused downloads of versions [catalog] no longer lists: after an update replaces a
     * collection, its half-downloaded old version would otherwise hold up to its full size for good.
     */
    fun discardStale(catalog: List<KnowledgeCatalogEntry>): Int {
        val current = catalog.map { "${it.id}-${it.version}.part" }.toSet()
        return File(storageRoot, "knowledge-downloads").listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".part") && it.name !in current }
            .count { it.delete() }
    }

    suspend fun install(pack: KnowledgeCatalogEntry, downloadUrl: String = pack.url, onProgress: (Long) -> Unit): InstalledAsset =
        withContext(Dispatchers.IO) {
            validatedDownloadUrl(downloadUrl, ".fapack")
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
            downloader.download(downloadUrl, pack.bytes, pack.sha256, partial, onProgress)
            // A killed process can leave an extracted pack without a registry entry.
            val orphan = File(storageRoot, "packs/${pack.id}/${pack.version}")
            if (orphan.exists()) check(orphan.deleteRecursively()) { "Could not clear interrupted collection install" }
            val installed = importer.importArchive(partial, pack.id, pack.version, PackType.KNOWLEDGE)
            partial.delete() // Only remove the verified archive once installation is registered.
            installed
        }
}
