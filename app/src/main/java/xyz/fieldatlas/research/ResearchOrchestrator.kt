package xyz.fieldatlas.research

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import xyz.fieldatlas.inference.InferenceGateway

class ResearchOrchestrator(
    private val retriever: Retriever,
    private val inference: InferenceGateway,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    /** Prefill progress of the loaded engine, surfaced for the activity line. */
    val promptProgress: StateFlow<PromptProgress?> get() = inference.promptProgress

    /**
     * Live retrieval progress (0.0..1.0) for the activity line. Deliberately a StateFlow and
     * not a flow of events: the retriever reports milestones from inside its withContext(IO)
     * coroutine, and emitting flow values across coroutine contexts is illegal (it crashed
     * real searches on-device with "Flow exception transparency is violated") while a
     * StateFlow value may be published from any thread.
     */
    private val _searchProgress = MutableStateFlow(0.0)
    val searchProgress: StateFlow<Double> = _searchProgress

    private val activeRun = AtomicBoolean(false)

    fun research(
        question: String,
        resultLimit: Int = 8,
        contextTokenBudget: Int = 2_048,
        maxOutputTokens: Int = 1_536,
    ): Flow<ResearchEvent> = flow {
        if (question.isBlank()) {
            emit(ResearchEvent.Failed("Question must not be blank"))
            return@flow
        }
        require(resultLimit in 1..50) { "resultLimit must be between 1 and 50" }
        require(maxOutputTokens > 0) { "maxOutputTokens must be positive" }
        if (!activeRun.compareAndSet(false, true)) {
            emit(ResearchEvent.Failed("Another local research run is active"))
            return@flow
        }
        try {
            val startedAt = monotonicMillis()
            _searchProgress.value = 0.0
            emit(ResearchEvent.Planning(question))
            // Query understanding runs before retrieval: the loaded model turns "Tell me about
            // viruses" into the terms documents actually use (virus, viral, infection), because
            // FTS5 AND-matching over the user's raw phrasing mostly matches filler words. The
            // raw question remains an automatic second attempt whenever the keyword search is
            // empty or the model turn fails, so a weak model turn can never lose a retrieval the
            // old lexical path would have found.
            // Raw pass: two planner turns with different sampler seeds; the union of their
            // terms is strictly better recall than one sample, and keyword turns stay a small
            // slice of query time (see KEYWORD_SEEDS).
            val terms = LinkedHashSet<String>()
            for (seed in KEYWORD_SEEDS) {
                try {
                    val raw = StringBuilder()
                    inference
                        .generate(
                            QueryExpansion.prompt(question),
                            QueryExpansion.GENERATION_BUDGET,
                            QueryExpansion.SYSTEM_PROMPT,
                            seed = seed,
                        )
                        .collect { token -> raw.append(token) }
                    terms += QueryExpansion.parse(raw.toString())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // keep whatever the earlier pass produced; empties fall back to the question
                }
                if (terms.size >= QueryExpansion.MAX_TERMS) break
            }
            val keywords = terms.take(QueryExpansion.MAX_TERMS).toList()
            if (keywords.isNotEmpty()) emit(ResearchEvent.Keywords(keywords))
            var evidence = emptyList<Evidence>()
            emit(ResearchEvent.Searching(question))
            // Keyword and question terms retrieve together: the planner's synonyms ("myocardial
            // infarction") and the question's own entities ("Singapore") compete on equal footing
            // in the retriever's rarest-term conjunctions.
            val questionTerms = FtsQuery.from(question)?.terms.orEmpty()
            // Question terms LEAD the merged query: they are the user's own words for the
            // entities in play, while planner keywords are guesses. Term order is an importance
            // signal downstream (coverage pass order, title-boost tie-breaks), so the question
            // must speak first - planner synonyms fill in breadth behind it.
            val merged = (questionTerms + keywords).distinct()
            if (merged.isNotEmpty()) {
                evidence = retriever.search(merged.joinToString(" "), resultLimit) { fraction ->
                    _searchProgress.value = fraction
                }
            }
            if (evidence.isEmpty()) {
                evidence = retriever.search(question, resultLimit)
            }
            val retrievalFinishedAt = monotonicMillis()
            if (evidence.isEmpty()) {
                emit(ResearchEvent.InsufficientEvidence("No matching offline evidence was found"))
                return@flow
            }
            val packed = PromptBuilder.build(question, evidence, contextTokenBudget)
            if (packed.sources.isEmpty()) {
                emit(ResearchEvent.InsufficientEvidence("The context budget could not fit any evidence"))
                return@flow
            }
            emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
            // Field Atlas: an empty token flips the UI into the "writing" phase before the
            // first real token arrives, so prefill progress can show as tokens read/written.
            emit(ResearchEvent.Token(""))

            var firstTokenAt: Long? = null
            var generatedTokenCount = 0
            val output = StringBuilder()
            inference.generate(packed.prompt, maxOutputTokens).collect { token ->
                if (firstTokenAt == null) firstTokenAt = monotonicMillis()
                generatedTokenCount++
                output.append(token)
                emit(ResearchEvent.Token(token))
            }
            val finishedAt = monotonicMillis()
            val markers = CITATION_PATTERN.findAll(output).map { "S${it.groupValues[1]}" }.toSet()
            val mapped = packed.sources.map { it.citationId }.toSet()
            emit(
                ResearchEvent.Complete(
                    ResearchMetrics(
                        retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                        timeToFirstTokenMillis = firstTokenAt?.let { elapsed(startedAt, it) },
                        totalMillis = elapsed(startedAt, finishedAt),
                        generatedTokenCount = generatedTokenCount,
                        citedSourceIds = markers intersect mapped,
                        hasUnmappedCitation = (markers - mapped).isNotEmpty(),
                    ),
                ),
            )
        } finally {
            activeRun.set(false)
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(ResearchEvent.Failed(error.message ?: "Research failed"))
    }

    private fun elapsed(start: Long, end: Long): Long = (end - start).coerceAtLeast(0)

    private companion object {
        val CITATION_PATTERN = Regex("\\[S([1-9][0-9]*)]")

        /** Two distinct planner samples; the union of their keywords drives retrieval. */
        val KEYWORD_SEEDS = intArrayOf(17, 89)
    }
}
