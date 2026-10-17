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

    /**
     * Whether the question names a place, or anything else, with a capital letter followed by
     * lowercase. An acronym ("GPS", "DEET", "SIM") is a thing, not a name: its article is read like
     * any subject's, and no destination guide answers it. [destinations] are the places the saved
     * place packs confirmed the question names (see [DestinationScope]): "food markets in lyon"
     * names Lyon as surely as a capital letter would.
     */
    fun namesPlace(question: String, destinations: List<String> = emptyList()): Boolean {
        if (destinations.isNotEmpty()) return true
        val starts = sentenceStart.findAll(question).map { it.groups[1]!!.range.first }.toSet()
        return word.findAll(question).any { m ->
            m.value.first().isUpperCase() && m.value.drop(1).any(Char::isLowerCase) && !m.value.startsWith("I'") &&
                (m.range.first !in starts || m.value.lowercase() !in openers)
        }
    }

    private fun isListing(item: Evidence) = item.documentId.startsWith("wv-eat-") || item.documentId.startsWith("wv-place-") ||
        item.documentId.startsWith("osm-place-")

    private fun isGuideSection(item: Evidence) =
        !isListing(item) && item.source.contains("wikivoyage", ignoreCase = true) && " — " in item.title

    /**
     * [evidence] without map listings and destination guide sections, for a question that names
     * no place ([destinations] as for [namesPlace]); [sectionsOf] finds a guide's sections titled
     * with [HEADINGS], to tell a destination from a topic.
     */
    suspend fun withoutOtherPlaces(
        question: String,
        evidence: List<Evidence>,
        destinations: List<String> = emptyList(),
        chosen: Collection<String> = emptyList(),
        sectionsOf: suspend (String) -> List<Evidence>,
    ): List<Evidence> {
        if (namesPlace(question, destinations)) return evidence
        val guides = evidence.filter(::isGuideSection).map { it.title.substringBefore(" — ").trim() }.distinct()
        // "best way to see ha long bay": typed in lowercase, the place is still named, and the guide the
        // app chose for the question is the one it asked about.
        val asked = (chosen.map { it.substringBefore('/').trim() } + guides.filter { namedLowercase(question, it) }).toSet()
        val articles = guides.filterNot { it in asked }.take(MAX_ARTICLES)
        val destinations = articles.filter { article ->
            sectionsOf(article).any {
                it.title.startsWith("$article — ") && it.title.substringAfter(" — ").substringBefore(" > ").trim().lowercase() in destinationHeadings
            }
        }.toSet()
        return evidence.filterNot { item ->
            isListing(item) && item.title.substringBefore(" — ").trim() !in asked ||
                (isGuideSection(item) && item.title.substringBefore(" — ").trim() in destinations)
        }
    }

    /**
     * Whether [question] names the guide [name] without a capital: "hoi an what food should i try". A
     * name of one word counts only in a question typed all in lowercase, so "charge my mobile" in an
     * otherwise capitalized question is not the city of Mobile.
     */
    internal fun namedLowercase(question: String, name: String): Boolean {
        val plain = QuestionText.foldAccents(name.substringBefore('/').trim()).lowercase()
        if (plain.length < 4) return false
        val text = QuestionText.foldAccents(question).lowercase()
        if (!Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(plain) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)) return false
        return ' ' in plain || question == question.lowercase()
    }

    /**
     * The counterpart of [withoutOtherPlaces]: a question naming a place is not answered by
     * another destination's guide. "Is tap water safe in Mexico City?" matched "Cagayan de Oro —
     * Stay safe > Tap water", and the model credited that city's water district to Mexico City.
     * Kept: guides the question names (and their districts, "Mexico City/Centro"), the places the
     * app itself chose ([chosen]: a beach read through its country), any guide whose name or text
     * names the place (a region), topic guides and listings.
     */
    suspend fun withoutUnaskedPlaces(
        question: String,
        evidence: List<Evidence>,
        chosen: Collection<String>,
        sectionsOf: suspend (String) -> List<Evidence>,
    ): List<Evidence> {
        fun article(item: Evidence) = item.title.substringBefore(" — ").trim()
        fun base(name: String) = name.substringBefore('/').trim()
        // As written, capital included: "tap water" does not name the "Water" topic guide.
        // A lowercase city arrives confirmed through [chosen] instead.
        fun named(name: String) = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(base(name)) + "(?![\\p{L}\\p{N}])")
            .containsMatchIn(question) || chosen.any { base(it).equals(base(name), ignoreCase = true) }
        val articles = evidence.filter(::isGuideSection).map(::article).distinct()
        val kept = articles.filter { named(it) }.map(::base).toSet() + chosen.map(::base)
        if (articles.none { named(it) }) return evidence
        fun mentions(text: String) = kept.any { name ->
            Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)
        }
        // A region or part of the place still speaks about it: "Nordic countries — Do > Saunas"
        // for Finland, "Finland Proper" for Finland, "Southeast Asia — Respect" naming Thailand.
        val others = articles.filter { other ->
            base(other) !in kept && !mentions(other) && evidence.none { article(it) == other && mentions(it.text) }
        }.take(MAX_ARTICLES)
        val elsewhere = others.filter { other ->
            sectionsOf(other).any {
                it.title.startsWith("$other — ") && it.title.substringAfter(" — ").substringBefore(" > ").trim().lowercase() in destinationHeadings
            }
        }.toSet()
        return evidence.filterNot { isGuideSection(it) && article(it) in elsewhere }
    }
}
