package xyz.fieldatlas.ui.setup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun rememberSetupConnectivity(): Boolean {
    val context = LocalContext.current.applicationContext
    // Starts from the real state, so an offline phone never flashes "Online" first.
    val initial = remember(context) { currentlyOnline(context) }
    return remember(context) { setupConnectivity(context) }.collectAsState(initial = initial).value
}

/** Observe connectivity only; this does not initiate downloads or refreshes. */
internal fun setupConnectivity(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    // Each event says what changed. Asking for the active network inside onLost can still return the network
    // just lost, which left the app showing "Online" after Wi-Fi was switched off.
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) { trySend(false) }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }
    trySend(currentlyOnline(context))
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

private fun currentlyOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
