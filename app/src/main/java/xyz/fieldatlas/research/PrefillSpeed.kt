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

        /**
         * A question about the person's own files reads at least this much of them, however slow the phone: sized
         * for the first word, a slow phone got about a third of a one-page note and answered "not enough evidence"
         * from the part it missed. File answers skip the model's review, so the wait is about the same.
         */
        const val FILE_MIN_EVIDENCE = 900

        /** Evidence tokens for a question asked with files: [evidenceTokens], but never under [FILE_MIN_EVIDENCE]. */
        fun fileEvidenceTokens(rate: Double?): Int = maxOf(FILE_MIN_EVIDENCE, evidenceTokens(rate))

        /** Evidence tokens for the next answer; the top passage always fits. */
        fun evidenceTokens(rate: Double?): Int {
            if (rate == null || !rate.isFinite() || rate <= 0) return UNKNOWN_EVIDENCE
            return (rate * TARGET_SECONDS - FRAME_TOKENS).toInt().coerceIn(MIN_EVIDENCE, AttachmentEvidence.EVIDENCE_TOKENS)
        }

        /**
         * Where [modelId]'s rate is saved. The recommended model keeps the key it had before each
         * model had its own, so an update does not forget what the phone has learned.
         */
        fun storageKey(modelId: String?): String =
            if (modelId == null || modelId == xyz.fieldatlas.assets.ModelOffers.recommended.id) "prefill_tokens_per_second"
            else "prefill_tokens_per_second:$modelId"

        /** Blends a new measurement in, so one slow run (a hot phone) does not swing it alone. */
        fun update(previous: Double?, promptTokens: Int, prefillMillis: Long): Double? {
            if (promptTokens < 200 || prefillMillis < 500) return previous
            val measured = promptTokens * 1000.0 / prefillMillis
            return if (previous == null) measured else previous * 0.6 + measured * 0.4
        }
    }
}

class InMemoryPrefillSpeed(override var tokensPerSecond: Double? = null) : PrefillSpeed
