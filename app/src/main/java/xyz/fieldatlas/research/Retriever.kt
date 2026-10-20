package xyz.fieldatlas.research

fun interface Retriever {
    /** Whether a search for this question could reach any enabled local collection. */
    fun hasEligiblePacks(query: String): Boolean = true

    /** Place names confirmed against installed listing packs, including lowercase input. */
    suspend fun destinations(question: String): List<String> = emptyList()

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

    /**
     * Sections whose title holds [phrase] and one of [sections] ("EIP-4844" and "Abstract"),
     * in document order. A number shared by hundreds of passages cannot rank one document's
     * own section first; its title can.
     */
    suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> = emptyList()

    /**
     * [titled] with [phrase] anchored at the title's first word: "India — Get in", never "Uri
     * (India) — Get in", so a big guide's own sections are not crowded out of [limit].
     */
    suspend fun titled(phrase: String, sections: List<String>, limit: Int, anchored: Boolean): List<Evidence> =
        titled(phrase, sections, limit)

    /** Overviews of airport guides whose text names [place] ("the main airport of Delhi"). */
    suspend fun airportOverviews(place: String, limit: Int): List<Evidence> = emptyList()

    /** Passages that list [name] among an article's other names ("Also known as: GPS; Gps"). */
    suspend fun aliased(name: String, limit: Int): List<Evidence> = emptyList()

    /**
     * The sections of the article titled [article] ("Passport — What if I lose it while
     * travelling?"), shortest titles first; not those of articles that only begin with the same
     * words ("Passport control").
     */
    suspend fun sections(article: String, limit: Int): List<Evidence> = emptyList()

    /**
     * Rows whose title holds [phrase] and one of [words] ("Soviet Union" and "dissolution"), and one
     * of [sections] when given, shortest titles first, from every collection that is not a place
     * listing, including the pack of whole articles by section that [titled] leaves out. Travel and
     * place lookups keep to [titled], so an English article's headings never answer for a town.
     */
    suspend fun headed(phrase: String, words: List<String>, limit: Int, sections: List<String> = emptyList()): List<Evidence> = emptyList()

    /**
     * The article titled [article] from each collection that holds it, whole and in its own order:
     * its lead ("<article> — Overview") first, then its sections. Read by position, so a long
     * article is never cut short by the many other titles that begin with the same words.
     */
    suspend fun article(article: String, limit: Int): List<Evidence> = emptyList()

    /**
     * How many saved passages mention [phrase], counted up to [cap]: a subject's readership as the collections see
     * it ("cast iron" in thousands, "Battle of Ramillies" in a few dozen). [cap] when unknown, so a subject whose
     * count cannot be had is taken as well known.
     */
    suspend fun mentions(phrase: String, cap: Int): Int = cap

    /** A search for [question]; [placesOnly] keeps a place lookup to the place collections. */
    suspend fun searchQuestion(
        query: String,
        question: String,
        limit: Int,
        placesOnly: Boolean,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = search(query, limit, onProgress)
}

/** Specialized multi-pack filtering; other retrievers retain their existing search contract. */
suspend fun Retriever.searchForQuestion(
    query: String,
    question: String,
    limit: Int,
    placesOnly: Boolean = false,
    onProgress: suspend (SearchProgress) -> Unit,
): List<Evidence> = searchQuestion(query, question, limit, placesOnly, onProgress)

/** Retrieval progress: work fraction plus the running count of vector-search matches. */
data class SearchProgress(val fraction: Double, val vectorMatches: Int = 0)

/** Callers without a progress interest keep the two-argument shape. */
suspend fun Retriever.search(query: String, limit: Int): List<Evidence> = search(query, limit) { }
