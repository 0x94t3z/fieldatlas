package xyz.fieldatlas.speech

/**
 * On-device speech capture contract used by the research screen.
 *
 * [start] streams microphone loudness (0f..1f) for the live "you are being heard" meter
 * and, where the engine supports it, the words recognised so far, so the question box can
 * fill in while the person speaks. [stop] returns the final transcript, which replaces the
 * live text; nothing is ever submitted automatically.
 */
interface SpeechTranscriber {
    /**
     * Loads the model (first call may take seconds) and begins capturing audio.
     * [onLevel] fires repeatedly with normalised loudness while speaking.
     */
    suspend fun start(onLevel: (Float) -> Unit)

    /** As [start], also reporting the whole text heard so far whenever it changes. */
    suspend fun start(onLevel: (Float) -> Unit, onText: (String) -> Unit) = start(onLevel)

    /** Stops capture, releases the microphone and returns the final transcript. */
    suspend fun stop(): String

    /** Aborts capture without producing a transcript. */
    suspend fun cancel()
}
