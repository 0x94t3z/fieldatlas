package xyz.fieldatlas.research

/**
 * A follow-up searches anew, but the passages behind the answer it follows are often the ones it needs: "when was it
 * turned back into a mosque?" after an answer about Hagia Sophia is answered by the passage that answer already
 * read, which a fresh search for the combined question can miss. Those passages come along, ranked by the follow-up's
 * own words, without crowding out what the new search found.
 */
object TopicEvidence {
    /** The answer a follow-up builds on: what was asked as the follow-up, and the sources shown with that answer. */
    data class Carried(val asked: String, val sources: List<Evidence>)

    /** At most this many earlier passages lead the prompt; the rest of the slots stay with the new search. */
    const val MAX_LEADING = 3

    fun merge(found: List<Evidence>, carried: Carried?, limit: Int): List<Evidence> {
        if (carried == null || carried.sources.isEmpty()) return found.take(limit)
        val seen = found.map { it.chunkId }.toHashSet()
        val terms = FtsQuery.from(carried.asked)?.terms.orEmpty().map { it.lowercase() }.filter { it.length > 2 }
        val earlier = carried.sources.distinctBy { it.chunkId }.filter { it.chunkId !in seen }
            .map { it to terms.count { term -> term in it.text.lowercase() || term in it.title.lowercase() } }
        // Earlier passages that share the follow-up's words go first; the others only fill slots the search left empty.
        val leading = earlier.filter { it.second > 0 }.sortedByDescending { it.second }.take(MAX_LEADING).map { it.first }
        val rest = earlier.map { it.first }.filter { it !in leading }
        return (leading + found + rest).take(limit)
    }
}
