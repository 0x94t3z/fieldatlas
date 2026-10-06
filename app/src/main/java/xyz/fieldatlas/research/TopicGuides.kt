package xyz.fieldatlas.research

/**
 * A practical question about a travel topic rather than a place ("I lost my passport while
 * travelling", "How can I reduce jet lag?") is answered by the travel guide written on that topic:
 * "Passport — What if I lose it while travelling?", "Jet lag — Prepare". Keyword search ranks
 * passages that repeat the question's other words higher, so the guide is found by its title, which
 * the question names in its own words, and its sections by the words they share with the question.
 * A question that names its topic only by what it asks about ("pay in euros or my home currency")
 * reads the guides Wikivoyage writes for that topic ("Money"), by [guidesFor].
 */
object TopicGuides {
    private const val MAX_WORDS = 3
    private const val MAX_NAMES = 10
    // A section must share a heading word (counted three times) or two words of its text.
    private const val MIN_SCORE = 2

    // Words that cannot begin or end a topic's name.
    private val edge = setOf(
        "i", "i'm", "me", "my", "we", "our", "you", "your", "a", "an", "the", "of", "to", "in", "on", "at", "for", "from",
        "by", "with", "and", "or", "but", "is", "are", "was", "were", "be", "been", "do", "does", "did", "have", "has", "had",
        "can", "could", "should", "would", "will", "what", "which", "who", "where", "when", "why", "how", "this", "that",
        "these", "those", "it", "its", "it's", "there", "here", "while", "after", "before", "during", "about", "into", "than",
        "then", "so", "if", "not", "no", "any", "some", "all", "need", "want", "know", "get", "go", "best", "good", "way",
        "ways", "tell", "please", "much", "many", "more", "most", "very", "really", "also", "just", "still", "out", "up",
        "off", "over", "near", "around", "between", "safe", "safely", "help", "make", "use",
    )
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]*")
    private val token = Regex("[\\p{L}\\p{N}]+")

    /**
     * The topic guides that answer each practical topic of a destination guide, for a question
     * that names no destination: visas and money are the same everywhere.
     */
    private val guidesByTopic = mapOf(
        "Buy" to listOf("Money", "Shopping"),
        "Stay healthy" to listOf("Travel health kit", "Infectious diseases", "Pests", "Medication", "Flying and health"),
        "Connect" to listOf("Telephone service", "Internet access"),
        "Stay safe" to listOf("Common scams", "Crime"),
        "Get in" to listOf("Air travel", "Flying and health"),
    )
    // A guide the question's own words name, whatever topic they fall under ("why do my ears pop on planes").
    private val guidesByWord = listOf(
        Regex("(?i)\\b(?:planes?|flights?|flying|fly|landing|take-?off|cabin|airlines?|jet ?lag)\\b") to listOf("Flying and health"),
    )

    /** The topic guides for the question's [topics] (see [TravelSections.topicsOf]); one its own words name comes first and wins ties. */
    fun guidesFor(topics: List<String>, question: String = ""): List<String> =
        (guidesByWord.filter { (words, _) -> words.containsMatchIn(question) }.flatMap { it.second } +
            topics.flatMap { guidesByTopic[it].orEmpty() }).distinct()

    /**
     * Names the topic's guide could have, from the question's runs of up to three words, longest
     * first: "jet lag", "passport". A run of capitalized words names a place ("New York", "Spain"),
     * which the place guides answer; an acronym ("DEET", "GPS") is a thing and may name a guide.
     */
    fun names(question: String): List<String> {
        val words = word.findAll(question).map { it.value.trim('\'', '-') }.filter(String::isNotEmpty).toList()
        // The question's first word is capitalized whatever it is.
        fun proper(i: Int) = i > 0 && words[i].first().isUpperCase() && words[i].drop(1).any(Char::isLowerCase)
        val names = ArrayList<String>()
        for (n in MAX_WORDS downTo 1) for (start in 0..words.size - n) {
            val run = words.subList(start, start + n)
            if (run.first().lowercase() in edge || run.last().lowercase() in edge) continue
            if ((start until start + n).all(::proper) || run.any { w -> w.all(Char::isDigit) }) continue
            if (n == 1 && run[0].length < 4) continue
            names += run.joinToString(" ")
        }
        return names.distinctBy(String::lowercase).take(MAX_NAMES)
    }

    /**
     * The guide's sections closest to the question, one passage each, best first: a word the
     * question shares with the section's own heading counts three times, with a parent heading
     * once ("Make copies" under "What if I lose it while travelling?" is not itself about losing
     * a passport), and with the section's text once. The question's names ("New York") say where,
     * not what, and do not count. Only a travel guide with sections qualifies; an encyclopedia
     * article or a single listing is not a guide.
     */
    fun pick(question: String, article: String, found: List<Evidence>, limit: Int): List<Evidence> = pickAcross(question, listOf(article), mapOf(article to found), limit)

    /** [pick] over several guides at once: the sections closest to the question of any of them, in the guides' order on ties. */
    fun pickAcross(question: String, articles: List<String>, found: Map<String, List<Evidence>>, limit: Int): List<Evidence> {
        val scores = HashMap<Evidence, Int>()
        for (article in articles) scores += scored(question, article, found[article].orEmpty())
        // A stable order on ties: as the guides were given, then as each guide's sections were.
        val position = articles.flatMap { found[it].orEmpty() }.withIndex().associate { it.value to it.index }
        val ranked = scores.keys.sortedWith(compareByDescending<Evidence> { scores.getValue(it) }.thenBy { position[it] ?: Int.MAX_VALUE })
        return TravelSections.representatives(ranked, { scores.getValue(it) }, limit, HEADING_SCORE)
    }

    /** Each section of the guide [article] among [found] with its score, those below [MIN_SCORE] left out. */
    private fun scored(question: String, article: String, found: List<Evidence>): Map<Evidence, Int> {
        val guide = found.filter { it.source.contains("wikivoyage", ignoreCase = true) && it.title.startsWith("$article — ", ignoreCase = true) }
        if (guide.map { it.title }.distinct().size < 2) return emptyMap()
        val named = (FtsQuery.from(article)?.terms.orEmpty() + TravelSections.nameTerms(question)).map(TravelSections::stem).toSet()
        val words = FtsQuery.from(question)?.terms.orEmpty().filterNot { it in TravelSections.filler }.map(TravelSections::stem).distinct().filterNot { it in named }
        if (words.isEmpty()) return emptyMap()
        fun tokens(text: String) = token.findAll(TravelSections.fold(text)).map { it.value }.toSet()
        return guide.associateWith { item ->
            val own = tokens(TravelSections.ownHeading(item.title))
            val parents = tokens(item.title.substringAfter(" — ").substringBeforeLast(" > ", ""))
            val body = tokens(item.text)
            fun shared(set: Set<String>) = words.count { w -> set.any { it.startsWith(w) } }
            3 * shared(own) + shared(parents) + shared(body)
        }.filterValues { it >= MIN_SCORE }
    }

    // A section whose own heading shares a question word is stood for by its opening passage (see TravelSections.representatives).
    private const val HEADING_SCORE = 3
}
