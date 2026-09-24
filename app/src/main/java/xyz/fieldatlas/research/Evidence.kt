package xyz.fieldatlas.research

data class Evidence(
    val documentId: String,
    val chunkId: String,
    val title: String,
    val source: String,
    val text: String,
    val score: Double,
    /**
     * Why this chunk was retrieved, in user words: "keyword: tiger, size" when BM25 matched
     * query terms, "concept match 0.81" when the pack embedding did. Null for callers that
     * construct evidence without a retrieval story (tests, fixtures).
     */
    val matchedBy: String? = null,
) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        require(chunkId.isNotBlank()) { "chunkId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(text.isNotBlank()) { "text must not be blank" }
        require(score.isFinite()) { "score must be finite" }
    }
}
