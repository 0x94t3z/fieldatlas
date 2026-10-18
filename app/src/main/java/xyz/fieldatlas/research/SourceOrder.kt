package xyz.fieldatlas.research

/**
 * A finished answer's sources numbered in the order the answer first cites them, then the passages it did not cite in
 * their retrieval order. The model numbers sources by where they sat in its prompt, so an answer that cites only its
 * second passage opened with a "2" above a list starting at "1". The answer's markers and the list change together,
 * so a number always opens the passage it names.
 */
object SourceOrder {
    private val marker = Regex("\\[S([0-9]+)]")

    fun citedFirst(answer: String, sources: List<Evidence>): Pair<String, List<Evidence>> {
        if (sources.size < 2) return answer to sources
        val cited = marker.findAll(answer).mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..sources.size }.distinct().toList()
        val order = cited + (1..sources.size).filterNot { it in cited }
        if (order == (1..sources.size).toList()) return answer to sources
        val renumbered = IntArray(sources.size + 1).also { order.forEachIndexed { place, old -> it[old] = place + 1 } }
        val text = marker.replace(answer) { match ->
            val old = match.groupValues[1].toIntOrNull()
            if (old != null && old in 1..sources.size) "[S${renumbered[old]}]" else match.value
        }
        return text to order.map { sources[it - 1] }
    }
}
