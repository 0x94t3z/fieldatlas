package xyz.fieldatlas.research

object AnswerText {
    const val MODEL_LABEL = "Model explanation—not verified against saved sources"
    private val quoteLine = Regex("^(?:[-*]\\s+)?[\"“](.+)[\"”]\\s*((?:\\[(?:S)?[0-9]+]\\s*)*)[.!]?\\s*$", RegexOption.IGNORE_CASE)
    private val inlineSourceHeading = Regex("(?im)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?From saved sources(?:\\*\\*)?\\s*:(?:\\*\\*)?\\s*(?=[\"“])")
    private val danglingAttribution = Regex("(?i)according to sources?\\s+(?:\\[(?:S)?[0-9]+]\\s*)+,\\s*")

    /**
     * Mixed answers fail closed: only a verbatim quotation from the cited passage gets
     * a source link. An unnumbered quote may link one unique exact passage match.
     * This validates attribution, not the truth or completeness of a source.
     * Everything else remains useful model text, explicitly unverified and uncited.
     */
    fun mixed(raw: String, sources: List<Evidence>): String {
        val model = mutableListOf<String>()
        val quotes = mutableListOf<String>()
        var sourceSection = false
        val text = inlineSourceHeading.replace(normalizeReferences(visible(raw)), "From saved sources\n")
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            val heading = line.trim().trim('#', '*', ' ', ':').lowercase()
            when (heading) {
                "from saved sources" -> sourceSection = true
                "model explanation", MODEL_LABEL.lowercase() -> sourceSection = false
                else -> {
                    // Hold a heading while it is streaming, rather than showing it as prose.
                    if (index == lines.lastIndex && heading.isNotEmpty() &&
                        listOf("from saved sources", "model explanation").any { it.startsWith(heading) }) {
                        return@forEachIndexed
                    }
                    val match = if (sourceSection) quoteLine.matchEntire(line.trim()) else null
                    val quote = match?.groupValues?.get(1).orEmpty()
                    val normalizedQuote = normalizeQuote(quote)
                    val hasExplicitCitation = !match?.groupValues?.get(2).isNullOrBlank()
                    val explicitNumbers = citationPattern.findAll(match?.groupValues?.get(2).orEmpty())
                        .mapNotNull { it.groupValues[1].toIntOrNull() }
                        .distinct().toList()
                    val eligible = if (!hasExplicitCitation) sources.indices.map { it + 1 } else explicitNumbers
                    val matches = eligible.filter { number ->
                        val passage = sources.getOrNull(number - 1)?.text
                        passage != null && normalizedQuote.length >= 12 &&
                            !citationPattern.containsMatchIn(quote) && !placeholderPattern.containsMatchIn(quote) &&
                            normalizeQuote(EvidenceRelevance.passageText(passage)).contains(normalizedQuote)
                    }
                    // Infer attribution only for an exact, uniquely matching quotation.
                    // Explicit wrong citations never get reassigned to another source.
                    val verifiedNumbers = if (hasExplicitCitation || matches.size == 1) matches else emptyList()
                    if (verifiedNumbers.isNotEmpty()) {
                        quotes += "- \"$quote\" " + verifiedNumbers.joinToString("") { "[S$it]" }
                    } else {
                        val withoutDanglingAttribution = line.replace(danglingAttribution, "")
                        val uncited = finalized(withoutDanglingAttribution, 0)
                            .replace(Regex("\\[(?:S)?[0-9#]*$", RegexOption.IGNORE_CASE), "")
                            .trim()
                        if (uncited.isNotEmpty()) model += uncited
                    }
                }
            }
        }
        return listOfNotNull(
            model.takeIf { it.isNotEmpty() }?.let { "## $MODEL_LABEL\n\n" + it.joinToString("\n") },
            quotes.takeIf { it.isNotEmpty() }?.let { "## From saved sources\n\n" + it.distinct().joinToString("\n") },
        ).joinToString("\n\n")
    }

    private fun normalizeQuote(value: String): String = value.replace(Regex("\\s+"), " ").trim()
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"
    private val citationPattern = Regex("\\[(?:S)?([+-]?\\p{Nd}+)]", RegexOption.IGNORE_CASE)
    private val placeholderPattern = Regex("\\[S#]", RegexOption.IGNORE_CASE)
    private val parenthesizedReferences = Regex(
        "\\(\\s*(?:sources?\\s*:\\s*)?S[+-]?\\p{Nd}+(?:\\s*[,;]\\s*S[+-]?\\p{Nd}+)*\\s*\\)",
        RegexOption.IGNORE_CASE,
    )
    private val sourceNumber = Regex("S([+-]?\\p{Nd}+)", RegexOption.IGNORE_CASE)

    // A source ID written as prose ("the parameters from S1") after a referring word.
    private val bareReference = Regex("(?<=\\b(?:from|in|see|per|by|to|source)\\s)S(\\p{Nd}{1,2})\\b(?![-.]\\p{Nd})")

    // Only explicit S-number references qualify; ordinary parentheses remain prose.
    // Normalize before attribution checks, never instead of them.
    private fun normalizeReferences(text: String): String = parenthesizedReferences.replace(text) { group ->
        sourceNumber.findAll(group.value).joinToString("") { "[S${it.groupValues[1]}]" }
    }.replace(bareReference) { "[S${it.groupValues[1]}]" }

    data class CitationAudit(val citedSourceIds: Set<String>, val hasUnmappedCitation: Boolean)

    internal fun citationMarkerCount(raw: String): Int = citationPattern.findAll(normalizeReferences(visible(raw))).count()

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
        return normalizeReferences(visible(raw))
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
        val visible = normalizeReferences(visible(raw))
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
