package xyz.fieldatlas.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.research.ResearchCompletion
import xyz.fieldatlas.research.ResearchMetrics

object AndroidDiagnosticsCollector {
    fun collect(
        context: Context,
        assets: List<InstalledAsset>,
        latestResearchMetrics: ResearchMetrics? = null,
        latestResearchCompletion: ResearchCompletion? = null,
        latestQuestion: String? = null,
        includeQuestion: Boolean = false,
        notices: List<String> = emptyList(),
    ): DiagnosticsSnapshot {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return DiagnosticsProvider.create(
            appVersion = packageInfo.versionName ?: packageInfo.longVersionCode.toString(),
            device = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            deviceTotalMemoryBytes = memoryInfo.totalMem,
            memoryClassBytes = Math.multiplyExact(activityManager.memoryClass.toLong(), MEBIBYTE),
            measuredPssBytes = Math.multiplyExact(Debug.getPss(), KIBIBYTE),
            measuredRssBytes = if (Build.VERSION.SDK_INT >= 35) {
                Math.multiplyExact(Debug.getRss(), KIBIBYTE)
            } else {
                null
            },
            networkPermissionPresent = packageManager.checkPermission(
                Manifest.permission.INTERNET,
                context.packageName,
            ) == PackageManager.PERMISSION_GRANTED,
            assets = assets,
            cleartextTrafficPermitted = context.applicationInfo.flags and
                ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
            latestResearchMetrics = latestResearchMetrics,
            latestResearchCompletion = latestResearchCompletion,
            latestQuestion = latestQuestion,
            includeQuestion = includeQuestion,
            notices = notices,
        )
    }

    private const val KIBIBYTE = 1024L
    private const val MEBIBYTE = 1024L * KIBIBYTE
}
