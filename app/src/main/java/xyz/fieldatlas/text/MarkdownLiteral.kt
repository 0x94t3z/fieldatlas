package xyz.fieldatlas.text

/** Literal source wording in an otherwise formatted answer. Character references survive the
 * model-output cleanup and are decoded only into plain text, never reparsed as markup/citations. */
internal object MarkdownLiteral {
    private val syntax = "&<>[]()*_`~\\,S\n\r".toSet()
    private val reference = Regex("&#(?:x([0-9a-fA-F]{1,6})|([0-9]{1,7}));")

    fun encode(text: String): String = buildString {
        text.forEachIndexed { index, char ->
            val preserveSpace = char == '\t' || char == ' ' &&
                (index == 0 || index == text.lastIndex || text.getOrNull(index - 1)?.isWhitespace() == true ||
                    text.getOrNull(index + 1)?.let { it.isWhitespace() || it in ".,;:" } == true)
            if (char in syntax || preserveSpace) append("&#").append(char.code).append(';') else append(char)
        }
    }

    fun at(text: String, offset: Int): Pair<String, Int>? {
        if (!text.startsWith("&#", offset)) return null
        // Inspect this position only. Searching the remaining tail for every malformed
        // '&#' prefix makes long untrusted excerpts quadratic to decode/render.
        val match = reference.matchAt(text, offset) ?: return null
        val value = if (match.groupValues[1].isNotEmpty()) match.groupValues[1].toIntOrNull(16)
            else match.groupValues[2].toIntOrNull()
        if (value == null || value !in 1..0x10ffff || value in 0xd800..0xdfff) return null
        return String(Character.toChars(value)) to match.range.last + 1
    }

    /** Only for a final plain-text destination (clipboard/share), never before Markdown parsing
     * or answer cleanup: those must continue to distinguish native markers from app citations. */
    fun decode(text: String): String = buildString {
        var cursor = 0
        while (cursor < text.length) {
            val literal = at(text, cursor)
            if (literal == null) append(text[cursor++]) else {
                append(literal.first)
                cursor = literal.second
            }
        }
    }
}
