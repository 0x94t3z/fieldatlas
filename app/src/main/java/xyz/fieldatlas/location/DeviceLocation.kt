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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
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
        // GPS needs precise permission; with approximate only, the system still serves the fused,
        // network and passive providers (fuzzed to about 2 km), which suits city answers. The
        // fused provider is Android's own, so it works without Play Services (GrapheneOS).
        val providers = buildList {
            add(LocationManager.FUSED_PROVIDER)
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val recent = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.elapsedRealtimeNanos }
        if (recent != null && ageMillis(recent) <= FRESH_MILLIS) return recent.toPoint()

        // Every active provider is asked at once and the first fix wins: offline, the fused
        // provider may wait on GPS anyway, and a cold GPS indoors can take most of a minute.
        val active = providers.filter { it != LocationManager.PASSIVE_PROVIDER }
        val fresh = if (active.isEmpty()) null else withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            callbackFlow {
                val requests = active.map { provider -> launch { send(requestFix(manager, provider)) } }
                // Once every provider has answered, even with no fix, there is nothing left to wait for.
                launch { requests.joinAll(); close() }
                awaitClose()
            }.filterNotNull().firstOrNull()
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

    private fun Location.toPoint(): GeoPoint? = runCatching { GeoPoint(latitude, longitude, ageMillis(this) / 60_000L) }.getOrNull()

    private companion object {
        const val FRESH_MILLIS = 10 * 60 * 1000L
        const val USABLE_MILLIS = 2 * 60 * 60 * 1000L
        const val FIX_TIMEOUT_MILLIS = 45_000L
    }
}
