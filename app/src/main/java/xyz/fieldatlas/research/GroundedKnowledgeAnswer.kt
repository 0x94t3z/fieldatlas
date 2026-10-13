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
                !StudyApplicability.asks(question) && !asksForProof(asked) && !structuredInference(asked, packed)) return null

        // The caller publishes this exact source list. Never renumber, silently drop
        // an invalid/conflicting unit, or fall back to an invented paraphrase on error.
        if (packed.sources.withIndex().any { (index, source) ->
                source.citationId != "S${index + 1}" || PreciseReferenceAnswer.copiedExcerpt(source) == null
            }) return INVALID
        return "## $HEADING\n\n" + packed.sources.joinToString("\n\n", transform = PreciseReferenceAnswer::quote) +
            "\n\n_${NOTE}_"
    }

    // A proof question must not become a causal conclusion merely because retrieval
    // returned prose rather than a structured row. Preserve the original limitations.
    private fun asksForProof(question: String): Boolean =
        Regex("\\b(?:prove|proves|proven|proof|disprove|disproves|establish|establishes|established|confirm|confirms|confirmed|guarantee|guarantees|guaranteed|imply|implies)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(question)

    /** A row's direction/association codes are not a causal interpretation. Keep
     * these originals rather than letting a model invent the schema semantics.
     * Direct field lookups still use their existing route. */
    private fun structuredInference(question: String, packed: PackedPrompt): Boolean {
        // Do not depend on the user spelling out "proof": "would changing X
        // extend life?" asks for the same unsupported inference. Only a narrow
        // literal field lookup may use the existing extraction route.
        if (Regex("(?i)^what\\s+(?:does|is)\\b.*\\bfield\\b").containsMatchIn(question) &&
            !Regex("(?i)\\b(?:prove|mean|imply|cause|guarantee|establish)\\b").containsMatchIn(question)) return false
        return packed.sources.any { source ->
            val lines = source.evidence.text.lineSequence().map(String::trim)
                .filter(String::isNotEmpty).toList()
            lines.firstOrNull()?.startsWith("Dataset: ") == true && lines.size >= 4 &&
                lines.drop(1).all { Regex("[^:\\r\\n]{1,80}: .+").matches(it) }
        }
    }

}
