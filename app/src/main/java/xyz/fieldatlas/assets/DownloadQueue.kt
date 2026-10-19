package xyz.fieldatlas.assets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.fieldatlas.speech.NaturalVoice

/** Something Library can download: an answer model, a knowledge collection, or the natural reading voice. */
sealed interface DownloadItem {
    val key: String
    val title: String
    val bytes: Long

    data class Model(val offer: ModelOffer) : DownloadItem {
        override val key: String get() = "model:${offer.id}:${offer.version}"
        override val title: String get() = offer.name
        override val bytes: Long get() = offer.bytes
    }

    data class Knowledge(val pack: KnowledgeCatalogEntry) : DownloadItem {
        override val key: String get() = "knowledge:${pack.id}:${pack.version}"
        override val title: String get() = pack.title
        override val bytes: Long get() = pack.bytes
    }

    data object Voice : DownloadItem {
        override val key: String get() = "voice:${NaturalVoice.ID}"
        // Named by its model, as the answer models are; what it does is said beside it.
        override val title: String get() = NaturalVoice.NAME
        override val bytes: Long get() = NaturalVoice.BYTES
    }
}

data class DownloadFailure(val item: DownloadItem, val message: String)

data class DownloadQueueState(
    /** The download running now, or held until Wi-Fi ([waitingForWifi]). */
    val active: DownloadItem? = null,
    val bytes: Long = 0,
    /** Seconds left at the recent speed; null until a few seconds of transfer have been seen. */
    val etaSeconds: Long? = null,
    val waitingForWifi: Boolean = false,
    /** Asked for while another was running; each starts by itself, in order. */
    val queued: List<DownloadItem> = emptyList(),
    /** The last failure of each item, until it is retried or dismissed. Saved bytes are always kept. */
    val failures: Map<String, DownloadFailure> = emptyMap(),
    val onlyOnWifi: Boolean = true,
) {
    /** All bytes are in; the file is being checked against its fingerprint and installed. */
    val checking: Boolean get() = active != null && !waitingForWifi && bytes >= active.bytes

    fun isActive(item: DownloadItem) = active?.key == item.key
    fun queuePosition(item: DownloadItem) = queued.indexOfFirst { it.key == item.key }
}

/** How a download ended, as the engine running it reports. */
sealed interface DownloadOutcome {
    data object Done : DownloadOutcome
    data object Stopped : DownloadOutcome
    data class Failed(val message: String) : DownloadOutcome
}

/** Starts and stops the actual transfers; [DownloadQueue] only decides which, and when. */
interface DownloadEngine {
    /** False when the transfer could not start; [failure] then says why. */
    fun start(item: DownloadItem): Boolean
    fun failure(item: DownloadItem): String?
    /** Stops [item], keeping its saved bytes; [keepForLater] also keeps it to resume when the app reopens. */
    fun stop(item: DownloadItem, keepForLater: Boolean)
}

/**
 * One download at a time, the rest waiting their turn: a second download asked for while one runs is queued rather
 * than refused, and starts by itself. With [DownloadQueueState.onlyOnWifi], a download on mobile data (or with no
 * connection) waits, and one under way stops when the phone leaves Wi-Fi and carries on when it returns. Pausing
 * the running download lets the next one start. Everything runs on one thread (the main thread in the app).
 */
class DownloadQueue(
    private val engine: DownloadEngine,
    onlyOnWifi: Boolean,
    private val persistOnlyOnWifi: (Boolean) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val onFinished: (DownloadItem) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(DownloadQueueState(onlyOnWifi = onlyOnWifi))
    val state: StateFlow<DownloadQueueState> = mutableState

    private var unmetered = false
    /** Stopped by the queue itself to wait for Wi-Fi: its end is not a pause by the person. */
    private var holding: String? = null
    /** Allowed on mobile data this once, because the person said so. */
    private val mobileAllowed = mutableSetOf<String>()
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** Bytes per second over the last transfer, for estimating the next one before it starts. */
    var lastSpeed: Long? = null
        private set

    /** Asks for [item]; [allowMobileData] lets this one run on mobile data even with "only on Wi-Fi" on. */
    fun enqueue(item: DownloadItem, allowMobileData: Boolean = false) {
        if (allowMobileData) mobileAllowed += item.key
        val current = mutableState.value
        if (current.isActive(item) || current.queuePosition(item) >= 0) return
        val base = current.copy(failures = current.failures - item.key)
        if (base.active == null) begin(item, base) else mutableState.value = base.copy(queued = base.queued + item)
    }

    /** Stops the running download (its saved bytes stay) and starts the next in line. */
    fun pause() {
        val current = mutableState.value
        val item = current.active ?: return
        if (current.waitingForWifi) {
            advance(current.copy(active = null, bytes = 0, waitingForWifi = false))
            return
        }
        engine.stop(item, keepForLater = false)
    }

    fun pauseAll() {
        mutableState.value = mutableState.value.copy(queued = emptyList())
        pause()
    }

    /** Takes a waiting download out of the line. */
    fun remove(item: DownloadItem) {
        val current = mutableState.value
        mutableState.value = current.copy(queued = current.queued.filterNot { it.key == item.key })
    }

    fun dismissFailure(item: DownloadItem) {
        val current = mutableState.value
        mutableState.value = current.copy(failures = current.failures - item.key)
    }

    fun setOnlyOnWifi(on: Boolean) {
        persistOnlyOnWifi(on)
        val current = mutableState.value.copy(onlyOnWifi = on)
        mutableState.value = current
        val item = current.active ?: return
        if (!on && current.waitingForWifi) begin(item, current)
        else if (on && !current.waitingForWifi && !unmetered && item.key !in mobileAllowed) hold(item, current)
    }

    /** The waiting download goes ahead on mobile data, this once. */
    fun useMobileDataNow() {
        val current = mutableState.value
        val item = current.active ?: return
        if (!current.waitingForWifi) return
        mobileAllowed += item.key
        begin(item, current)
    }

    fun onNetwork(unmetered: Boolean) {
        this.unmetered = unmetered
        val current = mutableState.value
        val item = current.active ?: return
        if (current.waitingForWifi && unmetered) begin(item, current)
        else if (!current.waitingForWifi && current.onlyOnWifi && !unmetered && item.key !in mobileAllowed) hold(item, current)
    }

    /** A transfer the engine is running that the queue did not start (one resumed when the app opened). */
    fun adopt(item: DownloadItem, bytes: Long) {
        val current = mutableState.value
        if (current.active == null) mutableState.value = current.copy(active = item, bytes = bytes, waitingForWifi = false,
            queued = current.queued.filterNot { it.key == item.key })
    }

    fun onProgress(item: DownloadItem, bytes: Long) {
        val current = mutableState.value
        if (!current.isActive(item) || current.waitingForWifi) return
        val at = now()
        samples.addLast(at to bytes)
        while (samples.size > 2 && at - samples.first().first > SPEED_WINDOW_MS) samples.removeFirst()
        val (since, from) = samples.first()
        val speed = if (at - since >= MIN_SPEED_SPAN_MS && bytes > from) (bytes - from) * 1000 / (at - since) else null
        speed?.let { lastSpeed = it }
        mutableState.value = current.copy(bytes = bytes,
            etaSeconds = speed?.let { ((item.bytes - bytes).coerceAtLeast(0) / it.coerceAtLeast(1)) } ?: current.etaSeconds)
    }

    fun onEnded(item: DownloadItem, outcome: DownloadOutcome) {
        if (holding == item.key) {
            holding = null
            // A failure while being stopped for Wi-Fi is not news; it waits like any other.
            if (outcome != DownloadOutcome.Done) return
        }
        val current = mutableState.value
        if (!current.isActive(item) || current.waitingForWifi && outcome != DownloadOutcome.Done) return
        mobileAllowed -= item.key
        val failures = when (outcome) {
            is DownloadOutcome.Failed -> current.failures + (item.key to DownloadFailure(item, outcome.message))
            else -> current.failures - item.key
        }
        if (outcome == DownloadOutcome.Done) onFinished(item)
        advance(current.copy(active = null, bytes = 0, etaSeconds = null, waitingForWifi = false, failures = failures))
    }

    private fun hold(item: DownloadItem, current: DownloadQueueState) {
        holding = item.key
        mutableState.value = current.copy(waitingForWifi = true, etaSeconds = null)
        engine.stop(item, keepForLater = true)
    }

    private fun begin(item: DownloadItem, base: DownloadQueueState) {
        samples.clear()
        val rest = base.queued.filterNot { it.key == item.key }
        if (base.onlyOnWifi && !unmetered && item.key !in mobileAllowed) {
            mutableState.value = base.copy(active = item, bytes = 0, etaSeconds = null, waitingForWifi = true, queued = rest)
            return
        }
        mutableState.value = base.copy(active = item, bytes = 0, etaSeconds = null, waitingForWifi = false, queued = rest)
        if (!engine.start(item)) {
            val message = engine.failure(item) ?: "Couldn't start the download. Try again."
            advance(mutableState.value.copy(active = null, failures = mutableState.value.failures +
                (item.key to DownloadFailure(item, message))))
        }
    }

    private fun advance(base: DownloadQueueState) {
        val next = base.queued.firstOrNull()
        if (next == null) mutableState.value = base.copy(active = null, bytes = 0, etaSeconds = null, waitingForWifi = false)
        else begin(next, base)
    }

    private companion object {
        const val SPEED_WINDOW_MS = 20_000L
        const val MIN_SPEED_SPAN_MS = 3_000L
    }
}
