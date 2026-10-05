package xyz.fieldatlas.research

/**
 * A question that names no place is not answered by one destination's guide or map listings.
 * "How does a hardware wallet keep my private key safe?" matched "Kigali — Stay safe" and
 * "Paris — Stay safe > Crime"; "Should I use a debit card or a credit card abroad?" matched
 * "Iran — Buy > Debit card and ATM", whose sanctions the model then gave as general advice.
 * Travel topic guides ("Money", "Hostels", "Passport") stay: they are written for anywhere.
 */
object GuideScope {
    // Every destination guide tells the traveller how to get there and around; a topic guide does not.
    val HEADINGS = listOf("Get in", "Get around")
    private val destinationHeadings = HEADINGS.map(String::lowercase).toSet()
    private const val MAX_ARTICLES = 8
    const val SECTION_CANDIDATES = 20
    private val sentenceStart = Regex("(?:^|[.!?:]\\s+)([\\p{L}\\p{N}'-]+)")
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]*")
    // Words that open a sentence whatever it is about; any other capitalized opener may be a name ("Hanoi: ...").
    private val openers = setOf("how", "what", "what's", "which", "where", "when", "why", "who", "is", "are", "was", "were", "can",
        "could", "do", "does", "did", "should", "would", "will", "i", "i'm", "i've", "my", "we", "tell", "please", "the", "a", "an",
        "in", "on", "at", "for", "if", "any", "best", "give", "list", "find", "show", "explain", "describe", "compare", "help")

    /** Whether the question names a place, or anything else, with a capital letter. */
    fun namesPlace(question: String): Boolean {
        val starts = sentenceStart.findAll(question).map { it.groups[1]!!.range.first }.toSet()
        return word.findAll(question).any { m ->
            m.value.first().isUpperCase() && m.value != "I" && !m.value.startsWith("I'") &&
                (m.range.first !in starts || m.value.lowercase() !in openers)
        }
    }

    private fun isListing(item: Evidence) = item.documentId.startsWith("wv-eat-") || item.documentId.startsWith("wv-place-") ||
        item.documentId.startsWith("osm-place-")

    private fun isGuideSection(item: Evidence) =
        !isListing(item) && item.source.contains("wikivoyage", ignoreCase = true) && " — " in item.title

    /**
     * [evidence] without map listings and destination guide sections, for a question that names
     * no place; [sectionsOf] finds a guide's sections titled with [HEADINGS], to tell a
     * destination from a topic.
     */
    suspend fun withoutOtherPlaces(
        question: String,
        evidence: List<Evidence>,
        sectionsOf: suspend (String) -> List<Evidence>,
    ): List<Evidence> {
        if (namesPlace(question)) return evidence
        val articles = evidence.filter(::isGuideSection).map { it.title.substringBefore(" — ").trim() }.distinct().take(MAX_ARTICLES)
        val destinations = articles.filter { article ->
            sectionsOf(article).any {
                it.title.startsWith("$article — ") && it.title.substringAfter(" — ").substringBefore(" > ").trim().lowercase() in destinationHeadings
            }
        }.toSet()
        return evidence.filterNot { item ->
            isListing(item) || (isGuideSection(item) && item.title.substringBefore(" — ").trim() in destinations)
        }
    }
}
