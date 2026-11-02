package xyz.fieldatlas.research

/**
 * The sentence of a passage that answers a short question, shown at once on the best-match card while the model is
 * still to write: "Trump received 304 electoral votes" for "how many electoral votes did Trump have in 2016". A
 * sentence is picked only when it names the question's own words and holds the kind of thing asked for (a number for
 * "how many", a year for "when", a name for "who", a "known as" for a nickname); otherwise the card shows the passage's
 * opening as before.
 */
object AnswerSentence {
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val sentenceEnd = Regex("(?<=[.!?][\"”’)]?)\\s+(?=[\\p{Lu}\\p{N}\"“(])")
    private val year = Regex("\\b(1[0-9]{3}|20[0-9]{2})\\b")
    private val number = Regex("\\b\\d[\\d,.]*\\b|\\b(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|hundred|thousand|million|billion)\\b",
        RegexOption.IGNORE_CASE)
    private val name = Regex("\\b\\p{Lu}\\p{Ll}+(?:\\s+\\p{Lu}\\p{Ll}+)+")
    private val digits = Regex("\\d")
    private val pronounStart = Regex("^(?:It|They|He|She|This|These|Its|Their|His|Her)\\b")
    private val measures = mapOf(
        "tall" to setOf("high", "height", "tall", "metres", "meters", "feet"), "high" to setOf("high", "height", "metres", "meters", "feet"),
        "deep" to setOf("deep", "depth", "metres", "meters", "feet"), "long" to setOf("long", "length", "kilometres", "kilometers", "miles"),
        "far" to setOf("far", "distance", "kilometres", "kilometers", "miles"), "old" to setOf("old", "age", "years"),
        "big" to setOf("area", "size", "square"), "large" to setOf("area", "size", "square"),
        "heavy" to setOf("weight", "weighs", "kilograms", "tonnes", "pounds"), "fast" to setOf("speed", "fast", "per hour"))
    private val nickname = Regex("(?i)\\b(?:known as|called|named|nicknamed?|name comes|named after|takes its name)\\b")

    private enum class Kind { WHEN, COUNT, WHO, NAME }

    private fun kind(question: String): Kind? {
        val q = question.lowercase()
        return when {
            Regex("\\b(?:why (?:is|are|was|were|do|did)\\b.*\\b(?:called|known as|considered|nicknamed|named)|how did .* get (?:its|their|his|her|the) (?:name|nickname)|where (?:does|did) (?:the )?(?:name|nickname|word|term))").containsMatchIn(q) -> Kind.NAME
            Regex("^\\s*(?:when\\b|(?:in )?(?:what|which) year\\b)").containsMatchIn(q) -> Kind.WHEN
            Regex("^\\s*how (?:many|much|tall|high|deep|long|far|old|big|large|heavy|fast)\\b").containsMatchIn(q) -> Kind.COUNT
            Regex("^\\s*who\\b").containsMatchIn(q) -> Kind.WHO
            else -> null
        }
    }

    private fun words(text: String) = word.findAll(text.lowercase().replace("'", "").replace("’", "")).map { it.value }.toList()

    /** The answering sentence of [passage] for [question], or null when no sentence clearly answers it. */
    fun pick(question: String, passage: String): String? {
        val kind = kind(question) ?: return null
        val asked = FtsQuery.from(question)?.terms.orEmpty().map { it.replace("'", "") }.filter { it.length >= 3 }.toSet()
        if (asked.isEmpty()) return null
        val sentences = passage.lineSequence().filterNot { it.startsWith("Also known as:") }.joinToString(" ")
            .split(sentenceEnd).map(String::trim).filter { it.length in 20..400 }
        // A measure ("how tall") is answered by a figure in digits, and its word may be the measure's own ("high", "metres").
        val measure = Regex("^\\s*how (tall|high|deep|long|far|old|big|large|heavy|fast)\\b", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.lowercase()
        val measureWords = measure?.let { measures[it] }.orEmpty()
        val askedWords = asked + measureWords
        var previous = 0
        return sentences.map { sentence ->
            val said = words(sentence)
            val own = askedWords.count { term -> said.any { it == term || it.startsWith(term) && term.length >= 4 } }
            // "It is about 8,848 metres high", after a sentence that named Mount Everest: the pronoun carries the subject.
            val hits = if (pronounStart.containsMatchIn(sentence)) own + previous else own
            previous = own
            val fits = when (kind) {
                Kind.WHEN -> year.containsMatchIn(sentence)
                Kind.COUNT -> if (measure != null) digits.containsMatchIn(sentence.replace(year, "")) else number.containsMatchIn(sentence.replace(year, ""))
                Kind.WHO -> name.containsMatchIn(sentence)
                Kind.NAME -> nickname.containsMatchIn(sentence)
            }
            Triple(sentence, hits, fits)
        }.filter { (_, hits, fits) -> fits && hits >= 2 }.maxByOrNull { it.second }?.first
    }
}
