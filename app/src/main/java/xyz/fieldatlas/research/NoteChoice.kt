package xyz.fieldatlas.research

/**
 * Which passages a model that answers from its knowledge reads as notes, and which part of each, for the two cases the
 * first 700 characters of the packing order miss. "When did The Strokes release Is This It": the section "History >
 * 2001–2002: Is This It" came fourth, so the 4B never saw it. "How many electoral votes did Trump have in 2016": the
 * article's lead says "Trump received 304 electoral votes" at character 950, past the cut, and on the emulator the 4B
 * answered that he had none (2 Nov). Every other note is read as before.
 */
object NoteChoice {
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val sentenceEnd = Regex("(?<=[.!?])\\s+")

    private fun words(text: String) = word.findAll(QuestionText.foldAccents(text).lowercase().replace("'", "").replace("’", ""))
        .map { it.value }.toList()

    /** A section whose own heading the question names in full ("Is This It", "Early life"). */
    internal fun headingAsked(question: String, title: String): Boolean {
        val heading = title.substringAfterLast(" > ", "").replace(Regex("^[\\d\\s–-]+:\\s*"), "").trim()
        val named = words(heading)
        if (named.size < 2 || named.joinToString("").length < 5) return false
        return words(question).windowed(named.size).any { it == named }
    }

    /** The passages' places in reading order: a section the question names by its heading first, the rest as packed. */
    fun order(question: String, sources: List<PromptSource>): List<Int> {
        val (named, rest) = sources.indices.partition { headingAsked(question, sources[it].evidence.title) }
        return named + rest
    }

    /**
     * The text of a note in [limit] characters: its excerpt as packed, unless a word the question asks about that the
     * title does not ("electoral", "Americas") is missing from the excerpt's first [limit] characters and found later
     * in the passage; then the passage from the sentence that names the most such words.
     */
    fun excerpt(question: String, source: PromptSource, limit: Int): String {
        val text = source.evidence.text
        if (text.length <= limit) return source.excerpt
        val titled = words(source.evidence.title).toSet()
        val asked = FtsQuery.from(question)?.terms.orEmpty().map { it.replace("'", "") }.filter { it.length >= 4 && it !in titled }
        if (asked.isEmpty()) return source.excerpt
        fun has(piece: String, term: String) = term in words(piece)
        val head = source.excerpt.take(limit)
        val missing = asked.filter { !has(head, it) && has(text, it) }
        if (missing.isEmpty()) return source.excerpt
        val sentences = text.split(sentenceEnd)
        val best = sentences.indices.maxByOrNull { start ->
            val run = StringBuilder()
            for (s in sentences.drop(start)) { if (run.length + s.length > limit && run.isNotEmpty()) break; run.append(s).append(' ') }
            missing.count { has(run.toString(), it) } * 100 - start
        } ?: return source.excerpt
        return sentences.drop(best).joinToString(" ")
    }

    /** The model's [S1].. for its notes turned into the passages' own numbers; a note it was never shown is cited by nothing. */
    fun renumber(raw: String, shown: List<Int>): String = Regex("\\[S(\\d+)]").replace(raw) { m ->
        m.groupValues[1].toIntOrNull()?.let { shown.getOrNull(it - 1) }?.let { "[S${it + 1}]" } ?: ""
    }
}
