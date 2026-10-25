package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.attachments.AttachmentPage
import xyz.fieldatlas.attachments.AttachmentPhase
import xyz.fieldatlas.attachments.AttachmentUiState
import xyz.fieldatlas.attachments.ExtractedAttachment
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class SubmittedAttachmentUiTest {
    @get:Rule val compose = foregroundComposeRule()

    @Test fun sentFileBelongsToTheQuestionWhileRunningAndAfterCompletion() {
        val notes = attachment("notes", "notes.txt", "The notes describe the local water cycle.")
        var state by mutableStateOf(ResearchUiState(
            question = "Summarize these notes.",
            phase = ResearchPhase.Searching,
            attachments = listOf(notes),
            submittedAttachments = listOf(notes),
            sentAttachmentIds = setOf(notes.id),
        ))
        compose.setContent {
            FieldAtlasTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                    AnswerScreen(state, onBack = {}, onAskAnother = {}, onCitation = {})
                }
            }
        }

        compose.onNodeWithText("Files for this question").assertIsDisplayed()
        compose.onNodeWithText("notes.txt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove notes.txt").assertDoesNotExist()

        compose.runOnIdle {
            state = state.copy(phase = ResearchPhase.Complete, answer = "The notes describe the water cycle.")
        }
        compose.onNodeWithText("Files for this question").assertIsDisplayed()
        compose.waitForIdle()
        captureSentFile()
        compose.onNodeWithText("notes.txt").assertIsDisplayed().performClick()
        // Submission must not discard the extracted text or disable the document preview.
        compose.onNodeWithText("The notes describe the local water cycle.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Remove notes.txt").assertDoesNotExist()
    }

    @Test fun onlyNewFilesRemainRemovableAndSubmissionMovesThemOutOfTheComposer() {
        val notes = attachment("notes", "notes.txt", "Original notes.")
        val draft = attachment("draft", "draft.txt", "Additional notes for the next question.")
        var state by mutableStateOf(ResearchUiState(
            question = "Summarize these notes.",
            answer = "The original notes have been summarized.",
            phase = ResearchPhase.Complete,
            attachments = listOf(notes, draft),
            submittedAttachments = listOf(notes),
            sentAttachmentIds = setOf(notes.id),
        ))
        compose.setContent {
            FieldAtlasTheme { AnswerScreen(state, onBack = {}, onAskAnother = {}, onCitation = {}) }
        }

        compose.onNodeWithText("Files for this question").assertIsDisplayed()
        compose.onNodeWithText("notes.txt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove notes.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove draft.txt").assertIsDisplayed()

        compose.runOnIdle {
            state = state.copy(
                question = "Compare both sets of notes.",
                answer = "",
                phase = ResearchPhase.Searching,
                submittedAttachments = listOf(notes, draft),
                sentAttachmentIds = setOf(notes.id, draft.id),
            )
        }
        compose.onNodeWithText("Files for this question").assertIsDisplayed()
        compose.onNodeWithText("notes.txt").assertExists()
        compose.onNodeWithText("draft.txt").assertExists()
        compose.onNodeWithContentDescription("Remove notes.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove draft.txt").assertDoesNotExist()

        compose.runOnIdle {
            state = state.copy(phase = ResearchPhase.Complete, answer = "Both files were used for this answer.")
        }
        compose.onNodeWithText("draft.txt").assertExists()
        compose.onNodeWithContentDescription("Remove notes.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove draft.txt").assertDoesNotExist()
    }

    @Test fun sentImageTileHasNoRemoveAction() {
        // No preview file is supplied: exercise the image tile without file IO or bitmap decoding.
        val image = AttachmentUiState(
            id = "photo",
            displayName = "page.jpg",
            phase = AttachmentPhase.Ready,
            kind = AttachmentKind.IMAGE,
            extracted = ExtractedAttachment("extracted-photo", "page.jpg",
                listOf(AttachmentPage(1, "Recognized text.")), fromOcr = true, kind = AttachmentKind.IMAGE),
        )
        val state = ResearchUiState(
            question = "Read this page.",
            answer = "The page contains recognized text.",
            phase = ResearchPhase.Complete,
            attachments = listOf(image),
            submittedAttachments = listOf(image),
            sentAttachmentIds = setOf(image.id),
        )
        compose.setContent {
            FieldAtlasTheme { AnswerScreen(state, onBack = {}, onAskAnother = {}, onCitation = {}) }
        }
        compose.onNodeWithText("Files for this question").assertIsDisplayed()
        compose.onNodeWithText("page.jpg").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove page.jpg").assertDoesNotExist()
    }

    private fun captureSentFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "submitted-attachment-check").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "sent-file.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun attachment(id: String, name: String, text: String) = AttachmentUiState(
        id = id,
        displayName = name,
        phase = AttachmentPhase.Ready,
        kind = AttachmentKind.TEXT,
        extracted = ExtractedAttachment(
            id = "extracted-$id",
            displayName = name,
            pages = listOf(AttachmentPage(1, text)),
            kind = AttachmentKind.TEXT,
        ),
    )
}
