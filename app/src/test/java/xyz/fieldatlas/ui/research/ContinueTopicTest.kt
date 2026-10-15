package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.AnswerHistoryStore
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

/** A topic has no last follow-up, and any answer in History can pick its topic up again. */
class ContinueTopicTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val evidence = Evidence("hagia", "hagia:0000", "Hagia Sophia — Overview", "Source",
        "Hagia Sophia is a former church in Istanbul; in July 2020 it was turned back into a mosque.", 1.0)
    @After fun tearDown() = scope.cancel()

    private fun viewModel(store: AnswerHistoryStore) = ResearchViewModel(
        savedStateHandle = SavedStateHandle(),
        orchestrator = ResearchOrchestrator(Retriever { _, _, _ -> listOf(evidence) }, FakeInferenceGateway(List(40) { "Answer $it [S1]" })),
        launchScope = scope,
        historyStore = store,
    )

    private fun store() = AnswerHistoryStore(
        java.io.File(java.nio.file.Files.createTempDirectory("topic").toFile(), "answers.json"), synchronousWrites = true)

    @Test fun aTopicGoesOnForAsManyFollowUpsAsThePersonAsks() {
        val vm = viewModel(store())
        vm.updateQuestion("tell me about the hagia sophia")
        vm.submit()
        repeat(25) { n ->
            vm.submitFollowUp("and what happened to it after step $n?")
            assertEquals(ResearchPhase.Complete, vm.uiState.value.phase)
        }
        assertEquals(25, vm.uiState.value.earlier.size)
        assertEquals("tell me about the hagia sophia", vm.uiState.value.earlier.first().asked)
    }

    @Test fun continuingFromHistoryReopensTheTopicAtItsLatestAnswer() {
        val history = store()
        val first = viewModel(history)
        first.updateQuestion("tell me about the hagia sophia")
        first.submit()
        first.submitFollowUp("when was it turned back into a mosque?")
        first.startNewQuestion()
        val records = history.records.value
        assertEquals(2, records.size)

        // A fresh screen, as after reopening the app; Continue is tapped on the topic's FIRST answer.
        val vm = viewModel(history)
        assertTrue(vm.continueTopic(records.last(), records))
        val state = vm.uiState.value
        assertEquals(ResearchPhase.Complete, state.phase)
        assertEquals("when was it turned back into a mosque?", state.shownQuestion)
        assertEquals(listOf("tell me about the hagia sophia"), state.earlier.map { it.asked })
        assertEquals(records.first().topic, state.topic)
        assertEquals(listOf("hagia:0000"), state.sources.map { it.chunkId })

        vm.submitFollowUp("is it free to visit?")
        val next = vm.uiState.value
        assertEquals(ResearchPhase.Complete, next.phase)
        assertEquals(2, next.earlier.size)
        assertEquals(records.first().topic, next.topic)
        assertEquals(3, history.records.value.count { it.topic == records.first().topic })
    }

    @Test fun anAnswerSavedBeforeTopicsExistedStartsOneThatStaysTogether() {
        val history = store()
        // As 1.4.1 saved it: no topic.
        history.record("tell me about the hagia sophia", "Hagia Sophia is in Istanbul [S1].", listOf("Hagia Sophia — Overview"), listOf(evidence))
        val old = history.records.value.single()
        assertEquals(null, old.topic)
        val vm = viewModel(history)
        assertTrue(vm.continueTopic(old, history.records.value))
        vm.submitFollowUp("when was it turned back into a mosque?")
        vm.startNewQuestion()
        val followUp = history.records.value.first()
        assertEquals(old.createdAtEpochMs, followUp.topic)
        // Continuing again from the follow-up still finds the old first question.
        val again = viewModel(history)
        assertTrue(again.continueTopic(followUp, history.records.value))
        assertEquals(listOf("tell me about the hagia sophia"), again.uiState.value.earlier.map { it.asked })
    }
}
