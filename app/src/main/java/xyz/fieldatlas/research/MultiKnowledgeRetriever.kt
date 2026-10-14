package xyz.fieldatlas.research

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import xyz.fieldatlas.assets.PackEmbedding
import xyz.fieldatlas.assets.PackDiscovery

/**
 * Searches eligible enabled knowledge packs and merges the results into one ranking.
 *
 * Each pack carries its own FTS5 database. BM25 scores are negative and more negative means
 * a stronger match, but the raw scale is corpus-dependent (vocabulary size, document length,
 * how common the query terms happen to be in that pack), so a strong match in an encyclopaedia
 * can look weaker than a weak match in a topical library. The merge therefore ranks by
 * RELATIVE strength: every score is shifted by its pack's best (strongest) match, and the
 * merged list sorts those shifts ascending within original-question coverage priority. An
 * eligible passage covering the whole question must survive partial matches from earlier
 * packs when the library outgrows the result budget. The best evidence each pack can offer
 * leads together, and a pack's lower-ranked matches only overtake when they are barely worse
 * than their own top hit. The first entry's raw score still backs the answer screen's
 * evidence-strength signal.
 *
 * Open connections are cached per path: packs are immutable once installed (id/version
 * directories), so reopening per query only repeated the open-time validation on every search.
 * A failing database is evicted and closed so the next search retries from a fresh handle.
 */
class MultiKnowledgeRetriever(
    private val databaseFiles: () -> List<File>,
    private val open: (File) -> KnowledgeDatabase = { file -> KnowledgeDatabase.open(file) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Per-pack embedding metadata, INDEX-ALIGNED with [databaseFiles] (null = keyword-only
     * pack). [embed] encodes a query string with the pack's queryPrefix already prepended.
     */
    private val packEmbeddings: () -> List<PackEmbedding?> = { emptyList() },
    /** Coverage metadata, index-aligned with [databaseFiles], gates expensive vector scans only. */
    private val packDiscoveries: () -> List<PackDiscovery?> = { emptyList() },
    private val embed: suspend (String) -> FloatArray? = { null },
    private val embedForPack: suspend (PackEmbedding, String) -> FloatArray? = { _, text -> embed(text) },
) : Retriever, java.io.Closeable {
    private val opened = ConcurrentHashMap<String, KnowledgeDatabase>()
    /** Packs are searched in parallel; the query encoder is one model and runs one call at a time. */
    private val embedLock = Mutex()

    /** Closes the stores opened from files under [root], so a deleted pack's space is freed at once. */
    fun release(root: File) {
        val prefix = root.absolutePath.trimEnd('/') + "/"
        opened.keys.filter { it.startsWith(prefix) || it == root.absolutePath }.forEach { opened.remove(it)?.close() }
    }

    /** Owner must stop outstanding research before releasing its cached stores. */
    override fun close() {
        opened.values.forEach { it.close() }
        opened.clear()
    }

    override fun hasEligiblePacks(query: String): Boolean {
        return shouldSearchPack(query) && databaseFiles().isNotEmpty()
    }

    override suspend fun search(
        query: String,
        limit: Int,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = searchForQuestion(query, query, limit, onProgress)

    override suspend fun searchQuestion(
        query: String,
        question: String,
        limit: Int,
        placesOnly: Boolean,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = searchForQuestion(query, question, limit, onProgress, placesOnly)

    suspend fun searchForQuestion(
        query: String,
        question: String,
        limit: Int,
        onProgress: suspend (SearchProgress) -> Unit,
        placesOnly: Boolean = false,
    ): List<Evidence> = withContext(ioDispatcher) {
        require(limit in 1..50) { "Evidence limit must be between 1 and 50" }
        if (FtsQuery.from(query) == null) return@withContext emptyList()
        val allFiles = databaseFiles()
        val embeddings = runCatching(packEmbeddings).getOrDefault(emptyList())
        val discoveries = runCatching(packDiscoveries).getOrDefault(emptyList())
        // A place lookup gains nothing from reference or science packs, and those are the large
        // ones. Without any place pack installed, every pack is still searched.
        val indices = allFiles.indices.toList().let { all ->
            if (!placesOnly) all else all.filter { index -> openOrNull(allFiles[index])?.isPlaceListing() == true }.ifEmpty { all }
        }
        val total = indices.size.coerceAtLeast(1)
        // The places the question may name, found once; each place pack confirms the ones it holds
        // and is searched within them (see DestinationScope). The expanded query keeps the question.
        val destinationCandidates = DestinationScope.candidates(question)
        val technicalFocus = TechnicalRuleFocus.from(question)
        val vectorMatches = AtomicInteger(0)
        val fractions = DoubleArray(indices.size)
        val progressLock = Mutex()
        var reported = 0.0
        // Bound simultaneous database scans as optional modules grow. Each permit covers the
        // full pack operation, including suspending vector work, and is released on failure or
        // cancellation. Progress stays monotonic regardless of completion order.
        val packPermits = Semaphore(MAX_PARALLEL_PACKS)
        suspend fun report(slot: Int, fraction: Double) = progressLock.withLock {
            fractions[slot] = maxOf(fractions[slot], fraction.coerceIn(0.0, 1.0))
            val overall = fractions.sum() / total
            if (overall > reported) { reported = overall; onProgress(SearchProgress(overall, vectorMatches.get())) }
        }
        val runs = coroutineScope {
            indices.mapIndexed { slot, index ->
                async {
                    packPermits.withPermit {
                        val file = allFiles[index]
                        val discovery = discoveries.getOrNull(index)
                        runCatching {
                            val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                            // Native search drops its own stopwords ("time", "last", "into"); a query left with
                            // none lists arbitrary records, so it takes the keyword path instead.
                            val terms = FtsQuery.from(query)!!.terms
                            val nativeTerms = terms.filter { it.lowercase() !in LEVIATHAN_STOPWORDS }
                            val destinations = if (database.isPlaceListing()) {
                                DestinationScope.resolve(destinationCandidates, database::holdsDestination)
                            } else emptyList()
                            val candidates = when {
                                nativeTerms.isEmpty() -> null
                                database.isPlaceListing() -> placeCandidates(database, terms, destinations, limit)
                                else -> database.leviathanCandidates(terms.joinToString(" "), limit)
                            }
                            // A pack of whole articles by section is read only through article(), for the
                            // subject of an explanation: its million passages would crowd every other
                            // pack's matches and slow each search.
                            if (candidates == null && database.isArticleSections()) {
                                report(slot, 1.0)
                                return@runCatching emptyList()
                            }
                            val ranking = FtsRetriever(database)
                            val keyword = if (candidates != null) {
                                ranking.rankVerifiedCandidates(query, candidates, limit, technicalFocus).map { row ->
                                    // Leviathan's BM25 stays on the row, so the merge weighs this pack's weak
                                    // matches against other packs' strong ones as it does keyword results.
                                    row.copy(matchedBy = if (candidates.any { it.chunkId == row.chunkId })
                                        "Leviathan candidate; full saved passage; app ranking; Leviathan BM25"
                                    else "App title anchor; full saved passage; not a Leviathan hit")
                                }
                            } else ranking.searchFocused(query, limit, destinations, technicalFocus) { inner -> report(slot, inner.fraction) }
                            val embedding = embeddings.getOrNull(index)
                                ?.takeIf { shouldSearchVectors(discovery, query, it.count) }
                            val (run, hits) = keyword.withVectorEvidence(database, embedding, query, limit)
                            vectorMatches.addAndGet(hits)
                            report(slot, 1.0)
                            run
                        }.getOrElse { error ->
                            if (error is OutOfMemoryError) throw error
                            if (error is CancellationException) throw error
                            opened.remove(file.absolutePath)?.close()
                            report(slot, 1.0)
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
        }
        onProgress(SearchProgress(1.0, vectorMatches.get()))
        mergeRelevantEvidence(runs, query, question, limit)
    }

    /**
     * A place pack's Leviathan search. With destinations named, it runs within their listings
     * (the index's places filter) on the words left once the city's own words and lookup
     * wording are dropped, so the kind of place asked for decides the ranking, as in
     * [FtsRetriever.searchFocused]. Too few listings there search the whole pack, as before.
     * Null keeps the pack's own keyword search: an old pack, or no native engine.
     */
    private suspend fun placeCandidates(database: KnowledgeDatabase, terms: List<String>, destinations: List<String>, limit: Int): List<Evidence>? {
        if (destinations.isNotEmpty()) {
            val destinationTerms = destinations.flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toHashSet()
            val scoped = terms.filterNot { term -> term in destinationTerms || term in EvidenceRelevance.placeSearchFiller }
            if (scoped.none { it.lowercase() !in LEVIATHAN_STOPWORDS }) return database.leviathanCandidates(terms.joinToString(" "), limit)
            val rows = database.leviathanCandidates(scoped.joinToString(" "), limit, destinations.map(DestinationScope::fold).distinct())
                ?: return null
            val within = rows.filter { row -> DestinationScope.scopes(row.title, destinations) }
            if (within.size >= FtsRetriever.MIN_SCOPED_ROWS) return within
        }
        return database.leviathanCandidates(terms.joinToString(" "), limit)
    }

    private fun quoted(text: String) = "\"" + text.replace("\"", " ").trim() + "\""

    override suspend fun sections(article: String, limit: Int): List<Evidence> {
        if (article.isBlank()) return emptyList()
        // "^" anchors the phrase at the title's first word; each pack keeps only the article's own
        // sections before the packs are merged, so "Paris Hilton" cannot crowd out "Paris".
        return matching("{title} : ^" + quoted(article), limit) { it.title.startsWith(article.trim() + " — ", ignoreCase = true) }
    }

    override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> = titled(phrase, sections, limit, anchored = false)

    override suspend fun titled(phrase: String, sections: List<String>, limit: Int, anchored: Boolean): List<Evidence> {
        if (phrase.isBlank() || sections.none(String::isNotBlank)) return emptyList()
        return matching("{title} : (" + (if (anchored) "^" else "") + quoted(phrase) + " AND (" +
            sections.filter(String::isNotBlank).joinToString(" OR ", transform = ::quoted) + "))", limit)
    }

    override suspend fun airportOverviews(place: String, limit: Int): List<Evidence> {
        if (place.isBlank()) return emptyList()
        return matching("{title} : (\"airport\" AND \"overview\") AND {text} : " + quoted(place), limit)
    }

    override suspend fun aliased(name: String, limit: Int): List<Evidence> {
        if (name.isBlank()) return emptyList()
        return matching("{text} : (\"also known as\" AND \"" + name.replace("\"", " ").trim() + "\")", limit)
    }

    override suspend fun article(article: String, limit: Int): List<Evidence> = withContext(ioDispatcher) {
        if (article.isBlank()) return@withContext emptyList()
        require(limit in 1..200) { "Article limit must be between 1 and 200" }
        val lead = "{title} : (^" + quoted(article) + " AND \"overview\")"
        databaseFiles().flatMap { file ->
            runCatching {
                val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                if (database.isPlaceListing()) emptyList() else database.article(lead, article.trim() + " — Overview", limit)
            }.getOrElse { error ->
                if (error is OutOfMemoryError) throw error
                if (error is CancellationException) throw error
                opened.remove(file.absolutePath)?.close()
                emptyList()
            }
        }
    }

    private suspend fun matching(match: String, limit: Int, keep: (Evidence) -> Boolean = { true }): List<Evidence> = withContext(ioDispatcher) {
        require(limit in 1..50) { "Title limit must be between 1 and 50" }
        databaseFiles().flatMap { file ->
            runCatching {
                val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                // Title lookups serve travel guides, places and the subject of an explanation as
                // before; a pack of whole articles by section joins only through article().
                if (database.isArticleSections()) emptyList() else database.titled(match, limit).filter(keep)
            }
                .getOrElse { error ->
                    if (error is OutOfMemoryError) throw error
                    if (error is CancellationException) throw error
                    opened.remove(file.absolutePath)?.close()
                    emptyList()
                }
        }.distinctBy { Triple(it.documentId, it.chunkId, it.source) }.take(limit)
    }

    private fun openOrNull(file: File): KnowledgeDatabase? = runCatching {
        opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
    }.getOrNull()

    /**
     * The mapped packs answer from their coordinate tables first; the city they file the nearest
     * places under then names the guide whose listings (which carry coordinates but no table) are
     * read for the same circle, so a souvlaki taverna the guide recommends joins the map's places.
     */
    override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>): List<NearbyPlace> =
        withContext(ioDispatcher) {
            require(limit in 1..200) { "Nearby limit must be between 1 and 200" }
            fun placesFrom(file: File, read: (KnowledgeDatabase) -> List<NearbyPlace>): List<NearbyPlace> = runCatching {
                read(opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) })
            }.getOrElse { error ->
                if (error is OutOfMemoryError) throw error
                if (error is CancellationException) throw error
                opened.remove(file.absolutePath)?.close()
                emptyList()
            }
            val files = databaseFiles()
            val (mapped, guides) = files.partition { file -> openOrNull(file)?.hasPlacePoints() == true }
            val fromMaps = mapped.flatMap { file -> placesFrom(file) { it.nearbyPlaces(point.lat, point.lon, radiusKm, limit, categories) } }
            val city = VenueLookup.cityOf(fromMaps)
            val fromGuides = if (city == null) emptyList() else guides.flatMap { file ->
                placesFrom(file) { it.nearbyPlaces(point.lat, point.lon, radiusKm, limit, categories, city) }
            }
            (fromMaps + fromGuides).distinctBy { Triple(it.evidence.documentId, it.evidence.chunkId, it.evidence.source) }
                .sortedWith(compareBy({ it.distanceKm }, { it.evidence.chunkId }))
                .take(limit)
        }

    /**
     * The destinations [question] names that any place pack holds listings of, resolved once for
     * the callers that need to know a place was named without a capital letter ("in lyon"); the
     * search itself resolves them per pack (see [DestinationScope]).
     */
    override suspend fun destinations(question: String): List<String> = withContext(ioDispatcher) {
        val candidates = DestinationScope.candidates(question)
        if (candidates.isEmpty()) return@withContext emptyList()
        databaseFiles().flatMap { file ->
            runCatching {
                val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                if (database.isPlaceListing()) DestinationScope.resolve(candidates, database::holdsDestination) else emptyList()
            }.getOrElse { error ->
                if (error is OutOfMemoryError) throw error
                if (error is CancellationException) throw error
                opened.remove(file.absolutePath)?.close()
                emptyList()
            }
        }.distinctBy { it.lowercase() }
    }

    /**
     * Prepares semantic hits ahead of the keyword list for one pack. Vector evidence arrives
     * with POSITIVE cosine scores; the cross-pack merge shifts by raw scores assuming negative-
     * bm25 direction, so passing cosines through would sink them below every keyword hit.
     * Instead the vector block is anchored to a fixed floor (VECTOR_FLOOR): every vector hit
     * scores lower (stronger) than any realistic bm25, preserving vector order inside the
     * pack and letting genuinely semantic evidence lead the merged ranking across packs.
     * Weak vector matches (below the pack's rejectBelow — foreign-domain queries) are dropped
     * and the pack behaves exactly as a keyword-only pack.
     */
    private suspend fun List<Evidence>.withVectorEvidence(
        database: KnowledgeDatabase,
        embedding: PackEmbedding?,
        query: String,
        limit: Int,
    ): Pair<List<Evidence>, Int> {
        if (embedding == null || !supportsVectorLayout(embedding) || !database.hasVectorTable()) return this to 0
        val vector = runCatching { embedLock.withLock { embedForPack(embedding, embedding.queryPrefix + query) } }.getOrNull()
            ?: return this to 0
        if (vector.size != embedding.dim) return this to 0
        val hits = database
            .vectorSearch(vector, embedding.dim, limit)
            .filter { evidence -> evidence.score >= embedding.rejectBelow }
        if (hits.isEmpty()) return this to 0
        return fuseVectorAhead(hits, this) to hits.size
    }

    internal companion object {
        /** Sentinel score anchoring vector hits below (stronger than) any bm25 score. */
        internal const val VECTOR_FLOOR = -100_000.0

        /** A short discovery summary is not a vocabulary index. Search enabled packs with
         * bounded FTS retrieval and let the existing relevance filter judge the passages. */
        internal fun shouldSearchPack(query: String): Boolean = FtsQuery.from(query) != null

        /**
         * A focused vector pack is expensive to scan and harmful outside its documented scope.
         * Use its own summary/examples as a cheap local router. General-domain packs still
         * receive keyword retrieval even when their vectors are skipped.
         */
        internal fun shouldSearchVectors(discovery: PackDiscovery?, query: String, vectorCount: Long? = null): Boolean {
            val queryTerms = FtsQuery.from(query)?.terms.orEmpty().filter { it.length >= 3 }.toSet()
            if (queryTerms.isEmpty()) return false
            // A small index is cheap to scan. Its discovery summary cannot enumerate
            // every synonym; applying a keyword router defeats semantic retrieval.
            if (vectorCount != null && vectorCount in 1..10_000) return true
            if (discovery == null) return true
            val coverage = buildString {
                append(discovery.coverageSummary)
                discovery.exampleQuestions.forEach { append(' '); append(it) }
            }
            val coverageTerms = FtsQuery.from(coverage)?.terms.orEmpty().toSet()
            return queryTerms.any(coverageTerms::contains)
        }

        /**
         * Places vector evidence ahead of keyword evidence for one pack: vector hits are
         * re-scored onto a floor far below any realistic bm25 (preserving their order), and
         * keyword duplicates of those chunks are dropped so the vector copy wins.
         */
        internal fun fuseVectorAhead(hits: List<Evidence>, keyword: List<Evidence>): List<Evidence> {
            val anchored = hits.mapIndexed { rank, evidence ->
                // The cosine that earned this chunk its place is the honest per-source
                // attribution shown under the answer; the anchored score is merge plumbing.
                evidence.copy(
                    score = VECTOR_FLOOR - (hits.size - rank),
                    matchedBy = "concept match ${"%.2f".format(evidence.score)}",
                )
            }
            val seen = hits.map { evidence ->
                Triple(evidence.documentId, evidence.chunkId, evidence.source)
            }.toHashSet()
            return anchored + keyword.filter { evidence ->
                seen.add(Triple(evidence.documentId, evidence.chunkId, evidence.source))
            }
        }

        /** A pack earns merge slots only for passages relevant to the original question. */
        internal fun mergeRelevantEvidence(
            runs: List<List<Evidence>>,
            query: String,
            question: String,
            limit: Int,
        ): List<Evidence> {
            val questionTerms = FtsQuery.from(question)?.terms.orEmpty()
            val expandedTerms = FtsQuery.from(query)?.terms.orEmpty()
                .filterNot(questionTerms::contains)
            // A first-clause topic in one pack may anchor an explicit same-topic
            // passage in another. Establish this bounded context before per-pack
            // merging without changing any pack's existing internal ordering.
            val clauseEligible = if (MultipartEvidence.clauses(question).isNotEmpty())
                EvidenceRelevance.keep(runs.flatten(), questionTerms, expandedTerms, question)
                    .map { Triple(it.documentId, it.chunkId, it.source) }.toSet() else null
            val eligible = runs.map { run ->
                if (clauseEligible == null) EvidenceRelevance.keep(run, questionTerms, expandedTerms, question)
                else run.filter { Triple(it.documentId, it.chunkId, it.source) in clauseEligible }
            }
            val focus = TechnicalRuleFocus.from(question)
            if (focus != null) {
                // Reserve the exact-condition tier before ANY pack or row cutoff. Merely
                // sorting each run would still lose its second rule to another pack's
                // overview when the number of packs exceeds the candidate budget.
                val focused = mergeEvidence(eligible.map { it.filter(focus::matches) }, limit)
                if (focused.size >= limit) return focused
                val remaining = mergeCoveredEvidence(eligible.map { it.filterNot(focus::matches) },
                    question, questionTerms, limit - focused.size)
                return focused + remaining
            }
            if (MultipartEvidence.clauses(question).isNotEmpty()) {
                val anchors = MultipartEvidence.anchors(EvidenceRelevance.clauseGroups(eligible.flatten(), question)).take(limit)
                if (anchors.size == limit) return anchors
                val identities = anchors.map { Triple(it.documentId, it.chunkId, it.source) }.toSet()
                return anchors + mergeCoveredEvidence(eligible.map { run ->
                    run.filterNot { Triple(it.documentId, it.chunkId, it.source) in identities }
                }, question, questionTerms, limit - anchors.size)
            }
            return mergeCoveredEvidence(eligible, question, questionTerms, limit)
        }

        private fun mergeCoveredEvidence(
            eligible: List<List<Evidence>>, question: String, questionTerms: List<String>, limit: Int,
        ): List<Evidence> {
            // Preserve place semantics and two-subject diversity. For ordinary questions,
            // promote only passages covering every original content term (or an accepted
            // concept match); partial lexical overlaps keep their existing ordering. This
            // is retrieval priority, never a claim that the passage answers the question.
            if (VenueLookup.isPlaceLookup(question) || EvidenceRelevance.comparisonTerms(question).isNotEmpty()) {
                return mergeEvidence(eligible, limit)
            }
            val priority = eligible.flatten().associateWith { row ->
                EvidenceRelevance.coversQuestionForMerge(row, questionTerms)
            }
            val prioritized = eligible.map { run -> run.sortedByDescending { priority.getValue(it) } }
                .sortedByDescending { run -> run.firstOrNull()?.let { priority.getValue(it) } ?: false }
            // Apply priority before bounded selection AND after the relative-score merge:
            // ResearchOrchestrator subsequently takes a smaller final source budget.
            return mergeEvidence(prioritized, limit).sortedByDescending { priority.getValue(it) }
        }

        /**
         * Merges per-pack result lists. Documents are deduplicated on (documentId, chunkId,
         * source) — two packs installed from the same corpus keep identical rows only when
         * they genuinely hold the same chunk.
         */
        fun mergeEvidence(runs: List<List<Evidence>>, limit: Int): List<Evidence> {
            val activeRuns = runs.filter { it.isNotEmpty() }
            if (activeRuns.isEmpty()) return emptyList()
            val seen = HashSet<Triple<String, String, String>>()
            fun isFresh(evidence: Evidence) =
                seen.add(Triple(evidence.documentId, evidence.chunkId, evidence.source))
            val shifts = HashMap<Triple<String, String, String>, Double>()
            fun shiftOf(evidence: Evidence) = shifts.getValue(
                Triple(evidence.documentId, evidence.chunkId, evidence.source),
            )
            // Selection and ordering are separate concerns. SELECT: every pack's own top-
            // GUARANTEED_PER_PACK matches enter the candidate set before any fill (pack order,
            // then pack-internal rank), so an encyclopaedia's #2 article cannot be buried
            // under a weak pack's junk by relative shifts alone; remaining slots fill by
            // relative strength. ORDER: the selected evidence sorts by shift ascending, so a
            // pack's guaranteed-but-weak extra still sinks below other packs' strong picks.
            val selected = ArrayList<Evidence>()
            val quota = minOf(GUARANTEED_PER_PACK, limit)
            // SELECT, part 1 — guaranteed share: pack-internal rank round-robin (every pack's
            // best, then every pack's second, …) reserves slots up to the limit BEFORE any
            // shift ordering. An encyclopaedia's #2 article (the Lion entry, relative shift
            // ~10) must not be buried under a weak pack's junk: best-vs-best shifts say
            // nothing about where a pack's second-best belongs, so rank itself earns slots.
            for (rank in 0 until minOf(quota, 2)) {
                for (run in activeRuns) {
                    if (selected.size >= limit) break
                    val evidence = run.getOrNull(rank) ?: continue
                    val key = Triple(evidence.documentId, evidence.chunkId, evidence.source)
                    if (seen.add(key)) {
                        shifts[key] = evidence.score - run.minOf { it.score }
                        selected += evidence
                    }
                }
            }
            // The third guaranteed slot cannot be a free-for-all: three packs x three slots
            // overflows the limit, and pack order alone would let the last pack's title-boosted
            // article lose to a pack listed earlier. Rank 2 enters by pack-RELATIVE strength.
            if (quota >= 3) {
                activeRuns.mapNotNull { run ->
                    run.getOrNull(2)?.let { evidence -> evidence to evidence.score - run.minOf { it.score } }
                }.sortedBy { (_, shift) -> shift }.forEach { (evidence, shift) ->
                    val key = Triple(evidence.documentId, evidence.chunkId, evidence.source)
                    if (selected.size < limit && seen.add(key)) {
                        shifts[key] = shift
                        selected += evidence
                    }
                }
            }
            // SELECT, part 2 — fill: remaining slots by cross-pack RELATIVE strength (score
            // shifted by the pack's best match), so weak packs' leftovers only creep in when
            // they are barely worse than their own top hit.
            // Fill orders GLOBALLY by relative shift: the previous pack-sequential fill let
            // the first-listed pack's leftovers claim every remaining slot before an
            // encyclopaedia's rank-3 title article (boosted, tiny relative shift) could speak.
            activeRuns.flatMap { run ->
                val best = run.minOf { evidence -> evidence.score }
                run.withIndex()
                    .filter { (rank, _) -> rank >= quota }
                    .map { (_, evidence) -> (evidence.score - best) to evidence }
            }.sortedBy { (shift, _) -> shift }.forEach { (shift, evidence) ->
                if (selected.size < limit) {
                    val key = Triple(evidence.documentId, evidence.chunkId, evidence.source)
                    if (seen.add(key)) {
                        shifts[key] = shift
                        selected += evidence
                    }
                }
            }
            // ORDER — the selected evidence sorts purely by relative strength, so a
            // guaranteed-but-weak extra still sinks below everyone's strong picks.
            return selected.sortedBy { evidence -> shiftOf(evidence) }
        }

        private const val GUARANTEED_PER_PACK = 3
        internal const val MAX_PARALLEL_PACKS = 2
    }

    override suspend fun headed(phrase: String, words: List<String>, limit: Int, sections: List<String>): List<Evidence> = withContext(ioDispatcher) {
        val wanted = words.filter(String::isNotBlank)
        if (phrase.isBlank() || wanted.isEmpty()) return@withContext emptyList()
        require(limit in 1..50) { "Title limit must be between 1 and 50" }
        val match = buildString {
            append("{title} : (").append(quoted(phrase)).append(" AND (").append(wanted.joinToString(" OR ", transform = ::quoted)).append(")")
            sections.filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let { append(" AND (").append(it.joinToString(" OR ", transform = ::quoted)).append(")") }
            append(")")
        }
        databaseFiles().flatMap { file ->
            runCatching {
                val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                // Unlike titled(), the pack of whole articles by section is read: the article on how a
                // subject ended ("Dissolution of the Soviet Union") and the section headed with a
                // condition ("Motion sickness — … > Seasickness") are only there.
                if (database.isPlaceListing()) emptyList() else database.titled(match, limit)
            }.getOrElse { error ->
                if (error is OutOfMemoryError) throw error
                if (error is CancellationException) throw error
                opened.remove(file.absolutePath)?.close()
                emptyList()
            }
        }.distinctBy { Triple(it.documentId, it.chunkId, it.source) }.sortedBy { it.title.length }.take(limit)
    }
}

/** Leviathan's own stopwords (third_party/leviathan/src/text.rs), which its query parser removes. */
internal val LEVIATHAN_STOPWORDS = setOf("a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "did", "do", "does", "for",
    "from", "had", "has", "have", "how", "i", "if", "in", "into", "is", "it", "its", "last", "me", "my", "of", "on", "or", "our", "so",
    "that", "the", "their", "then", "there", "this", "time", "to", "was", "we", "were", "what", "when", "where", "which", "who",
    "why", "will", "with", "you")
