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
    @Test fun explanationPromptCannotUseTextOmittedFromTheActualPrompt() = runBlocking {
        val omitted = "The archive was opened in 1920."
        val mechanism = "A boat floats because the upward buoyant force balances its weight."
        val source = Evidence("boat", "boat:0", "Boat — Principles", "Local",
            (omitted + " ").repeat(30) + "\n\n" + mechanism, -1.0)
        // A yes/no lookup keeps the generated attribution path. Ordinary how/why
        // explanations now finish from copied originals (covered separately below).
        val question = "Do boats float?"
        val packed = PromptBuilder.build(question, listOf(source), 2048)
        assertFalse(packed.prompt.contains(omitted))
        assertEquals(source, packed.sources.single().evidence)
        assertTrue(packed.sources.single().excerpt.contains(mechanism))
        val model = FakeInferenceGateway(listOf("Upward buoyancy balances the boat's weight [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(source) }, model)
            .research(question).toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer.contains("\"$omitted\" [S1]"))
        assertTrue(answer.contains("Upward buoyancy balances the boat's weight [S1]"))
        assertFalse(model.prompts.last().first.contains(omitted))
        assertEquals(source, events.filterIsInstance<ResearchEvent.Sources>().single().evidence.single())
        assertFalse((events.last() as ResearchEvent.Complete).metrics.hasUnmappedCitation)
    }

    @Test fun missingAttachmentAndLiveLocationSkipBothRetrievalAndGeneration() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Must not be generated"))
        val retriever = Retriever { _, _, _ -> error("Missing input must not search unrelated collections") }
        for (question in listOf("According to my attached report, what were my blood test results?",
            "Which roads near me are closed right now?")) {
            val events = ResearchOrchestrator(retriever, inference).research(question).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            assertTrue(events.none { it is ResearchEvent.Sources || it is ResearchEvent.Planning })
            assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
        }
        assertEquals(0, inference.generateCalls)
    }
    @Test fun shortCausalQuestionRejectsQueryDriftWithoutExtraModelPlanning() = runBlocking {
        var searches = 0
        val unrelated = Evidence("study", "study:0", "Drug delivery", "Saved study",
            "The floating duration of tablets in water increased with the treatment.", -1.0)
        val inference = FakeInferenceGateway(listOf("Ice is less dense than liquid water."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> searches++; listOf(unrelated) }, inference)
            .research("Explain why ice floats on water.").toList()
        assertEquals(1, searches)
        assertEquals(1, inference.generateCalls)
        assertTrue(events.none { it is ResearchEvent.Planning || it is ResearchEvent.Sources })
        assertTrue(events.last() is ResearchEvent.Complete)
    }

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

    @Test fun generatedExplanationKeepsItsCitationsWithoutAnExcerptAppendix() = runBlocking {
        val fact = Evidence("d", "c", "Mitosis", "Local", "Mitosis produces two cells.", -1.0)
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) },
            FakeInferenceGateway(listOf("Mitosis results in two cells [S1].")))
            .research("Does mitosis produce two cells?").toList()
        val answer = StringBuilder()
        events.filterIsInstance<ResearchEvent.Token>().forEach {
            if (it.replace) answer.clear()
            answer.append(it.text)
        }
        assertEquals("Mitosis results in two cells [S1].", answer.toString())
        assertEquals(setOf("S1"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
        assertFalse((events.last() as ResearchEvent.Complete).metrics.hasUnmappedCitation)
    }

    @Test fun sourceBackedExplanationGeneratesWithConditionsAndOriginalsInThePrompt() = runBlocking {
        val fact = Evidence("luma", "luma:0", "Luma projector — Test", "fixture://luma",
            "The Luma projector produces a beam through its emitter. The test establishes operation only after dry storage. " +
                "It does not establish whether wet storage is safe or how long it could last.", -1.0)
        val explanation = "The emitter produces the beam. The test covers dry storage; wet-storage safety is unknown [S1]."
        val plain = "How does the Luma projector produce a beam?"
        for (question in listOf(plain, "According to the saved sources, $plain")) {
            val inference = FakeInferenceGateway(listOf(explanation))
            val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(fact) }, inference)
                .research(question, contextTokenBudget = 8192).toList()
            val answer = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
            val decoded = xyz.fieldatlas.text.MarkdownLiteral.decode(answer)
            assertEquals(question, 1, inference.generateCalls)
            assertTrue(inference.prompts.last().first.contains(fact.text))
            assertFalse(decoded.contains("Operation only occurs"))
            assertFalse(decoded.contains("always dangerous"))
            assertEquals(explanation, decoded)
            assertEquals(listOf(fact), events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
            val metrics = (events.last() as ResearchEvent.Complete).metrics
            assertEquals(1, metrics.generatedTokenCount)
            assertEquals(setOf("S1"), metrics.citedSourceIds)
            assertFalse(metrics.hasUnmappedCitation)
        }
    }

    @Test fun inventedCitationFailsWithoutDisplayingDraftOrCopiedAppendix() = runBlocking {
        val question = "How does the Luma projector produce a beam, and what limits wet storage?"
        val first = Evidence("luma", "luma:0", "Luma projector — Beam", "fixture://luma",
            "The Luma projector produces a beam through its emitter.", -1.0)
        val second = Evidence("luma", "luma:1", "Luma projector — Storage", "fixture://luma",
            "Wet storage limits remain unknown. Only dry storage was evaluated; wet storage safety is not established.", -0.5)
        var packed: PackedPrompt? = null
        val inference = FakeInferenceGateway(listOf("Invented universal wet-storage rule [S7]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(first, second) }, inference,
            onEvidencePacked = { packed = it }).research(question, contextTokenBudget = 8192).toList()
        val sources = checkNotNull(packed).sources
        assertEquals(2, sources.size)
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        // The invented rule is never shown; the original excerpts are, labelled as excerpts.
        assertFalse(answer.contains("universal wet-storage rule"))
        assertTrue(answer, answer.contains("wet storage safety is not established") && answer.contains("not a generated explanation"))
        assertEquals(sources.map { it.evidence }, events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
        assertEquals(2, inference.generateCalls) // initial draft and one bounded repair
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        // Only the copied excerpts are cited; the invented [S7] appears nowhere.
        assertEquals(setOf("S1", "S2"), metrics.citedSourceIds)
        assertFalse(metrics.hasUnmappedCitation)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().none { "S7" in it.text })
    }

    @Test fun unknownPrivateRecordsFinishWithoutSearchingOrGenerating() = runBlocking {
        val inference = FakeInferenceGateway(listOf("An invented private diagnosis [S1]."))
        val retriever = Retriever { _, _, _ -> error("No supplied private record: must not search") }
        for (question in listOf("What diagnosis did an unnamed private person receive last week?",
            "What is the unnamed person's account balance?")) {
            val events = ResearchOrchestrator(retriever, inference).research(question).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            assertTrue(events.none { it is ResearchEvent.Searching || it is ResearchEvent.Planning || it is ResearchEvent.Sources })
            val answer = events.filterIsInstance<ResearchEvent.Token>().single().text
            assertTrue(answer.contains("private records"))
            assertTrue(answer.contains("permission to use"))
            val metrics = (events.last() as ResearchEvent.Complete).metrics
            assertEquals(0L, metrics.retrievalMillis)
            assertEquals(0, metrics.generatedTokenCount)
            assertTrue(metrics.citedSourceIds.isEmpty())
            assertFalse(metrics.hasUnmappedCitation)
        }
        assertEquals(0, inference.generateCalls)
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
        // Both drafts cite a source that does not exist; neither is shown. The answer falls back to a
        // model explanation labelled as unverified, beside the saved excerpt.
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, answer.startsWith("## Model explanation—not verified against saved sources") && "[S9]" !in answer)
        // The invalid drafts were never streamed: no token ever carried the invented [S9].
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().none { "[S9]" in it.text })
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        // The metric still records that the model's raw text held an invalid citation, hidden from the answer.
        assertTrue(metrics.hasUnmappedCitation)
        assertTrue(metrics.timeToFirstTokenMillis != null)
    }

    @Test fun answerWithoutMarkersDoesNotManufactureCitationsOrCopiedExcerpts() = runBlocking {
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("uncited answer")),
        ).research("Explain").toList()
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer.contains("uncited answer [S1]"))
        assertTrue(answer, answer.startsWith("## Model explanation—not verified against saved sources"))
        assertFalse(metrics.hasUnmappedCitation)
    }

    @Test fun normalizedNumericCitationCountsAsCitedSource() = runBlocking {
        val events = ResearchOrchestrator(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("Grounded fact [1].")),
        ).research("Explain").toList()
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertFalse(metrics.hasUnmappedCitation)
    }

    @Test fun cancellationStopsTokenFlow() = runBlocking {
        var cancelled = false
        val gateway = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
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
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
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

    private fun knowledgeModel(prompts: MutableList<String>, reviews: MutableList<String>, reply: String, knows: Boolean = false) = object : InferenceGateway {
        override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
        override val knowledgeFirst = knows
        override suspend fun load(modelPath: String, systemPrompt: String) = Unit
        override suspend fun unload() = Unit
        override fun reviewEvidence(prompt: String, savedPrefixChars: Int): Flow<String> = flow { reviews += prompt; emit("SUPPORTED") }
        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
            prompts += prompt
            emit(reply)
        }
    }

    @Test fun knowledgeFirstAnswersFromTheModelInOnePassAndListsThePassages() = runBlocking {
        val boats = Evidence("boat", "boat:0", "Boat — Buoyancy", "Local",
            "A boat floats because it displaces a weight of water equal to its own weight.", -1.0)
        val knowledge = "A boat floats because the water it pushes aside weighs as much as the boat does."
        val prompts = mutableListOf<String>(); val reviews = mutableListOf<String>()
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(boats) }, knowledgeModel(prompts, reviews, knowledge), modelFirstNotes = 0)
            .research("Why do boats float?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        // One generation from what the model knows: no cited draft, no review, and no passage in the prompt.
        assertEquals(prompts.toString(), 1, prompts.size)
        assertTrue(prompts.single(), "from what you know" in prompts.single() && "displaces a weight" !in prompts.single())
        assertTrue(reviews.isEmpty())
        assertTrue(answer, answer.startsWith("## " + AnswerText.MODEL_LABEL) && knowledge in answer)
        // The passages found stay under the answer for checking.
        assertTrue(answer, "Saved excerpts" in answer && "displaces a weight" in answer)
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().last().evidence.isNotEmpty())
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun aModelChosenToReasonThinksBeforeAKnowledgeFirstAnswer() = runBlocking {
        val boats = Evidence("boat", "boat:0", "Boat — Buoyancy", "Local",
            "A boat floats because it displaces a weight of water equal to its own weight.", -1.0)
        val thoughts = mutableListOf<Pair<String, Int>>(); val plain = mutableListOf<String>()
        val model = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override val thinkingBudget = 2048
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> =
                flow { plain += prompt; emit("unused") }
            override fun think(prompt: String, maxTokens: Int, savedPrefixChars: Int): Flow<String> = flow {
                thoughts += prompt to maxTokens
                emit("<|channel>thought\nWater pushed aside weighs as much as the boat.<channel|>")
                emit("A boat floats because the water it displaces weighs as much as it does.")
            }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(boats) }, model, modelFirstNotes = 0)
            .research("Why do boats float?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertEquals(1, thoughts.size)
        assertTrue(plain.toString(), plain.isEmpty())
        // The reasoning gets its own budget on top of the answer's, and the prompt does not switch thinking off.
        assertTrue(thoughts.single().second > 2048)
        assertFalse(thoughts.single().first, "/no_think" in thoughts.single().first)
        assertTrue(answer, "the water it displaces" in answer && "pushed aside" !in answer)
    }

    @Test fun theLoadedModelDecidesWhetherAnswersComeFromItsKnowledge() = runBlocking {
        val boats = Evidence("boat", "boat:0", "Boat — Buoyancy", "Local",
            "A boat floats because it displaces a weight of water equal to its own weight.", -1.0)
        val knowing = mutableListOf<String>()
        ResearchOrchestrator(Retriever { _, _, _ -> listOf(boats) }, knowledgeModel(knowing, mutableListOf(), "It floats.", knows = true))
            .research("Why do boats float?").toList()
        assertTrue(knowing.toString(), knowing.size == 1 && "from what you know" in knowing.single())
        // A model that knows too little (the 2B) keeps answering from the passages.
        val reading = mutableListOf<String>()
        ResearchOrchestrator(Retriever { _, _, _ -> listOf(boats) }, knowledgeModel(reading, mutableListOf(), "It floats [S1]."))
            .research("Why do boats float?").toList()
        assertTrue(reading.toString(), reading.isNotEmpty() && reading.none { "from what you know" in it })
    }

    @Test fun knowledgeFirstLeavesBusinessesAndCalculationsToTheirOwnRoutes() = runBlocking {
        val prompts = mutableListOf<String>(); val reviews = mutableListOf<String>()
        val model = knowledgeModel(prompts, reviews, "Try Café Vegano on the main square.")
        val listing = Evidence("osm-place-n1", "osm-place-n1:0000", "Valencia — Green Leaf", "https://www.openstreetmap.org/node/1",
            "Destination: Valencia\nCategory: Eat\nPlace: Green Leaf\nType: restaurant\nVegan: vegan options", -1.0)
        ResearchOrchestrator(Retriever { _, _, _ -> listOf(listing) }, model, modelFirstNotes = 0)
            .research("Any good vegan restaurants in Valencia?").toList()
        assertTrue(prompts.toString(), prompts.none { "from what you know" in it })
        val calc = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model, modelFirstNotes = 0)
            .research("My car does 30 mpg (US). What is that in L/100 km?").toList()
        assertTrue(calc.filterIsInstance<ResearchEvent.Token>().last().text.contains("7.8"))
        assertTrue(prompts.toString(), prompts.none { "from what you know" in it })
    }

    @Test fun explanationThatOnlySaysTheExcerptsMissIsAskedAgainWithoutThem() = runBlocking {
        val unrelated = Evidence("boat", "boat:0", "Boat — History", "Local",
            "Boats float on rivers and seas. The first boats were dugout canoes, and boats have carried goods for thousands of years.", -1.0)
        val refusal = "The provided excerpts do not explain why boats float."
        val knowledge = "Yes. A boat floats because the water it pushes aside weighs as much as the boat."
        val prompts = mutableListOf<String>()
        val model = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun reviewEvidence(prompt: String, savedPrefixChars: Int): Flow<String> = flow { emit("UNSUPPORTED") }
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
                prompts += prompt
                emit(when { "EVIDENCE:" in prompt -> refusal; "offline knowledge only" in prompt -> knowledge; else -> "boats, float" })
            }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(unrelated) }, model)
            .research("Do boats float?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(prompts.toString(), prompts.any { "EVIDENCE:" in it })
        assertTrue(answer, answer.contains(knowledge))
        assertFalse(answer, answer.contains(refusal))
        assertFalse(answer, answer.contains("Saved excerpts"))
        assertTrue(answer, answer.startsWith("## " + AnswerText.MODEL_LABEL))
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().last().evidence.isEmpty())
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun aSecondRefusalKeepsTheFirstReplyAndItsExcerpts() = runBlocking {
        val unrelated = Evidence("boat", "boat:0", "Boat — History", "Local",
            "Boats float on rivers and seas. The first boats were dugout canoes, and boats have carried goods for thousands of years.", -1.0)
        val refusal = "The provided excerpts do not explain why boats float."
        val model = object : InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun reviewEvidence(prompt: String, savedPrefixChars: Int): Flow<String> = flow { emit("UNSUPPORTED") }
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
                emit(when { "EVIDENCE:" in prompt -> refusal; "offline knowledge only" in prompt -> "No sources cover this, so I cannot answer from the provided text."; else -> "boats, float" })
            }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(unrelated) }, model)
            .research("Do boats float?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, answer.contains(refusal))
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().last().evidence.isNotEmpty())
    }
}
