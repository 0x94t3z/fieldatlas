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

    /** Of the titled passages found, those of the named place, closest to the question first. */
    fun pick(question: String, place: String, found: List<Evidence>, limit: Int): List<Evidence> {
        val words = FtsQuery.from(question)?.terms.orEmpty().toSet()
        return found.filter { it.title.startsWith("$place — ") }
            .sortedByDescending { item ->
                val tokens = Regex("[\\p{L}\\p{N}]+").findAll((item.title + " " + item.text).lowercase()).map { it.value }.toSet()
                // The section heading naming the topic ("Traffic", "Beachgoing") counts most.
                words.count { w -> tokens.any { it.startsWith(w) } } + 3 * words.count { w -> item.title.lowercase().contains(w) }
            }
            .take(limit)
    }
}
