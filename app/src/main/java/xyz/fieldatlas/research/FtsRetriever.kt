package xyz.fieldatlas.research

class FtsRetriever(private val database: KnowledgeDatabase) : Retriever {
    override suspend fun search(query: String, limit: Int): List<Evidence> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val sanitized = FtsQuery.from(query) ?: return emptyList()
        val exact = database.search(sanitized.matchExpression, limit)
        if (exact.size >= limit || sanitized.fallbackExpression == sanitized.matchExpression) return exact
        val seen = exact.mapTo(mutableSetOf()) { it.chunkId }
        val broad = database.search(sanitized.fallbackExpression, limit)
            .filter { seen.add(it.chunkId) }
        return (exact + broad).take(limit)
    }
}
