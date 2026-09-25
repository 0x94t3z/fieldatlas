package xyz.fieldatlas.research

fun interface Retriever {
    /**
     * [onProgress] receives a monotonically increasing 0.0-1.0 estimate of retrieval work
     * completed plus how many matches so far came from vector (concept) search rather than
     * keywords; implementations must call it from the calling coroutine and never go back.
     */
    suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence>
}

/** Retrieval progress: work fraction plus the running count of vector-search matches. */
data class SearchProgress(val fraction: Double, val vectorMatches: Int = 0)

/** Callers without a progress interest keep the two-argument shape. */
suspend fun Retriever.search(query: String, limit: Int): List<Evidence> = search(query, limit) { }

