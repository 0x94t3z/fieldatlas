package xyz.fieldatlas.research

fun interface Retriever {
    /**
     * [onProgress] receives a monotonically increasing 0.0-1.0 estimate of retrieval work
     * completed; implementations must call it from the calling coroutine and never go back.
     */
    suspend fun search(query: String, limit: Int, onProgress: suspend (Double) -> Unit): List<Evidence>
}

/** Callers without a progress interest keep the two-argument shape. */
suspend fun Retriever.search(query: String, limit: Int): List<Evidence> = search(query, limit) { }

