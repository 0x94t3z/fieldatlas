package xyz.fieldatlas.research

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import xyz.fieldatlas.inference.InferenceGateway

class ResearchOrchestrator(
    private val retriever: Retriever,
    private val inference: InferenceGateway,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val activeRun = AtomicBoolean(false)

    fun research(
        question: String,
        resultLimit: Int = 8,
        contextTokenBudget: Int = 2_048,
        maxOutputTokens: Int = 512,
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
            emit(ResearchEvent.Searching(question))
            val evidence = retriever.search(question, resultLimit)
            val retrievalFinishedAt = monotonicMillis()
            val packed = if (evidence.isEmpty()) {
                PromptBuilder.buildModelOnly(question)
            } else {
                PromptBuilder.build(question, evidence, contextTokenBudget)
            }
            if (packed.sources.isEmpty()) {
                if (evidence.isNotEmpty()) {
                    emit(ResearchEvent.InsufficientEvidence("The context budget could not fit any evidence"))
                    return@flow
                }
            } else {
                emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
            }

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
    }
}
