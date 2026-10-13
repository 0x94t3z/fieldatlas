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
    private val clock = Regex("(?<![\\d.:])([01]?\\d|2[0-3])([.:])([0-5]\\d)(?![\\d.:])")
    private val sentenceEnd = Regex("(?<=[.!?])\\s+|\\n+")
    // "€12.50", "12.50 EUR" and "12.50%" are amounts, whatever separator they use.
    private val amountBefore = Regex("(?i)(?:[€$£¥₹₽¢]|\\b(?:eur|usd|gbp|chf|sek|nok|dkk|pln|czk|huf|jpy|cny|inr|aud|cad|nzd|zar|price|fee|cost|costs|from|only)\\s*)$")
    private val amountAfter = Regex("(?i)^\\s*(?:[€$£¥₹₽¢%]|(?:eur|usd|gbp|chf|sek|nok|dkk|pln|czk|huf|jpy|cny|inr|aud|cad|nzd|zar|kr|zł|kč|ft|euros?|dollars?|pounds?|francs?|cents?|per\\b|each\\b|km\\b|kg\\b|m\\b|mm\\b|cm\\b|%))")
    // "18.00" is a clock time only beside time words; "18:00" always is.
    private val dotTimeContext = Regex("(?i)(?:\\b(?:at|from|until|till|to|by|between|and|before|after|around|about|opens?|closes?|closing|opening|starts?|ends?|departs?|arrives?|leaves?|doors|o'?clock)\\s*$)|(?:^\\s*(?:h\\b|hrs?\\b|uhr\\b|am\\b|pm\\b|a\\.m\\.|p\\.m\\.|o'?clock|–|-|to\\b|until\\b|till\\b))")

    private fun isAmount(text: String, m: MatchResult): Boolean =
        amountBefore.containsMatchIn(text.substring(0, m.range.first).takeLast(12)) ||
            amountAfter.containsMatchIn(text.substring(m.range.last + 1).take(12))

    private fun times(text: String): Set<String> = clock.findAll(text)
        .filterNot { isAmount(text, it) }
        .filter { m ->
            m.groupValues[2] == ":" || dotTimeContext.containsMatchIn(text.substring(0, m.range.first).takeLast(12)) ||
                dotTimeContext.containsMatchIn(text.substring(m.range.last + 1).take(10))
        }
        .map { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[3]) }.toSet()

    fun note(question: String, answer: String, sources: List<PromptSource>): String? {
        if (EvidenceRelevance.conceptualComparisonSubjects(question).isNotEmpty()) return null
        val stated = times(answer)
        if (stated.isEmpty()) return null
        val terms = FtsQuery.from(question)?.terms.orEmpty()
        if (terms.size < 2) return null
        val cited = AnswerText.citationAudit(answer, sources.size).citedSourceIds
        val differing = sources.filter { it.citationId !in cited }.mapNotNull { source ->
            source.excerpt.split(sentenceEnd).map(String::trim).firstOrNull { sentence ->
                val found = times(sentence)
                found.isNotEmpty() && found.none { it in stated } && sentence.length <= 300 &&
                    EvidenceRelevance.termHits(sentence, terms) >= 2
            }?.let { sentence -> "“${MarkdownLiteral.encode(sentence)}” [${source.citationId}]" }
        }.take(2)
        if (differing.isEmpty()) return null
        return "_Another selected source gives a different time:_ " + differing.joinToString(" ") +
            " _Check which one is current._"
    }
}
