package xyz.fieldatlas.research

/** Animal findings and human applicability are different evidence questions.
 * Sharing a disease name or mentioning both species does not answer whether results
 * transfer. This checks a stated limitation/translation, not the truth of a study. */
internal object StudyApplicability {
    private val animals = Regex("(?i)\\b(?:animals?|mice|mouse|rats?|rodents?)\\b")
    private val humans = Regex("(?i)\\b(?:humans?|people|patients?)\\b")
    private val research = Regex("(?i)\\b(?:research|studies|study|experiments?|findings|results)\\b")
    private val transfer = Regex("(?i)\\b(?:prove|proves|proof|apply|applies|applicable|translate|translates|translation|mean|means|guarantee|guarantees)\\b")
    fun asks(question: String): Boolean =
        animals.containsMatchIn(question) && humans.containsMatchIn(question) &&
            research.containsMatchIn(question) && transfer.containsMatchIn(question)

    fun supports(item: Evidence): Boolean = EvidenceRelevance.passageText(item.text)
        .split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
        .any { sentence ->
            sentence.length <= 600 && animals.containsMatchIn(sentence) && humans.containsMatchIn(sentence) &&
                Regex("(?i)\\b(?:translat\\w*|extrapolat\\w*|same results|may not|does not|do not|cannot|not necessarily|differ\\w*)\\b")
                    .containsMatchIn(sentence)
        }
}
