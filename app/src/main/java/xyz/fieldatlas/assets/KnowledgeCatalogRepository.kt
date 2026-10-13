package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class CatalogState(
    val packs: List<KnowledgeCatalogEntry>,
    val refreshing: Boolean = false,
    val error: String? = null,
    val lastCheckedMillis: Long? = null,
)

/** Preserve the running transfer's size, title and cancel control across catalog refreshes. */
fun catalogWithActiveDownload(packs: List<KnowledgeCatalogEntry>, active: KnowledgeCatalogEntry?): List<KnowledgeCatalogEntry> =
    if (active == null) packs else listOf(active) + packs.filterNot { it.id == active.id && it.version == active.version }

/**
 * Local construction; networking occurs only when the user requests refresh.
 * A saved or fetched catalog extends the APK's offers, never replaces its entire catalog.
 */
class KnowledgeCatalogRepository(
    bundled: KnowledgeCatalog,
    private val cache: File,
    private val fetch: suspend () -> ByteArray,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(CatalogState(
        runCatching {
            cache.inputStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                parse(output.toByteArray())
            }.let { merge(bundled.packs, it.packs) }
        }.getOrDefault(bundled.packs),
    ))
    val state: StateFlow<CatalogState> = mutable.asStateFlow()

    /** Accept once at download initiation; refresh cannot mutate this immutable entry. */
    fun selectForDownload(candidate: KnowledgeCatalogEntry): KnowledgeCatalogEntry =
        state.value.packs.firstOrNull { it == candidate }
            ?: throw IllegalArgumentException("Collection is no longer in the catalog. Refresh and try again.")

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        mutable.value = mutable.value.copy(refreshing = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                val bytes = fetch()
                val catalog = parse(bytes)
                val packs = merge(mutable.value.packs, catalog.packs)
                // Persist the effective list, so a stale server cannot roll back a previously
                // discovered edition after restart. Validate the merged cache before writing it.
                val savedBytes = Json.encodeToString(KnowledgeCatalog(1, packs)).encodeToByteArray()
                parse(savedBytes)
                currentCoroutineContext().ensureActive()
                cache.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                val staging = File(cache.parentFile, "${cache.name}.part")
                try {
                    FileOutputStream(staging).use { it.write(savedBytes); it.fd.sync() }
                    currentCoroutineContext().ensureActive()
                    Files.move(staging.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
                    mutable.value = CatalogState(packs, lastCheckedMillis = clock())
                } finally { staging.delete() }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(error = REFRESH_ERROR)
        } finally {
            mutable.value = mutable.value.copy(refreshing = false)
            mutex.unlock()
        }
    }

    companion object {
        const val MAX_BYTES = 1_048_576
        const val REFRESH_ERROR = "Couldn't refresh collections. Your saved collections are still available."
        /**
         * Preserve pinned identities and metadata already offered by this APK/cache. Dotted
         * numeric editions (our dates and release numbers) have numeric component ordering.
         * Opaque editions have no implied chronology: keep both rather than guess or hide one.
         * Installed packs and accepted transfers are managed separately and are never changed.
         */
        private fun merge(
            existing: List<KnowledgeCatalogEntry>, incoming: List<KnowledgeCatalogEntry>,
        ): List<KnowledgeCatalogEntry> {
            val entries = existing.associateByTo(linkedMapOf()) { it.id to it.version }
            incoming.forEach { pack ->
                val key = pack.id to pack.version
                val pinned = entries[key]
                require(pinned == null || (pinned.bytes == pack.bytes && pinned.sha256 == pack.sha256)) {
                    "Catalog changed an existing collection's size or checksum"
                }
                if (pinned == null) entries[key] = pack
            }
            val groups = entries.values.groupBy { it.id }
            val editions = entries.values.filter { pack ->
                groups.getValue(pack.id).none { other ->
                    compareNumericEditions(other.version, pack.version)?.let { it > 0 } == true
                }
            }
            val replacedIds = editions.flatMap { it.replaces }.toSet()
            // A chain or cycle would hide its own replacement; reject it instead of
            // silently losing all download choices. Installed assets are independent.
            require(editions.none { it.id in replacedIds && it.replaces.isNotEmpty() }) {
                "Ambiguous collection replacement chain"
            }
            val result = editions.filterNot { it.id in replacedIds }
            require(result.size in 1..500) { "Invalid merged catalog entry count" }
            return result
        }

        private fun compareNumericEditions(left: String, right: String): Int? {
            val numeric = Regex("[0-9]+(?:\\.[0-9]+)*")
            if (!numeric.matches(left) || !numeric.matches(right)) return null
            val a = left.split('.').map { it.toBigInteger() }
            val b = right.split('.').map { it.toBigInteger() }
            for (index in 0 until maxOf(a.size, b.size)) {
                val difference = (a.getOrNull(index) ?: java.math.BigInteger.ZERO)
                    .compareTo(b.getOrNull(index) ?: java.math.BigInteger.ZERO)
                if (difference != 0) return difference
            }
            return 0
        }

        private fun parse(bytes: ByteArray): KnowledgeCatalog {
            require(bytes.size <= MAX_BYTES) { "Catalog exceeds size limit" }
            return KnowledgeCatalog.parse(bytes.decodeToString(throwOnInvalidSequence = true))
        }
    }
}
