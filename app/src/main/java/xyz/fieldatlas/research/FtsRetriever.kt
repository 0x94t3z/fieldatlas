package xyz.fieldatlas.research

import java.text.Normalizer

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
    ): List<Evidence> = search(query, limit, emptyList(), onProgress)

    override suspend fun searchQuestion(
        query: String,
        question: String,
        limit: Int,
        placesOnly: Boolean,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = searchFocused(query, limit, emptyList(), TechnicalRuleFocus.from(question), onProgress)

    /**
     * [destinations] are the places the question names that this pack holds listings for (see
     * [DestinationScope]); a place-listing pack is then searched within their titles first.
     */
    suspend fun search(
        query: String,
        limit: Int,
        destinations: List<String>,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = searchFocused(query, limit, destinations, TechnicalRuleFocus.from(query), onProgress)

    /** [focus] belongs to the original question, not any planner-expanded search string. */
    internal suspend fun searchFocused(
        query: String,
        limit: Int,
        destinations: List<String>,
        focus: TechnicalRuleFocus?,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val sanitized = FtsQuery.from(query) ?: return emptyList()
        // Terms that occur nowhere in the pack can never match any conjunction; keeping them
        // (models invent plausible compound words) would make every conjunction empty.
        val alive = sanitized.terms.filter { term -> frequencyOf(term) > 0 }
        if (alive.isEmpty() && focus == null) return emptyList()
        onProgress(SearchProgress(0.3))

        // A pack of place listings ("Cape Town — Swimming") is searched within the destinations
        // the question names: searched whole, the city's words match every listing of the city
        // and the question's other words match place NAMES anywhere ("Cookstown — TIME", "Boise
        // — First Thursday" for a Mexico City street-food question). The city's own words and
        // lookup wording are dropped from the scoped terms, so the kind of place asked for
        // ("street food", "swim") decides the ranking. Too few scoped rows (a city with three
        // listings, or none of the kind asked) fall back to the whole pack, as before.
        val placeListing = database.isPlaceListing()
        if (placeListing && destinations.isNotEmpty()) {
            val destinationTerms = destinations.flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toHashSet()
            val scoped = alive.filterNot { term -> term in destinationTerms || term in EvidenceRelevance.placeSearchFiller }
            if (scoped.isNotEmpty()) {
                val scope = "{title} : (" + destinations.joinToString(" OR ") { "^\"" + it.replace("\"", " ") + "\"" } + ")"
                val pool = candidates(scoped, limit, scope) { fraction -> onProgress(SearchProgress(0.3 + fraction * 0.3)) }
                    .values.filter { row -> DestinationScope.scopes(row.title, destinations) }.sortedBy { row -> row.score }
                val picked = pick(pool, limit, query, sanitized)
                if (picked.size >= MIN_SCOPED_ROWS) {
                    return picked.map { row -> row.copy(matchedBy = keywordAttribution(row, scoped)) }
                        .also { onProgress(SearchProgress(1.0)) }
                }
            }
        }

        val pool = candidates(alive, limit, null) { fraction -> onProgress(SearchProgress(0.3 + fraction * 0.5)) }
        val ruleFocus = focus.takeUnless { placeListing }
        // This pass is independent of the ordinary pool's fullness. Prefix/stem queries
        // and title leads can otherwise spend every slot before an exact rule is read.
        ruleFocus?.let { wanted ->
            database.search(wanted.matchExpression, minOf(limit, FOCUSED_CANDIDATE_CAP))
                .filter(wanted::matches).forEach { row -> pool[row.documentId + ":" + row.chunkId] = row }
        }
        return rankPool(pool, limit, query, sanitized, alive, placeListing, ruleFocus)
            .map { row -> row.copy(matchedBy = keywordAttribution(row, alive)) }
            .also { onProgress(SearchProgress(1.0)) }
    }

    /** Test candidate engines retain the app's title nomination and document diversity.
     * Callers must verify candidates against this immutable pack before ranking them.
     * Candidate scores must use one consistent ordering; they are not BM25 confidence. */
    internal fun rankVerifiedCandidates(query: String, candidates: List<Evidence>, limit: Int): List<Evidence> =
        rankVerifiedCandidates(query, candidates, limit, null)

    /**
     * [focus] belongs to the original question: its exact rule passages join the pool as in
     * [searchFocused], since prefix and stem matching can spend every candidate before the rule.
     */
    internal fun rankVerifiedCandidates(query: String, candidates: List<Evidence>, limit: Int, focus: TechnicalRuleFocus?): List<Evidence> {
        require(limit in 1..50)
        val sanitized = FtsQuery.from(query) ?: return emptyList()
        val alive = sanitized.terms.filter { frequencyOf(it) > 0 }
        val pool = LinkedHashMap<String, Evidence>()
        candidates.forEach { row -> pool.putIfAbsent(row.documentId + ":" + row.chunkId, row) }
        val placeListing = database.isPlaceListing()
        val ruleFocus = focus.takeUnless { placeListing }
        ruleFocus?.let { wanted ->
            database.search(wanted.matchExpression, minOf(limit, FOCUSED_CANDIDATE_CAP))
                .filter(wanted::matches).forEach { row -> pool.putIfAbsent(row.documentId + ":" + row.chunkId, row) }
        }
        return rankPool(pool, limit, query, sanitized, alive, placeListing,
            (ruleFocus ?: TechnicalRuleFocus.from(query)).takeUnless { placeListing })
    }

    private fun rankPool(
        pool: LinkedHashMap<String, Evidence>, limit: Int, query: String,
        sanitized: FtsQuery, alive: List<String>, placeListing: Boolean,
        focus: TechnicalRuleFocus? = null,
    ): List<Evidence> {
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
        // A place-listing pack gets no title boost at all: its titles are place NAMES, and the
        // shortest name holding a question word ("Cape Town — Spar", "Alcamo — Good Friday") is
        // junk that this boost put ahead of the pack's strongest bm25 match.
        val titleRows = if (placeListing) emptyList() else database.titleLeadChunks(nominatedTitles(query, alive))
        // Chosen title articles re-score below the whole pool's floor, ordered by their
        // weighted coverage, so the pack's ranking leads with the articles the question names.
        val titleBoosts = LinkedHashMap<String, Int>()
        titleRows.forEachIndexed { index, row ->
            val key = row.documentId + ":" + row.chunkId
            pool[key] = row
            titleBoosts[key] = index
        }
        val floor = pool.filterKeys { key -> key !in titleBoosts }.values.minOfOrNull { row -> row.score } ?: 0.0
        // "Which attribute does NamedOwner use?" needs their relationship, not
        // only a definition of Attribute. Reserve at most two slots for concise
        // original passages covering both; this is ranking, not semantic verification.
        val relationBoosts = if (placeListing) emptyMap() else relationshipCandidates(query, pool.values.toList())
            .mapIndexed { index, row -> (row.documentId + ":" + row.chunkId) to index }.toMap()
        val ranked = pool
            .map { (key, row) ->
                val boost = titleBoosts[key]
                val relation = relationBoosts[key]
                when {
                    relation != null -> row.copy(score = floor - titleRows.size - relationBoosts.size + relation)
                    boost != null -> row.copy(score = floor - (titleRows.size - boost))
                    else -> row
                }
            }
            .sortedBy { row -> row.score }
        return pick(ranked, limit, query, sanitized, focus)
    }

    /**
     * The three matching stages over [terms] into ONE candidate pool, keyed by document and
     * chunk; [scope] is an FTS5 expression every stage is confined to (a destination's titles),
     * or null for the whole pack. [onProgress] receives the stages' share of the work, 0 to 1.
     */
    private suspend fun candidates(
        terms: List<String>,
        limit: Int,
        scope: String?,
        onProgress: suspend (Double) -> Unit,
    ): LinkedHashMap<String, Evidence> {
        if (terms.isEmpty()) return LinkedHashMap()
        fun scoped(expression: String) = if (scope == null) expression else "($scope) AND ($expression)"
        val rareFirst = terms.sortedWith(compareBy({ frequencyOf(it) }, { -it.length }))
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
            val exact = database.search(scoped(conjunction), limit)
            if (exact.isEmpty()) continue
            consider(exact)
            break
        }
        onProgress(0.5)

        // Pack-strength pass: bm25 over every surviving term at once. The question's entities
        // are often corpus-common (encyclopaedic) words that the rare-core stage discards; this
        // is the pass that retrieves the Lion and Taiwan ARTICLES for questions about them. Its
        // full fetched head goes into the pool, so it can never be gated out by other stages.
        consider(database.search(scoped(terms.joinToString(" OR ", transform = ::ftsTerm)), limit * 3))
        onProgress(1.0)

        // Coverage pass: per-term probes so every entity of a compound question ("Singapore or
        // Honduras") keeps a candidate even when the pack's overall bm25 leader is something
        // else entirely. Only runs while the pool is thin — in caller's term order (question
        // content words lead, planner vocabulary follows): rarity-first coverage let generic
        // synonyms spend every slot before the question's own entities were ever queried.
        if (pool.size < limit) {
            val perTerm = maxOf(2, (limit + 2) / 3)
            for (term in terms) {
                if (pool.size >= limit * 2) break
                consider(database.search(scoped(ftsTerm(term)), perTerm))
            }
        }
        return pool
    }

    /** The title-boost candidates for [alive]: the articles the question names, most-covered and shortest first. */
    private fun nominatedTitles(query: String, alive: List<String>): List<TitleCandidate> {
        val candidateIndex = if (alive.isEmpty()) emptyMap() else database.titleCandidateTitles(
            alive.take(TITLE_TERM_SCAN)
                .joinToString(" OR ") { term -> "title : \"" + term.replace("\"", "") + "\"*" },
        ).associateWith { candidate -> candidate.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toHashSet() }
        val comparisonSubjects = EvidenceRelevance.comparisonTerms(query)
        return alive.take(TITLE_TERM_SCAN).mapNotNull { term ->
            candidateIndex.entries.filter { (_, tokens) ->
                term in tokens || term + "s" in tokens ||
                    (term.endsWith("s") && term.dropLast(1) in tokens)
            }
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
    }

    /** The strongest [ranked] rows, at most [MAX_CHUNKS_PER_DOCUMENT] per document. */
    private fun pick(
        ranked: List<Evidence>, limit: Int, query: String, sanitized: FtsQuery,
        focus: TechnicalRuleFocus? = null,
    ): List<Evidence> {
        val picked = ArrayList<Evidence>()
        val chunksPerDocument = HashMap<String, Int>()
        val passages = EvidenceRelevance.rankByPassage(ranked,
            EvidenceRelevance.comparisonTerms(query).ifEmpty { sanitized.terms })
        for (row in focus?.prioritize(passages) ?: passages) {
            if (picked.size >= limit) break
            if (PassageContent.isEmptyWebPage(row, query) || PassageContent.isContentsOnly(row, query)) continue
            val used = chunksPerDocument[row.documentId] ?: 0
            if (used >= MAX_CHUNKS_PER_DOCUMENT) continue
            chunksPerDocument[row.documentId] = used + 1
            picked += row
        }
        return picked
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
        internal fun relationshipCandidates(query: String, candidates: List<Evidence>): List<Evidence> {
            val terms = QuestionSubject.relationTerms(query)
            if (terms.isEmpty()) return emptyList()
            val owner = QuestionSubject.relationOwner(query) ?: return emptyList()
            val statedUse = Regex("(?i)\\b" + Regex.escape(owner) + "\\s+uses?\\b")
            return candidates.filter { row ->
                val passage = EvidenceRelevance.passageText(row.text)
                passage.length <= 700 && statedUse.containsMatchIn(passage) &&
                    EvidenceRelevance.termHits(passage, terms) == terms.size
            }.sortedBy { it.score }.take(2)
        }

        /** For explicit two-subject comparisons, don't nominate a short History or
         * Types section instead of the subject's introduction. Other searches retain
         * their existing ranking. This is coverage selection, not claim verification. */
        internal fun comparisonTitlePriority(title: String, term: String, subjects: List<String>): Int =
            if (term in subjects && title.equals("$term — Overview", ignoreCase = true)) 0 else 1

        const val FREQUENCY_PROBE_CAP = 100_000
        private const val FOCUSED_CANDIDATE_CAP = 8
        const val CORE_TERMS = 5
        const val MAX_CHUNKS_PER_DOCUMENT = 2
        /** Fewer scoped rows than this, and the whole pack is searched instead. */
        const val MIN_SCOPED_ROWS = 3
        const val TITLE_BOOST_CAP = 8
        const val TITLE_TERM_SCAN = 16
        const val NAME_TERM_SCAN = 4
        private const val OVERVIEW = " — Overview"
        const val NAME_BOOST_CAP = 2
        private val NAME_JOINERS = setOf("of", "the", "and", "in", "on", "a", "an", "de", "la", "le", "von", "van", "der")
    }
}

/**
 * The destinations a question names, as a place-listing pack titles them ("Cape Town — Swimming",
 * "Mexico City/Centro — Street food alley"). Candidates are the question's capitalised phrases and
 * the words after "in", "at", "near" and "around" (so "in budapest" works), longest first; a pack
 * confirms each by holding a title that starts with it, so "Nice", "Split" and "Bath" scope only
 * where the pack really has that destination, and a question's filler words never do.
 */
internal object DestinationScope {
    /** A phrase that may name a destination, with the question word positions it covers. */
    internal data class Candidate(val phrase: String, val start: Int, val end: Int)

    private val word = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")
    private val clauseBreak = Regex("[,;:.?!()\\[\\]\"“”\\n]+")
    private val prepositions = setOf("in", "at", "near", "around")
    // Lowercase words a multiword name may contain ("Rio de Janeiro", "Isle of Wight", "Frankfurt am Main").
    private val joiners = setOf("of", "de", "del", "della", "la", "le", "les", "da", "do", "dos", "das", "am", "an", "im", "upon", "on", "the", "sur", "and", "y", "e")
    // Words that open a question whatever it asks; a name opening a question is still found after "in".
    private val openers = setOf("best", "good", "great", "top", "any", "which", "where", "what", "when", "who", "how", "is", "are",
        "can", "could", "should", "would", "do", "does", "did", "please", "tell", "give", "show", "help", "need", "want", "looking",
        "find", "recommend", "suggest", "first", "nice", "going", "heading", "visiting", "planning", "there", "we", "my", "our", "the",
        "a", "an", "i", "im", "anyone", "got", "cheap", "quick", "name", "list", "ok", "okay", "hey", "hi")
    private const val MAX_PHRASE_WORDS = 4
    private const val MAX_RUN_WORDS = 6

    /** Every phrase of [question] that may name a destination, longest first; computed once per question. */
    fun candidates(question: String): List<Candidate> {
        data class Word(val text: String, val clause: Int)
        val words = ArrayList<Word>()
        var clause = 0
        var last = 0
        for (match in clauseBreak.findAll(question)) {
            for (w in word.findAll(question.substring(last, match.range.first))) words += Word(w.value, clause)
            clause++
            last = match.range.last + 1
        }
        for (w in word.findAll(question.substring(last))) words += Word(w.value, clause)

        val found = ArrayList<Candidate>()
        fun add(start: Int, end: Int) {
            for (length in minOf(MAX_PHRASE_WORDS, end - start) downTo 1) {
                for (from in start..end - length) {
                    val span = words.subList(from, from + length)
                    if (span.first().text.lowercase() in joiners || span.last().text.lowercase() in joiners) continue
                    // A phrase of nothing but filler ("Which", "the") names no place.
                    if (span.all { w -> FtsQuery.from(w.text) == null || w.text.lowercase() in joiners }) continue
                    found += Candidate(span.joinToString(" ") { it.text }, from, from + length)
                }
            }
        }
        var i = 0
        while (i < words.size) {
            if (words[i].text.first().isUpperCase()) {
                var j = i
                while (j < words.size && words[j].clause == words[i].clause && j - i < MAX_RUN_WORDS &&
                    (words[j].text.first().isUpperCase() || words[j].text.lowercase() in joiners)) j++
                while (j > i && words[j - 1].text.lowercase() in joiners) j--
                if (!(i == 0 && j - i == 1 && words[0].text.lowercase() in openers)) add(i, j)
                i = maxOf(j, i + 1)
            } else i++
        }
        words.forEachIndexed { index, w ->
            if (w.text.lowercase() !in prepositions) return@forEachIndexed
            var j = index + 1
            while (j < words.size && words[j].clause == w.clause && j - index - 1 < MAX_PHRASE_WORDS) {
                // "in the morning" names nothing; "in Rio de Janeiro" keeps its joining word.
                val lower = words[j].text.lowercase()
                if (lower in prepositions || FtsQuery.from(lower) == null || lower in openers) break
                j++
            }
            if (j > index + 1) add(index + 1, j)
        }
        return found.sortedWith(compareBy({ -(it.end - it.start) }, { it.start })).distinctBy { it.phrase.lowercase() to it.start }
    }

    /**
     * The candidates [holds] confirms, longest first; a confirmed phrase's words are not tried again,
     * so "Mexico City" is one destination, not also "Mexico" and "City".
     */
    fun resolve(candidates: List<Candidate>, holds: (String) -> Boolean): List<String> {
        val consumed = HashSet<Int>()
        val found = ArrayList<String>()
        for (candidate in candidates) {
            if ((candidate.start until candidate.end).any(consumed::contains)) continue
            if (!holds(candidate.phrase)) continue
            consumed.addAll(candidate.start until candidate.end)
            if (found.none { it.equals(candidate.phrase, ignoreCase = true) }) found += candidate.phrase
        }
        return found
    }

    /** Whether [title] is a listing of one of [destinations] or its districts ("Cape Town — X", "Mexico City/Centro — X"). */
    fun scopes(title: String, destinations: List<String>): Boolean {
        val folded = fold(title)
        return destinations.any { destination ->
            val name = fold(destination)
            folded.startsWith("$name — ") || folded.startsWith("$name/")
        }
    }

    /** Lowercase without accents, as the FTS index compares ("zurich" finds "Zürich — Kunsthaus"). */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
