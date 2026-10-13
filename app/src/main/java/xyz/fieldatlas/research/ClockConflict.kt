package xyz.fieldatlas.research

import xyz.fieldatlas.text.MarkdownLiteral

/**
 * Two saved notices can give different times for the same event, and a small model tends to
 * cite one and ignore the other. When an uncited source states a different clock time in a
 * sentence about what was asked, the answer quotes that sentence unchanged with its citation.
 * Clock times only: different quantities are usually different subjects (32 vs 128 bits),
 * not a disagreement. Lexical overlap only; this does not decide which source is right.
 */
internal object ClockConflict {
    // "[.:]", not "[:.]": Android's ICU engine reads the latter as a POSIX class and fails.
    // A full stop after the minutes ends the sentence ("leaves at 22:00."); one before a digit does not.
    // A following "am" or "pm" is read, so 9:30 pm and 21:30 are the same time.
    private val clock = Regex("(?i)(?<![\\d.:])([01]?\\d|2[0-3])([.:])([0-5]\\d)(?![\\d:]|\\.\\d)(?:\\s*([ap])\\.?\\s?m\\b\\.?)?")
    // What a time is the time of. An opening time and a closing time are not a disagreement.
    private val events = listOf(
        // Not "from": "the last ferry from Koh Samui" is a departure, not an opening.
        "open" to Regex("(?i)\\b(open\\w*|start\\w*|begin\\w*)\\b"),
        "close" to Regex("(?i)\\b(clos\\w*|end\\w*|finish\\w*|until|till|last (?:entry|admission))\\b"),
        "depart" to Regex("(?i)\\b(depart\\w*|leav\\w*)\\b"),
        "arrive" to Regex("(?i)\\b(arriv\\w*|gets? in)\\b"),
    )
    private fun eventsOf(text: String): Set<String> = events.filter { (_, words) -> words.containsMatchIn(text) }.map { it.first }.toSet()
    private val sentenceEnd = Regex("(?<=[.!?])\\s+|\\n+")
    // "€12.50", "12.50 EUR" and "12.50%" are amounts.
    private val amountBefore = Regex("(?i)(?:[€$£¥₹₽¢]|\\b(?:eur|usd|gbp|chf|sek|nok|dkk|pln|czk|huf|jpy|cny|inr|aud|cad|nzd|zar|price|fee|cost|costs|only)\\s*)$")
    private val amountAfter = Regex("(?i)^\\s*(?:[€$£¥₹₽¢%]|(?:eur|usd|gbp|chf|sek|nok|dkk|pln|czk|huf|jpy|cny|inr|aud|cad|nzd|zar|kr|zł|kč|ft|euros?|dollars?|pounds?|francs?|cents?|per\\b|each\\b|km\\b|kg\\b|m\\b|mm\\b|cm\\b|%))")
    // "18.00" is a clock time only beside a word that marks a time. Joining words ("and", "to") and
    // dashes are left out: "€12.50 and 15.00" and "12.50–15.00 EUR" are prices, and a missed
    // conflict note costs less than a false one.
    private val dotTimeBefore = Regex("(?i)\\b(?:at|until|till|before|after|opens?|closes?|starts?|ends?|departs?|arrives?|leaves?|doors open)\\s*$")
    private val dotTimeAfter = Regex("(?i)^\\s*(?:h\\b|hrs?\\b|uhr\\b|am\\b|pm\\b|a\\.m\\.|p\\.m\\.|o'?clock)")

    private fun isAmount(text: String, m: MatchResult): Boolean =
        amountBefore.containsMatchIn(text.substring(0, m.range.first).takeLast(12)) ||
            amountAfter.containsMatchIn(text.substring(m.range.last + 1).take(12))

    // "18:00" is always a time; "18.00" is one only when no amount surrounds it and a time word does.
    private fun times(text: String): Set<String> = clock.findAll(text)
        .filter { m ->
            m.groupValues[2] == ":" || m.groupValues[4].isNotEmpty() || !isAmount(text, m) &&
                (dotTimeBefore.containsMatchIn(text.substring(0, m.range.first).takeLast(12)) ||
                    dotTimeAfter.containsMatchIn(text.substring(m.range.last + 1).take(10)))
        }
        .map { m ->
            val hour = m.groupValues[1].toInt()
            val h24 = when (m.groupValues[4].lowercase()) {
                "p" -> if (hour in 1..11) hour + 12 else hour
                "a" -> if (hour == 12) 0 else hour
                else -> hour
            }
            "%02d:%s".format(h24, m.groupValues[3])
        }.toSet()

    fun note(question: String, answer: String, sources: List<PromptSource>): String? {
        if (EvidenceRelevance.conceptualComparisonSubjects(question).isNotEmpty()) return null
        val stated = times(answer)
        if (stated.isEmpty()) return null
        val terms = FtsQuery.from(question)?.terms.orEmpty()
        if (terms.size < 2) return null
        val cited = AnswerText.citationAudit(answer, sources.size).citedSourceIds
        val statedEvents = eventsOf(answer)
        val differing = sources.filter { it.citationId !in cited }.mapNotNull { source ->
            source.excerpt.split(sentenceEnd).map(String::trim).firstOrNull { sentence ->
                val found = times(sentence)
                val sentenceEvents = eventsOf(sentence)
                found.isNotEmpty() && found.none { it in stated } && sentence.length <= 300 &&
                    EvidenceRelevance.termHits(sentence, terms) >= 2 &&
                    (statedEvents.isEmpty() || sentenceEvents.isEmpty() || sentenceEvents.any { it in statedEvents })
            }?.let { sentence -> "“${MarkdownLiteral.encode(sentence)}” [${source.citationId}]" }
        }.take(2)
        if (differing.isEmpty()) return null
        return "_Another selected source gives a different time:_ " + differing.joinToString(" ") +
            " _Check which one is current._"
    }
}
