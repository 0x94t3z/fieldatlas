package xyz.fieldatlas.diagnostics

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import xyz.fieldatlas.assets.StorageBudget

@Serializable
data class StorageMeasurement(
    val measuredAtUtc: String,
    val registeredAssetPayloadBytes: Long,
    val appOwnedLogicalFileBytes: Long?,
    val withinStorageLimit: Boolean?,
    val error: String? = null,
    val storageLimitBytes: Long = StorageBudget.MAX_TOTAL_BYTES,
    val scope: String = "APKs, native libraries, credential/device-protected app data, and app-specific external files and cache; includes downloads and extraction staging in these locations.",
    val caveat: String = "Logical file lengths at measurement time, not allocated disk blocks or a peak. Traversal is not atomic. Excludes shared Downloads, filesystem overhead and OS-managed files outside these locations. The cap check uses the larger of measured files and registered payload bytes. Does not verify payload hashes or guarantee space for future growth.",
)

object StorageDiagnostics {
    /** A failed traversal stays unavailable; it must never become zero or a passing cap check. */
    fun measure(
        registeredAssetPayloadBytes: Long,
        readLogicalBytes: () -> Long,
        now: () -> Instant = Instant::now,
    ): StorageMeasurement {
        require(registeredAssetPayloadBytes >= 0)
        val bytes = try {
            readLogicalBytes().also { require(it >= 0) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return StorageMeasurement(
            measuredAtUtc = now().toString(),
            registeredAssetPayloadBytes = registeredAssetPayloadBytes,
            appOwnedLogicalFileBytes = bytes,
            withinStorageLimit = bytes?.let {
                maxOf(it, registeredAssetPayloadBytes) <= StorageBudget.MAX_TOTAL_BYTES
            },
            error = if (bytes == null) "Storage measurement unavailable" else null,
        )
    }
}
