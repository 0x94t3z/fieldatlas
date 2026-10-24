package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyData
import xyz.fieldatlas.emergency.EmergencyNumbers
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.research.AnswerHistoryStore
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

/**
 * Saved emergency guidance never waits for another answer (24 Oct: a bee sting with swelling lips waited 1,238 s behind a
 * slow answer on a 6 GB phone). Here the first answer's search is held at a gate, the way a slow model holds the phone.
 */
class EmergencyWithoutWaitingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val evidence = Evidence("doc", "doc:0000", "Hagia Sophia — Overview", "Source", "Hagia Sophia is in Istanbul.", 1.0)
    private val gate = CompletableDeferred<Unit>()
    private var holding = true
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
    private val numbers = EmergencyNumbers.book(File("src/main/assets/emergency/numbers.json").readText())
    private val history = AnswerHistoryStore(
        File(java.nio.file.Files.createTempDirectory("emergency-now").toFile(), "answers.json"), synchronousWrites = true)
    @After fun tearDown() = scope.cancel()

    private fun viewModel(model: InferenceGateway, letGoLate: Boolean = false) = ResearchViewModel(
        savedStateHandle = SavedStateHandle(),
        orchestrator = ResearchOrchestrator(Retriever { _, _, _ ->
            // A stopped native answer cannot be interrupted mid-call: with [letGoLate] the held search ignores Stop
            // until the gate opens, as the model does on the phone.
            if (holding) { if (letGoLate) withContext(NonCancellable) { gate.await() } else gate.await() }
            listOf(evidence)
        }, model, emergencyGuides = { guides }, emergencyNumbers = { numbers }),
        launchScope = scope,
        historyStore = history,
    )

    private fun ResearchViewModel.ask(question: String) { updateQuestion(question); submit() }

    @Test fun anEmergencyRightAfterStopDoesNotWaitForTheStoppedAnswerToLetGo() {
        val model = FakeInferenceGateway(List(10) { "Answer $it [S1]" })
        val viewModel = viewModel(model, letGoLate = true)
        viewModel.ask("tell me about the hagia sophia")
        viewModel.cancelResearch()
        assertFalse(viewModel.uiState.value.isRunning)
        // Stopped, but still holding the search and the model until its call returns.
        viewModel.startNewQuestion()
        viewModel.ask("my friend was stung by a bee and her lips are swelling")
        val shown = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, shown.phase)
        assertTrue(shown.answer, shown.answer.startsWith("**Call your local emergency number now**"))
        assertEquals(listOf("emergency:anaphylaxis"), shown.sources.map { it.documentId })
        // An ordinary question still waits for the stopped answer to let go, then runs.
        viewModel.startNewQuestion()
        viewModel.ask("what is the hagia sophia?")
        assertTrue(viewModel.uiState.value.isRunning)
        holding = false
        gate.complete(Unit)
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.error)
    }

    @Test fun anEmergencyNumberFollowUpIsAnsweredWhileAnotherAnswerRuns() {
        val viewModel = viewModel(FakeInferenceGateway(List(10) { "Answer $it [S1]" }))
        viewModel.ask("tell me about the hagia sophia")
        viewModel.startNewQuestion()
        viewModel.ask("my friend was stung by a bee and her lips are swelling")
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        viewModel.submitFollowUp("which number do I call for an ambulance in Thailand?")
        val followUp = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, followUp.phase)
        assertNull(followUp.waitingFor)
        assertTrue(followUp.answer, followUp.answer.contains("**1669**"))
        // Still the same topic: the sting is the question it follows.
        assertEquals("my friend was stung by a bee and her lips are swelling", followUp.earlier.single().asked)
        assertTrue(viewModel.background.value.single().running)
        holding = false
        gate.complete(Unit)
        assertTrue(viewModel.background.value.single().done)
    }

    @Test fun aBusyModelIsNotCalledUnavailable() {
        // 24 Oct, Redmi: the guide shown beside a running answer said "The answer model isn't available right now"; the
        // model was answering the other question. Here the model reports itself busy, as on the phone.
        val inner = FakeInferenceGateway(List(10) { "Answer $it [S1]" })
        val busy = object : InferenceGateway by inner {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<xyz.fieldatlas.inference.InferenceState>(
                xyz.fieldatlas.inference.InferenceState.Generating)
        }
        val viewModel = viewModel(busy)
        viewModel.ask("tell me about the hagia sophia")
        viewModel.startNewQuestion()
        viewModel.ask("my friend was stung by a bee and her lips are swelling")
        val shown = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, shown.phase)
        assertFalse(shown.answer, shown.answer.contains("isn't available"))
        assertTrue(shown.answer, shown.answer.contains("For safety, this is the guide as published, without a model rewrite."))
        holding = false
        gate.complete(Unit)
    }

    @Test fun aModelThatWritesFirstAidShowsThePublishedGuideWhileBusy() {
        // Gemma writes first aid for the situation when it is free. Busy with another answer, the guide as published
        // is shown at once instead of waiting for it.
        val inner = FakeInferenceGateway(List(10) { "Answer $it [S1]" })
        val writer = object : InferenceGateway by inner { override val writesFirstAid = true }
        val viewModel = viewModel(writer)
        viewModel.ask("tell me about the hagia sophia")
        viewModel.startNewQuestion()
        val calls = inner.generateCalls
        viewModel.ask("my friend was stung by a bee and her lips are swelling")
        val shown = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, shown.phase)
        assertTrue(shown.answer, shown.answer.contains("For safety, this is the guide as published, without a model rewrite."))
        assertEquals("nothing was written by the busy model", calls, inner.generateCalls)
        holding = false
        gate.complete(Unit)
        assertTrue(viewModel.background.value.single().done)
    }
}
