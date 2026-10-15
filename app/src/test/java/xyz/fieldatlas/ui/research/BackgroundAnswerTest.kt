package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.AnswerHistoryStore
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

/** New topic never throws an answer away: it goes on in the background, and the next question waits its turn. */
class BackgroundAnswerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val evidence = Evidence("doc", "doc:0000", "Hagia Sophia — Overview", "Source", "Hagia Sophia is in Istanbul.", 1.0)
    private val gate = CompletableDeferred<Unit>()
    private var holding = true
    private val history = AnswerHistoryStore(
        java.io.File(java.nio.file.Files.createTempDirectory("background").toFile(), "answers.json"), synchronousWrites = true)
    @After fun tearDown() = scope.cancel()

    // The first question's search waits at the gate; anything asked after it is let through.
    private val viewModel = ResearchViewModel(
        savedStateHandle = SavedStateHandle(),
        orchestrator = ResearchOrchestrator(Retriever { _, _, _ -> if (holding) gate.await(); listOf(evidence) },
            FakeInferenceGateway(List(10) { "Answer $it [S1]" })),
        launchScope = scope,
        historyStore = history,
    )

    private fun startFirst() {
        viewModel.updateQuestion("tell me about the hagia sophia")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isRunning)
    }

    private fun release() { holding = false; gate.complete(Unit) }

    @Test fun newTopicKeepsTheAnswerGoingAndSavesItToHistory() {
        startFirst()
        viewModel.startNewQuestion()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        assertEquals(BackgroundAnswer("tell me about the hagia sophia", viewModel.background.value?.startedAtNanos),
            viewModel.background.value)
        assertTrue(viewModel.background.value!!.running)
        release()
        assertTrue(viewModel.background.value!!.done)
        assertEquals(listOf("tell me about the hagia sophia"), history.records.value.map { it.question })
        // The answer that finished in the background never touched the new screen.
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        viewModel.clearBackground()
        assertNull(viewModel.background.value)
    }

    @Test fun aQuestionAskedMeanwhileWaitsItsTurnThenRuns() {
        startFirst()
        viewModel.startNewQuestion()
        viewModel.updateQuestion("when was it built?")
        viewModel.submit()
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.waitingFor)
        assertTrue(viewModel.uiState.value.isRunning)
        release()
        val second = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, second.phase)
        assertNull(second.waitingFor)
        assertEquals("when was it built?", second.shownQuestion)
        assertEquals(setOf("tell me about the hagia sophia", "when was it built?"), history.records.value.map { it.question }.toSet())
    }

    @Test fun newTopicWhileAQuestionWaitsDropsOnlyThatQuestion() {
        startFirst()
        viewModel.startNewQuestion()
        viewModel.updateQuestion("when was it built?")
        viewModel.submit()
        viewModel.startNewQuestion()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        assertTrue(viewModel.background.value!!.running)
        release()
        assertEquals(listOf("tell me about the hagia sophia"), history.records.value.map { it.question })
    }

    @Test fun theHomeRowReopensTheAnswerStillRunning() {
        startFirst()
        viewModel.startNewQuestion()
        assertTrue(viewModel.reopenBackground())
        assertNull(viewModel.background.value)
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
        assertTrue(viewModel.uiState.value.isRunning)
        release()
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.answer.isBlank())
    }

    @Test fun aFinishedBackgroundAnswerIsNotReopenedOverANewOne() {
        startFirst()
        viewModel.startNewQuestion()
        release()
        assertFalse("saved answers are opened from History", viewModel.reopenBackground())
    }
}
