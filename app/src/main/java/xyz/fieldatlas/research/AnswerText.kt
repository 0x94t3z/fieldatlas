package xyz.fieldatlas.research

object AnswerText {
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"
    private val citationPattern = Regex("(?<![\\p{L}\\p{N}])\\[(?:S)?([0-9]+)]", RegexOption.IGNORE_CASE)
    private val placeholderPattern = Regex("\\[S#]", RegexOption.IGNORE_CASE)

    data class CitationAudit(val citedSourceIds: Set<String>, val hasUnmappedCitation: Boolean)

    fun visible(raw: String): String {
        val visible = StringBuilder()
        var cursor = 0
        while (cursor < raw.length) {
            val opening = raw.indexOf(THINK_OPEN, cursor)
            if (opening < 0) {
                val remainder = raw.substring(cursor)
                val held = (THINK_OPEN.length - 1 downTo 1).firstOrNull { length ->
                    remainder.endsWith(THINK_OPEN.take(length))
                } ?: 0
                visible.append(remainder.dropLast(held))
                break
            }
            visible.append(raw, cursor, opening)
            val closing = raw.indexOf(THINK_CLOSE, opening + THINK_OPEN.length)
            if (closing < 0) break
            cursor = closing + THINK_CLOSE.length
        }
        return visible.toString().trimStart()
    }

    /** Normalizes model citation variants and removes placeholders that cannot open a source. */
    fun finalized(raw: String, sourceCount: Int): String {
        val sourceRange = 1..sourceCount.coerceAtLeast(0)
        return visible(raw)
            // Small models commonly emit [1] despite being asked for [S1]. Normalize it so the
            // same citation chip and source navigation work instead of showing dead punctuation.
            .replace(citationPattern) { match ->
                val number = match.groupValues[1].toIntOrNull()
                if (number != null && number in sourceRange) "[S$number]" else ""
            }
            .replace(placeholderPattern, "")
            .replace(Regex("(?:,\\s*)+(?=[.!?])"), "")
            .replace(Regex("[ \\t]+(?=[.,;:])"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    /** Uses the same citation rules as [finalized], so metrics describe clickable sources. */
    fun citationAudit(raw: String, sourceCount: Int): CitationAudit {
        val visible = visible(raw)
        val validRange = 1..sourceCount.coerceAtLeast(0)
        val cited = linkedSetOf<String>()
        var unmapped = placeholderPattern.containsMatchIn(visible)
        citationPattern.findAll(visible).forEach { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null && number in validRange) {
                cited += "S$number"
            } else {
                unmapped = true
            }
        }
        return CitationAudit(cited, unmapped)
    }
}
