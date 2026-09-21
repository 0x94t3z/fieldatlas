package xyz.fieldatlas.compliance

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineManifestPolicyTest {
    @Test
    fun acceptsAnAppWithoutNetworkPermissionOrCleartextTraffic() {
        val snapshot = ManifestSnapshot(
            permissions = setOf("android.permission.VIBRATE"),
            usesCleartextTraffic = false,
        )

        assertEquals(emptyList<String>(), OfflineManifestPolicy.violations(snapshot))
    }

    @Test
    fun rejectsEveryPermissionThatCanOpenNetworkSockets() {
        val dangerous = listOf(
            "android.permission.INTERNET",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )

        dangerous.forEach { permission ->
            val violations = OfflineManifestPolicy.violations(
                ManifestSnapshot(setOf(permission), usesCleartextTraffic = false),
            )
            assertEquals(listOf("forbidden permission: $permission"), violations)
        }
    }

    @Test
    fun rejectsCleartextTrafficEvenWithoutNetworkPermission() {
        val violations = OfflineManifestPolicy.violations(
            ManifestSnapshot(emptySet(), usesCleartextTraffic = true),
        )

        assertEquals(listOf("cleartext traffic must be disabled"), violations)
    }
}
