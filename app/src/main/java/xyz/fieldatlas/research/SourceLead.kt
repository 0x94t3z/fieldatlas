package xyz.fieldatlas.research

/**
 * Verbatim opening of a requested reference overview, shown before model generation.
 *
 * Small on-device models can distort a passage they were given (the boat/raft reversal
 * recorded in the release audit). For "what is X" / "tell me about X" questions the saved
 * article's first sentences are already the answer, so they are shown immediately and
 * the slower model explanation follows below them. The text is copied, never paraphrased,
 * which makes its citation correct by construction; it is not a claim that the source is
 * current or complete.
 */
object SourceLead {
    const val HEADING = "From the saved reference"
    private const val TARGET_CHARS = 320
    private const val MAX_CHARS = 640
    private const val MAX_SENTENCES = 3

    data class Lead(val citationId: String, val text: String) {
        fun render(): String = "## $HEADING\n\n> $text [$citationId]"
    }

    // A full stop after these words does not end a sentence; splitting there would cut a
    // definition mid-claim.
    private val abbreviations = setOf(
        "e.g", "i.e", "etc", "vs", "approx", "c", "ca", "cf", "no", "st", "dr", "mr", "mrs", "ms",
        "prof", "jr", "sr", "fig", "vol", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep",
        "sept", "oct", "nov", "dec", "u.s", "u.k",
    )
    private val boundary = Regex("(?<=[.!?])[\"”’)]?\\s+(?=[\\p{Lu}\\p{N}\"“(])")

    /**
     * Returns a lead only from the first chunk of an overview section the question names,
     * restricted to sources actually packed into the prompt so its number matches the
     * source list. A later chunk would start mid-article and is never used.
     */
    fun select(question: String, sources: List<PromptSource>): Lead? {
        val subjects = EvidenceRelevance.overviewSubjects(question)
        if (subjects.isEmpty()) return null
        // Comparisons need both subjects; one article's opening is not an answer to them.
        if (EvidenceRelevance.comparisonTerms(question).isNotEmpty()) return null
        for (source in sources) {
            val item = source.evidence
            if (!isFirstChunk(item) || !EvidenceRelevance.isRequestedOverview(item, question)) continue
            val text = leadText(EvidenceRelevance.passageText(item.text)) ?: continue
            // The overview check alone falls back to title overlap for text it cannot parse
            // as English, so the first sentence must itself name every subject term.
            val opening = sentences(text).first()
            val named = subjects.all { subject ->
                val terms = FtsQuery.from(subject)?.terms.orEmpty()
                terms.isNotEmpty() && EvidenceRelevance.termHits(opening, terms) == terms.size
            }
            // Encyclopedia openings put pronunciations and alternative names in parentheses
            // ("Buoyancy (/ˈbɔɪənsi/), or upthrust, is ..."). Drop them for the definition
            // check only; the displayed lead stays verbatim.
            val checked = text.replace(Regex("\\s*\\([^()]*\\)"), "")
            if (!named || !EvidenceRelevance.hasOverviewStatement(item.copy(text = checked), subjects)) continue
            return Lead(source.citationId, text)
        }
        return null
    }

    internal fun leadText(passage: String): String? {
        val paragraph = passage.split(Regex("\\n\\s*\\n")).firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")?.trim() ?: return null
        val sentences = sentences(paragraph)
        if (sentences.isEmpty() || sentences.first().length > MAX_CHARS) return null
        val lead = StringBuilder(sentences.first())
        for (sentence in sentences.drop(1).take(MAX_SENTENCES - 1)) {
            if (lead.length >= TARGET_CHARS || lead.length + 1 + sentence.length > MAX_CHARS) break
            lead.append(' ').append(sentence)
        }
        // Only complete sentences qualify; an unterminated fragment could drop a qualifier.
        return lead.toString().takeIf { it.last() in ".!?\"”’)" }
    }

    private fun sentences(paragraph: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (match in boundary.findAll(paragraph)) {
            // The match begins after the terminal punctuation and may carry a closing quote.
            val end = match.range.first + match.value.takeWhile { it in "\"”’)" }.length
            val candidate = paragraph.substring(start, end)
            val lastWord = candidate.trimEnd('.', '!', '?', '"', '”', '’', ')')
                .substringAfterLast(' ').lowercase()
            // Single initials ("J. Smith") and listed abbreviations continue the sentence.
            if (lastWord in abbreviations || (lastWord.length == 1 && lastWord[0].isLetter())) continue
            result += candidate.trim()
            start = match.range.last + 1
        }
        paragraph.substring(start).trim().takeIf(String::isNotEmpty)?.let(result::add)
        return result
    }

    private fun isFirstChunk(item: Evidence): Boolean {
        val prefix = item.documentId + ":"
        if (!item.chunkId.startsWith(prefix)) return false
        return item.chunkId.removePrefix(prefix).toLongOrNull() == 0L
    }
}
