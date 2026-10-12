package xyz.fieldatlas.diagnostics

import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.assets.StorageBudget
import xyz.fieldatlas.assets.StorageFootprint

class StorageDiagnosticsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val time = Instant.parse("2026-10-09T08:00:00Z")

    @Test fun measuredFilesIncludeUnregisteredStagingAndApkSeparatelyFromPayloadMetadata() {
        val data = temporary.newFolder("data")
        File(data, "content.sqlite").writeBytes(ByteArray(100))
        File(data, "cache").mkdir()
        File(data, "cache/temporary").writeBytes(ByteArray(20))
        File(data, "pack-staging").mkdir()
        File(data, "pack-staging/incoming.fapack").writeBytes(ByteArray(30))
        val apk = temporary.newFile("app.apk").apply { writeBytes(ByteArray(10)) }
        val footprint = StorageFootprint { listOf(data, apk) }

        val result = StorageDiagnostics.measure(100, footprint::bytes) { time }

        assertEquals(100, result.registeredAssetPayloadBytes)
        assertEquals(160L, result.appOwnedLogicalFileBytes)
        assertEquals(time.toString(), result.measuredAtUtc)
        assertEquals(true, result.withinStorageLimit)
        assertNull(result.error)
        assertTrue(result.caveat.contains("not allocated disk blocks or a peak"))
        assertTrue(result.caveat.contains("Excludes shared Downloads"))
    }

    @Test fun decimalCapIncludesUnregisteredFilesAndDoesNotUnderstateLargerRegistryTotals() {
        val cap = StorageBudget.MAX_TOTAL_BYTES
        assertEquals(50_000_000_000L, cap)
        assertEquals(true, StorageDiagnostics.measure(10, { cap }) { time }.withinStorageLimit)
        assertEquals(false, StorageDiagnostics.measure(10, { cap + 1 }) { time }.withinStorageLimit)
        assertEquals(false, StorageDiagnostics.measure(cap + 1, { 10 }) { time }.withinStorageLimit)
    }

    @Test fun unreadableStorageIsUnavailableAndDoesNotLeakFailurePaths() {
        val result = StorageDiagnostics.measure(100, {
            throw IOException("Cannot read /data/user/0/xyz.fieldatlas/files/private.db")
        }) { time }
        val json = DiagnosticsProvider.toJson(snapshot().copy(storageMeasurement = result))

        assertNull(result.appOwnedLogicalFileBytes)
        assertNull(result.withinStorageLimit)
        assertEquals("Storage measurement unavailable", result.error)
        assertFalse(json.contains("/data/user/"))
        assertTrue(json.contains("\"appOwnedLogicalFileBytes\":null"))
        assertTrue(json.contains("\"withinStorageLimit\":null"))
    }

    @Test fun invalidOrOverflowingMeasurementsNeverPassTheCap() {
        val negative = StorageDiagnostics.measure(100, { -1 }) { time }
        val overflowing = StorageDiagnostics.measure(100, { throw ArithmeticException("overflow") }) { time }
        assertNull(negative.appOwnedLogicalFileBytes)
        assertNull(negative.withinStorageLimit)
        assertNull(overflowing.appOwnedLogicalFileBytes)
        assertNull(overflowing.withinStorageLimit)
        assertThrows(IllegalArgumentException::class.java) {
            StorageDiagnostics.measure(-1, { 100 }) { time }
        }
    }

    @Test fun cancellationPropagatesInsteadOfProducingAnExport() {
        assertThrows(CancellationException::class.java) {
            StorageDiagnostics.measure(100, { throw CancellationException("cancelled") }) { time }
        }
    }

    @Test fun exportPreservesRegistryTotalAndSerializesMeasuredBytesWithTheirTimestamp() {
        val original = snapshot()
        val measured = original.copy(storageMeasurement = StorageDiagnostics.measure(100, { 160 }) { time })
        val json = Json.parseToJsonElement(DiagnosticsProvider.toJson(measured)).jsonObject
        val storage = json.getValue("storageMeasurement").jsonObject

        assertEquals("3", json.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("100", json.getValue("totalInstalledBytes").jsonPrimitive.content)
        assertEquals("100", storage.getValue("registeredAssetPayloadBytes").jsonPrimitive.content)
        assertEquals("160", storage.getValue("appOwnedLogicalFileBytes").jsonPrimitive.content)
        assertEquals(time.toString(), storage.getValue("measuredAtUtc").jsonPrimitive.content)
        assertNull(original.storageMeasurement)
    }

    private fun snapshot() = DiagnosticsProvider.create(
        appVersion = "1", device = "phone", apiLevel = 33, supportedAbis = listOf("arm64-v8a"),
        deviceTotalMemoryBytes = 6_000_000_000, memoryClassBytes = 256_000_000,
        measuredPssBytes = 1, measuredRssBytes = null, networkPermissionPresent = true,
        assets = emptyList(),
    ).copy(totalInstalledBytes = 100)
}
