package xyz.fieldatlas.ui.markdown

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: List<MarkdownInline>) : MarkdownBlock
    data class Paragraph(val content: List<MarkdownInline>) : MarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<MarkdownInline>>) : MarkdownBlock
    data class Quote(val content: List<MarkdownInline>) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val value: String) : MarkdownInline
    data class Strong(val content: List<MarkdownInline>) : MarkdownInline
    data class Emphasis(val content: List<MarkdownInline>) : MarkdownInline
    data class Code(val value: String) : MarkdownInline
    data class Citation(val number: Int) : MarkdownInline
}

fun parseAnswerMarkdown(markdown: String): List<MarkdownBlock> = MarkdownParser(markdown).parse()

fun MarkdownBlock.plainText(): String = when (this) {
    is MarkdownBlock.Heading -> content.plainText()
    is MarkdownBlock.Paragraph -> content.plainText()
    is MarkdownBlock.ListBlock -> items.joinToString("\n") { it.plainText() }
    is MarkdownBlock.Quote -> content.plainText()
    is MarkdownBlock.CodeBlock -> code
}

private fun List<MarkdownInline>.plainText(): String = joinToString(separator = "") { inline ->
    when (inline) {
        is MarkdownInline.Text -> inline.value
        is MarkdownInline.Strong -> inline.content.plainText()
        is MarkdownInline.Emphasis -> inline.content.plainText()
        is MarkdownInline.Code -> inline.value
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

    private fun parseList(ordered: Boolean): MarkdownBlock.ListBlock {
        val items = mutableListOf<List<MarkdownInline>>()
        while (index < lines.size) {
            val item = if (ordered) orderedItem(lines[index]) else unorderedItem(lines[index])
            if (item == null) break
            items += parseInline(item)
            index += 1
        }
        return MarkdownBlock.ListBlock(ordered, items)
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
        isCodeFence(line) || heading(line) != null || unorderedItem(line) != null ||
            orderedItem(line) != null || quoteText(line) != null

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

        fun heading(line: String): Pair<Int, String>? = headingPattern.matchEntire(line)?.let {
            it.groupValues[1].length to it.groupValues[2]
        }

        fun unorderedItem(line: String): String? = unorderedPattern.matchEntire(line)?.groupValues?.get(1)

        fun orderedItem(line: String): String? = orderedPattern.matchEntire(line)?.groupValues?.get(1)

        fun quoteText(line: String): String? = quotePattern.matchEntire(line)?.groupValues?.get(1)

        fun isCodeFence(line: String): Boolean = line.trim().startsWith("```")

        fun codeFenceLanguage(line: String): String? {
            val trimmed = line.trim()
            return trimmed.removePrefix("```").trim().ifEmpty { null }
        }
    }
}
