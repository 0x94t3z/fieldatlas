package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.attachments.AttachmentInput
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.attachments.AttachmentPage
import xyz.fieldatlas.attachments.AttachmentReader
import xyz.fieldatlas.attachments.ExtractedAttachment
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever
import xyz.fieldatlas.speech.SpeechTranscriber

/** A follow-up can be typed, dictated or asked about a file, without disturbing the answer it follows. */
class FollowUpInputTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val evidence = Evidence("doc", "doc:0000", "Title", "Source", "Fact", 1.0)
    @After fun tearDown() = scope.cancel()

    private class ScriptedSpeech(private val words: String) : SpeechTranscriber {
        override suspend fun start(onLevel: (Float) -> Unit) = Unit
        override suspend fun start(onLevel: (Float) -> Unit, onText: (String) -> Unit) { onText(words.substringBefore(' ')) }
        override suspend fun stop() = words
        override suspend fun cancel() = Unit
    }

    private val reader = AttachmentReader { input -> ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Menu: falafel, hummus"))) }

    private fun answered(speech: String = "", inference: FakeInferenceGateway = FakeInferenceGateway(listOf("Answer [S1]", "Second [S1]")),
                         seen: MutableList<Int>? = null): ResearchViewModel {
        val viewModel = ResearchViewModel(
            savedStateHandle = SavedStateHandle(),
            orchestrator = ResearchOrchestrator(Retriever { _, _, _ -> listOf(evidence) }, inference),
            launchScope = scope,
            createTranscriber = { ScriptedSpeech(speech) },
            attachmentReader = reader,
        )
        viewModel.updateQuestion("Best vegan food in Lisbon")
        viewModel.submit()
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        return viewModel
    }

    @Test fun dictationFillsTheFollowUpBoxAndLeavesTheAnswer() {
        val viewModel = answered(speech = "which one is closest to the castle")
        val answer = viewModel.uiState.value.answer
        viewModel.selectVoiceTarget(followUp = true)
        viewModel.onMicClick()
        assertEquals(VoicePhase.Recording, viewModel.voiceState.value.phase)
        assertTrue(viewModel.voiceState.value.forFollowUp)
        viewModel.onMicClick()
        assertEquals("which one is closest to the castle", viewModel.followUpDraft.value)
        // The question box and the answer are untouched.
        assertEquals("Best vegan food in Lisbon", viewModel.uiState.value.question)
        assertEquals(answer, viewModel.uiState.value.answer)
        assertEquals(ResearchPhase.Complete, viewModel.uiState.value.phase)
        viewModel.submitFollowUp(viewModel.followUpDraft.value)
        assertEquals("which one is closest to the castle", viewModel.uiState.value.shownQuestion)
        assertEquals("", viewModel.followUpDraft.value)
    }

    @Test fun dictationIntoTheQuestionBoxStillStartsANewQuestion() {
        val viewModel = answered(speech = "rainy day museums")
        viewModel.selectVoiceTarget(followUp = false)
        viewModel.onMicClick(); viewModel.onMicClick()
        // As before, dictation adds to what the question box holds, and the finished answer gives way to a new question.
        assertEquals("Best vegan food in Lisbon rainy day museums", viewModel.uiState.value.question)
        assertEquals("", viewModel.followUpDraft.value)
        assertEquals(ResearchPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test fun leavingTheAnswerDropsAFollowUpRecording() {
        val viewModel = answered(speech = "unused")
        viewModel.selectVoiceTarget(followUp = true)
        viewModel.onMicClick()
        viewModel.leaveFollowUp()
        assertEquals(VoicePhase.Idle, viewModel.voiceState.value.phase)
        assertEquals("", viewModel.followUpDraft.value)
    }

    @Test fun aFileAddedToAFollowUpKeepsTheAnswerAndGoesWithTheFollowUp() {
        val viewModel = answered()
        val answer = viewModel.uiState.value.answer
        viewModel.selectAttachmentTarget(followUp = true)
        viewModel.addAttachment("menu.jpg") { AttachmentInput("menu", "menu.jpg", File("menu.jpg"), AttachmentKind.IMAGE) }
        val state = viewModel.uiState.value
        assertEquals(ResearchPhase.Complete, state.phase)
        assertEquals(answer, state.answer)
        assertEquals(listOf("menu.jpg"), state.attachments.map { it.displayName })
        assertTrue(state.attachmentsReady)
        viewModel.submitFollowUp("which of these dishes are vegan?")
        val followUp = viewModel.uiState.value
        assertEquals("which of these dishes are vegan?", followUp.shownQuestion)
        assertEquals(listOf("menu.jpg"), followUp.attachments.map { it.displayName })
    }

    @Test fun aFileAddedFromTheQuestionBoxStillStartsANewQuestion() {
        val viewModel = answered()
        viewModel.selectAttachmentTarget(followUp = false)
        viewModel.addAttachment("menu.jpg") { AttachmentInput("menu", "menu.jpg", File("menu.jpg"), AttachmentKind.IMAGE) }
        assertEquals(ResearchPhase.Idle, viewModel.uiState.value.phase)
        assertEquals("", viewModel.uiState.value.answer)
    }
}
