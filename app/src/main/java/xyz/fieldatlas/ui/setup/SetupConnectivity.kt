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
    return remember(context) { setupConnectivity(context) }.collectAsState(initial = true).value
}

/** Observe connectivity only; this does not initiate downloads or refreshes. */
internal fun setupConnectivity(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    fun publish() {
        trySend(manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
    }
    manager.registerDefaultNetworkCallback(callback)
    publish()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
