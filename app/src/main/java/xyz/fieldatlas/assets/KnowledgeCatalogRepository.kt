package xyz.fieldatlas.assets

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

/** Local construction; networking occurs only when the user requests refresh. */
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
            }.packs
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
                currentCoroutineContext().ensureActive()
                cache.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                val staging = File(cache.parentFile, "${cache.name}.part")
                try {
                    FileOutputStream(staging).use { it.write(bytes); it.fd.sync() }
                    currentCoroutineContext().ensureActive()
                    Files.move(staging.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
                    mutable.value = CatalogState(catalog.packs, lastCheckedMillis = clock())
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
        private fun parse(bytes: ByteArray): KnowledgeCatalog {
            require(bytes.size <= MAX_BYTES) { "Catalog exceeds size limit" }
            return KnowledgeCatalog.parse(bytes.decodeToString(throwOnInvalidSequence = true))
        }
    }
}
