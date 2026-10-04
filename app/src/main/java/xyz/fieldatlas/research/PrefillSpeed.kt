package xyz.fieldatlas.research

/**
 * How fast this phone reads a prompt, in estimated tokens per second ([TokenEstimate] units),
 * learned from finished answers. Phones differ tenfold: a Helio G85 reads about 15 tokens a
 * second, a recent flagship well over 100. File evidence is sized from this so the wait before
 * the first word stays near [TARGET_SECONDS] instead of growing with the document.
 */
interface PrefillSpeed {
    var tokensPerSecond: Double?

    companion object {
        const val TARGET_SECONDS = 40.0
        /** Instructions, question and record framing, in estimated tokens. */
        const val FRAME_TOKENS = 450
        const val MIN_EVIDENCE = 350
        const val UNKNOWN_EVIDENCE = 700

        /** Evidence tokens for the next answer; the top passage always fits. */
        fun evidenceTokens(rate: Double?): Int {
            if (rate == null || !rate.isFinite() || rate <= 0) return UNKNOWN_EVIDENCE
            return (rate * TARGET_SECONDS - FRAME_TOKENS).toInt().coerceIn(MIN_EVIDENCE, AttachmentEvidence.EVIDENCE_TOKENS)
        }

        /** Blends a new measurement in, so one slow run (a hot phone) does not swing it alone. */
        fun update(previous: Double?, promptTokens: Int, prefillMillis: Long): Double? {
            if (promptTokens < 200 || prefillMillis < 500) return previous
            val measured = promptTokens * 1000.0 / prefillMillis
            return if (previous == null) measured else previous * 0.6 + measured * 0.4
        }
    }
}

class InMemoryPrefillSpeed(override var tokensPerSecond: Double? = null) : PrefillSpeed
