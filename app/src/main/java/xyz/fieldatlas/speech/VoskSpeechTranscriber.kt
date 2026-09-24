package xyz.fieldatlas.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.AudioRecord
import android.media.AudioFormat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Vosk-backed transcription: the microphone is read through [AudioRecord], the
 * same PCM bytes feed both the loudness meter and the recogniser, so the level
 * bar reflects exactly what the model is hearing. Everything stays in-process
 * and offline. The model is either an installed AUDIO pack (e.g. the large-graph
 * dictation model) or, as a fallback, the small model bundled in the APK assets.
 */
class VoskSpeechTranscriber(
    private val context: Context,
    /** Installed AUDIO pack directory (contains conf/, am/, graph/…); null = bundled small model. */
    private val filesystemModelDir: String? = null,
) : SpeechTranscriber {
    private var recognizer: Recognizer? = null
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private val capturing = AtomicBoolean(false)
    private val heard = StringBuilder()

    override suspend fun start(onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            error("Microphone permission has not been granted")
        }
        // Field Atlas: open the microphone FIRST so nothing the user says during model /
        // recogniser setup is lost; the large ring buffer keeps it until the capture loop
        // drains it.
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, MIN_BUFFER_BYTES),
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            error("The microphone could not be opened")
        }
        recorder = audio
        audio.startRecording()
        val model = acquireModel()
        val recogniser = Recognizer(model, SAMPLE_RATE)
        recogniser.setWords(false)
        heard.setLength(0)
        recognizer = recogniser
        capturing.set(true)
        captureThread = Thread {
            // A short silence lead-in gives the recogniser acoustic context before the
            // first real samples; feeding speech as the very first bytes clips word one.
            recogniser.acceptWaveForm(ByteArray(WARMUP_BYTES), WARMUP_BYTES)
            val samples = ShortArray(SAMPLES_PER_CHUNK)
            while (capturing.get() && !Thread.currentThread().isInterrupted) {
                val read = audio.read(samples, 0, samples.size)
                if (read <= 0) continue
                var sumOfSquares = 0.0
                for (index in 0 until read) {
                    val normalised = samples[index] / 32768.0
                    sumOfSquares += normalised * normalised
                }
                val rms = sqrt(sumOfSquares / read)
                // Small speech sits well below a raw full-scale RMS; a gain keeps
                // the meter responsive without letting silence read as a peak.
                onLevel(min(1f, (rms * 6.0).toFloat()))
                if (recogniser.acceptWaveForm(samples, read)) {
                    recogniser.result.jsonToText()?.let { segment ->
                        if (heard.isNotEmpty()) heard.append(' ')
                        heard.append(segment)
                    }
                    recogniser.reset()
                }
            }
        }.apply {
            name = "fieldatlas-voice"
            isDaemon = true
            start()
        }
    }

    override suspend fun stop(): String = withContext(Dispatchers.IO) {
        val audio = recorder ?: return@withContext ""
        capturing.set(false)
        captureThread?.join(THREAD_JOIN_MILLIS)
        captureThread = null
        val transcript = buildString {
            if (heard.isNotEmpty()) append(heard)
            recognizer?.finalResult?.jsonToText()?.let { final ->
                if (isNotEmpty()) append(' ')
                append(final)
            }
        }.trim()
        audio.stop()
        audio.release()
        recorder = null
        recognizer?.close()
        recognizer = null
        transcript
    }

    override suspend fun cancel() {
        capturing.set(false)
        captureThread?.join(THREAD_JOIN_MILLIS)
        captureThread = null
        recorder?.release()
        recorder = null
        recognizer?.close()
        recognizer = null
        heard.setLength(0)
    }

    /** Prefer the active dictation pack; fall back to the asset-bundled small model. */
    private fun acquireModel(): Model = filesystemModelDir?.let { dir ->
        runCatching { acquireCachedModel(dir) { Model(dir) } }.getOrNull()
    } ?: acquireCachedModel(ASSET_DIR) {
        Model(StorageService.sync(context, ASSET_DIR, MODEL_DIR))
    }

    /** Vosk answers are JSON envelopes such as {"text":"a tiger"}; only the text matters here. */
    private fun String.jsonToText(): String? = runCatching {
        JSONObject(this).optString("text").trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val ASSET_DIR = "vosk-model-en-us-015"
        const val MODEL_DIR = "vosk-model-en-us-015"
        const val SAMPLE_RATE = 16000f
        const val SAMPLES_PER_CHUNK = 3200
        /** ~150 ms of leading silence handed to the recogniser before any live audio. */
        const val WARMUP_BYTES = 4800
        const val MIN_BUFFER_BYTES = 128 * 1024
        const val THREAD_JOIN_MILLIS = 1_000L

        /** One native model at a time, keyed by source directory (swapping packs replaces it). */
        private var cachedKey: String? = null
        private var cachedModel: Model? = null

        @Synchronized
        private fun acquireCachedModel(key: String, load: () -> Model): Model {
            cachedModel?.let { if (cachedKey == key) return it }
            cachedModel?.close()
            return load().also { cachedModel = it; cachedKey = key }
        }
    }
}
