package xyz.fieldatlas.research

data class Evidence(
    val documentId: String,
    val chunkId: String,
    val title: String,
    val source: String,
    val text: String,
    val score: Double,
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
