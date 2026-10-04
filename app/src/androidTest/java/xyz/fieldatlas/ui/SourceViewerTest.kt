package xyz.fieldatlas.ui

import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.sources.SourceOriginal
import xyz.fieldatlas.ui.sources.SourcesScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme
import java.io.File

class SourceViewerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun evidence(title: String, text: String, kind: String = "text", page: Int = 1) =
        Evidence("attachment:fixture", "fixture:p$page:0", title, "Attached file · $kind · page $page", text, 1.0)

    @Test fun mixedAnswerShowsOriginalPassageAndOpensItsSource() {
        var opened = -1
        val original = "Original contextual passage. However, the result has limitations."
        compose.setContent { FieldAtlasTheme {
            xyz.fieldatlas.ui.research.AnswerScreen(
                xyz.fieldatlas.ui.research.ResearchUiState(question = "Explain the result",
                    answer = "## Model explanation—not verified against saved sources\n\nGenerated explanation.",
                    sources = listOf(evidence("Saved note", original))),
                onBack = {}, onAskAnother = {}, onCitation = { opened = it })
        } }
        compose.onNodeWithText("Saved passages").assertIsDisplayed()
        compose.onNodeWithText(original).assertIsDisplayed()
        // The passage opens a preview sheet first; "Full source" opens the complete source.
        compose.onNodeWithText("Open full passage").performClick()
        compose.onNodeWithText("Full source").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(0, opened) }
    }

    @Test fun uncitedSourceIsNotLabeledAsUsedInAnswer() {
        compose.setContent { FieldAtlasTheme {
            SourcesScreen(evidence("diagnostics.json", "{\"version\":1}"), 2, 2, null, {}, isCited = false)
        } }
        compose.onNodeWithText("Provided to the model · not cited").assertIsDisplayed()
        compose.onNodeWithText("Cited in this answer").assertDoesNotExist()
    }

    @Test fun readableOcrHidesBlankLinesWithoutRemovingContent() {
        val raw = "11:24\n\nNo linked local source - answered offline\n\nF1 stands for Formula 1."
        compose.setContent { FieldAtlasTheme {
            SourcesScreen(evidence("answer.png", raw, "image").copy(source = "Attached file · image · page 1 · recognized text"), 1, 1, null, {})
        } }
        compose.onNodeWithText("Recognized text").assertIsDisplayed()
        compose.onNodeWithText("Readable").performScrollTo().assertIsSelected()
        compose.onNodeWithText(raw.replace("\n\n", "\n")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Raw OCR").performScrollTo().performClick()
        compose.onNodeWithText(raw).performScrollTo().assertIsDisplayed()
    }

    @Test fun answerWarnsWhenCitedUploadContainsAnUnverifiedAnswerLabel() {
        val source = evidence("answer.png", "No linked local source - answered offline. An earlier answer.", "image")
        compose.setContent { FieldAtlasTheme {
            xyz.fieldatlas.ui.research.AnswerScreen(
                xyz.fieldatlas.ui.research.ResearchUiState(question = "Explain this", answer = "The screenshot says this. [S1]", sources = listOf(source)),
                onBack = {}, onAskAnother = {}, onCitation = {})
        } }
        compose.onNodeWithText("The cited upload contains an answer labeled as unverified. This explanation does not independently verify its claims.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Open source 1").assertExists()
    }

    @Test fun kotlinExcerptSupportsWrappingAndOriginalText() {
        val code = "fun greet(name: String): String {\n    return \"hello, \" + name\n}"
        val file = File.createTempFile("source-test", ".kt", context.cacheDir)
        try {
            file.writeText(code)
            compose.setContent { FieldAtlasTheme {
                SourcesScreen(evidence("greeting.kt", code), 1, 1, null, {}, SourceOriginal(file, AttachmentKind.TEXT))
            } }
            compose.onNodeWithText("Source excerpt").assertIsDisplayed()
            compose.onNodeWithContentDescription("Wrap source lines").performScrollTo().assertIsOn().performClick().assertIsOff()
            compose.onNodeWithText("View original file").performScrollTo().performClick()
            try {
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Copy original text").fetchSemanticsNodes().isNotEmpty() }
            } catch (error: Throwable) {
                // ComposeTimeoutException is a Throwable, not an Exception; catch it so the tree prints.
                throw AssertionError(compose.onAllNodes(isRoot(), useUnmergedTree = true).onLast().printToString(), error)
            }
            compose.onNodeWithText("Copy original text").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close original file").performClick()
            compose.onNodeWithText("Source excerpt").assertIsDisplayed()
        } finally { file.delete() }
    }

    @Test fun jsonCanShowRawAndCopyWithoutChangingTheEvidence() {
        val raw = "{\"version\":2,\"enabled\":true}"
        compose.setContent { FieldAtlasTheme { SourcesScreen(evidence("config.json", raw), 1, 1, null, {}) } }
        compose.onNodeWithText("Raw").performScrollTo().performClick()
        compose.onNodeWithText(raw).assertIsDisplayed()
        // Copy sits in the top bar, outside the scrolling content.
        compose.onNodeWithContentDescription("Copy source text").performClick()
        compose.onNodeWithText("Formatted").performScrollTo().performClick()
        compose.onNodeWithText("Formatted").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("Indentation added for readability. Copy keeps the original text.").performScrollTo().assertIsDisplayed()
    }

    @Test fun plainTextHasDocumentLayoutAndHonestMissingOriginalNotice() {
        compose.setContent { FieldAtlasTheme { SourcesScreen(evidence("notes.txt", "First paragraph.\n\nSecond paragraph."), 1, 1, null, {}) } }
        compose.onNodeWithContentDescription("Wrap source lines").assertDoesNotExist()
        compose.onNodeWithText("Original file is no longer available locally. The saved excerpt is still available.").assertIsDisplayed()
        compose.onNodeWithText("First paragraph.\n\nSecond paragraph.").assertIsDisplayed()
    }

    @Test fun pdfOpensTheCitedPageAndSupportsNavigationAndZoom() {
        val file = File.createTempFile("source-test", ".pdf", context.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                for (number in 1..2) {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, number).create())
                    page.canvas.drawText("Page $number", 20f, 40f, android.graphics.Paint())
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            } finally { pdf.close() }
            compose.setContent { FieldAtlasTheme {
                SourcesScreen(evidence("paper.pdf", "Page 2", "pdf", 2), 1, 1, null, {}, SourceOriginal(file, AttachmentKind.PDF))
            } }
            compose.onNodeWithText("View original PDF").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Original PDF page 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Next").assertIsNotEnabled()
            compose.onNodeWithText("Zoom in").performClick()
            compose.onNodeWithText("Zoom out").assertIsEnabled()
            compose.onNodeWithText("Previous").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Original PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Previous").assertIsNotEnabled()
        } finally { file.delete() }
    }
}
