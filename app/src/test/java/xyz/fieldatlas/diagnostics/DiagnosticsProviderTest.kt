package xyz.fieldatlas.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType

class DiagnosticsProviderTest {
    @Test fun reportsDistinctMemoryValuesDecimalTotalsAndDeviceFacts() {
        val snapshot = DiagnosticsProvider.create(
            appVersion = "1.0.0",
            device = "Infinix X6725",
            apiLevel = 36,
            supportedAbis = listOf("arm64-v8a", "arm64-v8a"),
            deviceTotalMemoryBytes = 4_000_000_000,
            memoryClassBytes = 4_000_000_000,
            measuredPssBytes = 1_234_567_890,
            measuredRssBytes = 1_500_000_000,
            networkPermissionPresent = false,
            assets = listOf(asset("knowledge", 2_000_000_001), asset("model", 1_000_000_002)),
        )
        assertEquals(3_000_000_003, snapshot.totalInstalledBytes)
        assertEquals(4_000_000_000, snapshot.deviceTotalMemoryBytes)
        assertEquals(4_000_000_000, snapshot.memoryClassBytes)
        assertEquals(1_234_567_890, snapshot.measuredPssBytes)
        assertEquals(1_500_000_000L, snapshot.measuredRssBytes)
        assertEquals(listOf("arm64-v8a"), snapshot.supportedAbis)
        assertFalse(snapshot.networkPermissionPresent)
    }

    @Test fun questionTextIsOptInAndPrivatePathsAreRedacted() {
        val hidden = DiagnosticsProvider.create(
            appVersion = "1", device = "phone", apiLevel = 36, supportedAbis = listOf("arm64-v8a"),
            deviceTotalMemoryBytes = 4,
            memoryClassBytes = 1, measuredPssBytes = 2, measuredRssBytes = 3,
            networkPermissionPresent = false, assets = emptyList(), latestQuestion = "private question",
            includeQuestion = false, notices = listOf("Failed /data/user/0/xyz/files/model.gguf: bad header"),
        )
        assertNull(hidden.latestQuestion)
        assertFalse(hidden.notices.single().contains("/data/"))
        assertTrue(hidden.notices.single().contains("bad header"))
        val visible = hidden.copy(latestQuestion = DiagnosticsProvider.questionForExport("private question", true))
        assertEquals("private question", visible.latestQuestion)
    }

    @Test fun outputIsDeterministicAndOverflowIsRejected() {
        val common = DiagnosticsInput(
            appVersion = "1", device = "phone", apiLevel = 36, supportedAbis = listOf("x", "a"),
            deviceTotalMemoryBytes = 4,
            memoryClassBytes = 1, measuredPssBytes = 2, measuredRssBytes = 3,
            networkPermissionPresent = false,
        )
        val first = DiagnosticsProvider.create(common, listOf(asset("z", 2), asset("a", 1)))
        val second = DiagnosticsProvider.create(common.copy(supportedAbis = listOf("a", "x")), listOf(asset("a", 1), asset("z", 2)))
        assertEquals(DiagnosticsProvider.toJson(first), DiagnosticsProvider.toJson(second))
        assertThrows(ArithmeticException::class.java) {
            DiagnosticsProvider.create(common, listOf(asset("a", Long.MAX_VALUE), asset("b", 1)))
        }
    }

    @Test fun schemaTwoSerializesUnavailableRssAsNull() {
        val snapshot = DiagnosticsProvider.create(
            appVersion = "1", device = "phone", apiLevel = 34, supportedAbis = listOf("arm64-v8a"),
            deviceTotalMemoryBytes = 4, memoryClassBytes = 1, measuredPssBytes = 2,
            measuredRssBytes = null, networkPermissionPresent = false, assets = emptyList(),
        )
        assertEquals(2, snapshot.schemaVersion)
        assertNull(snapshot.measuredRssBytes)
        assertTrue(DiagnosticsProvider.toJson(snapshot).contains("\"measuredRssBytes\":null"))
    }

    private fun asset(id: String, bytes: Long) = InstalledAsset(
        id = id, version = "1", type = PackType.KNOWLEDGE, title = id, license = "CC0-1.0",
        installedBytes = bytes, manifestSha256 = "a".repeat(64), rootPath = "/private/$id",
    )
}
