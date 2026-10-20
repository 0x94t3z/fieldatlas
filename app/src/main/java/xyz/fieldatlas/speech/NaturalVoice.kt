package xyz.fieldatlas.speech

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.fieldatlas.assets.AssetImportException
import xyz.fieldatlas.assets.BudgetDecision
import xyz.fieldatlas.assets.StorageBudget
import xyz.fieldatlas.assets.VerifiedResumableDownload
import xyz.fieldatlas.ui.setup.AssetAction
import xyz.fieldatlas.ui.setup.assetSetupError

/**
 * The natural reading voice: Supertonic 3 (int8) run by sherpa-onnx, voice F1 at its default five steps and normal
 * speed, the same voice the bundled emergency guides were recorded in. Its six files (about 145 MB) are downloaded
 * once, on request, each pinned by size and SHA-256 and resumable; after that it reads with no signal.
 */
class NaturalVoice internal constructor(
    filesDir: File,
    private val downloader: VerifiedResumableDownload = VerifiedResumableDownload(),
    private val scope: CoroutineScope,
    /** What Field Atlas holds now, counted as for models and collections, against the 50 GB limit they share. */
    private val footprint: () -> Long = { 0L },
    /**
     * Held while the voice claims storage, as a model, a collection or an import holds it: two at once would each
     * pass the limit check without counting the other's bytes.
     */
    private val provisioning: Mutex = Mutex(),
) {
    internal data class Part(val name: String, val bytes: Long, val sha256: String)

    sealed interface State {
        /** Not on the phone; [savedBytes] of an earlier attempt are kept to resume. */
        data class Missing(val savedBytes: Long = 0) : State
        data class Downloading(val bytes: Long) : State
        data object Ready : State
        data class Failed(val message: String, val savedBytes: Long) : State
    }

    class Audio(val samples: FloatArray, val sampleRate: Int)

    private val dir = File(filesDir, "voices/$ID")
    private val staging = File(filesDir, "voice-downloads/$ID")
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<State> = mutableState
    private var job: Job? = null

    val ready: Boolean get() = mutableState.value == State.Ready

    private fun installed() = File(dir, READY_MARK).isFile && PARTS.all { File(dir, it.name).length() == it.bytes }

    private fun initialState(): State = if (installed()) State.Ready else State.Missing(savedBytes())

    /** Bytes already on the phone toward the download: finished files and the part of the one in progress. */
    fun savedBytes(): Long = PARTS.sumOf { part ->
        File(dir, part.name).takeIf { it.length() == part.bytes }?.length() ?: File(staging, "${part.name}.part").length().coerceAtMost(part.bytes)
    }

    /** [source] is the folder holding the parts, ending in "/"; every part is still checked against its size and SHA-256. */
    fun download(source: String = DEFAULT_SOURCE) {
        if (job?.isActive == true || ready) return
        val previous = job
        mutableState.value = State.Downloading(savedBytes())
        job = scope.launch {
            val self = coroutineContext[Job]!!
            // A paused run can still be inside a stalled read; it lets go of the part file before this one writes to it.
            previous?.join()
            try {
                provisioning.withLock { withContext(Dispatchers.IO) {
                    val remaining = BYTES - savedBytes()
                    // The same 50 GB limit as a model or a collection: the voice's bytes count toward theirs, and theirs toward it.
                    admit(remaining + StorageBudget.METADATA_RESERVE_BYTES)
                    check(dir.parentFile!!.let { it.mkdirs() || it.isDirectory } && filesDirFree() - FREE_MARGIN >= remaining) {
                        "Not enough free space for the voice. It needs about ${remaining / 1_000_000 + FREE_MARGIN / 1_000_000} MB free."
                    }
                    check((dir.mkdirs() || dir.isDirectory) && (staging.mkdirs() || staging.isDirectory)) { "Could not create the voice folder" }
                    var done = 0L
                    for (part in PARTS) {
                        val target = File(dir, part.name)
                        if (target.length() != part.bytes) {
                            val partial = File(staging, "${part.name}.part")
                            downloader.download(source + part.name, part.bytes, part.sha256, partial) { bytes ->
                                // A read already under way when Pause was tapped must not bring the progress back.
                                if (self.isActive) mutableState.value = State.Downloading(done + bytes)
                            }
                            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                        }
                        done += part.bytes
                        if (self.isActive) mutableState.value = State.Downloading(done)
                    }
                    // History and files can grow during the download; counted again before the voice is marked ready. Its
                    // files stay, so a retry after freeing space only finishes.
                    admit(StorageBudget.METADATA_RESERVE_BYTES)
                    File(dir, READY_MARK).writeText(REVISION)
                    staging.deleteRecursively()
                } }
                mutableState.value = State.Ready
            } catch (cancelled: CancellationException) {
                if (job === self) mutableState.value = State.Missing(savedBytes())
                throw cancelled
            } catch (error: Exception) {
                if (job === self) mutableState.value = State.Failed(assetSetupError(error, AssetAction.VOICE_DOWNLOAD), savedBytes())
            } finally {
                if (job === self) job = null
            }
        }
    }

    /** Stops the download; what was fetched stays, to resume. */
    fun pauseDownload() {
        // A stalled connection only notices the cancel when its read times out; the card shows Paused at once.
        job?.cancel()
        mutableState.value = State.Missing(savedBytes())
    }

    /** Frees the space: the voice, and any part of a download. Reading falls back to the phone's voice. */
    suspend fun remove() {
        job?.cancelAndJoin()
        withContext(engine) {
            tts?.release(); tts = null
        }
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            staging.deleteRecursively()
        }
        mutableState.value = State.Missing()
    }

    // ---------- speaking ----------

    // One thread owns the native engine: it is not safe to call from two at once, and keeping it on one thread lets
    // the next sentence be made while the last one plays.
    private val engine = Executors.newSingleThreadExecutor { Thread(it, "natural-voice").apply { priority = Thread.NORM_PRIORITY + 1 } }
        .asCoroutineDispatcher()
    private var tts: OfflineTts? = null

    /** Makes one piece of speech; the first call loads the voice (a few seconds on an older phone). */
    suspend fun speak(text: String, speed: Float): Audio = withContext(engine) {
        check(ready) { "$NAME is not on this phone" }
        val began = android.os.SystemClock.elapsedRealtime()
        val audio = loaded().generateWithConfig(text, GenerationConfig(sid = STYLE_F1, numSteps = STEPS, speed = speed, extra = mapOf("lang" to "en")))
        // Lengths and times only, never the words: how fast this phone makes speech is what is worth knowing.
        android.util.Log.d(LOG_TAG, "made ${audio.samples.size * 1000L / audio.sampleRate.coerceAtLeast(1)} ms of speech from " +
            "${text.length} characters in ${android.os.SystemClock.elapsedRealtime() - began} ms")
        Audio(audio.samples, audio.sampleRate)
    }

    /**
     * Loads the voice ahead of time and makes one short phrase with it, so the first words of a reading come without
     * the wait for loading it or for the engine's first run.
     */
    suspend fun warmUp() = withContext(engine) {
        if (!ready || tts != null) return@withContext
        val began = android.os.SystemClock.elapsedRealtime()
        loaded().generateWithConfig("Ready.", GenerationConfig(sid = STYLE_F1, numSteps = STEPS, extra = mapOf("lang" to "en")))
        android.util.Log.d(LOG_TAG, "loaded and warmed in ${android.os.SystemClock.elapsedRealtime() - began} ms")
    }

    // Measured on a Redmi 13C (two A75 and six A55 cores): the voice ran at 2.4x real time on the little cores and
    // 1.1x on the big ones, with as many threads as big cores (four threads on two cores ran at 2.8x).
    private val fastCores = CpuAffinity.fastCoreMask()

    private fun loaded(): OfflineTts = tts ?: run {
        // On the voice's own thread, before the engine starts its threads: they inherit these cores.
        val pinned = CpuAffinity.pin(fastCores)
        android.util.Log.d(LOG_TAG, "fast cores ${java.lang.Long.toBinaryString(fastCores)}, pinned $pinned")
        createEngine(if (pinned) java.lang.Long.bitCount(fastCores) else Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
    }

    private fun createEngine(threads: Int): OfflineTts = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
        supertonic = OfflineTtsSupertonicModelConfig(
            durationPredictor = path("duration_predictor.int8.onnx"), textEncoder = path("text_encoder.int8.onnx"),
            vectorEstimator = path("vector_estimator.int8.onnx"), vocoder = path("vocoder.int8.onnx"),
            ttsJson = path("tts.json"), unicodeIndexer = path("unicode_indexer.bin"), voiceStyle = path("voice.bin"),
        ),
        numThreads = threads,
    ))).also { tts = it }

    /** Lets go of the loaded voice's memory (about 300 MB) when nothing is being read. */
    suspend fun unload() = withContext(engine) { tts?.release(); tts = null }

    private fun path(name: String) = File(dir, name).absolutePath

    private fun filesDirFree() = dir.parentFile!!.usableSpace

    /** Refuses [incoming] bytes that would take Field Atlas past its 50 GB limit, with the limit's own message. */
    private fun admit(incoming: Long) {
        val decision = StorageBudget.evaluate(footprint(), incoming, Long.MAX_VALUE)
        if (decision != BudgetDecision.Allowed) throw AssetImportException("The voice does not fit the storage limit", budgetDecision = decision)
    }

    companion object {
        const val ID = "supertonic-3-int8"
        /** What every screen calls the voice: its model's name, as the answer models go by theirs. */
        const val NAME = "Supertonic 3"
        private const val LOG_TAG = "FieldAtlasVoice"
        const val STYLE_F1 = 0
        const val STEPS = 5
        const val REVISION = "cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
        private const val BASE_URL = "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/$REVISION"
        private const val READY_MARK = "installed"
        private const val FREE_MARGIN = 200_000_000L
        internal val PARTS = listOf(
            Part("tts.json", 8_253, "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
            Part("unicode_indexer.bin", 262_144, "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
            Part("voice.bin", 517_168, "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
            Part("duration_predictor.int8.onnx", 3_700_147, "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
            Part("vocoder.int8.onnx", 25_991_073, "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
            Part("text_encoder.int8.onnx", 36_416_150, "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
            Part("vector_estimator.int8.onnx", 78_400_833, "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
        )
        val BYTES: Long = PARTS.sumOf { it.bytes }
        const val DEFAULT_SOURCE = "$BASE_URL/"
        /** One fingerprint for the whole voice, so a saved mirror is dropped when any part changes. */
        val FINGERPRINT: String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(PARTS.joinToString("\n") { "${it.name} ${it.bytes} ${it.sha256}" }.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
