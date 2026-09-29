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
    @Test fun sourceOnlyRequestWithoutEvidenceDoesNotGenerateAnUnsupportedAnswer() = runBlocking {
        val retriever = object : Retriever {
            override fun hasEligiblePacks(query: String) = false
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
        }
        val inference = FakeInferenceGateway(listOf("An invented explanation with invented references."))
        val events = ResearchOrchestrator(retriever, inference)
            .research("According to my saved sources, what is the population of Atlantis today?").toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().joinToString { it.text }.contains("saved sources"))
        assertTrue(events.none { it is ResearchEvent.Sources })
    }

    @Test fun mixedGenerationCannotGiveModelClaimsClickableSourceLinks() = runBlocking {
        val fact = Evidence("d", "c", "Mitosis", "Local", "Mitosis produces two cells.", -1.0)
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) },
            FakeInferenceGateway(listOf("Model explanation\nGeneral fact [S1].\n", "From saved sources\n\"Mitosis produces two cells.\" [S1]")))
            .research("Explain mitosis").toList()
        val answer = StringBuilder()
        events.filterIsInstance<ResearchEvent.Token>().forEach {
            if (it.replace) answer.clear()
            answer.append(it.text)
        }
        assertTrue(answer.contains("not verified against saved sources"))
        assertFalse(answer.contains("General fact [S1]"))
        assertTrue(answer.contains("\"Mitosis produces two cells.\" [S1]"))
        assertEquals(setOf("S1"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
        assertTrue((events.last() as ResearchEvent.Complete).metrics.hasUnmappedCitation)
    }

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
        assertEquals("## Model explanation—not verified against saved sources\n\nGeneral offline answer",
            events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertTrue(events.none { it is ResearchEvent.Sources })
        val metrics = events.last() as ResearchEvent.Complete
        assertTrue(metrics.metrics.citedSourceIds.isEmpty())
        assertFalse(metrics.metrics.hasUnmappedCitation)
        assertEquals(2, inference.generateCalls)
    }

    @Test fun outOfScopeCollectionsSkipRedundantKeywordPlanning() = runBlocking {
        val inference = FakeInferenceGateway(listOf("General offline answer"))
        var searches = 0
        val retriever = object : Retriever {
            override fun hasEligiblePacks(query: String) = false
            override suspend fun search(
                query: String,
                limit: Int,
                onProgress: suspend (SearchProgress) -> Unit,
            ): List<Evidence> {
                searches++
                return emptyList()
            }
        }
        val events = ResearchOrchestrator(retriever, inference).research("Why do hemispheres have opposite seasons?").toList()
        assertEquals(1, searches)
        assertEquals(1, inference.generateCalls)
        assertTrue(events.none { it is ResearchEvent.Planning })
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun modelKeywordsDriveTheFirstRetrievalAndDropFillerWords() = runBlocking {
        val queries = mutableListOf<String>()
        val retriever = Retriever { query, _, _ ->
            queries += query
            if (query.contains("infection")) listOf(evidence.copy(text = "Viruses are infectious agents that reproduce inside host cells.")) else emptyList()
        }
        val events = ResearchOrchestrator(
            retriever,
            FakeInferenceGateway(listOf("virus, viral, infection")),
        ).research("Tell me about viruses").toList()
        assertTrue(events.any { it is ResearchEvent.Sources })
        assertEquals(2, queries.size)
        assertTrue(queries[1].contains("virus"))
        assertTrue(queries[1].contains("infection"))
        assertFalse(queries[1].contains("tell"))
    }

    @Test fun strongRawQuestionHitSkipsKeywordPlanning() = runBlocking {
        val queries = mutableListOf<String>()
        val retriever = Retriever { query, _, _ ->
            queries += query
            if (query.lowercase().contains("grounded")) listOf(evidence.copy(text = "Grounded question fact")) else emptyList()
        }
        val events = ResearchOrchestrator(
            retriever,
            FakeInferenceGateway(listOf("fabricated nonsense")),
        ).research("Grounded question").toList()
        assertTrue(events.any { it is ResearchEvent.Sources })
        // A direct raw hit avoids the slow planner entirely, so fabricated model keywords never
        // get a chance to dilute the user's own terms.
        assertEquals(1, queries.size)
        assertTrue(queries[0].lowercase().contains("grounded"))
        assertFalse(queries[0].contains("fabricated"))
    }

    @Test fun relevantSourceSurvivesAfterUnrelatedPackFillsTheFirstPage() = runBlocking {
        val unrelated = List(8) { index ->
            evidence.copy(
                documentId = "biology-$index",
                chunkId = "biology-$index:0000",
                text = "Earth's geomagnetic field changes over seasons",
                matchedBy = "keyword: earth, seasons",
            )
        }
        val relevant = evidence.copy(
            documentId = "seasons",
            chunkId = "seasons:0000",
            text = "Earth's axial tilt gives opposite hemispheres different seasons",
            matchedBy = "keyword: earth, hemispheres, opposite, seasons",
        )
        var requestedLimit = 0
        val retriever = Retriever { _, limit, _ ->
            requestedLimit = limit
            (unrelated + relevant).take(limit)
        }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("[S1]")))
            .research("Why do Earth's hemispheres have opposite seasons?").toList()
        assertEquals(24, requestedLimit)
        assertEquals(listOf("seasons"), events.filterIsInstance<ResearchEvent.Sources>()
            .single().evidence.map { it.documentId })
    }

    @Test fun streamsSourcesTokensAndCitationMetrics() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Answer ", "[S1]", " and [S9]"))
        var tick = 0L
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
            inference,
            monotonicMillis = { tick.also { tick += 10 } },
        ).research("Explain", maxOutputTokens = 64).toList()

        assertTrue(events[0] is ResearchEvent.Searching)
        assertTrue(events.filterIsInstance<ResearchEvent.Searching>().isNotEmpty())
        assertTrue(events[events.indexOfFirst { it is ResearchEvent.Sources } - 1] is ResearchEvent.Searching)
        assertEquals("## Model explanation—not verified against saved sources\n\nAnswer and",
            events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().drop(1).all { it.replace })
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(3, metrics.generatedTokenCount)
        assertEquals(emptySet<String>(), metrics.citedSourceIds)
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

    @Test fun normalizedNumericCitationCountsAsCitedSource() = runBlocking {
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("From saved sources\n\"Grounded fact\" [1]\nInvalid [S#].")),
        ).research("Explain").toList()
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertTrue(metrics.hasUnmappedCitation)
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
            .research("Explain").take(4).toList()
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
