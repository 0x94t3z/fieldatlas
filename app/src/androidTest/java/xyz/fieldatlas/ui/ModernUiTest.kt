package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.ui.history.HistoryScreen
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.more.MoreScreen
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.research.SourcePreviewSheet
import xyz.fieldatlas.ui.theme.*

/** Presentation regressions: callbacks, actual state, large text, and unchanged source indices. */
class ModernUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val model = InstalledAsset("model", "1", PackType.MODEL, "Qwen3.5 2B", "Apache-2.0", 100, "a".repeat(64), "/fixture")
    private val knowledge = model.copy(id = "knowledge", type = PackType.KNOWLEDGE, title = "Saved knowledge")
    private val proof = ProofModel(emptyList(), emptyList(), emptyList())

    @Test fun modelMemorySwitchUsesExistingCallbacksAndCannotInterruptWork() {
        val state = mutableStateOf<InferenceState>(InferenceState.Ready)
        var releases = 0
        var prepares = 0
        compose.setContent { FieldAtlasTheme {
            MoreScreen(proof, state.value, { prepares++ }, { releases++ }, {}, {}, packs = listOf(model))
        } }
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(1, releases); assertEquals(0, prepares); state.value = InferenceState.Loading }
        compose.onNodeWithContentDescription("Model loaded").assertIsOff().assertIsNotEnabled()
        compose.runOnIdle { state.value = InferenceState.Generating }
        compose.onNodeWithContentDescription("Model loaded").assertIsOn().assertIsNotEnabled()
    }

    @Test fun readinessReportsRealPacksAndBundledVoiceWithoutExtraActions() {
        compose.setContent { FieldAtlasTheme {
            MoreScreen(proof, InferenceState.Idle, {}, {}, {}, {}, packs = listOf(model, knowledge))
        } }
        compose.onNodeWithText("Offline readiness").performClick()
        compose.onNodeWithText("1 saved").assertIsDisplayed()
        compose.onNodeWithText("Included").assertIsDisplayed()
        compose.onNodeWithText("Downloads need internet. Your saved files stay available offline.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        capture("readiness-light")
        compose.onNodeWithContentDescription("Close Offline readiness").performClick()
    }

    @Test fun moreFitsNarrowPhoneAtDoubleTextSize() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    MoreScreen(proof, InferenceState.Ready, {}, {}, {}, {}, packs = listOf(model))
                }
            }
        } }
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsDisplayed()
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithText("Offline readiness").performScrollTo().performClick()
        compose.onNodeWithText("Downloads need internet. Your saved files stay available offline.").performScrollTo().assertIsDisplayed()
        capture("readiness-dark-double-text")
    }

    @Test fun uploadHasOnlyThreeChoicesAndFileGoesStraightToSystemCallback() {
        var picked = 0
        compose.setContent { FieldAtlasTheme {
            ResearchScreen(ResearchUiState(), InferenceState.Ready, 1,
                onQuestionChange = {}, onSubmit = {}, onStop = {}, onPrepareModel = {}, onOpenAnswer = {},
                onFilesClick = { picked++ })
        } }
        compose.onNodeWithContentDescription("Add attachment").performScrollTo().performClick()
        compose.onNodeWithText("Take a photo").assertIsDisplayed()
        compose.onNodeWithText("Choose an image").assertIsDisplayed()
        compose.onNodeWithText("Choose a file").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        capture("attachment-choices-light")
        compose.onNodeWithText("Choose a file").performClick()
        compose.runOnIdle { assertEquals(1, picked) }
        compose.onNodeWithText("Choose a file").assertDoesNotExist()
    }

    @Test fun fourSuggestionsUseTwoColumnsAndOnlyChangeTheDraft() {
        var draft = ""
        var submitted = 0
        compose.setContent { FieldAtlasTheme {
            ResearchScreen(ResearchUiState(), InferenceState.Ready, 1,
                onQuestionChange = { draft = it }, onSubmit = { submitted++ }, onStop = {}, onPrepareModel = {}, onOpenAnswer = {})
        } }
        compose.onAllNodesWithText("Tap to try", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("A few places to start").performScrollTo()
        val heading = compose.onNodeWithText("A few places to start").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithText("Tap to try").fetchSemanticsNode().boundsInRoot
        assertEquals(heading.center.y, hint.center.y, 1f)
        assertTrue(heading.right <= hint.left)
        val first = compose.onNodeWithContentDescription("Use example: Find a pharmacy near me")
        first.performScrollTo()
        val a = first.fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithContentDescription("Use example: Why did the Berlin Wall fall?").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, b.top, 1f)
        assertTrue(a.right <= b.left)
        capture("suggestions-shared-hint-light")
        first.performClick()
        compose.runOnIdle { assertEquals("Find a pharmacy near me", draft); assertEquals(0, submitted) }
    }

    @Test fun sharedSuggestionHintFitsNarrowDarkLayoutAtDoubleTextSize() {
        var draft = ""
        var submitted = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ResearchScreen(ResearchUiState(), InferenceState.Ready, 1,
                        onQuestionChange = { draft = it }, onSubmit = { submitted++ }, onStop = {},
                        onPrepareModel = {}, onOpenAnswer = {})
                }
            }
        } }
        compose.onNodeWithText("A few places to start").performScrollTo()
        compose.onAllNodesWithText("Tap to try", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Tap to try").assertIsDisplayed()
        val heading = compose.onNodeWithText("A few places to start").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithText("Tap to try").fetchSemanticsNode().boundsInRoot
        assertTrue(heading.right <= hint.left)
        assertEquals(heading.center.y, hint.center.y, 1f)
        capture("suggestions-shared-hint-dark-double-text")
        compose.onNodeWithContentDescription("Use example: Why did the Berlin Wall fall?").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Why did the Berlin Wall fall?", draft); assertEquals(0, submitted) }
    }

    @Test fun filenameRemainsOneLineAndKeepsItsOriginalAccessibleText() {
        val name = "Helios_Pump_Revision_B_with_a_very_long_filename.txt"
        compose.setContent { FieldAtlasTheme {
            SourcePreviewSheet(listOf(Evidence("attachment:demo", "chunk", name, "Attached file · text", "Original text.", 1.0)),
                0, true, null, {}, null, {})
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText(name).assertIsDisplayed()
        capture("source-filename-light")
    }

    @Test fun quoteAndInlineCitationsKeepTheirSourceIndicesAndLargeTargets() {
        val clicked = mutableListOf<Int>()
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            Box(Modifier.width(320.dp)) {
                AnswerMarkdownRenderer(parseAnswerMarkdown("Use **Revision B**. [S1]\n\n> Use filter K-9 and connector M5. [S2]"),
                    2, { clicked += it }, sourceTitles = listOf("Revision B", "Pump manual.txt"))
            }
        } }
        val first = compose.onNodeWithContentDescription("Open source 1")
        val bounds = first.fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(bounds.width >= 48 * density - 1)
        assertTrue(bounds.height >= 48 * density - 1)
        first.performClick()
        compose.onNodeWithText("Pump manual.txt").performClick()
        compose.runOnIdle { assertEquals(listOf(0, 1), clicked) }
        capture("markdown-answer-dark")
    }

    @Test fun libraryTabsPreserveSavedCollectionsAndImportAction() {
        var imports = 0
        compose.setContent { FieldAtlasTheme {
            LibraryScreen(listOf(model, knowledge), { imports++ })
        } }
        compose.onNodeWithText("Saved knowledge").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        compose.onNodeWithText("Saved knowledge").assertDoesNotExist()
        compose.onNodeWithContentDescription("Import a pack").performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        compose.onNodeWithText("On this phone").performClick()
        compose.onNodeWithContentDescription("Use Saved knowledge in research").assertIsOn()
        capture("library-light")
    }

    @Test fun everyBundledVectorBuildsIncludingPixelDinosaurAndQuotes() {
        val icons = listOf(FieldAtlasIcons.Back, FieldAtlasIcons.ChevronRight, FieldAtlasIcons.Research,
            FieldAtlasIcons.Library, FieldAtlasIcons.More, FieldAtlasIcons.History, FieldAtlasIcons.Privacy,
            FieldAtlasIcons.Performance, FieldAtlasIcons.Diagnostics, FieldAtlasIcons.Benchmark, FieldAtlasIcons.Import,
            FieldAtlasIcons.AttachFile, FieldAtlasIcons.Camera, FieldAtlasIcons.Photo, FieldAtlasIcons.Check,
            FieldAtlasIcons.Citation, FieldAtlasIcons.Offline, FieldAtlasIcons.Model, FieldAtlasIcons.Archive,
            FieldAtlasIcons.Knowledge, FieldAtlasIcons.Place, FieldAtlasIcons.Collection, FieldAtlasIcons.Database,
            FieldAtlasIcons.ResearchSparkles, FieldAtlasIcons.Food, FieldAtlasIcons.Document, FieldAtlasIcons.Share,
            FieldAtlasIcons.Copy, FieldAtlasIcons.Close, FieldAtlasIcons.OpenExternal, FieldAtlasIcons.Trash,
            FieldAtlasIcons.Lock, FieldAtlasIcons.Info, FieldAtlasIcons.Plus, FieldAtlasIcons.Mic, FieldAtlasIcons.Code,
            FieldAtlasIcons.Pdf, FieldAtlasIcons.Download, FieldAtlasIcons.Storage, FieldAtlasIcons.Heart,
            FieldAtlasIcons.Globe, FieldAtlasIcons.MoreOptions)
        icons.forEach { assertTrue(it.name, it.root.size > 0) }
        assertEquals(32f, FieldAtlasIcons.Offline.viewportWidth)
    }

    @Test fun navigationKeepsAllFourTargetsVisibleAtDoubleTextSize() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    FieldAtlasApp(packs = listOf(model), importing = false, setupError = null,
                        researchState = ResearchUiState(), proof = proof,
                        onImportPack = {}, onQuestionChange = {}, onSubmit = {}, onStop = {})
                }
            }
        } }
        listOf("Research", "Library", "History", "More").forEach { label ->
            val node = compose.onNodeWithContentDescription(label)
            node.assertIsDisplayed().assertHasClickAction()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f)
            assertTrue(bounds.height >= 48f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("$label must remain fully readable", layouts.single().isLineEllipsized(0))
        }
        val first = compose.onNodeWithContentDescription("Research").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Library").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithContentDescription("History").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue("Large text navigation should reflow", third.top >= first.bottom)
        capture("navigation-double-text")
    }

    @Test fun historyExpansionRetainsAskAgainAndDeleteConfirmation() {
        val question = "Which filter applies to serial 2345?"
        var asked: String? = null
        var deleted = false
        val evidence = Evidence("attachment:pump", "chunk", "Helios_Pump_Revision_B.txt", "Attached file · text", "Use filter K-9.", 1.0)
        compose.setContent { FieldAtlasTheme {
            HistoryScreen(listOf(AnswerRecord(question, "For serial **2345**, use Revision B: filter K-9. [S1]",
                System.currentTimeMillis(), listOf(evidence.title), listOf(evidence))), { asked = it }, { deleted = true })
        } }
        compose.onNodeWithText(question).performClick()
        compose.onNodeWithText("Ask again").assertExists()
        compose.onNodeWithText(question).performScrollTo().performClick()
        compose.onNodeWithText("Ask again").assertDoesNotExist()
        compose.onNodeWithText(question).performClick()
        compose.onNodeWithText("Ask again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(question, asked) }
        capture("history-expanded-light")
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        listOf("Share answer", "Delete from History").forEach { label ->
            val action = compose.onNodeWithContentDescription(label).assertHasClickAction()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48 * density - 1)
            assertTrue(bounds.height >= 48 * density - 1)
        }
        compose.onNodeWithContentDescription("Delete from History").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Delete this answer?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertFalse(deleted) }
    }

    @Test fun historyPressedFeedbackStaysInsideRoundedCornersInBothStates() {
        val question = "Check rounded History feedback"
        var background = 0
        compose.setContent { FieldAtlasTheme {
            background = androidx.compose.material3.MaterialTheme.colorScheme.background.toArgb()
            androidx.compose.material3.Surface(Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                HistoryScreen(listOf(AnswerRecord(question, "A short saved answer.",
                    System.currentTimeMillis(), emptyList(), emptyList())))
            }
        } }
        for (state in listOf("Collapsed", "Expanded")) {
            val card = compose.onNode(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, state) and hasClickAction())
            card.performScrollTo()
            val bounds = card.fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            try {
                // Press the common header, not selectable answer text or the utility
                // buttons that appear in the expanded card.
                card.performTouchInput { down(androidx.compose.ui.geometry.Offset(center.x, 24.dp.toPx())) }
                compose.mainClock.advanceTimeBy(400)
                val root = compose.onNode(isRoot() and hasAnyDescendant(hasText(question)))
                val rootBounds = root.fetchSemanticsNode().boundsInRoot
                val pixels = root.captureToImage().toPixelMap()
                val left = (bounds.left - rootBounds.left).toInt() + 2
                val right = (bounds.right - rootBounds.left).toInt() - 3
                val top = (bounds.top - rootBounds.top).toInt() + 2
                assertEquals("$state upper-left press highlight must be clipped", background, pixels[left, top].toArgb())
                assertEquals("$state upper-right press highlight must be clipped", background, pixels[right, top].toArgb())
            } finally {
                card.performTouchInput { up() }
                compose.mainClock.autoAdvance = true
            }
            compose.waitForIdle()
        }
        compose.onNode(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapsed")).assertExists()
    }

    @Test fun researchProgressKeepsLivePhasesRestoredTimelineAndStopCallback() {
        val evidence = Evidence("attachment:pump", "chunk", "Pump manual.txt", "Attached file · text", "Use filter K-9.", 1.0)
        val state = mutableStateOf(ResearchUiState(question = "Which filter applies?", phase = ResearchPhase.Searching))
        var stopped = 0
        compose.setContent { FieldAtlasTheme {
            ResearchScreen(state.value, InferenceState.Generating, 1,
                onQuestionChange = {}, onSubmit = {}, onStop = { stopped++ }, onPrepareModel = {}, onOpenAnswer = {})
        } }
        compose.onNodeWithText("Looking into your question.").assertIsDisplayed()
        compose.onNodeWithText("Working locally. Your question stays on this phone.").assertIsDisplayed()
        compose.onNodeWithText("YOUR QUESTION").assertIsDisplayed()
        compose.onNodeWithText("Finding evidence").assertIsDisplayed()
        compose.onNodeWithText("Stop research").assertDoesNotExist()
        compose.onNodeWithContentDescription("Done").assertDoesNotExist()
        compose.onNodeWithContentDescription("Step 1").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "In progress"))
        compose.onNodeWithContentDescription("Research progress").assertExists()
        // An early source lead is not a model token: keep the reading phase until tokens arrive.
        compose.runOnIdle { state.value = state.value.copy(phase = ResearchPhase.Generating,
            sources = listOf(evidence), answer = "Saved source lead", promptRead = 25 to 100) }
        compose.onNodeWithText("Reading your sources").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Completed"))
        compose.onNodeWithContentDescription("Step 2").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "In progress"))
        // The restored timeline places the real indicator under the active stage, not below all three.
        val reading = compose.onNodeWithText("Read the sources").fetchSemanticsNode().boundsInRoot
        val pending = compose.onNodeWithText("Write the answer").fetchSemanticsNode().boundsInRoot
        val indicator = compose.onNodeWithContentDescription("Research progress").fetchSemanticsNode().boundsInRoot
        assertTrue(indicator.top >= reading.bottom)
        assertTrue(indicator.bottom <= pending.top)
        compose.onNodeWithText("1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("2", useUnmergedTree = true).assertDoesNotExist()
        capture("restored-timeline-reading-light")
        compose.runOnIdle { state.value = state.value.copy(tokensWritten = 7, answer = "Use filter K-9.") }
        compose.onNodeWithText("Writing your answer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 3").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "In progress"))
        compose.onNodeWithText("Stop").performClick()
        compose.runOnIdle { assertEquals(1, stopped) }
    }

    @Test fun researchStopRemainsReachableWithLargeTextInDarkMode() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ResearchScreen(ResearchUiState(question = "A long question about a saved document",
                        phase = ResearchPhase.Generating), InferenceState.Generating, 1,
                        onQuestionChange = {}, onSubmit = {}, onStop = {}, onPrepareModel = {}, onOpenAnswer = {})
                }
            }
        } }
        compose.onNodeWithText("Stop").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Prepare the model").assertExists()
        compose.onNodeWithText("Model-generated · no supporting sources found").assertExists()
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "ui-review")
        check(directory.isDirectory || directory.mkdirs())
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
