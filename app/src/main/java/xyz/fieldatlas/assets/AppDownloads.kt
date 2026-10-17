package xyz.fieldatlas.assets

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.fieldatlas.speech.NaturalVoice

/**
 * The app's [DownloadQueue], wired to the transfers that already exist: models and collections run in
 * [BackgroundAssetDownloads] (with its foreground service and resume after process death), the natural voice in
 * [NaturalVoice]. Their states are watched here and reported to the queue, and the default network decides when
 * "only on Wi-Fi" lets a download run.
 */
class AppDownloads(
    private val context: Context,
    private val background: BackgroundAssetDownloads,
    private val voice: NaturalVoice,
    private val isInstalled: (DownloadItem) -> Boolean,
    onFinished: (DownloadItem) -> Unit,
    /** The folder the voice's files come from: the default, or a mirror saved in Download sources. */
    private val voiceSource: () -> String = { NaturalVoice.DEFAULT_SOURCE },
) {
    private val preferences = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    // Main, not Main.immediate: a state change the queue makes while handling one event is seen after it, never inside it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val engine = object : DownloadEngine {
        override fun start(item: DownloadItem): Boolean = when (item) {
            is DownloadItem.Model -> background.startModel(item.offer)
            is DownloadItem.Knowledge -> background.startKnowledge(item.pack)
            DownloadItem.Voice -> {
                // Already whole (finished while the queue was not looking): report it rather than wait for a change.
                if (voice.ready) scope.launch { queue.onEnded(DownloadItem.Voice, DownloadOutcome.Done) }
                voice.download(voiceSource())
                // The voice has no resume after process death of its own, but the service keeps the process (and its
                // notification) alive while it downloads. Started from the background it can be refused; the transfer
                // still runs while the app does.
                if (!AssetDownloadService.running) runCatching { AssetDownloadService.start(context) }
                true
            }
        }

        override fun failure(item: DownloadItem): String? = background.state.value.error

        override fun stop(item: DownloadItem, keepForLater: Boolean) = when (item) {
            DownloadItem.Voice -> voice.pauseDownload()
            else -> background.cancel(keepPending = keepForLater)
        }
    }

    val queue: DownloadQueue = DownloadQueue(engine, preferences.getBoolean(ONLY_ON_WIFI, true),
        persistOnlyOnWifi = { preferences.edit().putBoolean(ONLY_ON_WIFI, it).apply() },
        onFinished = { item -> preferences.edit().putLong(LAST_SPEED, queueSpeed()).apply(); onFinished(item) })

    val state: StateFlow<DownloadQueueState> get() = queue.state

    /** Where the voice will be fetched from, for the confirm step and the source line. */
    fun voiceSourceUrl(): String = voiceSource()

    /** Bytes per second the last download reached, for the wait shown before the next one starts. */
    val knownSpeed: Long? get() = queue.lastSpeed ?: preferences.getLong(LAST_SPEED, 0).takeIf { it > 0 }

    private fun queueSpeed(): Long = queue.lastSpeed ?: preferences.getLong(LAST_SPEED, 0)

    /** True on Wi-Fi or another network that does not count against a data plan. */
    var unmetered: Boolean = false
        private set

    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        unmetered = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)?.isUnmetered() == true
        queue.onNetwork(unmetered)
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = report(capabilities.isUnmetered())
            override fun onLost(network: Network) = report(false)
        })
        scope.launch { watchBackground() }
        scope.launch { watchVoice() }
    }

    private fun report(unmetered: Boolean) {
        scope.launch {
            if (this@AppDownloads.unmetered == unmetered) return@launch
            this@AppDownloads.unmetered = unmetered
            queue.onNetwork(unmetered)
        }
    }

    private suspend fun watchBackground() {
        var running: DownloadItem? = null
        background.state.collect { status ->
            val item = status.model?.let { DownloadItem.Model(it) } ?: status.pack?.let { DownloadItem.Knowledge(it) }
            if (status.kind != null && item != null) {
                if (running?.key != item.key) { running = item; queue.adopt(item, status.downloadedBytes) }
                queue.onProgress(item, status.downloadedBytes)
            } else running?.let { ended ->
                running = null
                val failedHere = status.error != null && (status.errorModel?.let { DownloadItem.Model(it).key } ?:
                    status.errorPack?.let { DownloadItem.Knowledge(it).key }) == ended.key
                queue.onEnded(ended, when {
                    failedHere -> DownloadOutcome.Failed(status.error!!)
                    isInstalled(ended) -> DownloadOutcome.Done
                    else -> DownloadOutcome.Stopped
                })
            }
        }
    }

    private suspend fun watchVoice() {
        var downloading = false
        voice.state.collect { state ->
            when (state) {
                is NaturalVoice.State.Downloading -> {
                    if (!downloading) { downloading = true; queue.adopt(DownloadItem.Voice, state.bytes) }
                    queue.onProgress(DownloadItem.Voice, state.bytes)
                }
                NaturalVoice.State.Ready -> if (downloading) { downloading = false; queue.onEnded(DownloadItem.Voice, DownloadOutcome.Done) }
                is NaturalVoice.State.Failed -> if (downloading) { downloading = false; queue.onEnded(DownloadItem.Voice, DownloadOutcome.Failed(state.message)) }
                is NaturalVoice.State.Missing -> if (downloading) { downloading = false; queue.onEnded(DownloadItem.Voice, DownloadOutcome.Stopped) }
            }
        }
    }

    private fun NetworkCapabilities.isUnmetered() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

    private companion object {
        const val ONLY_ON_WIFI = "only-on-wifi"
        const val LAST_SPEED = "last-bytes-per-second"
    }
}
