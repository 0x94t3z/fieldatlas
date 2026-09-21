package xyz.fieldatlas.proof

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.diagnostics.DiagnosticsProvider

class ProofMapperTest {
    @Test fun unavailableRssIsNotReportedAsZero() {
        val proof = ProofMapper.map(snapshot(measuredRssBytes = null))
        assertEquals("Not measured", proof.device.single { it.label == "RSS" }.value)
        assertEquals(ProofState.Missing, proof.device.single { it.label == "RSS" }.state)
    }

    @Test fun factsRetainTheirEvidenceOrigin() {
        val proof = ProofMapper.map(snapshot(measuredRssBytes = 3))
        assertEquals(
            ProofOrigin.ManifestAudit,
            proof.offline.single { it.label == "Internet permission" }.origin,
        )
        assertEquals(
            "Disabled",
            proof.offline.single { it.label == "Cleartext traffic" }.value,
        )
        assertEquals(
            ProofOrigin.OsReport,
            proof.device.single { it.label == "Physical memory" }.origin,
        )
        assertEquals(
            ProofOrigin.MeasuredQuery,
            proof.device.single { it.label == "RSS" }.origin,
        )
    }

    private fun snapshot(measuredRssBytes: Long?) = DiagnosticsProvider.create(
        appVersion = "1",
        device = "Infinix test device",
        apiLevel = 36,
        supportedAbis = listOf("arm64-v8a"),
        deviceTotalMemoryBytes = 4_000_000_000,
        memoryClassBytes = 256_000_000,
        measuredPssBytes = 64_000_000,
        measuredRssBytes = measuredRssBytes,
        networkPermissionPresent = false,
        assets = emptyList(),
    )
}
