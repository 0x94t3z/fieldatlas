package xyz.fieldatlas.research

/** Missing generated citations are never repaired by assigning a source to a paraphrase. */
internal object SavedEvidenceAppendix {
    const val NOT_VERIFICATION = "_These citations identify the copied excerpts, not verification of the model explanation above._"
    /** Under an answer the model wrote from its own knowledge, with no passage in its prompt. */
    const val NOT_USED = "_Found in your collections for checking. The answer above was written from the model's own knowledge, " +
        "without these excerpts, so they are not its source._"

    /** [writtenWithout]: the model saw none of the passages, so they are listed only to check the answer against. */
    fun append(answer: String, packed: PackedPrompt, writtenWithout: Boolean = false): String {
        if (!packed.mixedAnswer || packed.sources.isEmpty() ||
            AnswerText.citationAudit(answer, packed.sources.size).citedSourceIds.isNotEmpty()) return answer
        val sources = packed.sources.take(3)
        if (sources.any { PreciseReferenceAnswer.copiedExcerpt(it) == null }) return answer
        return answer + "\n\n## Saved excerpts\n\n" + sources.joinToString("\n\n", transform = PreciseReferenceAnswer::quote) +
            "\n\n" + if (writtenWithout) NOT_USED else NOT_VERIFICATION
    }
}
