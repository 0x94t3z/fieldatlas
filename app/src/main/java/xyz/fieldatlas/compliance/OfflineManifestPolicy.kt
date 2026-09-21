package xyz.fieldatlas.compliance

data class ManifestSnapshot(
    val permissions: Set<String>,
    val usesCleartextTraffic: Boolean,
)

object OfflineManifestPolicy {
    private val forbiddenPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
    )

    fun violations(snapshot: ManifestSnapshot): List<String> = buildList {
        snapshot.permissions
            .filter { it in forbiddenPermissions }
            .sorted()
            .forEach { add("forbidden permission: $it") }
        if (snapshot.usesCleartextTraffic) {
            add("cleartext traffic must be disabled")
        }
    }
}
