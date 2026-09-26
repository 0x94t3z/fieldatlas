package xyz.fieldatlas.research

object AnswerText {
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"

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
            .replace(Regex("(?<![\\p{L}\\p{N}])\\[([1-9][0-9]*)]")) { match ->
                val number = match.groupValues[1].toInt()
                if (number in sourceRange) "[S$number]" else ""
            }
            .replace(Regex("\\[S#]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[S([1-9][0-9]*)]", RegexOption.IGNORE_CASE)) { match ->
                val number = match.groupValues[1].toInt()
                if (number in sourceRange) "[S$number]" else ""
            }
            .replace(Regex("[ \\t]+(?=[.,;:])"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }
}
