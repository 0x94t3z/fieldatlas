package xyz.fieldatlas.speech

/**
 * On-device speech capture contract used by the research screen.
 *
 * The contract is deliberately push-based on levels and pull-based on text:
 * [start] streams microphone loudness (0f..1f) for the live "you are being heard"
 * meter, while the actual transcript is produced only when the user stops, as
 * the caller wants the text dropped into the question box, not acted upon live.
 */
interface SpeechTranscriber {
    /**
     * Loads the model (first call may take seconds) and begins capturing audio.
     * [onLevel] fires repeatedly with normalised loudness while speaking.
     */
    suspend fun start(onLevel: (Float) -> Unit)

    /** Stops capture, releases the microphone and returns the final transcript. */
    suspend fun stop(): String

    /** Aborts capture without producing a transcript. */
    suspend fun cancel()
}
