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
}
