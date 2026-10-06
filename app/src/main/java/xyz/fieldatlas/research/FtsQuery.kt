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
            val terms = termPattern.findAll(withoutContractions(capped))
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

        // "we're", "I'll", "they've", "it's": the ending after the apostrophe is not a word, and
        // "n't" is "not", so "can't" does not leave a "ca" and "won't" a "wo" to search for.
        private val negation = Regex("(?i)\\b(won|can)['’]t\\b")
        private val contractedNot = Regex("(?i)n['’]t\\b")
        private val contractedEnding = Regex("(?<=\\p{L})['’](?:re|ll|ve|d|m|s)\\b")

        internal fun withoutContractions(text: String): String = text
            .replace(negation) { if (it.groupValues[1].lowercase() == "won") "will not" else "can not" }
            .replace(contractedNot, " not").replace(contractedEnding, "")

        /**
         * A word of three letters or fewer is matched in its own inflections, not as a prefix:
         * "get" finds "gets" and "getting", "bus" finds "buses", but "mev" never "mevalonate".
         * Verb endings are only added to words with a vowel; "bls" is an acronym.
         */
        /** Whether a noun ending in [base] takes an -es plural ("box", "dish"), not a plain -s. */
        private fun takesEs(base: String): Boolean =
            base.endsWith("s") || base.endsWith("x") || base.endsWith("z") || base.endsWith("ch") || base.endsWith("sh")

        internal fun shortForms(term: String): List<String> {
            val plural = if (term.endsWith("s") || term.endsWith("x") || term.endsWith("z") || term.endsWith("ch") || term.endsWith("sh")) "es" else "s"
            val forms = linkedSetOf(term, term + plural)
            when {
                // "use": used, using; "die": died, dying; "fly": flies, flew? no, flying; "dry": dried.
                term.endsWith("ie") -> forms += listOf(term + "d", term.dropLast(2) + "ying")
                term.endsWith("e") -> forms += listOf(term + "d", term.dropLast(1) + "ing")
                term.length == 3 && term.endsWith("y") && term[1] !in VOWELS -> forms += listOf(term.dropLast(1) + "ies", term.dropLast(1) + "ied", term + "ing")
            }
            if (term.all(Char::isLetter) && term.any { it in VOWELS } && !term.endsWith("e") && !term.endsWith("y")) {
                forms += listOf(term + "ing", term + "ed")
                // A consonant-vowel-consonant word doubles its last letter: "get" -> "getting".
                if (term.length == 3 && term[0] !in VOWELS && term[1] in VOWELS && term[2] !in "aeiouwxy") {
                    forms += listOf(term + term[2] + "ing", term + term[2] + "ed")
                }
            }
            return forms.toList()
        }

        private const val VOWELS = "aeiou"

        private fun searchableTerm(term: String): String {
            // A short term as a prefix matches too much: "mev"* found "mevalonate" for a question
            // about MEV, "kem"* a town called Kemer for ML-KEM. Match it whole, or as a plural.
            if (term.codePointCount(0, term.length) <= 3) return shortForms(term).joinToString(" OR ", "(", ")") { "\"$it\"" }
            // A number is never a stem: "7702"* matched the postcode 77020 for a question about EIP-7702.
            if (term.all(Char::isDigit)) return "\"$term\""
            val stem = when {
                // FTS uses prefix matching, not a linguistic stemmer. Keep the shared spelling
                // prefix (alli* -> allies) instead of rewriting ies to y (ally* cannot match
                // "allies" in a unicode61 index).
                term.length > 4 && term.endsWith("ies") -> term.dropLast(2)
                // "boxes" and "dishes" add -es; "tides" and "planes" add -s, and "tid"* or "plan"*
                // would miss "tide" and match "planet".
                term.length > 4 && term.endsWith("es") -> if (takesEs(term.dropLast(2))) term.dropLast(2) else term.dropLast(1)
                term.length > 3 && term.endsWith("s") -> term.dropLast(1)
                term.length > 4 && term.endsWith("ing") -> term.dropLast(3)
                term.length > 4 && term.endsWith("ed") -> term.dropLast(2)
                else -> term
            }
            // A stem this short as a prefix matches other words: "ear"* for "ears" found earthquakes.
            // Match the word and the stem's own inflections instead.
            if (stem.codePointCount(0, stem.length) <= 3) return (listOf(term) + shortForms(stem)).distinct().joinToString(" OR ", "(", ")") { "\"$it\"" }
            return "\"$stem\"*"
        }

        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by", "can",
            "could", "did", "do", "does", "for", "from", "have", "has", "had", "how", "if", "in", "is", "it", "its",
            "may", "might", "must", "not", "of", "on", "or", "shall", "should", "that", "the",
            "then", "these", "this", "those", "to", "was", "were", "what", "when", "where",
            "who", "why", "will", "with", "would",
            // conversational wrappers: no retrieval value, and question terms now merge into
            // the same query as model keywords, so they must not carry filler either
            "about", "compare", "compared", "describe", "explain", "find", "give", "versus",
            "show", "tell", "me", "my", "mine", "our", "ours", "us", "please", "provide",
            "write", "vs", "which", "only", "any", "each", "every", "using",
            // instructions about the answer, not its subject: in "summarize what saved sources
            // say about rapamycin; separate findings from uncertainty" they outranked rapamycin
            "summarize", "summarise", "summary", "separate", "distinguish", "say", "says", "said",
            "saved", "source", "sources", "reported", "findings", "remaining", "uncertainty",
            "details", "detail", "need", "needs", "trying", "deciding", "conclusion", "conclusions",
            "you", "your", "we", "i", "so", "there", "their", "they", "also", "some", "other",
        )
    }
}
