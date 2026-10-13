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
    private val clock = Regex("(?<![\\d.:])([01]?\\d|2[0-3])[.:]([0-5]\\d)(?![\\d.:])")
    private val sentenceEnd = Regex("(?<=[.!?])\\s+|\\n+")

    private fun times(text: String): Set<String> = clock.findAll(text)
        .map { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2]) }.toSet()

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
