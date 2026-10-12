package xyz.fieldatlas.research

/** Missing generated citations are never repaired by assigning a source to a paraphrase. */
internal object SavedEvidenceAppendix {
    fun append(answer: String, packed: PackedPrompt): String {
        if (!packed.mixedAnswer || packed.sources.isEmpty() ||
            AnswerText.citationAudit(answer, packed.sources.size).citedSourceIds.isNotEmpty()) return answer
        val sources = packed.sources.take(3)
        if (sources.any { PreciseReferenceAnswer.copiedExcerpt(it) == null }) return answer
        return answer + "\n\n## Saved excerpts\n\n" + sources.joinToString("\n\n", transform = PreciseReferenceAnswer::quote) +
            "\n\n_These citations identify the copied excerpts, not verification of the model explanation above._"
    }
}
