package xyz.fieldatlas.research

/**
 * Ranks one pack's chunks for a question.
 *
 * Matching runs in three stages:
 * 1. Conjunctions of the rarest terms (rarest-first, most-terms-first). A chunk holding the
 *    3-4 rarest query terms is the best lexical match a library can give; "all terms"
 *    conjunctions only ever empty out once keyword expansion adds vocabulary. Zero-frequency
 *    terms are dropped first (keyword models invent plausible compounds that match nothing).
 * 2. A bounded bm25 OR over every surviving term: this is what lets the pack's own strongest
 *    matches through — including the question's *entities* when they are common words in a
 *    general corpus ("lion", "tiger"), which rare-term conjunctions systematically exclude.
 * 3. Term-by-term top-ups, rarest first with a per-term cap, so every entity of a compound
 *    question ("Singapore or Honduras") reaches the evidence pack and one mistyped keyword
 *    cannot monopolise it.
 * Across all stages a document may contribute at most [MAX_CHUNKS_PER_DOCUMENT] chunks.
 * The final list is ordered by bm25 strength (more negative = stronger) because the
 * cross-pack merge trusts the strongest absolute matches first.
 */
class FtsRetriever(private val database: KnowledgeDatabase) : Retriever {
    private val frequencyCache = HashMap<String, Int>()

    override suspend fun search(
        query: String,
        limit: Int,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val sanitized = FtsQuery.from(query) ?: return emptyList()
        // Terms that occur nowhere in the pack can never match any conjunction; keeping them
        // (models invent plausible compound words) would make every conjunction empty.
        val alive = sanitized.terms.filter { term -> frequencyOf(term) > 0 }
        if (alive.isEmpty()) return emptyList()
        onProgress(SearchProgress(0.3))

        val rareFirst = alive.sortedWith(compareBy({ frequencyOf(it) }, { -it.length }))
        val core = rareFirst.take(CORE_TERMS)

        // Every stage contributes to ONE candidate pool; selection happens once, at the end,
        // purely by score. Stages used to race for slots sequentially with per-stage budgets,
        // and a fat junk conjunction (a "Basque Country" article holding five rare query words)
        // could fill the slots and starve the pass that would have found the real articles.
        // As equal pool entries, junk simply ranks below strength instead of blocking it.
        val pool = LinkedHashMap<String, Evidence>()
        fun consider(rows: List<Evidence>) {
            for (row in rows) pool.putIfAbsent(row.documentId + ":" + row.chunkId, row)
        }

        for (size in core.size downTo 2) {
            val conjunction = core.take(size).joinToString(" AND ", transform = ::ftsTerm)
            val exact = database.search(conjunction, limit)
            if (exact.isEmpty()) continue
            consider(exact)
            break
        }
        onProgress(SearchProgress(0.55))

        // Pack-strength pass: bm25 over every surviving term at once. The question's entities
        // are often corpus-common (encyclopaedic) words that the rare-core stage discards; this
        // is the pass that retrieves the Lion and Taiwan ARTICLES for questions about them. Its
        // full fetched head goes into the pool, so it can never be gated out by other stages.
        consider(database.search(alive.joinToString(" OR ", transform = ::ftsTerm), limit * 3))
        onProgress(SearchProgress(0.8))

        // Coverage pass: per-term probes so every entity of a compound question ("Singapore or
        // Honduras") keeps a candidate even when the pack's overall bm25 leader is something
        // else entirely. Only runs while the pool is thin — in caller's term order (question
        // content words lead, planner vocabulary follows): rarity-first coverage let generic
        // synonyms spend every slot before the question's own entities were ever queried.
        if (pool.size < limit) {
            val perTerm = maxOf(2, (limit + 2) / 3)
            for (term in alive) {
                if (pool.size >= limit * 2) break
                consider(database.search(ftsTerm(term), perTerm))
            }
        }

        // Title boost: an article whose TITLE contains query terms ("World War I" for a WWI
        // question, "Taiwan" for a Taiwan question) is the single most valuable chunk this pack
        // can offer, but plain bm25 routinely loses its lede to unrelated pages that merely
        // mention the words. Candidates are every article the query terms hit in the title
        // index; the slots go to the articles whose titles hold the MOST query terms — the
        // stub article "World" holds one term, "Allies of World War I" holds three, and the
        // article that carries most of the question is the one the question is about. Ties
        // break to the shortest title ("Taiwan" over "Taiwan Railway").
        // Title boost, second pass: article titles are searched for every (surviving) query
        // term. EVERY name-like article gets a rank-1-style boost, not a lucky few: eligibility
        // is the guard against junk (title nearly equals one query term, or its title covers
        // two+ terms), and the boost ORDER is position-weighted coverage - titles built around
        // early, identifying terms ("Japan" for a Japan question) lead titles built around
        // planner filler ("La Liga records and statistics" for statistics/records). Slot
        // scarcity was removed on purpose: three slots let three cov-2 noise pages bury the one
        // article a question is actually about.
        // Title boost: one nominated article PER TERM - the SHORTEST-titled article whose
        // title contains that term (so "Taiwan" beats "Taiwan Railway"), then the slots go to
        // the RAREST terms (frequency-probed) so generic words ("mount", "data") cannot spend
        // the whole budget on stub pages while "allies" - the word that actually names the
        // answer - is crowded out. This is the fixed21 behaviour, restored verbatim after
        // coverage-tier experiments proved it worse on the 16-case answer battery: what was
        // actually broken around it (merge starvation, probe cap, query order) got fixed
        // separately and stays fixed.
        val candidateIndex = if (alive.isEmpty()) emptyMap() else database.titleCandidateTitles(
            alive.take(TITLE_TERM_SCAN)
                .joinToString(" OR ") { term -> "title : \"" + term.replace("\"", "") + "\"*" },
        ).associateWith { candidate -> candidate.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toHashSet() }
        val comparisonSubjects = EvidenceRelevance.comparisonTerms(query)
        val nominated = alive.take(TITLE_TERM_SCAN).mapNotNull { term ->
            candidateIndex.entries.filter { (_, tokens) -> term in tokens }
                .minWithOrNull(compareBy(
                    { (candidate, _) -> comparisonTitlePriority(candidate.title, term, comparisonSubjects) },
                    // Shortest title first, except that an article's overview counts as its bare
                    // name: "X — Details" is a letter shorter than "X — Overview" but must not
                    // lead it. Place titles ("Berlin — Best Grill") keep plain length order.
                    { (candidate, _) -> overviewAwareLength(candidate.title) },
                ))
                ?.let { entry -> frequencyOf(term) to entry.key }
        }.sortedBy { (frequency, _) -> frequency }
            .distinctBy { (_, candidate) -> candidate.title.lowercase() }
            .map { (_, candidate) -> candidate }
            // An article named entirely by question words ("Marie Curie", "History of Japan")
            // is what the question asks about; per-term nomination alone would pick the
            // shortest title holding one word ("Ève Curie") ahead of it.
            .let { perTerm -> (fullNameTitles(alive) + perTerm).distinctBy { it.title.lowercase() } }
            .take(TITLE_BOOST_CAP)
        val titleRows = database.titleLeadChunks(nominated)
        // Chosen title articles re-score below the whole pool's floor, ordered by their
        // weighted coverage, so the pack's ranking leads with the articles the question names.
        val titleBoosts = LinkedHashMap<String, Int>()
        titleRows.forEachIndexed { index, row ->
            val key = row.documentId + ":" + row.chunkId
            pool[key] = row
            titleBoosts[key] = index
        }
        val floor = pool.filterKeys { key -> key !in titleBoosts }.values.minOfOrNull { row -> row.score } ?: 0.0
        val ranked = pool
            .map { (key, row) ->
                val boost = titleBoosts[key]
                if (boost == null) row else row.copy(score = floor - (titleRows.size - boost))
            }
            .sortedBy { row -> row.score }

        val picked = ArrayList<Evidence>()
        val chunksPerDocument = HashMap<String, Int>()
        for (row in EvidenceRelevance.rankByPassage(ranked,
            EvidenceRelevance.comparisonTerms(query).ifEmpty { sanitized.terms })) {
            if (picked.size >= limit) break
            val used = chunksPerDocument[row.documentId] ?: 0
            if (used >= MAX_CHUNKS_PER_DOCUMENT) continue
            chunksPerDocument[row.documentId] = used + 1
            picked += row
        }
        return picked.map { row -> row.copy(matchedBy = keywordAttribution(row, alive)) }
            .also { onProgress(SearchProgress(1.0)) }
    }

    /** "keyword: a, b" naming up to four query terms actually present in the chunk. */
    private fun keywordAttribution(evidence: Evidence, terms: List<String>): String? {
        val tokens = (evidence.title + " " + evidence.text).lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+")).toHashSet()
        val covered = terms.filter { term -> term in tokens }.distinct().take(4)
        return if (covered.isEmpty()) null else "keyword: " + covered.joinToString(", ")
    }

    private fun frequencyOf(term: String): Int = frequencyCache.getOrPut(term) {
        database.countCapped(ftsTerm(term), FREQUENCY_PROBE_CAP)
    }

    private fun ftsTerm(term: String): String = "\"" + stemOf(term.replace("\"", "")) + "\"*"

    /**
     * Titles whose name (before any " — Section") is two or more words, every one of them a
     * question term apart from joining words. Searched pairwise over the leading terms, so a
     * question with extra words ("Who was Marie Curie and what did she find?") still reaches
     * the article; the closest names come first, an overview before its details.
     */
    private fun fullNameTitles(terms: List<String>): List<TitleCandidate> {
        val scan = terms.take(NAME_TERM_SCAN)
        if (scan.size < 2) return emptyList()
        val stems = terms.map(::stemOf)
        return scan.indices.flatMap { first -> (first + 1 until scan.size).map { second -> scan[first] to scan[second] } }
            .flatMap { (a, b) -> database.titleCandidateTitles("title : ${ftsTerm(a)} AND title : ${ftsTerm(b)}") }
            .distinctBy { candidate -> candidate.title }
            .mapNotNull { candidate ->
                val words = candidate.title.substringBefore(" — ").lowercase()
                    .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in NAME_JOINERS }
                val named = words.size >= 2 && words.all { word ->
                    stems.any { stem -> word.startsWith(stem) && word.length <= stem.length + 3 }
                }
                if (named) candidate to words.size else null
            }
            .sortedWith(compareBy(
                { (_, size) -> -size },
                { (candidate, _) -> !candidate.title.endsWith(OVERVIEW) },
                { (candidate, _) -> candidate.title.length },
            ))
            .take(NAME_BOOST_CAP)
            .map { (candidate, _) -> candidate }
    }

    private fun overviewAwareLength(title: String): Int =
        if (title.endsWith(OVERVIEW)) title.length - OVERVIEW.length else title.length

    private fun stemOf(term: String): String = when {
        term.length > 4 && term.endsWith("ies") -> term.dropLast(2)
        term.length > 4 && term.endsWith("es") -> term.dropLast(2)
        term.length > 3 && term.endsWith("s") -> term.dropLast(1)
        term.length > 4 && term.endsWith("ing") -> term.dropLast(3)
        term.length > 4 && term.endsWith("ed") -> term.dropLast(2)
        else -> term
    }

    internal companion object {
        /** For explicit two-subject comparisons, don't nominate a short History or
         * Types section instead of the subject's introduction. Other searches retain
         * their existing ranking. This is coverage selection, not claim verification. */
        internal fun comparisonTitlePriority(title: String, term: String, subjects: List<String>): Int =
            if (term in subjects && title.equals("$term — Overview", ignoreCase = true)) 0 else 1

        const val FREQUENCY_PROBE_CAP = 100_000
        const val CORE_TERMS = 5
        const val MAX_CHUNKS_PER_DOCUMENT = 2
        const val TITLE_BOOST_CAP = 8
        const val TITLE_TERM_SCAN = 16
        const val NAME_TERM_SCAN = 4
        private const val OVERVIEW = " — Overview"
        const val NAME_BOOST_CAP = 2
        private val NAME_JOINERS = setOf("of", "the", "and", "in", "on", "a", "an", "de", "la", "le", "von", "van", "der")
    }
}
