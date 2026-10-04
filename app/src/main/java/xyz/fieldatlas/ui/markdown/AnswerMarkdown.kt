package xyz.fieldatlas.ui.markdown

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: List<MarkdownInline>) : MarkdownBlock
    data class Paragraph(val content: List<MarkdownInline>) : MarkdownBlock
    /** [depths] gives each item's nesting level (0 = top), from two-space indentation. */
    data class ListBlock(val ordered: Boolean, val items: List<List<MarkdownInline>>, val depths: List<Int> = emptyList()) : MarkdownBlock
    data class Quote(val content: List<MarkdownInline>) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock
    data class Table(
        val headers: List<List<MarkdownInline>>,
        val rows: List<List<List<MarkdownInline>>>,
    ) : MarkdownBlock
    data object ThematicBreak : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val value: String) : MarkdownInline
    data class Strong(val content: List<MarkdownInline>) : MarkdownInline
    data class Emphasis(val content: List<MarkdownInline>) : MarkdownInline
    data class Strikethrough(val content: List<MarkdownInline>) : MarkdownInline
    data class Code(val value: String) : MarkdownInline
    data class Link(val label: List<MarkdownInline>, val destination: String) : MarkdownInline
    data class Citation(val number: Int) : MarkdownInline
}

fun parseAnswerMarkdown(markdown: String): List<MarkdownBlock> = MarkdownParser(markdown).parse()

/** How an answer section heading is presented: a short title plus a provenance badge. */
data class AnswerSection(val title: String, val badge: String, val quoted: Boolean)

/**
 * Sections the app writes itself get a compact title and an explicit badge, so verbatim
 * source text and unverified model text are told apart at a glance. The stored answer
 * keeps its full heading text; this only changes how it is drawn.
 */
fun answerSection(heading: MarkdownBlock.Heading): AnswerSection? =
    when (heading.content.plainText().trim()) {
        xyz.fieldatlas.research.AnswerText.MODEL_LABEL -> AnswerSection("Model explanation", "Not verified", quoted = false)
        xyz.fieldatlas.research.SourceLead.HEADING -> AnswerSection("From the saved reference", "Quoted", quoted = true)
        "From saved sources" -> AnswerSection("From saved sources", "Quoted", quoted = true)
        else -> null
    }

/** Splits citations (and the spaces around them) off the end of a quote for a footer row. */
fun List<MarkdownInline>.trailingCitations(): Pair<List<MarkdownInline>, List<Int>> {
    var end = size
    val numbers = ArrayList<Int>()
    while (end > 0) {
        val inline = this[end - 1]
        when {
            inline is MarkdownInline.Citation -> numbers.add(0, inline.number)
            inline is MarkdownInline.Text && inline.value.isBlank() -> Unit
            else -> break
        }
        end--
    }
    if (numbers.isEmpty()) return this to emptyList()
    val body = take(end).toMutableList()
    // Drop the space that separated the sentence from its first citation.
    (body.lastOrNull() as? MarkdownInline.Text)?.let { last ->
        body[body.lastIndex] = MarkdownInline.Text(last.value.trimEnd())
    }
    return body to numbers.distinct()
}

fun MarkdownBlock.plainText(): String = when (this) {
    is MarkdownBlock.Heading -> content.plainText()
    is MarkdownBlock.Paragraph -> content.plainText()
    is MarkdownBlock.ListBlock -> items.joinToString("\n") { it.plainText() }
    is MarkdownBlock.Quote -> content.plainText()
    is MarkdownBlock.CodeBlock -> code
    is MarkdownBlock.Table -> (listOf(headers) + rows).joinToString("\n") { row ->
        row.joinToString(" | ") { cell -> cell.plainText() }
    }
    MarkdownBlock.ThematicBreak -> ""
}

private fun List<MarkdownInline>.plainText(): String = joinToString(separator = "") { inline ->
    when (inline) {
        is MarkdownInline.Text -> inline.value
        is MarkdownInline.Strong -> inline.content.plainText()
        is MarkdownInline.Emphasis -> inline.content.plainText()
        is MarkdownInline.Strikethrough -> inline.content.plainText()
        is MarkdownInline.Code -> inline.value
        is MarkdownInline.Link -> inline.label.plainText()
        is MarkdownInline.Citation -> "[S${inline.number}]"
    }
}

private class MarkdownParser(markdown: String) {
    private val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    private var index = 0

    fun parse(): List<MarkdownBlock> = buildList {
        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index += 1
                continue
            }
            add(parseBlock())
        }
    }

    private fun parseBlock(): MarkdownBlock {
        val line = lines[index]
        if (isCodeFence(line)) return parseCodeBlock(codeFenceLanguage(line))
        if (isTableStart()) return parseTable()
        if (isThematicBreak(line)) {
            index += 1
            return MarkdownBlock.ThematicBreak
        }
        heading(line)?.let { (level, text) ->
            index += 1
            return MarkdownBlock.Heading(level, parseInline(text))
        }
        if (unorderedItem(line) != null) return parseList(ordered = false)
        if (orderedItem(line) != null) return parseList(ordered = true)
        if (quoteText(line) != null) return parseQuote()
        return parseParagraph()
    }

    private fun parseCodeBlock(language: String?): MarkdownBlock.CodeBlock {
        index += 1
        val content = mutableListOf<String>()
        while (index < lines.size && !isCodeFence(lines[index])) {
            content += lines[index]
            index += 1
        }
        if (index < lines.size) index += 1
        return MarkdownBlock.CodeBlock(content.joinToString("\n"), language)
    }

    private fun parseTable(): MarkdownBlock.Table {
        val headers = tableCells(lines[index]).map(::parseInline)
        index += 2
        val rows = mutableListOf<List<List<MarkdownInline>>>()
        while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
            val cells = tableCells(lines[index])
            if (cells.isEmpty()) break
            rows += cells.map(::parseInline)
            index += 1
        }
        return MarkdownBlock.Table(headers, rows)
    }

    private fun parseList(ordered: Boolean): MarkdownBlock.ListBlock {
        val items = mutableListOf<List<MarkdownInline>>()
        val depths = mutableListOf<Int>()
        while (index < lines.size) {
            val line = lines[index]
            // A nested item may use the other marker style ("1." under "-"), so accept both here.
            val item = (if (ordered) orderedItem(line) else unorderedItem(line))
                ?: (if (items.isNotEmpty() && indentOf(line) >= 2) (unorderedItem(line) ?: orderedItem(line)) else null)
                ?: break
            items += parseInline(item)
            depths += (indentOf(line) / 2).coerceAtMost(3)
            index += 1
        }
        return MarkdownBlock.ListBlock(ordered, items, if (depths.all { it == 0 }) emptyList() else depths)
    }

    private fun parseQuote(): MarkdownBlock.Quote {
        val quoted = mutableListOf<String>()
        while (index < lines.size) {
            val text = quoteText(lines[index]) ?: break
            quoted += text
            index += 1
        }
        return MarkdownBlock.Quote(parseInline(quoted.joinToString(" ")))
    }

    private fun parseParagraph(): MarkdownBlock.Paragraph {
        val paragraph = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank() && !startsNonParagraphBlock(lines[index])) {
            paragraph += lines[index].trim()
            index += 1
        }
        return MarkdownBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
    }

    private fun startsNonParagraphBlock(line: String): Boolean =
        isCodeFence(line) || isTableStart() || heading(line) != null || unorderedItem(line) != null ||
            orderedItem(line) != null || quoteText(line) != null || isThematicBreak(line)

    private fun isTableStart(): Boolean = index + 1 < lines.size &&
        lines[index].contains('|') && tableDelimiterPattern.matches(lines[index + 1]) &&
        tableCells(lines[index]).isNotEmpty()

    private fun tableCells(line: String): List<String> = line.trim().trim('|')
        .split('|')
        .map(String::trim)

    private fun parseInline(text: String): List<MarkdownInline> {
        val result = mutableListOf<MarkdownInline>()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                result += MarkdownInline.Text(plain.toString())
                plain.clear()
            }
        }

        var cursor = 0
        while (cursor < text.length) {
            val citation = citationAt(text, cursor)
            if (citation != null) {
                flushPlain()
                result += MarkdownInline.Citation(citation.first)
                cursor = citation.second
                continue
            }

            val strongMarker = when {
                text.startsWith("**", cursor) -> "**"
                text.startsWith("__", cursor) -> "__"
                else -> null
            }
            if (strongMarker != null) {
                val close = text.indexOf(strongMarker, cursor + strongMarker.length)
                if (close >= 0) {
                    flushPlain()
                    result += MarkdownInline.Strong(parseInline(text.substring(cursor + strongMarker.length, close)))
                    cursor = close + strongMarker.length
                } else {
                    cursor += strongMarker.length
                }
                continue
            }

            if (text.startsWith("~~", cursor)) {
                val close = text.indexOf("~~", cursor + 2)
                if (close >= 0) {
                    flushPlain()
                    result += MarkdownInline.Strikethrough(parseInline(text.substring(cursor + 2, close)))
                    cursor = close + 2
                } else {
                    cursor += 2
                }
                continue
            }

            if (text[cursor] == '[') {
                // Search for the closing `](` pair rather than the first `]`; citation nodes
                // such as [S1] are valid inside a Markdown link label.
                val labelClose = text.indexOf("](", cursor + 1)
                if (labelClose >= 0) {
                    val destinationClose = text.indexOf(')', labelClose + 2)
                    if (destinationClose >= 0) {
                        flushPlain()
                        result += MarkdownInline.Link(
                            label = parseInline(text.substring(cursor + 1, labelClose)),
                            destination = text.substring(labelClose + 2, destinationClose),
                        )
                        cursor = destinationClose + 1
                        continue
                    }
                }
            }

            val marker = text[cursor]
            if (marker == '*' || marker == '_') {
                val close = text.indexOf(marker, cursor + 1)
                if (close >= 0) {
                    flushPlain()
                    result += MarkdownInline.Emphasis(parseInline(text.substring(cursor + 1, close)))
                    cursor = close + 1
                } else {
                    cursor += 1
                }
                continue
            }

            if (marker == '`') {
                val close = text.indexOf('`', cursor + 1)
                if (close >= 0) {
                    flushPlain()
                    result += MarkdownInline.Code(text.substring(cursor + 1, close))
                    cursor = close + 1
                } else {
                    cursor += 1
                }
                continue
            }

            plain.append(marker)
            cursor += 1
        }
        flushPlain()
        return result
    }

    private fun citationAt(text: String, start: Int): Pair<Int, Int>? {
        if (!text.startsWith("[S", start, ignoreCase = true)) return null
        val close = text.indexOf(']', start + 2)
        if (close < 0) return null
        val number = text.substring(start + 2, close).toIntOrNull() ?: return null
        if (number < 1) return null
        return number to close + 1
    }

    private companion object {
        private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
        private val unorderedPattern = Regex("^\\s*[-*+]\\s+(.+)$")
        private val orderedPattern = Regex("^\\s*\\d+[.)]\\s+(.+)$")
        private val quotePattern = Regex("^\\s*>\\s?(.*)$")
        private val thematicBreakPattern = Regex("^\\s{0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$")
        private val tableDelimiterPattern = Regex(
            "^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$",
        )

        fun heading(line: String): Pair<Int, String>? = headingPattern.matchEntire(line)?.let {
            it.groupValues[1].length to it.groupValues[2]
        }

        fun unorderedItem(line: String): String? = unorderedPattern.matchEntire(line)?.groupValues?.get(1)

        fun orderedItem(line: String): String? = orderedPattern.matchEntire(line)?.groupValues?.get(1)

        fun indentOf(line: String): Int = line.takeWhile { it == ' ' }.length

        fun quoteText(line: String): String? = quotePattern.matchEntire(line)?.groupValues?.get(1)

        fun isThematicBreak(line: String): Boolean = thematicBreakPattern.matches(line)

        fun isCodeFence(line: String): Boolean = line.trim().startsWith("```")

        fun codeFenceLanguage(line: String): String? {
            val trimmed = line.trim()
            return trimmed.removePrefix("```").trim().ifEmpty { null }
        }
    }
}
