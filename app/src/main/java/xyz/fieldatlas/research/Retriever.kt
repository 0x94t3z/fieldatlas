package xyz.fieldatlas.research

fun interface Retriever {
    /** Whether a search for this question could reach any enabled local collection. */
    fun hasEligiblePacks(query: String): Boolean = true

    /**
     * [onProgress] receives a monotonically increasing 0.0-1.0 estimate of retrieval work
     * completed plus how many matches so far came from vector (concept) search rather than
     * keywords; implementations must call it from the calling coroutine and never go back.
     */
    suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence>

    /** Saved places near a point, nearest first. Only packs with coordinates contribute. */
    suspend fun nearby(
        point: GeoPoint,
        radiusKm: Double,
        limit: Int,
        categories: Set<String> = emptySet(),
    ): List<NearbyPlace> = emptyList()
}

/** Specialized multi-pack filtering; other retrievers retain their existing search contract. */
suspend fun Retriever.searchForQuestion(
    query: String,
    question: String,
    limit: Int,
    placesOnly: Boolean = false,
    onProgress: suspend (SearchProgress) -> Unit,
): List<Evidence> = if (this is MultiKnowledgeRetriever) {
    searchForQuestion(query, question, limit, onProgress, placesOnly)
} else {
    search(query, limit, onProgress)
}

/** Retrieval progress: work fraction plus the running count of vector-search matches. */
data class SearchProgress(val fraction: Double, val vectorMatches: Int = 0)

/** Callers without a progress interest keep the two-argument shape. */
suspend fun Retriever.search(query: String, limit: Int): List<Evidence> = search(query, limit) { }
