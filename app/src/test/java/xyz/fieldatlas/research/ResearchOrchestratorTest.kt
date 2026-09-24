package xyz.fieldatlas.research

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState

class ResearchOrchestratorTest {
    private val evidence = Evidence("doc", "doc:0000", "Title", "Source", "Grounded fact", 1.0)

    @Test fun blankInputCallsNothing() = runBlocking {
        var retrievalCalls = 0
        val retriever = Retriever { _, _ -> retrievalCalls++; listOf(evidence) }
        val inference = FakeInferenceGateway(listOf("unused"))
        val events = ResearchOrchestrator(retriever, inference).research(" ").toList()
        assertTrue(events.single() is ResearchEvent.Failed)
        assertEquals(0, retrievalCalls)
        assertEquals(0, inference.generateCalls)
    }

    @Test fun noEvidenceFallsBackToUncitedOfflineModelAnswer() = runBlocking {
        val inference = FakeInferenceGateway(listOf("General offline answer"))
        val events = ResearchOrchestrator(Retriever { _, _ -> emptyList() }, inference)
            .research("Question").toList()
        assertEquals(listOf("General offline answer"), events.filterIsInstance<ResearchEvent.Token>().map { it.text })
        assertTrue(events.none { it is ResearchEvent.Sources })
        val metrics = events.last() as ResearchEvent.Complete
        assertTrue(metrics.metrics.citedSourceIds.isEmpty())
        assertFalse(metrics.metrics.hasUnmappedCitation)
        assertEquals(1, inference.generateCalls)
    }

    @Test fun streamsSourcesTokensAndCitationMetrics() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Answer ", "[S1]", " and [S9]"))
        var tick = 0L
        val events = ResearchOrchestrator(
            Retriever { _, _ -> listOf(evidence) },
            inference,
            monotonicMillis = { tick.also { tick += 10 } },
        ).research("Explain", maxOutputTokens = 64).toList()

        assertTrue(events[0] is ResearchEvent.Searching)
        assertTrue(events[1] is ResearchEvent.Sources)
        assertEquals(listOf("Answer ", "[S1]", " and [S9]"), events.filterIsInstance<ResearchEvent.Token>().map { it.text })
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(3, metrics.generatedTokenCount)
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertTrue(metrics.hasUnmappedCitation)
        assertTrue(metrics.timeToFirstTokenMillis != null)
    }

    @Test fun answerWithoutMarkersReportsNoCitedSources() = runBlocking {
        val events = ResearchOrchestrator(
            Retriever { _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("uncited answer")),
        ).research("Explain").toList()
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertTrue(metrics.citedSourceIds.isEmpty())
        assertFalse(metrics.hasUnmappedCitation)
    }

    @Test fun cancellationStopsTokenFlow() = runBlocking {
        var cancelled = false
        val gateway = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
                try {
                    repeat(100) { emit("token-$it") }
                } finally {
                    cancelled = true
                }
            }
            override suspend fun unload() = Unit
        }
        ResearchOrchestrator(Retriever { _, _ -> listOf(evidence) }, gateway)
            .research("Explain").take(3).toList()
        assertTrue(cancelled)
    }

    @Test fun concurrentResearchAndBenchmarkRunsAreRejected() = runBlocking {
        val gateway = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
                emit("partial")
                awaitCancellation()
            }
            override suspend fun unload() = Unit
        }
        val orchestrator = ResearchOrchestrator(Retriever { _, _ -> listOf(evidence) }, gateway)
        val first = launch { orchestrator.research("First").collect {} }
        yield()
        val second = orchestrator.research("Second").toList()
        assertEquals("Another local research run is active", (second.single() as ResearchEvent.Failed).message)
        first.cancel()
    }
}
