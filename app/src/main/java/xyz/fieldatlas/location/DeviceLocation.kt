package xyz.fieldatlas.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import xyz.fieldatlas.research.GeoPoint

/**
 * The phone's position for "near me" questions, from Android's own LocationManager. GPS works
 * without a network or Google Play Services; nothing here sends the position anywhere.
 *
 * A fresh fix is preferred. A recent last-known fix is used if no fix arrives in time, but an
 * old one is not: a traveller's position from yesterday would answer for the wrong city.
 */
class DeviceLocation(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Permission is checked on entry and every provider call is wrapped for SecurityException
    // (the user can revoke it mid-call); lint cannot see through runCatching.
    @SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!manager.isLocationEnabled) return null
        // GPS needs precise permission; with approximate only, the system still serves the
        // network and passive providers (fuzzed to about 2 km), which suits city answers.
        val providers = buildList {
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val recent = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.elapsedRealtimeNanos }
        if (recent != null && ageMillis(recent) <= FRESH_MILLIS) return recent.toPoint()

        val fresh = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }?.let { provider ->
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { requestFix(manager, provider) }
        }
        return (fresh ?: recent?.takeIf { ageMillis(it) <= USABLE_MILLIS })?.toPoint()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                manager.getCurrentLocation(provider, cancellation, executor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (error: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (error: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun ageMillis(location: Location) =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L

    private fun Location.toPoint(): GeoPoint? = runCatching { GeoPoint(latitude, longitude) }.getOrNull()

    private companion object {
        const val FRESH_MILLIS = 10 * 60 * 1000L
        const val USABLE_MILLIS = 2 * 60 * 60 * 1000L
        const val FIX_TIMEOUT_MILLIS = 20_000L
    }
}
