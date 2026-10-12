package xyz.fieldatlas.research

/** Explicit source-only encyclopedia explanations show originals, not a model's
 * paraphrase with plausible citations. This guarantees copied text/identity, not
 * the source's truth, relevance, completeness or an inferred conclusion. Files,
 * guides, calculations and ordinary mixed explanations retain their own paths. */
object SavedReferenceAnswer {
    fun answer(question: String, packed: PackedPrompt, subjects: List<Evidence>): String? {
        if (!PromptBuilder.requiresSavedSources(question) || !SubjectLookup.leads(question) || subjects.isEmpty()) return null
        if (subjects.any { !ArticleDepth.isEncyclopedia(it) }) return null
        val titles = subjects.map { it.title.substringBefore(" — ") }.toSet()
        val sources = packed.sources.filter { ArticleDepth.isEncyclopedia(it.evidence) &&
            it.evidence.title.substringBefore(" — ") in titles && it.excerpt.isNotBlank() }
        if (sources.isEmpty()) return null
        return "## Selected passages from your saved sources\n\n" +
            sources.joinToString("\n\n", transform = PreciseReferenceAnswer::quote) +
            "\n\n_Original excerpts, not a generated explanation. These selections may not fully answer the question._"
    }
}
