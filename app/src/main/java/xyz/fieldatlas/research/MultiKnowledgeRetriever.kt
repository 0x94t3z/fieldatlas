package xyz.fieldatlas.research

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.assets.PackEmbedding

/**
 * Searches every enabled knowledge pack and merges the results into one ranking.
 *
 * Each pack carries its own FTS5 database. BM25 scores are negative and more negative means
 * a stronger match, but the raw scale is corpus-dependent (vocabulary size, document length,
 * how common the query terms happen to be in that pack), so a strong match in an encyclopaedia
 * can look weaker than a weak match in a topical library. The merge therefore ranks by
 * RELATIVE strength: every score is shifted by its pack's best (strongest) match, and the
 * merged list sorts those shifts ascending. The globally best evidence each pack can offer
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
    private val embed: suspend (String) -> FloatArray? = { null },
) : Retriever {
    private val opened = ConcurrentHashMap<String, KnowledgeDatabase>()

    override suspend fun search(
        query: String,
        limit: Int,
        onProgress: suspend (SearchProgress) -> Unit,
    ): List<Evidence> = withContext(ioDispatcher) {
        require(limit in 1..50) { "Evidence limit must be between 1 and 50" }
        if (FtsQuery.from(query) == null) return@withContext emptyList()
        val files = databaseFiles()
        val embeddings = runCatching(packEmbeddings).getOrDefault(emptyList())
        val total = files.size.coerceAtLeast(1)
        var vectorMatches = 0
        val runs = files.mapIndexed { index, file ->
            runCatching {
                val database = opened.computeIfAbsent(file.absolutePath) { path -> open(File(path)) }
                val keyword = FtsRetriever(database).search(query, limit) { inner ->
                    onProgress(SearchProgress((index + inner.fraction.coerceIn(0.0, 1.0)) / total, vectorMatches))
                }
                val (run, hits) = keyword.withVectorEvidence(database, embeddings.getOrNull(index), query, limit)
                vectorMatches += hits
                run
            }.getOrElse { error ->
                if (error is OutOfMemoryError) throw error
                opened.remove(file.absolutePath)?.close()
                onProgress(SearchProgress((index + 1.0) / total, vectorMatches))
                emptyList()
            }
        }
        onProgress(SearchProgress(1.0, vectorMatches))
        mergeEvidence(runs, limit)
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
        if (embedding == null || !database.hasVectorTable()) return this to 0
        val vector = runCatching { embed(embedding.queryPrefix + query) }.getOrNull()
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
        /**
         * Merges per-pack result lists. Documents are deduplicated on (documentId, chunkId,
         * source) — two packs installed from the same corpus keep identical rows only when
         * they genuinely hold the same chunk.
         */
        fun mergeEvidence(runs: List<List<Evidence>>, limit: Int): List<Evidence> {
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
                for (run in runs) {
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
                runs.mapNotNull { run ->
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
            runs.flatMap { run ->
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
    }
}
