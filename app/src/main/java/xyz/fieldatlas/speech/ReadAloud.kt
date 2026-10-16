package xyz.fieldatlas.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.fieldatlas.assets.VerifiedResumableDownload
import xyz.fieldatlas.emergency.EmergencyGuide

/**
 * Reads a page aloud. An emergency guide plays its recording, made in the natural voice when the app was built, so it
 * starts at once and sounds the same on every phone. Anything else is read sentence by sentence by the voice chosen in
 * [settings]: the natural voice once it is downloaded ([natural]), otherwise the phone's own. One page reads at a time;
 * [state] says which page ([Reading.key]) and which sentence, so the page can mark it. Another app taking the audio (a
 * call) pauses the reading; leaving the page stops it ([stop]).
 */
class ReadAloud(private val context: Context, private val guides: () -> List<EmergencyGuide>) {
    enum class Voice { Recorded, Natural, Phone }

    /** The voice for answers. Null until the person picks one; the phone's voice reads meanwhile. */
    enum class Choice { Natural, Phone }

    data class Settings(
        val voice: Choice? = null,
        val speed: Float = 1.0f,
        /** An answer to a question asked with the microphone is read back when it is done. */
        val readVoiceAnswers: Boolean = true,
        /** An emergency guide starts reading as soon as it opens, instead of waiting for Read aloud. */
        val readGuidesAtOnce: Boolean = false,
    )

    data class Reading(
        val key: String,
        val segments: List<SpeechText.Segment>,
        val index: Int,
        val voice: Voice,
        val paused: Boolean = false,
        /** The natural voice is loading or making the first sentence; nothing is heard yet. */
        val preparing: Boolean = false,
        /** While [preparing]: how much of the speech needed before playing is ready, 0 to 1. */
        val prepared: Float = 0f,
        /** Elapsed and total time, known for a recording; for the other voices the sentence count stands in. */
        val positionMs: Long = 0,
        val durationMs: Long = 0,
    ) {
        val shown: String? get() = if (preparing) null else segments.getOrNull(index)?.shown
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<Reading?>(null)
    val state: StateFlow<Reading?> = mutableState

    val natural = NaturalVoice(context.filesDir, VerifiedResumableDownload(), scope)

    private val preferences = context.getSharedPreferences("reading-aloud", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = mutableSettings

    fun updateSettings(change: (Settings) -> Settings) {
        val next = change(mutableSettings.value)
        mutableSettings.value = next
        preferences.edit().putString(VOICE, next.voice?.name).putFloat(SPEED, next.speed)
            .putBoolean(READ_VOICE_ANSWERS, next.readVoiceAnswers).putBoolean(READ_GUIDES_AT_ONCE, next.readGuidesAtOnce).apply()
    }

    private fun loadSettings() = Settings(
        voice = preferences.getString(VOICE, null)?.let { name -> Choice.entries.firstOrNull { it.name == name } },
        speed = preferences.getFloat(SPEED, 1.0f).coerceIn(0.5f, 2.0f),
        readVoiceAnswers = preferences.getBoolean(READ_VOICE_ANSWERS, true),
        readGuidesAtOnce = preferences.getBoolean(READ_GUIDES_AT_ONCE, false),
    )

    /** Which voice an answer would be read in now. */
    val answerVoice: Voice get() = if (mutableSettings.value.voice == Choice.Natural && natural.ready) Voice.Natural else Voice.Phone

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            scope.launch {
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> stop()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause()
                }
            }
        }.build()

    // The natural voice makes speech on the CPU between sentences; with the screen off the phone must stay awake for it.
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fieldatlas:read-aloud").apply { setReferenceCounted(false) }

    private val manifest: GuideAudioManifest? by lazy {
        runCatching {
            context.assets.open("emergency/audio/manifest.json").bufferedReader().use { json.decodeFromString(GuideAudioManifest.serializer(), it.readText()) }
        }.getOrNull()
    }

    private val sampleTimings: GuideAudioEntry? by lazy {
        runCatching {
            context.assets.open("speech/natural-sample.json").bufferedReader().use {
                json.decodeFromString(GuideAudioEntry.serializer(), it.readText())
            }
        }.getOrNull()
    }

    /** Whether [guideId] has a recording in the reading voice. */
    fun hasRecording(guideId: String): Boolean = manifest?.guides?.containsKey(guideId) == true

    fun recordingMillis(guideId: String): Long? = manifest?.guides?.get(guideId)?.durationMs

    /** Reads an emergency guide: its recording when there is one, otherwise the answer voice. */
    fun readGuide(key: String, guideId: String) {
        stop()
        val entry = manifest?.guides?.get(guideId)
        if (entry != null) {
            playRecording(key, entry.segments.map { SpeechText.Segment(it.shown, it.shown) }, entry.segments.map { it.startMs },
                "emergency/audio/${entry.file}", entry.durationMs)
        } else guides().firstOrNull { it.id == guideId }?.let { read(key, SpeechText.guide(it)) }
    }

    /** Reads an answer without its citations, labels or saved excerpts. */
    fun readAnswer(key: String, markdown: String) {
        stop()
        read(key, SpeechText.answer(markdown))
    }

    /** Lets the person hear [choice] before choosing it: the natural voice from its bundled clip until it is downloaded. */
    fun playSample(choice: Choice) {
        stop()
        val recorded = sampleTimings
        val sample = recorded?.segments?.map { SpeechText.Segment(it.shown, it.shown) }
            ?: listOf(SpeechText.Segment(SAMPLE_TEXT, SAMPLE_TEXT))
        when {
            choice == Choice.Phone -> speakWithPhone(SAMPLE_KEY, sample)
            natural.ready -> speakNatural(SAMPLE_KEY, sample)
            else -> playRecording(SAMPLE_KEY, sample, recorded?.segments?.map { it.startMs } ?: listOf(0L),
                "speech/natural-sample.ogg", recorded?.durationMs ?: 0)
        }
    }

    private fun read(key: String, segments: List<SpeechText.Segment>) {
        if (segments.isEmpty()) return
        if (answerVoice == Voice.Natural) speakNatural(key, segments) else speakWithPhone(key, segments)
    }

    fun pause() {
        val current = mutableState.value ?: return
        if (current.paused) return
        when (current.voice) {
            Voice.Recorded -> player?.takeIf { it.isPlaying }?.pause()
            Voice.Natural -> runCatching { track?.pause() }
            Voice.Phone -> tts?.stop()
        }
        mutableState.value = current.copy(paused = true)
    }

    fun resume() {
        val current = mutableState.value ?: return
        if (!current.paused || !takeFocus()) return
        mutableState.value = current.copy(paused = false)
        when (current.voice) {
            Voice.Recorded -> player?.start()
            Voice.Natural -> runCatching { track?.play() }
            Voice.Phone -> speakFrom(current.index)
        }
    }

    fun stop() {
        work?.cancel(); work = null
        player?.run { runCatching { stop() }; release() }; player = null
        // Paused and emptied, a write still blocked on the track returns at once; the reader then releases it.
        track?.let { runCatching { it.pause(); it.flush() } }; track = null
        tts?.stop()
        if (wakeLock.isHeld) wakeLock.release()
        mutableState.value = null
        audio.abandonAudioFocusRequest(focus)
        // The loaded voice holds a few hundred MB; it is let go after a quiet spell, not after every sentence.
        unloadLater?.cancel()
        unloadLater = scope.launch { delay(UNLOAD_AFTER_MS); natural.unload() }
    }

    private fun takeFocus() = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private var work: Job? = null
    private var unloadLater: Job? = null

    // ---------- recordings ----------

    private var player: MediaPlayer? = null

    private fun playRecording(key: String, segments: List<SpeechText.Segment>, starts: List<Long>, asset: String, durationMs: Long) {
        if (!takeFocus()) return
        val speed = mutableSettings.value.speed
        val media = runCatching {
            context.assets.openFd(asset).use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    prepare()
                }
            }
        }.getOrNull() ?: return
        player = media
        mutableState.value = Reading(key, segments, 0, Voice.Recorded, durationMs = durationMs.takeIf { it > 0 } ?: media.duration.toLong())
        media.setOnCompletionListener { scope.launch { if (player === media) stop() } }
        // Android keeps the pitch while it changes the pace, so a faster guide still sounds like the same person.
        if (speed != 1.0f) runCatching { media.playbackParams = PlaybackParams().setSpeed(speed) }
        media.start()
        work = scope.launch {
            while (isActive && player === media) {
                val at = runCatching { media.currentPosition.toLong() }.getOrDefault(0)
                val index = starts.indexOfLast { it <= at }.coerceAtLeast(0)
                mutableState.value?.let { current ->
                    if (current.key == key && (current.index != index || current.positionMs / 500 != at / 500))
                        mutableState.value = current.copy(index = index, positionMs = at)
                }
                delay(120)
            }
        }
    }

    // ---------- the natural voice ----------

    private var track: AudioTrack? = null

    /**
     * Makes the speech on the voice's own thread and streams it to one AudioTrack, noting where each sentence starts so
     * the sentence marked on screen is the one being heard. Each call to the voice has a fixed cost (several seconds
     * on an older phone) on top of the time the speech itself takes, so whole sentences are made, not small pieces.
     * Where the phone makes speech slower than it is spoken, playing starts only once enough is ready that the rest
     * arrives before it is needed: one wait at the start ([Reading.prepared] says how far along), then a reading
     * without long silences between sentences. On a fast phone that is the first sentence.
     */
    private fun speakNatural(key: String, segments: List<SpeechText.Segment>) {
        if (!takeFocus()) return
        unloadLater?.cancel()
        val speed = mutableSettings.value.speed
        mutableState.value = Reading(key, segments, 0, Voice.Natural, preparing = true)
        wakeLock.acquire(WAKE_LIMIT_MS)
        work = scope.launch {
            // A very long sentence is cut at its clauses, so no single piece holds up the start for too long.
            val pieces = segments.flatMapIndexed { index, segment ->
                val parts = if (segment.spoken.split(' ').size > LONG_SENTENCE_WORDS) SpeechText.clauses(segment.spoken) else listOf(segment.spoken)
                parts.mapIndexed { at, text -> Piece(index, text, at == parts.lastIndex) }
            }
            val totalChars = pieces.sumOf { it.text.length }.coerceAtLeast(1)
            val made = Channel<Made>(Channel.UNLIMITED)
            val maker = launch {
                try {
                    pieces.forEach { piece ->
                        val began = android.os.SystemClock.elapsedRealtime()
                        val sound = natural.speak(piece.text, speed)
                        made.send(Made(piece, sound, android.os.SystemClock.elapsedRealtime() - began))
                    }
                    made.close()
                } catch (error: Exception) {
                    made.close(error)
                }
            }
            var output: AudioTrack? = null
            val starts = LongArray(segments.size) { Long.MAX_VALUE }
            var written = 0L
            val held = ArrayDeque<Made>()
            var madeChars = 0
            var madeAudioMs = 0L
            var makingMs = 0L

            suspend fun put(item: Made) {
                val sink = output ?: openTrack(item.sound.sampleRate).also { output = it; track = it }
                val index = item.piece.segment
                if (starts[index] == Long.MAX_VALUE) starts[index] = written
                val pause = when {
                    item.piece === pieces.last() -> 0
                    item.piece.endsSegment -> (item.sound.sampleRate * pauseAfter(segments[index].shown)).toInt()
                    else -> (item.sound.sampleRate * CLAUSE_PAUSE).toInt()
                }
                withContext(Dispatchers.IO) {
                    write(sink, item.sound.samples)
                    write(sink, FloatArray(pause))
                }
                written += item.sound.samples.size + pause
            }

            suspend fun begin() {
                val first = held.firstOrNull() ?: return
                val sink = output ?: openTrack(first.sound.sampleRate).also { output = it; track = it }
                if (mutableState.value?.paused != true) sink.play()
                mutableState.value = mutableState.value?.copy(preparing = false, prepared = 1f)
                follow(key, sink, starts)
                while (held.isNotEmpty()) put(held.removeFirst())
            }

            try {
                for (item in made) {
                    madeChars += item.piece.text.length
                    madeAudioMs += item.sound.samples.size * 1000L / item.sound.sampleRate.coerceAtLeast(1)
                    makingMs += item.makingMs
                    // The whole reading's length, exact once everything is made and estimated from the words until then.
                    val totalMs = madeAudioMs + (totalChars - madeChars) * madeAudioMs / madeChars.coerceAtLeast(1) +
                        segments.size * (PAUSE_MS)
                    mutableState.value = mutableState.value?.copy(durationMs = totalMs)
                    if (output != null && held.isEmpty() && mutableState.value?.preparing == false) { put(item); continue }
                    held += item
                    val next = pieces.getOrNull(pieces.indexOf(item.piece) + 1)
                    val need = headStartMs(totalChars - madeChars, next?.text?.length ?: 0, madeChars, madeAudioMs, makingMs)
                    val ready = held.sumOf { it.sound.samples.size * 1000L / it.sound.sampleRate.coerceAtLeast(1) }
                    if (ready >= need) begin()
                    else mutableState.value = mutableState.value?.copy(prepared = (ready.toFloat() / need).coerceIn(0f, 0.99f))
                }
                begin()
                // Everything is written; wait until it has been heard. A track that stops moving while not paused
                // (the speaker went away) ends the reading instead of holding it open.
                var last = -1L; var still = 0
                while (isActive && output != null) {
                    val head = output!!.playbackHeadPosition.toLong() and 0xffffffffL
                    if (head >= written) break
                    if (mutableState.value?.paused != true) { if (head == last) still++ else still = 0 }
                    if (still > 40) break
                    last = head
                    delay(50)
                }
                if (isActive) stop()
            } catch (cancelled: CancellationException) {
                maker.cancel()
                throw cancelled
            } catch (error: Exception) {
                // The natural voice failed (memory, a damaged file): the phone's voice reads on from the same sentence.
                maker.cancel()
                val from = mutableState.value?.index ?: 0
                output?.let { runCatching { it.pause(); it.flush() } }
                speakWithPhone(key, segments, from)
            } finally {
                output?.let { sink -> withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { sink.release() } } }
                if (track === output) track = null
            }
        }
    }

    private class Made(val piece: Piece, val sound: NaturalVoice.Audio, val makingMs: Long)

    private class Piece(val segment: Int, val text: String, val endsSegment: Boolean)

    /**
     * Loads the natural voice in the background when an answer that may be listened to is on screen, so Listen starts
     * with the first words rather than with loading. Let go again after a while if nothing is read.
     */
    fun prepareNatural() {
        if (mutableSettings.value.voice != Choice.Natural || !natural.ready || mutableState.value?.voice == Voice.Natural) return
        unloadLater?.cancel()
        scope.launch { runCatching { natural.warmUp() } }
        unloadLater = scope.launch { delay(PREPARED_FOR_MS); if (mutableState.value?.voice != Voice.Natural) natural.unload() }
    }

    private fun openTrack(sampleRate: Int): AudioTrack {
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimum, sampleRate / 4 * 4)).setTransferMode(AudioTrack.MODE_STREAM).build()
    }

    /** Writes in small pieces, so a pause or stop takes effect within a tenth of a second. */
    private suspend fun write(sink: AudioTrack, samples: FloatArray) {
        var at = 0
        while (at < samples.size) {
            coroutineContext.ensureActive()
            if (mutableState.value?.paused == true) { delay(60); continue }
            val wrote = sink.write(samples, at, minOf(CHUNK, samples.size - at), AudioTrack.WRITE_BLOCKING)
            check(wrote >= 0) { "Audio output failed ($wrote)" }
            if (wrote == 0) delay(20)
            at += wrote
        }
    }

    /** Moves the mark to the sentence being heard, from where the track has played to. */
    private fun follow(key: String, sink: AudioTrack, starts: LongArray) {
        scope.launch {
            val rate = sink.sampleRate.coerceAtLeast(1)
            while (isActive && track === sink) {
                val head = runCatching { sink.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(0)
                var index = 0
                while (index + 1 < starts.size && starts[index + 1] <= head) index++
                val at = head * 1000 / rate
                mutableState.value?.let { current ->
                    if (current.key == key && (current.index != index || current.positionMs / 500 != at / 500))
                        mutableState.value = current.copy(index = index, positionMs = at)
                }
                delay(120)
            }
        }
    }

    // ---------- the phone's voice ----------

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: (() -> Unit)? = null

    private fun speakWithPhone(key: String, segments: List<SpeechText.Segment>, from: Int = 0) {
        if (segments.isEmpty() || !takeFocus()) return
        // The phone's voice tells when each sentence starts, not how long it is: the time shown is estimated from
        // the words, and kept from running past the sentence being said.
        val rate = mutableSettings.value.speed.coerceAtLeast(0.5f)
        val lengths = segments.map { (it.spoken.length * PHONE_MS_PER_CHAR / rate).toLong() + PAUSE_MS }
        val starts = lengths.runningFold(0L) { at, length -> at + length }
        mutableState.value = Reading(key, segments, from, Voice.Phone, positionMs = starts[from], durationMs = starts.last())
        withVoice { speakFrom(from) }
        work = scope.launch {
            while (isActive) {
                delay(250)
                val current = mutableState.value ?: break
                if (current.key != key || current.voice != Voice.Phone) break
                if (!current.paused) mutableState.value = current.copy(positionMs = (current.positionMs + 250)
                    .coerceIn(starts[current.index], starts[current.index + 1]))
            }
        }
    }

    private fun withVoice(action: () -> Unit) {
        if (ttsReady) return action()
        pending = action
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            scope.launch {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) { stop(); return@launch }
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(progress)
                pending?.invoke(); pending = null
            }
        }
    }

    private fun speakFrom(index: Int) {
        val current = mutableState.value ?: return
        val voice = tts ?: return
        voice.setSpeechRate(mutableSettings.value.speed)
        current.segments.drop(index).forEachIndexed { offset, segment ->
            voice.speak(segment.spoken, if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "${current.key}#${index + offset}")
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            scope.launch {
                val current = mutableState.value ?: return@launch
                val (key, index) = utteranceId.substringBeforeLast('#') to utteranceId.substringAfterLast('#').toIntOrNull()
                if (key == current.key && index != null && !current.paused) mutableState.value = current.copy(index = index,
                    positionMs = maxOf(current.positionMs, current.segments.take(index).sumOf {
                        (it.spoken.length * PHONE_MS_PER_CHAR / mutableSettings.value.speed.coerceAtLeast(0.5f)).toLong() + PAUSE_MS }))
            }
        }

        override fun onDone(utteranceId: String) {
            scope.launch {
                val current = mutableState.value ?: return@launch
                if (current.voice == Voice.Phone && utteranceId == "${current.key}#${current.segments.lastIndex}" && !current.paused) stop()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            scope.launch { if (mutableState.value?.voice == Voice.Phone) stop() }
        }
    }

    companion object {
        const val SAMPLE_KEY = "sample"
        const val SAMPLE_TEXT = "This is the natural voice. It reads your answers aloud on this phone, even with no signal."
        private const val VOICE = "voice"
        private const val SPEED = "speed"
        private const val READ_VOICE_ANSWERS = "read-voice-answers"
        private const val READ_GUIDES_AT_ONCE = "read-guides-at-once"
        private const val CHUNK = 4096
        private const val UNLOAD_AFTER_MS = 90_000L
        private const val PREPARED_FOR_MS = 180_000L
        private const val CLAUSE_PAUSE = 0.12f
        private const val WAKE_LIMIT_MS = 30 * 60_000L
        private val json = Json { ignoreUnknownKeys = true }

        private const val LONG_SENTENCE_WORDS = 30
        /** About how long the phone's voice takes per character at normal speed, for the time it shows. */
        private const val PHONE_MS_PER_CHAR = 65f
        private const val PAUSE_MS = 450L
        private const val HEAD_START_MARGIN_MS = 1_000L

        /**
         * How much speech must be ready before playing starts so that the rest is made before it is needed, from how
         * fast this phone has made speech so far. With [remainingChars] still to make at the speed seen, making them
         * takes P and saying them takes R; what is ready must cover the difference, and at least the time the next
         * piece takes to make. A phone faster than speech needs only a second in hand.
         */
        internal fun headStartMs(remainingChars: Int, nextChars: Int, madeChars: Int, madeAudioMs: Long, makingMs: Long): Long {
            if (remainingChars <= 0 || madeChars <= 0) return 0
            val makingPerChar = makingMs.toDouble() / madeChars
            val sayingPerChar = madeAudioMs.toDouble() / madeChars
            val shortfall = remainingChars * (makingPerChar - sayingPerChar)
            return (maxOf(shortfall, nextChars * makingPerChar).toLong() + HEAD_START_MARGIN_MS)
        }

        /** The same pauses the guide recordings have: longer after a heading. */
        internal fun pauseAfter(shown: String): Float = if (shown.trimEnd().endsWith('.') || shown.trimEnd().endsWith('?') ||
            shown.trimEnd().endsWith('!') || shown.trimEnd().endsWith(':')) 0.45f else 0.8f
    }
}

@Serializable
internal data class GuideAudioManifest(val guides: Map<String, GuideAudioEntry> = emptyMap())

@Serializable
internal data class GuideAudioEntry(val file: String = "", val durationMs: Long, val segments: List<GuideAudioSegment>)

@Serializable
internal data class GuideAudioSegment(val startMs: Long, val shown: String)
