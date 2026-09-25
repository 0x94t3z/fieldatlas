package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

class ResearchViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val evidence = Evidence("doc", "doc:0000", "Title", "Source", "Fact", 1.0)

    @After fun tearDown() = scope.cancel()

    @Test fun emptySubmitIsIgnored() {
        val inference = FakeInferenceGateway(listOf("unused"))
        val viewModel = viewModel(Retriever { _, _, _ -> listOf(evidence) }, inference)
        viewModel.updateQuestion("  ")
        viewModel.submit()
        assertEquals(ResearchPhase.Idle, viewModel.uiState.value.phase)
        assertEquals(0, inference.generateCalls)
    }

    @Test fun concurrentSubmitIsRejected() {
        var retrievalCalls = 0
        val retriever = Retriever { _, _, _ -> retrievalCalls++; awaitCancellation() }
        val viewModel = viewModel(retriever, FakeInferenceGateway())
        viewModel.updateQuestion("Question")
        viewModel.submit()
        viewModel.submit()
        assertEquals(1, retrievalCalls)
        assertEquals(ResearchPhase.Searching, viewModel.uiState.value.phase)
    }

    @Test fun sourceIdsAndTokensArePreserved() {
        val viewModel = viewModel(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("Grounded ", "answer [S1]")),
        )
        viewModel.updateQuestion("Explain")
        viewModel.submit()
        val state = viewModel.uiState.value
        assertEquals("Grounded answer [S1]", state.answer)
        assertEquals("doc:0000", state.sources.single().chunkId)
        assertEquals(ResearchPhase.Complete, state.phase)
    }

    @Test fun noEvidenceStillCompletesWithOfflineModelAnswer() {
        val viewModel = viewModel(
            Retriever { _, _, _ -> emptyList() },
            FakeInferenceGateway(listOf("Offline model answer")),
        )
        viewModel.updateQuestion("Explain F1")
        viewModel.submit()
        val state = viewModel.uiState.value
        assertEquals("Offline model answer", state.answer)
        assertTrue(state.sources.isEmpty())
        assertEquals(ResearchPhase.Complete, state.phase)
    }

    @Test fun modelThinkingIsNeverPresentedAsTheResearchAnswer() {
        val viewModel = viewModel(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("<thi", "nk>private reasoning", "</th", "ink>\n\nAnswer [S1]")),
        )
        viewModel.updateQuestion("Explain")
        viewModel.submit()
        val state = viewModel.uiState.value
        assertEquals("Answer [S1]", state.answer)
        assertEquals(ResearchPhase.Complete, state.phase)
    }

    @Test fun cancelStopsGenerationJob() {
        var cancelled = false
        val gateway = object : InferenceGateway {
            var calls = 0
            override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> = flow {
                try {
                    if (calls++ < 2) emit("keyword") else {
                        emit("first")
                        awaitCancellation()
                    }
                } finally {
                    cancelled = true
                }
            }
            override suspend fun unload() = Unit
        }
        val viewModel = viewModel(Retriever { _, _, _ -> listOf(evidence) }, gateway)
        viewModel.updateQuestion("Explain")
        viewModel.submit()
        viewModel.cancelResearch()
        assertTrue(cancelled)
        assertEquals(ResearchPhase.Idle, viewModel.uiState.value.phase)
        assertEquals(ResearchCompletion.Cancelled, viewModel.uiState.value.completion)
        assertEquals("first", viewModel.uiState.value.answer)
        assertEquals("doc:0000", viewModel.uiState.value.sources.single().chunkId)
    }

    @Test fun errorsKeepQuestionForRetry() {
        val viewModel = viewModel(
            Retriever { _, _, _ -> throw IOException("broken index") },
            FakeInferenceGateway(),
        )
        viewModel.updateQuestion("Retry me")
        viewModel.submit()
        assertEquals("Retry me", viewModel.uiState.value.question)
        assertEquals(ResearchPhase.Error, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("broken index"))
    }

    @Test fun recreationRestoresOnlyLightweightQuestion() {
        val handle = SavedStateHandle()
        val first = viewModel(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("answer [S1]")),
            handle,
        )
        first.updateQuestion("Persist me")
        first.submit()
        assertFalse(first.uiState.value.answer.isBlank())

        val recreated = viewModel(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(), handle)
        assertEquals("Persist me", recreated.uiState.value.question)
        assertTrue(recreated.uiState.value.answer.isBlank())
        assertTrue(recreated.uiState.value.sources.isEmpty())
        assertEquals(ResearchPhase.Idle, recreated.uiState.value.phase)
    }

    private fun viewModel(
        retriever: Retriever,
        inference: InferenceGateway,
        handle: SavedStateHandle = SavedStateHandle(),
        historyStore: xyz.fieldatlas.research.AnswerHistoryStore? = null,
    ) = ResearchViewModel(
        savedStateHandle = handle,
        orchestrator = ResearchOrchestrator(retriever, inference),
        launchScope = scope,
        historyStore = historyStore,
    )

    @Test fun completedAnswerIsSavedToHistory() {
        val store = xyz.fieldatlas.research.AnswerHistoryStore(
            java.io.File(temporaryDir, "answers.json"),
            synchronousWrites = true,
        )
        val viewModel = viewModel(
            Retriever { _, _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("Grounded ", "answer [S1]")),
            historyStore = store,
        )
        viewModel.updateQuestion("Explain")
        viewModel.submit()
        val saved = store.records.value.single()
        assertEquals("Explain", saved.question)
        assertEquals("Grounded answer [S1]", saved.answer)
        assertEquals(listOf("Title"), saved.sources)
        // Survives a fresh store reading the same file.
        val reopened = xyz.fieldatlas.research.AnswerHistoryStore(java.io.File(temporaryDir, "answers.json"))
        kotlinx.coroutines.runBlocking { reopened.ensureLoaded() }
        assertEquals(saved.question, reopened.records.value.single().question)
    }

    @Test fun cancelledPartialAnswerIsSavedToHistory() {
        val store = xyz.fieldatlas.research.AnswerHistoryStore(
            java.io.File(temporaryDir, "cancelled.json"),
            synchronousWrites = true,
        )
        // A retriever that never answers keeps the run in the Searching phase; cancelling then
        // records nothing (no text was produced) — blank answers must not litter History.
        val viewModel = viewModel(
            Retriever { _, _, _ -> kotlinx.coroutines.awaitCancellation() },
            FakeInferenceGateway(listOf("unused")),
            historyStore = store,
        )
        viewModel.updateQuestion("Long one")
        viewModel.submit()
        viewModel.cancelResearch()
        assertTrue(store.records.value.isEmpty())
    }

    private val temporaryDir: java.io.File by lazy { 
        java.nio.file.Files.createTempDirectory("history").toFile().apply { deleteOnExit() }
    }
}
