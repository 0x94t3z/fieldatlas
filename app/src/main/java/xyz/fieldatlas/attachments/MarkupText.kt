package xyz.fieldatlas.attachments

/**
 * Turns HTML/XHTML and office-document XML into readable text without an XML library, so the
 * same code runs on the phone and in JVM tests. It is a text extractor, not a validator: it
 * tolerates malformed markup and never resolves external entities or fetches anything.
 */
object MarkupText {
    private val htmlBlocks = setOf(
        "p", "div", "br", "li", "ul", "ol", "tr", "table", "section", "article", "header", "footer",
        "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "dd", "dt", "dl", "hr", "figcaption",
        "title", "main", "aside", "nav", "caption",
    )
    private val htmlSkipped = setOf("script", "style", "head", "noscript", "template", "svg", "math")
    private val tag = Regex("<(/?)([A-Za-z][\\w:.-]*)([^>]*?)(/?)>", RegexOption.DOT_MATCHES_ALL)

    /** Visible text of an HTML or XHTML document; headings and list items keep their own lines. */
    fun html(source: String): String {
        val body = source
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL), "$1")
            .replace(Regex("<![^>]*>|<\\?[^>]*\\?>"), " ")
        val out = StringBuilder()
        var skipping: String? = null
        var cursor = 0
        for (match in tag.findAll(body)) {
            val name = match.groupValues[2].substringAfter(':').lowercase()
            val closing = match.groupValues[1] == "/"
            if (skipping == null) out.append(body, cursor, match.range.first)
            cursor = match.range.last + 1
            when {
                skipping != null -> if (closing && name == skipping) skipping = null
                !closing && name in htmlSkipped && match.groupValues[4] != "/" -> skipping = name
                name == "li" && !closing -> out.append("\n• ")
                name in setOf("td", "th") && closing -> { trimNewlines(out); out.append(" | ") }
                name in htmlBlocks -> out.append('\n')
            }
        }
        if (skipping == null) out.append(body, cursor, body.length)
        return tidy(entities(out.toString())).replace(Regex("\n{2,}"), "\n")
    }

    /**
     * Text of office XML: [paragraph] elements end lines, [text] elements carry the words,
     * and [tab]/[lineBreak] elements map to whitespace. Table cells are separated by " | ".
     */
    fun officeXml(xml: String, paragraph: Set<String>, text: Set<String>, tab: Set<String>, lineBreak: Set<String>, cell: Set<String> = emptySet(), row: Set<String> = emptySet(), space: String? = null): String {
        val out = StringBuilder()
        var inText = 0
        var cursor = 0
        for (match in tag.findAll(xml)) {
            if (inText > 0) out.append(xml, cursor, match.range.first)
            cursor = match.range.last + 1
            val name = match.groupValues[2]
            val closing = match.groupValues[1] == "/"
            val selfClosing = match.groupValues[4] == "/"
            // An element can both hold text and end a paragraph (OpenDocument's text:p), so these
            // effects are applied independently rather than as one choice.
            if (name in text && !selfClosing) inText = (inText + if (closing) -1 else 1).coerceAtLeast(0)
            if (name in paragraph && (closing || selfClosing)) out.append('\n')
            when (name) {
                in tab -> out.append('\t')
                in lineBreak -> out.append('\n')
                // A cell's own paragraphs end in newlines; keep a table row on one line.
                in cell -> if (closing) { trimNewlines(out); out.append(" | ") }
                in row -> if (closing) out.append('\n')
                space -> if (!closing) {
                    val count = Regex("\\bc=\"(\\d+)\"").find(match.groupValues[3])?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    out.append(" ".repeat(count.coerceIn(1, 50)))
                }
            }
        }
        return tidy(entities(out.toString()))
    }

    private fun trimNewlines(out: StringBuilder) { while (out.isNotEmpty() && out.last() == '\n') out.setLength(out.length - 1) }

    fun entities(text: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(text) { m ->
        val code = m.groupValues[1]
        when {
            code.startsWith("#x") -> code.drop(2).toIntOrNull(16)?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.drop(1).toIntOrNull()?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
            else -> named[code.lowercase()]
        } ?: m.value
    }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·", "copy" to "©",
        "reg" to "®", "deg" to "°", "euro" to "€", "pound" to "£", "times" to "×", "shy" to "",
    )

    /** Collapses runs of spaces, trims each line and keeps at most one blank line in a row. */
    fun tidy(text: String): String = text
        .replace('\u00A0', ' ')
        .lineSequence()
        .map { it.replace(Regex("[ \\t\\u000B\\f]+"), " ").trim().removeSuffix(" |").trim() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
