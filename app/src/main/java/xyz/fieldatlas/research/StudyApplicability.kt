package xyz.fieldatlas.research

/** Animal findings and human applicability are different evidence questions.
 * Sharing a disease name or mentioning both species does not answer whether results
 * transfer. This checks a stated limitation/translation, not the truth of a study. */
internal object StudyApplicability {
    private val animals = Regex("(?i)\\b(?:animals?|mice|mouse|rats?|rodents?)\\b")
    private val humans = Regex("(?i)\\b(?:humans?|people|patients?)\\b")
    private val research = Regex("(?i)\\b(?:research|studies|study|experiments?|findings|results|treatments?|lifespans?|longevity)\\b")
    private val transfer = Regex("(?i)\\b(?:prove|proves|proof|establish(?:es)?|confirm(?:s)?|apply|applies|applicable|translate|translates|translation|mean|means|guarantee|guarantees|same)\\b")
    fun asks(question: String): Boolean =
        animals.containsMatchIn(question) && humans.containsMatchIn(question) &&
            research.containsMatchIn(question) && transfer.containsMatchIn(question)

    fun supports(item: Evidence): Boolean = EvidenceRelevance.passageText(item.text)
        .split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
        .any { sentence ->
            sentence.length <= 600 && animals.containsMatchIn(sentence) && humans.containsMatchIn(sentence) &&
                Regex("(?i)\\b(?:results?|findings?|studies|study|research|treatments?|translat\\w*|extrapolat\\w*)\\b")
                    .containsMatchIn(sentence) &&
                // A plan to test translation is not a conclusion about translation.
                // Require an explicit limit, not merely "to assess whether...".
                Regex("(?i)\\b(?:may not|does not|do not|cannot|not necessarily|not always|doesn't|don't)\\b")
                    .containsMatchIn(sentence)
        }

    /** Search for transfer evidence, not another treatment matching the same species.
     * One bounded, deterministic recall pass; every result must state applicability. */
    suspend fun recover(question: String, initial: List<Evidence>, retriever: Retriever, limit: Int): List<Evidence> {
        if (!asks(question)) return initial
        val existing = initial.filter(::supports)
        if (existing.isNotEmpty()) return existing
        return retriever.searchForQuestion("animal studies results humans translation", question, minOf(limit, 24)) { }
            .filter(::supports)
            .distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            .take(limit)
    }
}
