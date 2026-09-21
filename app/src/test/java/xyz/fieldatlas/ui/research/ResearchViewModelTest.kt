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
        val viewModel = viewModel(Retriever { _, _ -> listOf(evidence) }, inference)
        viewModel.updateQuestion("  ")
        viewModel.submit()
        assertEquals(ResearchPhase.Idle, viewModel.uiState.value.phase)
        assertEquals(0, inference.generateCalls)
    }

    @Test fun concurrentSubmitIsRejected() {
        var retrievalCalls = 0
        val retriever = Retriever { _, _ -> retrievalCalls++; awaitCancellation() }
        val viewModel = viewModel(retriever, FakeInferenceGateway())
        viewModel.updateQuestion("Question")
        viewModel.submit()
        viewModel.submit()
        assertEquals(1, retrievalCalls)
        assertEquals(ResearchPhase.Searching, viewModel.uiState.value.phase)
    }

    @Test fun sourceIdsAndTokensArePreserved() {
        val viewModel = viewModel(
            Retriever { _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("Grounded ", "answer [S1]")),
        )
        viewModel.updateQuestion("Explain")
        viewModel.submit()
        val state = viewModel.uiState.value
        assertEquals("Grounded answer [S1]", state.answer)
        assertEquals("doc:0000", state.sources.single().chunkId)
        assertEquals(ResearchPhase.Complete, state.phase)
    }

    @Test fun modelThinkingIsNeverPresentedAsTheResearchAnswer() {
        val viewModel = viewModel(
            Retriever { _, _ -> listOf(evidence) },
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
            override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
                try {
                    emit("first")
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            override suspend fun unload() = Unit
        }
        val viewModel = viewModel(Retriever { _, _ -> listOf(evidence) }, gateway)
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
            Retriever { _, _ -> throw IOException("broken index") },
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
            Retriever { _, _ -> listOf(evidence) },
            FakeInferenceGateway(listOf("answer [S1]")),
            handle,
        )
        first.updateQuestion("Persist me")
        first.submit()
        assertFalse(first.uiState.value.answer.isBlank())

        val recreated = viewModel(Retriever { _, _ -> emptyList() }, FakeInferenceGateway(), handle)
        assertEquals("Persist me", recreated.uiState.value.question)
        assertTrue(recreated.uiState.value.answer.isBlank())
        assertTrue(recreated.uiState.value.sources.isEmpty())
        assertEquals(ResearchPhase.Idle, recreated.uiState.value.phase)
    }

    private fun viewModel(
        retriever: Retriever,
        inference: InferenceGateway,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = ResearchViewModel(
        savedStateHandle = handle,
        orchestrator = ResearchOrchestrator(retriever, inference),
        launchScope = scope,
    )
}
