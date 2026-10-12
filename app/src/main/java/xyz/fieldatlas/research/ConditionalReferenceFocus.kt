package xyz.fieldatlas.research

/** A narrow boundary for quoting a saved explanation of an explicit apparent contradiction.
 * This is lexical selection, not an entailment or source-truth verifier. Callers must quote the
 * connected passage with its qualifications, not turn these matches into a new causal claim. */
internal object ConditionalReferenceFocus {
    private val opening = Regex("(?i)^\\s*(?:why|how)\\s+(?:can|could|does|do|did|is|are|was|were|would|may|might)\\b")
    private val contrast = Regex("(?i)\\b(?:even\\s+(?:when|though|if)|despite|although|in\\s+spite\\s+of)\\b")
    private val limitation = Regex("(?i)\\b(?:however|although|despite|but|except|nevertheless|nonetheless|whereas|only|not|cannot|rather\\s+than|unlike)\\b|\\b(?:doesn|isn|aren|can)['’]t\\b")
    private val token = Regex("[\\p{L}\\p{N}]+")
    private val weak = setOf("even", "still", "already", "apparently", "really", "very", "high", "higher", "highest",
        "low", "lower", "lowest", "good", "better", "best", "poor", "worse", "worst", "much", "many", "having")
    private val whitespace = Regex("\\s+")

    class Focus internal constructor(
        val outcomeTerms: List<String>,
        val conditionTerms: List<String>,
        private val articleTitles: Set<String>,
    ) {
        /** Both sides must occur in the same copied unit from the named article. A title alone,
         * a different article, or separate chunks stitched into a new statement do not qualify. */
        fun matches(evidence: Evidence, excerpt: String): Boolean {
            if (!ArticleDepth.isEncyclopedia(evidence) ||
                evidence.title.substringBefore(" — ").lowercase() !in articleTitles || excerpt.isBlank()) return false
            val unit = excerpt.removePrefix("… ").removeSuffix(" …").trim()
            if (unit.length > 2_400 || !limitation.containsMatchIn(unit)) return false
            fun plain(text: String) = text.replace(whitespace, " ").trim()
            if (!plain(EvidenceRelevance.passageText(evidence.text)).contains(plain(unit))) return false
            val words = token.findAll(unit.lowercase()).map { it.value }.toSet()
            return outcomeTerms.all { hasTerm(words, it) } && conditionTerms.all { hasTerm(words, it) }
        }
    }

    fun from(question: String, subjects: List<Evidence>): Focus? {
        val asked = QuestionSubject.text(question)
        if (!SubjectLookup.explains(question) || !opening.containsMatchIn(asked)) return null
        val split = contrast.find(asked) ?: return null
        // Multiple questions/contrasts need the ordinary partial-answer workflow, not this narrow quote route.
        if (contrast.findAll(asked).take(2).count() != 1 ||
            Regex("[;]|[?!].*\\p{L}|\\band\\s+(?:how|why|what|when|where)\\b", RegexOption.IGNORE_CASE).containsMatchIn(asked)) return null
        val titles = subjects.filter(ArticleDepth::isEncyclopedia).map { it.title.substringBefore(" — ") }
            .filter { QuestionSubject.containsMultiwordTitle(asked, it) && FtsQuery.from(it)?.terms.orEmpty().size >= 2 }
            .distinctBy { it.lowercase() }
        if (titles.isEmpty()) return null
        val names = titles.flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toSet()
        fun terms(text: String) = FtsQuery.from(text)?.terms.orEmpty().filterNot { it in names || it in weak }
        val outcome = terms(asked.substring(0, split.range.first))
        val condition = terms(asked.substring(split.range.last + 1))
        if (outcome.size !in 1..5 || condition.size !in 1..6) return null
        return Focus(outcome, condition, titles.map { it.lowercase() }.toSet())
    }

    /** Small inflections, not prefix matching: "leak" can be "leaked", never "leakageproof";
     * "rating" can be "ratings", never a stem that accidentally matches "rat". */
    private fun hasTerm(words: Set<String>, term: String): Boolean {
        val forms = linkedSetOf(term, term + "s", term + "es", term + "ed", term + "ing")
        if (term.length > 3 && term.endsWith("s") && !term.endsWith("ss")) forms += term.dropLast(1)
        if (term.length > 4 && term.endsWith("ies")) forms += term.dropLast(3) + "y"
        if (term.length > 4 && term.endsWith("ed")) forms += listOf(term.dropLast(2), term.dropLast(1))
        if (term.length > 4 && term.endsWith("ing")) forms += listOf(term.dropLast(3), term.dropLast(3) + "e")
        return forms.any(words::contains)
    }
}
