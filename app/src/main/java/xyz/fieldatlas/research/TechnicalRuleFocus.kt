package xyz.fieldatlas.research

import java.text.Normalizer
import java.util.Locale

/**
 * A deliberately narrow lexical lookup: one named standard, one code symbol, one hex
 * condition, and a return/result request. It extracts no answer or expected polarity.
 * A match earns retrieval priority, not a claim that the source entails an answer.
 */
internal class TechnicalRuleFocus private constructor(
    val owner: String,
    val symbol: String,
    val literal: String,
) {
    private val owners = if (owner.startsWith("ERC-") || owner.startsWith("EIP-")) {
        ProposalLookup.labels(owner)
    } else listOf(owner)
    private val ownerPatterns = owners.map { label ->
        val (prefix, number) = label.split('-')
        Regex("(?i)^\\s*${Regex.escape(prefix)}[- ]?${Regex.escape(number)}(?=[\\s:—–]|$)")
    }
    private val symbolPattern = exactToken(symbol)
    private val literalPattern = exactToken(literal)

    /** All interpolated fields come from the closed ASCII grammar below, never raw FTS. */
    val matchExpression: String = "{title} : (" + owners.joinToString(" OR ") {
        "^\"${it.replace('-', ' ')}\""
    } + ") AND {text} : \"$symbol\" AND {text} : \"$literal\""

    fun matches(evidence: Evidence): Boolean = matches(evidence.title, evidence.text)

    fun matches(title: String, text: String): Boolean = ownerPatterns.any {
        it.containsMatchIn(normalize(title))
    } && EvidenceRelevance.passageText(normalize(text)).let { body ->
        symbolPattern.containsMatchIn(body) && literalPattern.containsMatchIn(body)
    }

    /** Stable within each tier; raw BM25 scores and source identities remain untouched. */
    fun prioritize(evidence: List<Evidence>): List<Evidence> = evidence.sortedByDescending(::matches)

    companion object {
        private val standard = Regex("(?i)(?<![\\p{L}\\p{N}_])([a-z]{2,12})[- ]([0-9]{1,8})(?![\\p{L}\\p{N}_])|(?i)(?<![\\p{L}\\p{N}_])(ERC|EIP)([0-9]{1,5})(?![\\p{L}\\p{N}_])")
        private val hex = Regex("(?i)(?<![\\p{L}\\p{N}_$])0x[0-9a-f]+(?![\\p{L}\\p{N}_$])")
        private val identifier = Regex("(?<![\\p{L}\\p{N}_$])[a-zA-Z_][a-zA-Z0-9_]*(?![\\p{L}\\p{N}_$])")
        private val returnIntent = Regex("(?i)\\b(?:returns?|returned|result|output)\\b")
        private val reserved = setOf("return", "returns", "returned", "result", "output", "if", "when", "for", "under", "and", "or", "not", "true", "false")

        fun from(question: String): TechnicalRuleFocus? {
            val normalized = normalize(question)
            val capped = FtsQuery.from(question)?.normalizedInput ?: return null
            // Do not turn a cut-off identifier into another value, or miss ambiguity after
            // the cap. Longer questions retain the existing general retrieval behavior.
            if (capped != normalized || !returnIntent.containsMatchIn(capped)) return null
            val standards = standard.findAll(capped).toList()
            val labels = standards.map { match ->
                val prefix = match.groupValues[1].ifEmpty { match.groupValues[3] }.uppercase(Locale.ROOT)
                val number = match.groupValues[2].ifEmpty { match.groupValues[4] }
                "$prefix-$number"
            }.distinct()
            val owner = labels.singleOrNull() ?: return null
            val literals = hex.findAll(capped).toList()
            val literal = literals.map { it.value.lowercase(Locale.ROOT) }.distinct().singleOrNull() ?: return null
            val symbols = identifier.findAll(capped).filter { token ->
                standards.none { token.range.first in it.range } && literals.none { token.range.first in it.range } &&
                    token.value.lowercase(Locale.ROOT) !in reserved && (
                    Regex("[a-z][A-Z]").containsMatchIn(token.value) || token.value.contains('_') ||
                        capped.substring(token.range.last + 1).trimStart().startsWith('(') ||
                        (token.range.first > 0 && capped[token.range.first - 1] == '`' &&
                            capped.getOrNull(token.range.last + 1) == '`'))
            }.map { it.value.lowercase(Locale.ROOT) }.distinct().toList()
            val symbol = symbols.singleOrNull() ?: return null
            return TechnicalRuleFocus(owner, symbol, literal)
        }

        private fun exactToken(token: String) = Regex("(?i)(?<![\\p{L}\\p{N}_$])${Regex.escape(token)}(?![\\p{L}\\p{N}_$])")
        private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    }
}
