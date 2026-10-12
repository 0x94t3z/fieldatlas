package xyz.fieldatlas.research

/** Exact count memoization for one immutable, read-only pack handle.
 *
 * Callers hold the handle's database lock for both cache access and loading. Keys include
 * the full bound MATCH expression and SQL limit: prefix and exact matches, different
 * columns and different caps must never share a result. No source text is retained.
 * A fresh database handle owns a fresh cache; failures are never cached.
 */
internal class CappedMatchCountCache(
    private val maxEntries: Int = 256,
    private val maxExpressionChars: Int = 1024,
) {
    private data class Key(val expression: String, val cap: Int)
    private val counts = LinkedHashMap<Key, Int>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(maxExpressionChars > 0)
    }

    fun getOrLoad(expression: String, cap: Int, load: () -> Int): Int {
        // Bound retained key memory as well as entry count without changing query semantics.
        if (expression.length > maxExpressionChars) return load()
        val key = Key(expression, cap)
        counts[key]?.let { return it }
        val count = load()
        counts[key] = count
        if (counts.size > maxEntries) {
            val oldest = counts.entries.iterator()
            oldest.next()
            oldest.remove()
        }
        return count
    }

    fun clear() = counts.clear()
}
