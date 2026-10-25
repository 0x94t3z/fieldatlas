package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.attachments.*
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

/** Sending moves files out of the composer without losing their evidence or staged-file ownership. */
class SubmittedAttachmentTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @After fun tearDown() = scope.cancel()

    private class Gateway : InferenceGateway {
        override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
        var hold = false
        var gate: CompletableDeferred<Unit>? = null
        val prompts = mutableListOf<String>()
        override suspend fun load(modelPath: String, systemPrompt: String) = Unit
        override suspend fun unload() = Unit
        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
            prompts += prompt
            gate?.await()
            if (hold) awaitCancellation()
            emit(if ("Photosynthesis converts" in prompt) "Photosynthesis converts light into chemical energy [S1]."
                else "The museum opens on Tuesday at 10 am [S1].")
        }
        override fun reviewEvidence(prompt: String, savedPrefixChars: Int): Flow<String> = flow { emit("SUPPORTED") }
    }

    private val museumText = "The museum opens on Tuesday at 10 am. The deadline is Friday. Its entrance is on the north side."
    private val closingText = "The museum closes at 6 pm on Tuesday."
    private val libraryEvidence = Evidence("plants", "plants:1", "Photosynthesis", "Library",
        "Photosynthesis converts light into chemical energy.", 1.0)

    private inner class Fixture(
        val saved: SavedStateHandle = SavedStateHandle(),
        reader: AttachmentReader = AttachmentReader { input ->
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1,
                if (input.id == "closing") closingText else museumText)), kind = input.kind)
        },
        staged: (String) -> File? = { null },
    ) {
        val gateway = Gateway()
        val cleaned = mutableListOf<File>()
        val searched = mutableListOf<String>()
        val vm = ResearchViewModel(saved,
            ResearchOrchestrator(Retriever { question, _, _ -> searched += question; listOf(libraryEvidence) }, gateway),
            launchScope = scope, attachmentReader = reader, cleanupAttachment = { cleaned += it }, stagedAttachment = staged)

        fun add(id: String = "museum", followUp: Boolean = false) {
            vm.selectAttachmentTarget(followUp)
            vm.addAttachment("$id.txt") { AttachmentInput(id, "$id.txt", File("$id.txt"), AttachmentKind.TEXT) }
        }
        fun askWithFile() {
            add()
            vm.updateQuestion("Summarize this document")
            vm.submit()
            assertEquals(ResearchPhase.Complete, vm.uiState.value.phase)
        }
        fun savedFileIds(): List<String> = saved.get<ArrayList<String>>("research.attachments").orEmpty().map { it.substringBefore('\t') }
    }

    @Test fun sendingClearsTheComposerWhileTheRunStillOwnsAndReadsTheFile() {
        val f = Fixture()
        f.add()
        assertEquals(listOf("museum.txt"), f.vm.uiState.value.pendingAttachments.map { it.displayName })
        f.gateway.hold = true
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()

        val running = f.vm.uiState.value
        assertTrue(running.isRunning)
        assertTrue(running.pendingAttachments.isEmpty())
        assertEquals(listOf("museum.txt"), running.submittedAttachments.map { it.displayName })
        assertEquals(listOf("museum"), running.submittedAttachments.map { it.extracted!!.id })
        assertTrue(f.gateway.prompts.any { museumText in it })
        assertTrue(f.cleaned.isEmpty())
        assertEquals(listOf("museum"), f.savedFileIds())
    }

    @Test fun anActualFollowUpKeepsTheFileWithoutPuttingItBackInTheComposer() {
        val f = Fixture()
        f.askWithFile()
        f.gateway.prompts.clear()
        f.vm.submitFollowUp("what about its entrance?")

        val after = f.vm.uiState.value
        assertEquals(ResearchPhase.Complete, after.phase)
        assertEquals(listOf("museum"), after.submittedAttachments.map { it.extracted!!.id })
        assertTrue(after.pendingAttachments.isEmpty())
        assertTrue(f.gateway.prompts.any { museumText in it })
        assertTrue(after.sources.any { it.documentId == "attachment:museum" })
        assertTrue(f.searched.isEmpty())
        assertTrue(f.cleaned.isEmpty())
    }

    @Test fun aFollowUpThatNamesNoFileStillReadsTheDocument() {
        // Questions about a sent document rarely point back at it: these name no file and no earlier subject.
        for (question in listOf("Summarize the main points", "Explain the termination clause", "What are the key risks?",
            "Who are the parties?", "How much is the penalty for late payment?")) {
            val f = Fixture()
            f.askWithFile()
            f.gateway.prompts.clear()
            f.vm.submitFollowUp(question)
            val after = f.vm.uiState.value
            assertEquals(question, ResearchPhase.Complete, after.phase)
            assertEquals(question, listOf("museum"), after.submittedAttachments.map { it.extracted!!.id })
            assertTrue(question, f.gateway.prompts.any { museumText in it })
            assertTrue(question, after.pendingAttachments.isEmpty())
            assertEquals(question, listOf("museum"), f.savedFileIds())
            assertTrue(question, f.cleaned.isEmpty())
        }
    }

    @Test fun explicitDocumentReferencesKeepTheirEvidence() {
        for (question in listOf("Summarize the document", "Explain page 2", "What does museum.txt say?", "What is the deadline?",
            "Who signed the contract?", "What is the total amount?")) {
            val f = Fixture()
            f.askWithFile()
            f.gateway.prompts.clear()
            f.vm.submitFollowUp(question)
            assertEquals(question, listOf("museum"), f.vm.uiState.value.submittedAttachments.map { it.extracted!!.id })
            assertTrue(question, f.gateway.prompts.any { museumText in it })
            assertTrue(question, f.vm.uiState.value.pendingAttachments.isEmpty())
            assertTrue(question, f.vm.uiState.value.question != f.vm.uiState.value.shownQuestion)
        }
    }

    @Test fun leavingAndReopeningARunningAnswerKeepsItsOriginalAndStopRestoresItsDraft() {
        val f = Fixture()
        f.add()
        val original = f.vm.uiState.value.activeAttachments.single()
        f.gateway.hold = true
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()
        f.vm.leaveAnswer()
        val aside = f.vm.background.value.single()
        assertTrue(aside.running)
        assertTrue(f.vm.uiState.value.activeAttachments.isEmpty())
        assertTrue(f.cleaned.isEmpty())

        // A file picked on the home page must not leak into the reopened answer.
        f.add("closing")
        assertTrue(f.vm.openAside(aside.id))
        assertEquals(listOf(original), f.vm.uiState.value.submittedAttachments)
        assertTrue(f.vm.uiState.value.pendingAttachments.isEmpty())
        assertEquals(listOf("museum"), f.savedFileIds())
        assertEquals(listOf(File("closing.txt")), f.cleaned)

        f.vm.cancelResearch()
        assertEquals(listOf(original), f.vm.uiState.value.pendingAttachments)
        assertEquals(listOf("museum"), f.savedFileIds())
        f.vm.startNewQuestion()
        assertEquals(listOf(File("closing.txt"), File("museum.txt")), f.cleaned)
    }

    @Test fun aFinishedBackgroundAnswerOwnsItsFileUntilDismissed() {
        val f = Fixture()
        f.add()
        val gate = CompletableDeferred<Unit>()
        f.gateway.gate = gate
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()
        f.vm.startNewQuestion()
        val id = f.vm.background.value.single().id
        gate.complete(Unit)
        assertTrue(f.vm.background.value.single().done)
        assertTrue(f.cleaned.isEmpty())
        f.vm.dismissAside(id)
        assertEquals(listOf(File("museum.txt")), f.cleaned)
        f.vm.dismissAside(id)
        assertEquals(1, f.cleaned.size)
    }

    @Test fun reopeningAFinishedBackgroundAnswerKeepsTheFileForItsFollowUp() {
        val f = Fixture()
        f.add()
        val gate = CompletableDeferred<Unit>()
        f.gateway.gate = gate
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()
        f.vm.startNewQuestion()
        val id = f.vm.background.value.single().id
        gate.complete(Unit)
        assertTrue(f.vm.openAside(id))
        f.gateway.prompts.clear()
        f.vm.submitFollowUp("What is the deadline?")
        assertTrue(f.gateway.prompts.any { museumText in it })
        assertEquals(listOf("museum"), f.savedFileIds())
        assertTrue(f.cleaned.isEmpty())
        f.vm.startNewQuestion()
        assertEquals(listOf(File("museum.txt")), f.cleaned)
    }

    @Test fun aNewFileIsPendingUntilTheFollowUpIsSentThenBothFilesAreUsed() {
        val f = Fixture()
        f.askWithFile()
        f.add("closing", followUp = true)
        assertEquals(listOf("museum.txt"), f.vm.uiState.value.submittedAttachments.map { it.displayName })
        assertEquals(listOf("closing.txt"), f.vm.uiState.value.pendingAttachments.map { it.displayName })
        f.gateway.prompts.clear()
        f.vm.submitFollowUp("Compare the documents")

        val after = f.vm.uiState.value
        assertEquals(ResearchPhase.Complete, after.phase)
        assertEquals(listOf("museum", "closing"), after.submittedAttachments.map { it.extracted!!.id })
        assertTrue(after.pendingAttachments.isEmpty())
        assertTrue(f.gateway.prompts.any { museumText in it && closingText in it })
        assertEquals(listOf("museum", "closing"), f.savedFileIds())
        assertTrue(f.cleaned.isEmpty())
    }

    @Test fun stoppingTheInitialQuestionReturnsItsFileToTheDraft() {
        val f = Fixture()
        f.add()
        f.gateway.hold = true
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()
        assertTrue(f.vm.uiState.value.isRunning)
        f.vm.cancelResearch()

        val stopped = f.vm.uiState.value
        assertEquals(ResearchPhase.Idle, stopped.phase)
        assertTrue(stopped.submittedAttachments.isEmpty())
        assertEquals(listOf("museum.txt"), stopped.pendingAttachments.map { it.displayName })
        assertTrue(stopped.canSubmit)
        assertTrue(f.cleaned.isEmpty())
    }

    @Test fun stoppingAFollowUpRestoresOnlyItsNewFileToTheComposer() {
        val f = Fixture()
        f.askWithFile()
        val answer = f.vm.uiState.value.answer
        f.add("closing", followUp = true)
        f.gateway.hold = true
        f.vm.submitFollowUp("Compare the documents")
        assertTrue(f.vm.uiState.value.isRunning)
        assertTrue(f.vm.uiState.value.pendingAttachments.isEmpty())
        f.vm.cancelResearch()

        val stopped = f.vm.uiState.value
        assertEquals(ResearchPhase.Complete, stopped.phase)
        assertEquals(answer, stopped.answer)
        assertEquals(listOf("museum.txt"), stopped.submittedAttachments.map { it.displayName })
        assertEquals(listOf("closing.txt"), stopped.pendingAttachments.map { it.displayName })
        assertEquals("Compare the documents", f.vm.followUpDraft.value)
        assertTrue(f.cleaned.isEmpty())
    }

    @Test fun aTopicsFilesCountTowardsTheLimitAndAreCleanedOnceWhenItEnds() {
        val f = Fixture()
        for (id in listOf("museum", "extra", "notes")) f.add(id)
        assertFalse(f.vm.uiState.value.canAddAttachment)
        f.vm.updateQuestion("Summarize these documents")
        f.vm.submit()
        f.vm.submitFollowUp("What is photosynthesis?")
        // The follow-up keeps all three sent files, so there is still no room for a fourth.
        assertEquals(listOf("museum", "extra", "notes"), f.vm.uiState.value.submittedAttachments.map { it.extracted!!.id })
        assertFalse(f.vm.uiState.value.canAddAttachment)
        assertTrue(f.cleaned.isEmpty())
        f.vm.startNewQuestion()
        assertEquals(setOf("museum.txt", "extra.txt", "notes.txt"), f.cleaned.map { it.name }.toSet())
        assertEquals(3, f.cleaned.size)
        assertTrue(f.vm.uiState.value.activeAttachments.isEmpty())
    }

    @Test fun aReadingFileStaysPendingAndCannotBeSubmitted() {
        val reading = CompletableDeferred<ExtractedAttachment>()
        val f = Fixture(reader = AttachmentReader { reading.await() })
        f.add()
        f.vm.updateQuestion("Summarize this document")
        assertFalse(f.vm.uiState.value.attachmentsReady)
        assertFalse(f.vm.uiState.value.canSubmit)
        f.vm.submit()
        assertEquals(ResearchPhase.Idle, f.vm.uiState.value.phase)
        assertTrue(f.vm.uiState.value.submittedAttachments.isEmpty())
        assertEquals(1, f.vm.uiState.value.pendingAttachments.size)
        assertTrue(f.gateway.prompts.isEmpty())
        reading.complete(ExtractedAttachment("museum", "museum.txt", listOf(AttachmentPage(1, museumText))))
        assertTrue(f.vm.uiState.value.canSubmit)
    }

    @Test fun processDeathRestoresTheSubmittedFilesAsAnEditableDraft() {
        val f = Fixture()
        f.add()
        f.gateway.hold = true
        f.vm.updateQuestion("Summarize this document")
        f.vm.submit()
        val restored = Fixture(saved = f.saved, staged = { File("$it.txt") })

        assertEquals(ResearchPhase.Idle, restored.vm.uiState.value.phase)
        assertEquals("Summarize this document", restored.vm.uiState.value.question)
        assertTrue(restored.vm.uiState.value.submittedAttachments.isEmpty())
        assertEquals(listOf("museum.txt"), restored.vm.uiState.value.pendingAttachments.map { it.displayName })
        assertTrue(restored.vm.uiState.value.canSubmit)
    }
}
