package xyz.fieldatlas.research

class FtsRetriever(private val database: KnowledgeDatabase) : Retriever {
    override suspend fun search(query: String, limit: Int): List<Evidence> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val sanitized = FtsQuery.from(query) ?: return emptyList()
        // Do not broaden a miss with an OR query. A small pack often shares generic
        // words such as "explain" with an unrelated question; treating that as
        // evidence produces a misleading cited answer. A true miss is handled by
        // the offline-model path and is labelled uncited in the UI.
        return database.search(sanitized.matchExpression, limit)
    }
}
