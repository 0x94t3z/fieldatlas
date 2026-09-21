package xyz.fieldatlas.diagnostics

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.research.ResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics

object DiagnosticsProvider {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private val privatePath = Regex("(?:(?:[A-Za-z]:)?[/\\\\])(?:[^\\s:]+[/\\\\])*[^\\s:]+")

    fun create(
        input: DiagnosticsInput,
        assets: List<InstalledAsset>,
        latestResearchMetrics: ResearchMetrics? = null,
        latestResearchCompletion: ResearchCompletion? = null,
        latestQuestion: String? = null,
        includeQuestion: Boolean = false,
        notices: List<String> = emptyList(),
    ): DiagnosticsSnapshot = create(
        appVersion = input.appVersion,
        device = input.device,
        apiLevel = input.apiLevel,
        supportedAbis = input.supportedAbis,
        deviceTotalMemoryBytes = input.deviceTotalMemoryBytes,
        memoryClassBytes = input.memoryClassBytes,
        measuredPssBytes = input.measuredPssBytes,
        measuredRssBytes = input.measuredRssBytes,
        networkPermissionPresent = input.networkPermissionPresent,
        assets = assets,
        cleartextTrafficPermitted = input.cleartextTrafficPermitted,
        latestResearchMetrics = latestResearchMetrics,
        latestResearchCompletion = latestResearchCompletion,
        latestQuestion = latestQuestion,
        includeQuestion = includeQuestion,
        notices = notices,
    )

    fun create(
        appVersion: String,
        device: String,
        apiLevel: Int,
        supportedAbis: List<String>,
        deviceTotalMemoryBytes: Long,
        memoryClassBytes: Long,
        measuredPssBytes: Long,
        measuredRssBytes: Long?,
        networkPermissionPresent: Boolean,
        assets: List<InstalledAsset>,
        cleartextTrafficPermitted: Boolean = false,
        latestResearchMetrics: ResearchMetrics? = null,
        latestResearchCompletion: ResearchCompletion? = null,
        latestQuestion: String? = null,
        includeQuestion: Boolean = false,
        notices: List<String> = emptyList(),
    ): DiagnosticsSnapshot {
        val summaries = assets.map {
            InstalledAssetSummary(it.id, it.version, it.type, it.installedBytes, it.manifestSha256)
        }.sortedWith(compareBy(InstalledAssetSummary::id, InstalledAssetSummary::version))
        val total = summaries.fold(0L) { sum, asset -> Math.addExact(sum, asset.installedBytes) }
        return DiagnosticsSnapshot(
            appVersion = appVersion,
            device = device,
            apiLevel = apiLevel,
            supportedAbis = supportedAbis.distinct().sorted(),
            deviceTotalMemoryBytes = deviceTotalMemoryBytes,
            memoryClassBytes = memoryClassBytes,
            measuredPssBytes = measuredPssBytes,
            measuredRssBytes = measuredRssBytes,
            totalInstalledBytes = total,
            networkPermissionPresent = networkPermissionPresent,
            cleartextTrafficPermitted = cleartextTrafficPermitted,
            assets = summaries,
            latestResearchMetrics = latestResearchMetrics?.copy(
                citedSourceIds = latestResearchMetrics.citedSourceIds.toSortedSet(),
            ),
            latestResearchCompletion = latestResearchCompletion,
            latestQuestion = questionForExport(latestQuestion, includeQuestion),
            notices = notices.map(::sanitize).sorted(),
        )
    }

    fun questionForExport(question: String?, include: Boolean): String? =
        question?.takeIf { include }?.trim()?.takeIf(String::isNotEmpty)

    fun toJson(snapshot: DiagnosticsSnapshot): String = json.encodeToString(snapshot) + "\n"

    private fun sanitize(value: String): String = value.replace(privatePath, "<private-path>").take(500)
}
