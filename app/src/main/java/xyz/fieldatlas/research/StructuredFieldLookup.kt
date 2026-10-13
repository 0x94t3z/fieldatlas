package xyz.fieldatlas.research

/** Literal record lookup, not scientific interpretation. Anything ambiguous uses normal research. */
internal object StructuredFieldLookup {
    data class Ask(val dataset: String, val field: String, val record: String)
    data class Result(val evidence: List<Evidence>, val answer: String)

    private val request = Regex(
        "^(?:using only saved sources,\\s*)?what does the ([a-z][a-z0-9 -]{0,60}?) ([a-z][a-z0-9_]{0,40}) field say for ([a-z0-9][a-z0-9_.-]{0,40})\\??$",
        RegexOption.IGNORE_CASE,
    )

    fun ask(question: String): Ask? = request.matchEntire(question.trim())?.let {
        Ask(it.groupValues[1], it.groupValues[2], it.groupValues[3])
    }

    fun answer(ask: Ask, candidates: List<Evidence>): Result? {
        // A saturated lookup could hide a conflicting record; never present it as a unique value.
        if (candidates.isEmpty() || candidates.size >= 20) return null
        val matches = candidates.mapNotNull { evidence ->
            val lines = evidence.text.lines().filter(String::isNotBlank)
            if (lines.size < 4 || !lines.first().startsWith("Dataset: ")) return@mapNotNull null
            val dataset = lines.first().substringAfter(": ").trim()
            if (!dataset.equals(ask.dataset, true) &&
                !dataset.startsWith(ask.dataset + " ", true)) return@mapNotNull null
            if (!evidence.title.substringAfterLast(":", "").trim().equals(ask.record, true)) return@mapNotNull null
            if (lines.drop(1).any { !it.matches(Regex("[^:\\r\\n]{1,80}: .+")) }) return@mapNotNull null
            val fields = lines.drop(1).filter { it.substringBefore(":").equals(ask.field, true) }
            if (fields.size != 1) return null
            val value = fields.single().substringAfter(": ").trim()
            // Rich/multiline values need the regular excerpt route, not interpolated Markdown.
            if (!value.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9 ._/-]{0,159}"))) return null
            evidence to value
        }
        if (matches.isEmpty() || matches.map { it.second }.distinct().size != 1) return null
        val sources = matches.map { it.first }.distinctBy { it.documentId to it.chunkId }
        return Result(sources, "From saved sources\n\n" + sources.indices.joinToString("\n\n") {
            "> ${ask.field}: ${matches.first().second} [S${it + 1}]"
        })
    }
}
