package xyz.fieldatlas.research

fun interface Retriever {
    suspend fun search(query: String, limit: Int): List<Evidence>
}
