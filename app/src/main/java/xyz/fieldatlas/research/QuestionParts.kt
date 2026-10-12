package xyz.fieldatlas.research

/** Bounded explicit question clauses for passage selection, NOT a semantic coverage verdict.
 * Unknown/elliptical grammar keeps ordinary packing. Never changes the displayed question,
 * answer policy, source text, medical routing, or treats keyword presence as an answer. */
internal object QuestionParts {
    private val start = Regex("(?i)^\\s*(?:what|why|how|when|where|which|who|explain|describe|compare)\\b")
    private val boundary = Regex("(?i)(?:,?\\s+and\\s+|[?;]\\s+)(?=(?:what|why|how|when|where|which|who)\\b)")

    fun terms(question: String): List<List<String>> = clauses(question).map { FtsQuery.from(it)!!.terms }

    /** Discovery-only clauses. Quoted material stays masked; never substitute these
     * strings for the original question in the answer prompt or source policy. */
    fun clauses(question: String): List<String> {
        val asked = QuestionSubject.text(question)
        if (asked.length > 2_000 || !start.containsMatchIn(asked)) return emptyList()
        // Keep offsets while hiding quoted and parenthetical material. Do not split
        // a title, example, code fragment or aside into an instruction of its own.
        val masked = StringBuilder(asked)
        var quote: Char? = null
        var depth = 0
        var escaped = false
        for (i in asked.indices) {
            val c = asked[i]
            if (quote != null) {
                masked.setCharAt(i, ' ')
                if (!escaped && c == quote) quote = null
                escaped = !escaped && c == '\\'
            } else when {
                c == '"' || c == '`' || c == '“' -> {
                    quote = if (c == '“') '”' else c
                    masked.setCharAt(i, ' ')
                }
                c == '(' -> { depth++; masked.setCharAt(i, ' ') }
                c == ')' -> { if (depth == 0) return emptyList(); depth--; masked.setCharAt(i, ' ') }
                depth > 0 -> masked.setCharAt(i, ' ')
            }
        }
        if (quote != null || depth != 0) return emptyList()
        val splits = boundary.findAll(masked).toList()
        if (splits.size !in 1..2) return emptyList()
        val pieces = ArrayList<String>()
        var offset = 0
        for (split in splits) {
            pieces += masked.substring(offset, split.range.first)
            offset = split.range.last + 1
        }
        pieces += masked.substring(offset)
        val terms = pieces.map { FtsQuery.from(it)?.terms.orEmpty() }
        return pieces.map(String::trim).takeIf { terms.all { part -> part.size >= 2 } }.orEmpty()
    }

    /** Best existing whole unit per explicit clause, stable on ties. A two-term
     * lexical floor avoids promoting passages for a lone generic word. This only
     * reserves reading opportunities; it neither validates nor synthesizes claims. */
    fun <T> anchors(parts: List<List<String>>, items: List<T>, text: (T) -> String): List<T> {
        if (parts.isEmpty() || items.isEmpty()) return emptyList()
        val bodies = items.map(text)
        return parts.mapNotNull { terms ->
            val hits = bodies.map { EvidenceRelevance.termHits(it, terms) }
            val index = hits.indices.maxByOrNull { hits[it] } ?: return@mapNotNull null
            items[index].takeIf { hits[index] >= 2 }
        }.distinct()
    }
}
