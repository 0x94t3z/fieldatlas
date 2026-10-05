package xyz.fieldatlas.research

/**
 * A travel question that names a place and a practical topic ("How do I cross the street in
 * Hanoi?", "Which vaccinations for Kenya?") is answered best by that place's own guide section:
 * "Hanoi — Stay safe > Traffic", "Kenya — Stay healthy". Keyword search over hundreds of
 * thousands of passages often prefers another town's section that shares more words, so the
 * section is looked up by its title, as the travel guides name them.
 */
object TravelSections {
    /** Wikivoyage's standard sections, and the words that ask about each. */
    private val topics = listOf(
        "Stay safe" to Regex("(?i)\\b(safe|safety|dangerous|danger|scams?|crime|theft|pickpockets?|traffic|cross(?:ing)? the (?:street|road)|swim(?:ming)?|beach|flags?|rip|police|robbery|mugg\\w*)\\b"),
        "Stay healthy" to Regex("(?i)\\b(vaccin\\w*|jabs?|malaria|health|healthy|sick|illness|disease|tap water|drinking water|altitude|mosquito\\w*|doctor|hospital|pharmac\\w*|food poisoning|diarrh\\w*)\\b"),
        "Buy" to Regex("(?i)\\b(cash|cards?|credit|debit|atms?|money|currency|exchange|haggl\\w*|bargain\\w*|tip|tips|tipping|prices?|souks?|markets?|pay|paying)\\b"),
        "Get in" to Regex("(?i)\\b(airport|arrive|arriving|arrival|get (?:to|from)|visas?|border|ferry from|train to|flights?)\\b"),
        "Get around" to Regex("(?i)\\b(get around|getting around|bus|buses|taxi|taxis|metro|subway|tram|train|trains|ride[- ]hailing|grab|uber|rent a car|car rental|drive|driving|scooter|motorbike|bike)\\b"),
        "Respect" to Regex("(?i)\\b(wear|dress|clothing|temples?|mosques?|etiquette|customs?|rude|polite|respect\\w*|taboo|shoes)\\b"),
        "Connect" to Regex("(?i)\\b(sim|sim card|phone|mobile data|internet|wi-?fi|esim)\\b"),
        "Talk" to Regex("(?i)\\b(language|speak|say|phrase|english)\\b"),
        "Understand" to Regex("(?i)\\b(climate|weather|season|rainy|monsoon|best time|when to (?:go|visit)|holidays?|festivals?)\\b"),
        "Cope" to Regex("(?i)\\b(embass\\w*|consulate|laundry|launderette|toilets?)\\b"),
    )
    // A linking word only inside a name ("Rio de Janeiro"), never trailing it ("How do").
    private val place = Regex("\\b([A-Z][\\p{L}'-]+(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?[A-Z][\\p{L}'-]+)*)")
    private val notPlaces = setOf("How", "What", "Which", "Where", "When", "Why", "Who", "Is", "Are", "Can", "Do", "Does", "Should",
        "I", "I'm", "My", "We", "Tell", "Please", "The", "A", "An", "In", "At", "To", "From", "For", "Any", "Best", "EU")

    data class Lookup(val places: List<String>, val sections: List<String>)

    /** The places named in the question (longest first) and the guide sections its topic asks for. */
    fun lookup(question: String): Lookup? {
        val sections = topics.filter { (_, words) -> words.containsMatchIn(question) }.map { it.first }
        if (sections.isEmpty()) return null
        val places = place.findAll(question).map { it.groupValues[1].trim() }
            .flatMap { phrase -> phrase.split(Regex("\\s+")).let { words -> (words.size downTo 1).map { n -> words.takeLast(n).joinToString(" ") } } }
            .filter { it !in notPlaces && it.length >= 3 }
            .distinct().sortedByDescending { it.length }.toList()
        return if (places.isEmpty()) null else Lookup(places, sections)
    }

    // Forms that share no prefix with the rest of the verb: "lost" is "lose" and "losing".
    private val irregular = mapOf("lost" to "los", "lose" to "los", "losing" to "los", "loses" to "los")

    /** A question word as a prefix of its other forms: "souks" finds "souk", "lost" finds "losing". */
    internal fun stem(word: String): String = irregular[word.lowercase()] ?: word.lowercase().take(maxOf(4, word.length - 1))

    private val questionWord = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]*")

    /**
     * The words of the names a question gives after its first word ("New York", "Singapore"): they
     * say where, not what. A jet lag guide's sentence about a Paris–New York flight shares "New
     * York" with "after flying from New York to Singapore" and still says nothing about jet lag.
     * An acronym ("SIM", "ATM") is a thing, not a name, and stays.
     */
    internal fun nameTerms(question: String): Set<String> = questionWord.findAll(question).drop(1).map { it.value }
        .filter { w -> w.first().isUpperCase() && w.drop(1).any(Char::isLowerCase) && !w.startsWith("I'") }
        .flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toSet()

    // "a beach in Sydney", "in northern Cambodia", "on the outskirts of Kyoto".
    private val within = Regex("\\b(?:in|near|outside|of)\\s+(?:the\\s+)?(?:(?:north|south|east|west|central|northern|southern|eastern|western|" +
        "north-?eastern|north-?western|south-?eastern|south-?western)\\s+)?(\\p{Lu}[\\p{L}'’-]*(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?\\p{Lu}[\\p{L}'’-]*)*)")

    /**
     * The place a beach, district or village lies in, from the first sentence of its overview:
     * "Bondi Beach is a beach in Sydney, Australia" gives "Sydney", whose guide has the beach
     * safety section that Bondi Beach, with no guide of its own, lacks.
     */
    fun parentOf(place: String, overview: String): String? {
        val first = SourceLead.sentences(overview.replace(Regex("\\s+"), " ").trim()).firstOrNull() ?: return null
        return within.findAll(first).map { it.groupValues[1].removeSuffix("'s").removeSuffix("’s").trim() }
            .firstOrNull { it.length >= 3 && !it.equals(place, ignoreCase = true) && !place.contains(it, ignoreCase = true) && it !in notPlaces }
    }

    private val pointsBack = Regex("^(?:These|This|Those|They|It|Its|Such|That)\\b")

    /** A section's own heading, the last after its parents: "Make copies" in "What if I lose it? > Make copies". */
    internal fun ownHeading(title: String): String = title.substringAfter(" — ").substringAfterLast(" > ").lowercase()

    /**
     * The sentences of the place's own section that answer the question, quoted for the answer's
     * opening: the sentence sharing most of the question's words, with the one or two after it.
     * The question's own words count double; the section's heading and the topic's own words
     * count too, since a guide answers "how does haggling work" with "you generally need to
     * bargain", but a sentence needs at least one of the question's words.
     */
    fun lead(question: String, place: String?, sources: List<PromptSource>): SourceLead.Lead? {
        if (place == null) return null
        val placeWords = FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question)
        val words = FtsQuery.from(question)?.terms.orEmpty().filterNot { it in placeWords }.map(::stem).distinct()
        if (words.isEmpty()) return null
        val asked = topics.filter { (_, pattern) -> pattern.containsMatchIn(question) }.map { it.second }
        var best: Triple<Int, String, String>? = null // hits, citation, quote
        for (source in sources.filter { it.evidence.title.startsWith("$place — ") }) {
            // A passage may open inside a sentence (passages overlap a little); that piece is no quote.
            // A list item is quoted as the sentence it is, without its marker.
            val sentences = source.evidence.text.split(Regex("\\n+")).filterNot { it.startsWith("- ") && it.length < 40 }
                .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ") }
                .flatMap { SourceLead.sentences(it.replace(Regex("\\s+"), " ").trim()) }
                .dropWhile { s -> s.firstOrNull()?.let { !it.isUpperCase() && !it.isDigit() && it !in "\"“'‘(¥€$£" } ?: true }
            sentences.forEachIndexed { i, sentence ->
                val tokens = Regex("[\\p{L}\\p{N}]+").findAll(sentence.lowercase()).map { it.value }.toSet()
                // A section headed with the question's own word ("Stay safe > Traffic") counts double;
                // a subsection does not inherit its parent's heading ("... > Make copies").
                val heading = ownHeading(source.evidence.title)
                val own = words.count { w -> tokens.any { it.startsWith(w) } }
                val topical = asked.flatMap { p -> p.findAll(sentence).map { it.value.lowercase() }.toList() }.distinct().size
                val hits = 2 * own + words.count { w -> heading.contains(w) } + topical
                if (own > 0 && hits > (best?.first ?: 2)) {
                    // "These flags are placed by the lifeguards..." points back to the sentence
                    // before it ("swim between the yellow and red flags"), so the quote starts there.
                    val opening = if (i > 0 && pointsBack.containsMatchIn(sentence)) sentences[i - 1] + " " else ""
                    val quote = StringBuilder(opening + sentence)
                    for (next in sentences.drop(i + 1).take(2)) { if (quote.length + next.length > 520) break; quote.append(' ').append(next) }
                    if (quote.last() in ".!?\"”’)" && quote.length <= 640) best = Triple(hits, source.citationId, quote.toString())
                }
            }
        }
        return best?.let { SourceLead.Lead(it.second, it.third) }
    }

    /**
     * Of the titled passages found, those of the named place, closest to the question first, one
     * passage per section before a second of any: "Getting tickets" must not crowd out "Rules",
     * where Italy's guide says to validate a ticket.
     */
    fun pick(question: String, place: String, found: List<Evidence>, limit: Int): List<Evidence> {
        val words = FtsQuery.from(question)?.terms.orEmpty().toSet()
        val ranked = found.filter { it.title.startsWith("$place — ") }
            .sortedByDescending { item ->
                val tokens = Regex("[\\p{L}\\p{N}]+").findAll((item.title + " " + item.text).lowercase()).map { it.value }.toSet()
                // The section heading naming the topic ("Traffic", "Beachgoing") counts most.
                words.count { w -> tokens.any { it.startsWith(w) } } + 3 * words.count { w -> item.title.lowercase().contains(w) }
            }
        return (ranked.distinctBy { it.title } + ranked).distinct().take(limit)
    }
}
