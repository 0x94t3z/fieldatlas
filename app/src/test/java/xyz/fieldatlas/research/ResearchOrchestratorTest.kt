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


    @Test fun `search progress publishes on the state flow from any thread`() = runBlocking {
        // The retriever reports milestones from its withContext(IO) worker on device; a StateFlow
        // survives that, a flow emit crashed the whole search. Emit from a foreign thread here to
        // keep the contract honest.
        val orchestrator = ResearchOrchestrator(
            Retriever { _, _, onProgress ->
                // Real device publishes these from withContext(Dispatchers.IO) inside the
                // multi-pack retriever — the exact setting that crashed flow-emit progress.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    onProgress(SearchProgress(0.4)); onProgress(SearchProgress(1.0))
                }
                listOf(evidence)
            },
            FakeInferenceGateway(List(4) { "lion, tiger, weight" }),
        )
        // Milestones conflate on a fast run, so assert what matters: the run survives
        // foreign-context progress publishing (it used to die with a transparency violation)
        // and the flow ends at completion with the last published fraction readable.
        val events = orchestrator.research("what is heavier").toList()
        assertTrue(events.none { it is ResearchEvent.Failed })
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(1.0, orchestrator.searchProgress.value, 0.0)
    }
    @Test fun blankInputCallsNothing() = runBlocking {
        var retrievalCalls = 0
        val retriever = Retriever { _, _, _ -> retrievalCalls++; listOf(evidence) }
        val inference = FakeInferenceGateway(listOf("unused"))
        val events = ResearchOrchestrator(retriever, inference).research(" ").toList()
        assertTrue(events.single() is ResearchEvent.Failed)
        assertEquals(0, retrievalCalls)
        assertEquals(0, inference.generateCalls)
    }

    @Test fun noEvidenceFallsBackToUncitedOfflineModelAnswer() = runBlocking {
        val inference = FakeInferenceGateway(listOf("General offline answer"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research("Question").toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().map { it.text }.contains("General offline answer"))
        assertTrue(events.none { it is ResearchEvent.Sources })
        val metrics = events.last() as ResearchEvent.Complete
        assertTrue(metrics.metrics.citedSourceIds.isEmpty())
        assertFalse(metrics.metrics.hasUnmappedCitation)
        assertEquals(3, inference.generateCalls)
    }

    @Test fun modelKeywordsDriveTheFirstRetrievalAndDropFillerWords() = runBlocking {
        val queries = mutableListOf<String>()
        val retriever = Retriever { query, _, _ ->
            queries += query
            if (query.contains("virus")) listOf(evidence) else emptyList()
        }
        val events = ResearchOrchestrator(
            retriever,
            FakeInferenceGateway(listOf("virus, viral, infection")),
        ).research("Tell me about viruses").toList()
        assertTrue(events.any { it is ResearchEvent.Sources })
        assertEquals(1, queries.size)
        assertTrue(queries[0].contains("virus"))
        assertTrue(queries[0].contains("infection"))
        assertFalse(queries[0].contains("tell"))
    }

    @Test fun fabricatedKeywordsShareTheQueryWithQuestionTerms() = runBlocking {
        val queries = mutableListOf<String>()
        val retriever = Retriever { query, _, _ ->
            queries += query
            if (query.lowercase().contains("grounded")) listOf(evidence) else emptyList()
        }
        val events = ResearchOrchestrator(
            retriever,
            FakeInferenceGateway(listOf("fabricated nonsense")),
        ).research("Grounded question").toList()
        assertTrue(events.any { it is ResearchEvent.Sources })
        // Merged retrieval: invented keywords no longer get a query of their own that can
        // drown the question's real entities — both ride together from the first attempt.
        assertEquals(1, queries.size)
        assertTrue(queries[0].contains("fabricated"))
        assertTrue(queries[0].contains("grounded"))
    }

    @Test fun streamsSourcesTokensAndCitationMetrics() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Answer ", "[S1]", " and [S9]"))
        var tick = 0L
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
            inference,
            monotonicMillis = { tick.also { tick += 10 } },
        ).research("Explain", maxOutputTokens = 64).toList()

        assertTrue(events[0] is ResearchEvent.Planning)
        assertTrue(events.filterIsInstance<ResearchEvent.Searching>().isNotEmpty())
        assertTrue(events[events.indexOfFirst { it is ResearchEvent.Sources } - 1] is ResearchEvent.Searching)
        assertEquals(listOf("Answer ", "[S1]", " and [S9]"),
            events.filterIsInstance<ResearchEvent.Token>().map { it.text }.filter { it.isNotEmpty() })
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(3, metrics.generatedTokenCount)
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertTrue(metrics.hasUnmappedCitation)
        assertTrue(metrics.timeToFirstTokenMillis != null)
    }

    @Test fun answerWithoutMarkersReportsNoCitedSources() = runBlocking {
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
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
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
                try {
                    repeat(100) { emit("token-$it") }
                } finally {
                    cancelled = true
                }
            }
            override suspend fun unload() = Unit
        }
        ResearchOrchestrator(Retriever { _, _, _ -> listOf(evidence) }, gateway)
            .research("Explain").take(3).toList()
        assertTrue(cancelled)
    }

    @Test fun concurrentResearchAndBenchmarkRunsAreRejected() = runBlocking {
        val gateway = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
                emit("partial")
                awaitCancellation()
            }
            override suspend fun unload() = Unit
        }
        val orchestrator = ResearchOrchestrator(Retriever { _, _, _ -> listOf(evidence) }, gateway)
        val first = launch { orchestrator.research("First").collect {} }
        yield()
        val second = orchestrator.research("Second").toList()
        assertEquals("Another local research run is active", (second.single() as ResearchEvent.Failed).message)
        first.cancel()
    }
}
