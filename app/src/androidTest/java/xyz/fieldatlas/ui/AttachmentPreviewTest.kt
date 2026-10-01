package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.attachments.*
import xyz.fieldatlas.ui.research.AttachmentRows
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class AttachmentPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullStripDisablesPaperclipUntilOneAttachmentIsRemoved() {
        compose.setContent {
            var files by remember { mutableStateOf((1..3).map { AttachmentUiState("$it", "notes-$it.txt") }) }
            FieldAtlasTheme {
                ResearchScreen(ResearchUiState(attachments = files), InferenceState.Ready, 1,
                    onRemoveAttachment = { id -> files = files.filterNot { it.id == id } },
                    onQuestionChange = {}, onSubmit = {}, onStop = {}, onPrepareModel = {}, onOpenAnswer = {})
            }
        }
        compose.onNodeWithContentDescription("Add attachment").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove notes-1.txt").performClick()
        compose.onNodeWithContentDescription("Add attachment").assertIsEnabled().performClick()
        compose.onNodeWithText("Files").assertIsDisplayed()
    }

    @Test fun unreadableImageStillOffersPreviewZoomCloseAndRemove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("preview-test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var removed: String? = null
            val item = AttachmentUiState("photo", "holiday-image.png", AttachmentPhase.Error,
                error = "No readable text found.", kind = AttachmentKind.IMAGE, previewFile = file)
            compose.setContent { FieldAtlasTheme { AttachmentRows(listOf(item), true, { removed = it }, {}) } }
            compose.onNodeWithText(item.displayName).assertDoesNotExist()
            compose.onNodeWithText("Check text").assertDoesNotExist()
            compose.onNodeWithText("Read issue · Details").assertDoesNotExist()
            compose.onNodeWithContentDescription("Read issue for ${item.displayName}").performClick()
            compose.onNodeWithText("Couldn’t read attachment").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithContentDescription("Preview ${item.displayName}").performClick()
            compose.onNodeWithText("Image preview").assertIsDisplayed()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Attached image").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Zoom in").performClick()
            compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
            compose.onNodeWithText("Reset zoom").performClick()
            compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Close image preview").performClick()
            compose.onNodeWithText("Image preview").assertDoesNotExist()
            compose.onNodeWithContentDescription("Remove ${item.displayName}").performClick()
            assertEquals("photo", removed)
        } finally { bitmap.recycle(); file.delete() }
    }
}
