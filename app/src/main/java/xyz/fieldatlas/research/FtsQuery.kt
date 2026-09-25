package xyz.fieldatlas.research

import java.text.Normalizer
import java.util.Locale

class FtsQuery private constructor(
    val normalizedInput: String,
    val terms: List<String>,
    val matchExpression: String,
    val fallbackExpression: String,
) {
    companion object {
        private const val MAX_CODE_POINTS = 512
        private val termPattern = Regex("[\\p{L}\\p{N}]+")

        fun from(input: String): FtsQuery? {
            val normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
            val codePoints = normalized.codePointCount(0, normalized.length)
            val capped = if (codePoints > MAX_CODE_POINTS) {
                normalized.substring(0, normalized.offsetByCodePoints(0, MAX_CODE_POINTS))
            } else {
                normalized
            }
            val terms = termPattern.findAll(capped)
                .map { it.value.lowercase(Locale.ROOT) }
                .filter { it.codePointCount(0, it.length) >= 2 }
                .filterNot(STOP_WORDS::contains)
                .distinct()
                .toList()
            if (terms.isEmpty()) return null
            val searchableTerms = terms.map(::searchableTerm)
            return FtsQuery(
                normalizedInput = capped,
                terms = terms,
                matchExpression = searchableTerms.joinToString(" AND "),
                fallbackExpression = searchableTerms.joinToString(" OR "),
            )
        }

        private fun searchableTerm(term: String): String {
            val stem = when {
                term.length > 4 && term.endsWith("ies") -> term.dropLast(3) + "y"
                term.length > 4 && term.endsWith("es") -> term.dropLast(2)
                term.length > 3 && term.endsWith("s") -> term.dropLast(1)
                term.length > 4 && term.endsWith("ing") -> term.dropLast(3)
                term.length > 4 && term.endsWith("ed") -> term.dropLast(2)
                else -> term
            }
            return "\"$stem\"*"
        }

        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by", "can",
            "could", "did", "do", "does", "for", "from", "how", "if", "in", "is", "it", "its",
            "not", "of", "on", "or", "should", "that", "the", "then", "these", "this", "those",
            "to", "was", "were", "what", "when", "where", "who", "why", "with", "would",
            // conversational wrappers: no retrieval value, and question terms now merge into
            // the same query as model keywords, so they must not carry filler either
            "about", "compare", "compared", "describe", "explain", "find", "give", "versus",
            "show", "tell", "me", "my", "mine", "our", "ours", "us", "please", "provide",
            "write", "vs",
        )
    }
}
