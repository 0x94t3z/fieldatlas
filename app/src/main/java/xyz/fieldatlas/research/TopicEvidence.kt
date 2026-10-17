package xyz.fieldatlas.research

/**
 * A follow-up searches anew, but the passages behind the answer it follows are often the ones it needs: "when was it
 * turned back into a mosque?" after an answer about Hagia Sophia is answered by the passage that answer already
 * read, which a fresh search for the combined question can miss. Those passages come along, ranked by the follow-up's
 * own words, without crowding out what the new search found.
 */
object TopicEvidence {
    /** The answer a follow-up builds on: what was asked as the follow-up, and the sources shown with that answer. */
    data class Carried(val asked: String, val sources: List<Evidence>, val earlier: List<FollowUp.Turn> = emptyList())

    /** At most this many earlier passages lead the prompt; the rest of the slots stay with the new search. */
    const val MAX_LEADING = 3

    // Only a follow-up that points back at the earlier answer ("when was it…", "which of those…") reads its passages.
    // One that names something new ("and from the moon?") is about that, and the earlier subject's passages mislead.
    private val pointsBack = Regex("(?i)\\b(?:it|its|it's|that|those|these|this|they|them|their|theirs|there|which\\s+(?:one|ones|of))\\b")
    fun refersBack(asked: String): Boolean = pointsBack.containsMatchIn(asked)

    /** The earlier passages that share the follow-up's words, best first, at most [MAX_LEADING]. */
    fun leading(carried: Carried?): List<Evidence> {
        if (carried == null || carried.sources.isEmpty() || !refersBack(carried.asked)) return emptyList()
        val terms = FtsQuery.from(carried.asked)?.terms.orEmpty().map { it.lowercase() }.filter { it.length > 2 }
        return carried.sources.distinctBy { it.chunkId }
            .map { it to terms.count { term -> term in it.text.lowercase() || term in it.title.lowercase() } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(MAX_LEADING).map { it.first }
    }

    /**
     * Run last, after the subject overviews are put in front: those general passages ("Mosque — Overview") would
     * otherwise push the earlier answer's own passage past what a phone's prompt can hold.
     */
    fun lead(evidence: List<Evidence>, carried: Carried?, ahead: Boolean = true): List<Evidence> {
        // The reading names what the earlier answer gave ("Tokyo: how many people live there"): that subject's own
        // article answers, and the passages behind "the capital of Japan" would only crowd it out of a short prompt.
        if (!ahead) return evidence
        val first = leading(carried)
        if (first.isEmpty()) return evidence
        val ids = first.map { it.chunkId }.toHashSet()
        return first + evidence.filter { it.chunkId !in ids }
    }

    fun merge(found: List<Evidence>, carried: Carried?, limit: Int, ahead: Boolean = true): List<Evidence> {
        if (carried == null || carried.sources.isEmpty() || !refersBack(carried.asked)) return found.take(limit)
        val seen = found.map { it.chunkId }.toHashSet()
        val terms = FtsQuery.from(carried.asked)?.terms.orEmpty().map { it.lowercase() }.filter { it.length > 2 }
        val earlier = carried.sources.distinctBy { it.chunkId }.filter { it.chunkId !in seen }
            .map { it to terms.count { term -> term in it.text.lowercase() || term in it.title.lowercase() } }
        // Earlier passages that share the follow-up's words go first. One sharing none of them would only stand in for
        // a search that found nothing, and be quoted as if it answered the follow-up.
        val leading = earlier.filter { it.second > 0 }.sortedByDescending { it.second }.take(MAX_LEADING).map { it.first }
        return (if (ahead) leading + found else found + leading).take(limit)
    }
}
