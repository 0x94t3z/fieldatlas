package xyz.fieldatlas.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineAcceptanceTest {
    @Test fun installedArtifactHasNoNetworkPermissionOrPlayServicesAndContainsArmRuntime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        assertFalse(
            packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName) ==
                PackageManager.PERMISSION_GRANTED,
        )
        assertFalse(
            packageManager.checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, context.packageName) ==
                PackageManager.PERMISSION_GRANTED,
        )
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
        assertFalse(runCatching { Class.forName("com.google.android.gms.common.GoogleApiAvailability") }.isSuccess)
        val supportedAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" }
        assertTrue(supportedAbi != null)
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        assertTrue(File(nativeLibraryDir, "libai-chat.so").isFile)
        assertTrue(
            nativeLibraryDir.listFiles().orEmpty().any { file ->
                file.isFile && file.name.startsWith("libggml-cpu-") && file.name.endsWith(".so")
            },
        )
    }
}
