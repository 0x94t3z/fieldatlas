package xyz.fieldatlas.research

/**
 * A practical question about a travel topic rather than a place ("I lost my passport while
 * travelling", "How can I reduce jet lag?") is answered by the travel guide written on that topic:
 * "Passport — What if I lose it while travelling?", "Jet lag — Prepare". Keyword search ranks
 * passages that repeat the question's other words higher, so the guide is found by its title, which
 * the question names in its own words, and its sections by the words they share with the question.
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
     * Names the topic's guide could have, from the question's runs of up to three words, longest
     * first: "jet lag", "passport". A run of capitalized words names a place ("New York", "Spain"),
     * which the place guides answer.
     */
    fun names(question: String): List<String> {
        val words = word.findAll(question).map { it.value.trim('\'', '-') }.filter(String::isNotEmpty).toList()
        // The question's first word is capitalized whatever it is.
        fun proper(i: Int) = i > 0 && words[i].first().isUpperCase()
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
    fun pick(question: String, article: String, found: List<Evidence>, limit: Int): List<Evidence> {
        val guide = found.filter { it.source.contains("wikivoyage", ignoreCase = true) && it.title.startsWith("$article — ", ignoreCase = true) }
        if (guide.map { it.title }.distinct().size < 2) return emptyList()
        val named = (FtsQuery.from(article)?.terms.orEmpty() + TravelSections.nameTerms(question)).map(TravelSections::stem).toSet()
        val words = FtsQuery.from(question)?.terms.orEmpty().map(TravelSections::stem).distinct().filterNot { it in named }
        if (words.isEmpty()) return emptyList()
        fun tokens(text: String) = token.findAll(text.lowercase()).map { it.value }.toSet()
        return guide.map { item ->
            val own = tokens(TravelSections.ownHeading(item.title))
            val parents = tokens(item.title.substringAfter(" — ").substringBeforeLast(" > ", ""))
            val body = tokens(item.text)
            fun shared(set: Set<String>) = words.count { w -> set.any { it.startsWith(w) } }
            item to 3 * shared(own) + shared(parents) + shared(body)
        }.filter { it.second >= MIN_SCORE }.sortedByDescending { it.second }.map { it.first }.distinctBy { it.title }.take(limit)
    }
}
