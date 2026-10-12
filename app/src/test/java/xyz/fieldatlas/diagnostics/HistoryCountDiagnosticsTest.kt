package xyz.fieldatlas.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCountDiagnosticsTest {
    private fun snapshot() = DiagnosticsProvider.create(
        appVersion = "1.4.0", device = "phone", apiLevel = 33,
        supportedAbis = listOf("arm64-v8a"), deviceTotalMemoryBytes = 4,
        memoryClassBytes = 1, measuredPssBytes = 2, measuredRssBytes = null,
        networkPermissionPresent = false, assets = emptyList(),
        latestQuestion = "private research question", includeQuestion = false,
    )

    @Test fun ordinarySnapshotsKeepUnmeasuredHistoryNull() {
        val snapshot = snapshot()
        assertEquals(3, snapshot.schemaVersion)
        assertNull(snapshot.historyEntryCount)
        assertTrue(DiagnosticsProvider.toJson(snapshot).contains("\"historyEntryCount\":null"))
    }

    @Test fun measuredZeroNinetyTwoAndHundredSerializeWithoutHistoryText() {
        for (count in listOf(0, 92, 100)) {
            val json = DiagnosticsProvider.toJson(snapshot().copy(historyEntryCount = count))
            val restored = Json.decodeFromString<DiagnosticsSnapshot>(json)
            assertEquals(3, restored.schemaVersion)
            assertEquals(count, restored.historyEntryCount)
            assertTrue(json.contains("\"historyEntryCount\":$count"))
            assertNull(restored.latestQuestion)
            assertFalse(json.contains("private research question"))
            assertFalse(json.contains("createdAtEpochMs"))
            assertFalse(json.contains("\"records\""))
        }
    }

    @Test fun anOlderSchemaThreeReportDecodesCountAsUnmeasured() {
        val fields = Json.parseToJsonElement(DiagnosticsProvider.toJson(snapshot())).jsonObject
        val oldReport = JsonObject(fields.filterKeys { it != "historyEntryCount" }).toString()
        val restored = Json.decodeFromString<DiagnosticsSnapshot>(oldReport)
        assertEquals(3, restored.schemaVersion)
        assertNull(restored.historyEntryCount)
    }
}
