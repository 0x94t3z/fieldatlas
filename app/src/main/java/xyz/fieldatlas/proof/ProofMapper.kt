package xyz.fieldatlas.proof

import java.util.Locale
import xyz.fieldatlas.diagnostics.DiagnosticsSnapshot

object ProofMapper {
    fun map(snapshot: DiagnosticsSnapshot): ProofModel = ProofModel(
        offline = buildList {
            add(
                ProofFact(
                    label = "Internet permission",
                    value = if (snapshot.networkPermissionPresent) "Present" else "Absent",
                    origin = ProofOrigin.ManifestAudit,
                    state = if (snapshot.networkPermissionPresent) ProofState.Warning else ProofState.Pass,
                ),
            )
            add(
                ProofFact(
                    label = "Cleartext traffic",
                    value = if (snapshot.cleartextTrafficPermitted) "Permitted" else "Disabled",
                    origin = ProofOrigin.ManifestAudit,
                    state = if (snapshot.cleartextTrafficPermitted) ProofState.Warning else ProofState.Pass,
                ),
            )
            add(
                ProofFact(
                    label = "Installed assets",
                    value = "${snapshot.assets.size} verified · ${formatBytes(snapshot.totalInstalledBytes)}",
                    origin = ProofOrigin.ManifestAudit,
                    state = if (snapshot.assets.isEmpty()) ProofState.Missing else ProofState.Pass,
                ),
            )
            snapshot.assets.forEach { asset ->
                add(
                    ProofFact(
                        label = asset.id,
                        value = "${asset.type.name.lowercase()} ${asset.version} · ${asset.manifestSha256.take(12)}…",
                        origin = ProofOrigin.ManifestAudit,
                        state = ProofState.Pass,
                    ),
                )
            }
        },
        device = listOf(
            fact("Device", snapshot.device, ProofOrigin.OsReport),
            fact("Android API", snapshot.apiLevel.toString(), ProofOrigin.OsReport),
            fact("ABIs", snapshot.supportedAbis.joinToString(), ProofOrigin.OsReport),
            fact("Physical memory", formatBytes(snapshot.deviceTotalMemoryBytes), ProofOrigin.OsReport),
            fact("App memory class", formatBytes(snapshot.memoryClassBytes), ProofOrigin.OsReport),
            fact("PSS", formatBytes(snapshot.measuredPssBytes), ProofOrigin.MeasuredQuery),
            ProofFact(
                label = "RSS",
                value = snapshot.measuredRssBytes?.let(::formatBytes) ?: "Not measured",
                origin = ProofOrigin.MeasuredQuery,
                state = if (snapshot.measuredRssBytes == null) ProofState.Missing else ProofState.Info,
            ),
        ),
        latestRun = buildList {
            val completion = snapshot.latestResearchCompletion
            add(
                ProofFact(
                    label = "Status",
                    value = completion?.name ?: "No run recorded",
                    origin = ProofOrigin.MeasuredQuery,
                    state = if (completion == null) ProofState.Missing else ProofState.Info,
                ),
            )
            snapshot.latestResearchMetrics?.let { metrics ->
                add(fact("Retrieval", "${metrics.retrievalMillis} ms", ProofOrigin.MeasuredQuery))
                add(fact("Total", "${metrics.totalMillis} ms", ProofOrigin.MeasuredQuery))
                add(fact("Generated tokens", metrics.generatedTokenCount.toString(), ProofOrigin.MeasuredQuery))
                add(
                    ProofFact(
                        label = "Citation mapping",
                        value = if (metrics.hasUnmappedCitation) "Unmapped marker found" else "All markers mapped",
                        origin = ProofOrigin.MeasuredQuery,
                        state = if (metrics.hasUnmappedCitation) ProofState.Warning else ProofState.Pass,
                    ),
                )
            }
        },
    )

    private fun fact(label: String, value: String, origin: ProofOrigin) =
        ProofFact(label, value, origin, ProofState.Info)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
