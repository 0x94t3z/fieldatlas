package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever
import xyz.fieldatlas.speech.SpeechTranscriber

/**
 * Voice input contract checks: the mic must never auto-submit, the transcript
 * lands in the question box, and the level meter follows the transcriber.
 */
class ResearchViewModelVoiceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun tearDown() = scope.cancel()

    private class FakeTranscriber(
        private val transcript: String,
        private val failStart: Boolean = false,
        var emitLevelOnStart: Float? = null,
    ) : SpeechTranscriber {
        var startCalls = 0
        var stopCalls = 0
        var levelListener: ((Float) -> Unit)? = null
        override suspend fun start(onLevel: (Float) -> Unit) {
            startCalls++
            if (failStart && startCalls == 1) error("microphone busy")
            levelListener = onLevel
            emitLevelOnStart?.let(onLevel)
        }
        override suspend fun stop(): String { stopCalls++; return transcript }
        override suspend fun cancel() { stopCalls++ }
    }

    private fun viewModel(
        transcriber: FakeTranscriber,
        reported: MutableList<String> = mutableListOf(),
    ): Pair<ResearchViewModel, FakeTranscriber> {
        val model = ResearchViewModel(
            savedStateHandle = SavedStateHandle(),
            orchestrator = ResearchOrchestrator(
                retriever = Retriever { _, _, _ -> listOf(Evidence("d", "d:0000", "T", "S", "F", 1.0)) },
                inference = FakeInferenceGateway(listOf("unused")),
            ),
            launchScope = scope,
            createTranscriber = { transcriber },
            onError = { reported += it },
        )
        return model to transcriber
    }

    @Test fun micClickStartsRecordingWithoutSubmitting() {
        val (model, transcriber) = viewModel(FakeTranscriber("what does rapamycin do"))
        model.onMicClick()
        assertEquals(VoicePhase.Recording, model.voiceState.value.phase)
        assertEquals(1, transcriber.startCalls)
        assertEquals(ResearchPhase.Idle, model.uiState.value.phase)
    }

    @Test fun secondClickPutsTranscriptInTheQuestionBoxOnly() {
        val (model, transcriber) = viewModel(FakeTranscriber("  what is a tiger  "))
        model.onMicClick()
        model.onMicClick()
        assertEquals("what is a tiger", model.uiState.value.question)
        assertEquals(VoicePhase.Idle, model.voiceState.value.phase)
        assertEquals(1, transcriber.stopCalls)
        // Crucially: no research was started, the user still presses the button.
        assertEquals(ResearchPhase.Idle, model.uiState.value.phase)
        assertEquals("", model.uiState.value.answer)
    }

    @Test fun transcriptAppendsToExistingQuestion() {
        val (model, _) = viewModel(FakeTranscriber("and cubs"))
        model.updateQuestion("How heavy are tigers")
        model.onMicClick()
        model.onMicClick()
        assertEquals("How heavy are tigers and cubs", model.uiState.value.question)
    }

    @Test fun loudLevelsDriveTheMeter() {
        val (model, transcriber) = viewModel(FakeTranscriber("text", emitLevelOnStart = null))
        model.onMicClick()
        transcriber.levelListener?.invoke(0.73f)
        assertEquals(0.73f, model.voiceState.value.level, 0.0001f)
    }

    @Test fun failedStartReturnsToIdleAndReportsWhy() {
        val reported = mutableListOf<String>()
        val (model, _) = viewModel(FakeTranscriber("text", failStart = true), reported)
        model.onMicClick()
        assertEquals(VoicePhase.Idle, model.voiceState.value.phase)
        assertTrue(model.voiceState.value.error!!.contains("Could not start recording"))
        assertTrue(reported.single().contains("Could not start recording"))
        // The error clears as soon as a fresh capture begins.
        model.onMicClick()
        assertEquals(null, model.voiceState.value.error)
    }

    @Test fun emptyTranscriptLeavesQuestionUntouched() {
        val (model, _) = viewModel(FakeTranscriber("   "))
        model.updateQuestion("kept")
        model.onMicClick()
        model.onMicClick()
        assertEquals("kept", model.uiState.value.question)
    }
}
