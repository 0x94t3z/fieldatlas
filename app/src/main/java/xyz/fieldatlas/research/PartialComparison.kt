package xyz.fieldatlas.research

/** A two-subject comparison cannot be filled from memory when a selected subject is
 * missing. Preserve useful original excerpts, disclose the lexical gap and avoid
 * inventing the other side. This is partial coverage, not proof of source/world truth. */
internal object PartialComparison {
    fun answer(question: String, packed: PackedPrompt): String? {
        if (packed.sources.isEmpty()) return null
        val actual = packed.sources.map { it.evidence.copy(text = it.excerpt) }
        val missing = EvidenceRelevance.missingComparisonSubjects(question, actual)
        if (missing.isEmpty()) return null
        val subjects = EvidenceRelevance.comparisonTopics(question).filterNot { it in missing }
        val useful = packed.sources.filter { source ->
            subjects.any { subject ->
                val terms = FtsQuery.from(subject)?.terms.orEmpty()
                terms.isNotEmpty() && EvidenceRelevance.termHits(source.excerpt, terms) == terms.size
            }
        }.take(2)
        if (useful.isEmpty()) return null
        val quotes = useful.joinToString("\n\n", transform = PreciseReferenceAnswer::quote)
        return "## ${SourceLead.HEADING}\n\n$quotes\n\n_These selected passages do not mention " +
            missing.joinToString(", ") + "; they do not support a complete comparison._"
    }
}
