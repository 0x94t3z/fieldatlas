package xyz.fieldatlas.research

/** A conservative finish for ordinary knowledge explanations and comparisons.
 * Existing specialized routes run first. Copying selected originals avoids a small
 * model turning a study's limits into operating rules or resolving source conflicts.
 * It proves wording/provenance only, not truth, relevance or complete coverage. */
internal object GroundedKnowledgeAnswer {
    const val HEADING = "From your saved sources"
    private const val NOTE = "Original excerpts, not a generated explanation. These selections may not fully answer the question."
    private const val INVALID = "I couldn't display these selected excerpts safely. Open the sources to read the originals."

    fun answer(question: String, packed: PackedPrompt): String? {
        if (question.isBlank() || packed.sources.isEmpty()) return null
        val asked = QuestionText.normalised(QuestionSubject.text(question))
        if (EvidenceRelevance.isAttributeQuestion(asked) ||
            !SubjectLookup.leads(question) && EvidenceRelevance.overviewSubjects(question).isEmpty() &&
                !StudyApplicability.asks(question)) return null

        // The caller publishes this exact source list. Never renumber, silently drop
        // an invalid/conflicting unit, or fall back to an invented paraphrase on error.
        if (packed.sources.withIndex().any { (index, source) ->
                source.citationId != "S${index + 1}" || PreciseReferenceAnswer.copiedExcerpt(source) == null
            }) return INVALID
        return "## $HEADING\n\n" + packed.sources.joinToString("\n\n", transform = PreciseReferenceAnswer::quote) +
            "\n\n_${NOTE}_"
    }

}
