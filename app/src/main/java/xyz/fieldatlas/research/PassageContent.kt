package xyz.fieldatlas.research

/** Reject demonstrably empty web furniture, without rewriting a source or its citations. */
internal object PassageContent {
    private val navigation = Regex("(?i)(?:permalink(?: \\((?:with|no) comments\\))?|posted by\\b.*|" +
        "add a comment|no comments yet|(?:read )?\\d+ comments?|reply|share(?: this)?|tags?:.*|filed under\\b.*)")
    private val date = Regex("(?i)(?:january|february|march|april|may|june|july|august|september|october|november|december)" +
        "(?: \\d{1,2}(?:st|nd|rd|th)?,?)? \\d{4}(?:\\s*[·|]\\s*https?://\\S+)?")
    private fun words(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Numbered section labels contain no explanatory prose. Keep them for a
     * requested heading lookup, but never use the article title as their evidence. */
    fun isNumberedHeadingOnly(item: Evidence, question: String? = null): Boolean {
        if (question != null && Regex("(?i)\\b(?:headings?|titles?|contents|section number)\\b").containsMatchIn(question)) return false
        val text = item.text.trim()
        return text.length <= 160 && Regex("\\d+(?:\\.\\d+)+\\.?\\s+[\\p{L}][\\p{L}\\p{N} ,:/()-]*").matches(text)
    }

    fun isEmptyWebPage(item: Evidence, question: String? = null): Boolean {
        if (question != null && Regex("(?i)\\b(?:title|author|posted|publication|published|date|permalink|comments?)\\b")
                .containsMatchIn(question)) return false
        val lines = item.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.count(navigation::matches) < 2) return false
        val title = words(item.title)
        return lines.all { navigation.matches(it) || date.matches(it) || words(it) == title }
    }

    /** A linked newsletter contents list is not a passage explaining its listed topics. */
    fun isContentsOnly(item: Evidence, question: String): Boolean {
        if (!item.title.contains("newsletter", ignoreCase = true) ||
            Regex("(?i)\\b(?:newsletter|headlines?|contents|index|titles?|list)\\b").containsMatchIn(question)) return false
        val lines = item.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.none { it.startsWith("https://") || it.startsWith("http://") }) return false
        val headings = lines.filterNot { it.startsWith("https://") || it.startsWith("http://") || date.matches(it) }
        if (headings.size < 3) return false
        val connectors = setOf("a", "an", "the", "of", "in", "on", "and", "or", "to", "for", "with", "by", "as", "at", "via", "into")
        return headings.all { line ->
            val tokens = Regex("[\\p{L}]+").findAll(line).map { it.value }.toList()
            val content = tokens.filterNot { it.lowercase() in connectors }
            !line.endsWith('.') && !line.endsWith('!') && tokens.size in 3..24 && content.isNotEmpty() &&
                content.count { it.first().isUpperCase() } >= content.size * 0.8
        }
    }
}
