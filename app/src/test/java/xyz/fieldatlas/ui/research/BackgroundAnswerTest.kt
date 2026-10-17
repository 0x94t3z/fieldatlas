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
    private var gate = CompletableDeferred<Unit>()
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

    private fun aside() = viewModel.background.value.single()

    @Test fun newTopicKeepsTheAnswerGoingAndSavesItToHistory() {
        startFirst()
        viewModel.startNewQuestion()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        assertEquals("tell me about the hagia sophia", aside().question)
        assertTrue(aside().running)
        release()
        assertTrue(aside().done)
        assertEquals(listOf("tell me about the hagia sophia"), history.records.value.map { it.question })
        // The answer that finished off the screen never touched the new screen.
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        viewModel.dismissAside(aside().id)
        assertTrue(viewModel.background.value.isEmpty())
    }

    @Test fun aQuestionAskedMeanwhileWaitsItsTurnThenRuns() {
        startFirst()
        viewModel.startNewQuestion()
        viewModel.updateQuestion("when was it built?")
        viewModel.submit()
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.waitingFor)
        assertTrue(viewModel.uiState.value.isRunning)
        val askedAt = viewModel.uiState.value.startedAtNanos!!
        release()
        val second = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, second.phase)
        assertNull(second.waitingFor)
        // Its clock counts the work, from when its turn came, not the wait.
        assertTrue(second.clockStartNanos!! > askedAt)
        assertEquals(askedAt, second.startedAtNanos)
        assertEquals("when was it built?", second.shownQuestion)
        assertEquals(setOf("tell me about the hagia sophia", "when was it built?"), history.records.value.map { it.question }.toSet())
    }

    @Test fun newTopicWhileAQuestionWaitsKeepsThatOneToo() {
        startFirst()
        viewModel.startNewQuestion()
        viewModel.updateQuestion("when was it built?")
        viewModel.submit()
        viewModel.startNewQuestion()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        assertEquals(listOf(false, true), viewModel.background.value.map { it.waiting })
        release()
        assertTrue(viewModel.background.value.all { it.done })
        assertEquals(setOf("tell me about the hagia sophia", "when was it built?"), history.records.value.map { it.question }.toSet())
    }

    @Test fun theHomeRowReopensTheAnswerStillRunning() {
        startFirst()
        viewModel.startNewQuestion()
        assertTrue(viewModel.openAside(aside().id))
        assertTrue(viewModel.background.value.isEmpty())
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
        assertTrue(viewModel.uiState.value.isRunning)
        release()
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.answer.isBlank())
    }

    @Test fun aReadyAnswerOpensItsPageWithTheFollowUpBox() {
        startFirst()
        viewModel.startNewQuestion()
        release()
        assertTrue(viewModel.openAside(aside().id))
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
        assertTrue(viewModel.background.value.isEmpty())
        // A follow-up reads it like any other.
        viewModel.submitFollowUp("when was it built?")
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.earlier.size)
    }

    @Test fun leavingAFinishedAnswerStartsTheHomeAfreshWithoutARow() {
        holding = false
        viewModel.updateQuestion("tell me about the hagia sophia")
        viewModel.submit()
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        viewModel.leaveAnswer()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        // Just read, and in History: an "Answer ready" row for it would only repeat it.
        assertTrue(viewModel.background.value.isEmpty())
    }

    @Test fun leavingARunningAnswerKeepsItGoingAndMarksItReadyWhenDone() {
        viewModel.updateQuestion("tell me about the hagia sophia")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isRunning)
        viewModel.leaveAnswer()
        assertEquals(ResearchUiState(), viewModel.uiState.value)
        assertTrue(aside().running)
        release()
        assertTrue(aside().done)
        assertTrue(viewModel.openAside(aside().id))
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
    }

    @Test fun readingAReadyAnswerFromHistoryTakesItsRowAway() {
        startFirst()
        viewModel.startNewQuestion()
        release()
        assertTrue(aside().done)
        val record = history.records.value.single()
        assertTrue(viewModel.continueTopic(record, history.records.value))
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
        // Seen now: back on the home, no "Answer ready" row repeats it.
        assertTrue(viewModel.background.value.isEmpty())
    }

    @Test fun readingAnotherTopicFromHistoryKeepsTheReadyRow() {
        history.record(question = "what is a heat pump", answer = "A heat pump moves heat.", sources = emptyList(), topic = 1L)
        startFirst()
        viewModel.startNewQuestion()
        release()
        val other = history.records.value.first { it.question == "what is a heat pump" }
        assertTrue(viewModel.continueTopic(other, history.records.value))
        assertEquals(listOf("tell me about the hagia sophia"), viewModel.background.value.map { it.question })
        assertTrue(aside().done)
    }

    @Test fun openingAReadyAnswerWhileAnotherRunsKeepsTheOtherGoing() {
        viewModel.updateQuestion("tell me about the hagia sophia")
        viewModel.submit()
        viewModel.leaveAnswer()
        release()
        val ready = aside().id
        assertTrue(aside().done)
        holding = true
        gate = CompletableDeferred()
        viewModel.updateQuestion("when was it built?")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isRunning)
        assertTrue(viewModel.openAside(ready))
        assertEquals("tell me about the hagia sophia", viewModel.uiState.value.shownQuestion)
        assertEquals(listOf("when was it built?"), viewModel.background.value.map { it.question })
        assertTrue(aside().running)
        release()
        assertTrue(aside().done)
    }
}
