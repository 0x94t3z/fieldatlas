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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import xyz.fieldatlas.ui.research.AnswerScreen
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
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(1, releases); assertEquals(0, prepares); state.value = InferenceState.Loading }
        compose.onNodeWithContentDescription("Start answers faster").assertIsOff().assertIsNotEnabled()
        compose.runOnIdle { state.value = InferenceState.Generating }
        compose.onNodeWithContentDescription("Start answers faster").assertIsOn().assertIsNotEnabled()
    }

    @Test fun readinessReportsRealPacksAndBundledVoiceWithoutExtraActions() {
        compose.setContent { FieldAtlasTheme {
            MoreScreen(proof, InferenceState.Idle, {}, {}, {}, {}, packs = listOf(model, knowledge))
        } }
        compose.onNodeWithText("Offline readiness").performClick()
        compose.onNodeWithText("Ready to work offline").assertIsDisplayed()
        compose.onNodeWithText("1 in research").assertIsDisplayed()
        compose.onNodeWithText("Built in").assertIsDisplayed()
        compose.onNodeWithText("Only downloads need internet.").assertIsDisplayed()
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
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").assertIsDisplayed()
        compose.onNodeWithText("Qwen3.5 2B").performScrollTo().assertIsDisplayed()
        // A lazy list composes only what is near the screen: scroll the list itself to the row.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Offline readiness"))
        compose.onNodeWithText("Offline readiness").performClick()
        compose.onNodeWithText("Only downloads need internet.").performScrollTo().assertIsDisplayed()
        capture("readiness-dark-double-text")
    }

    @Test fun uploadHasOnlyThreeChoicesAndFileGoesStraightToSystemCallback() {
        var picked = 0
        compose.setContent { FieldAtlasTheme {
            ResearchScreen(ResearchUiState(), InferenceState.Ready, 1,
                onQuestionChange = {}, onSubmit = {}, onPrepareModel = {}, onOpenAnswer = {},
                onFilesClick = { picked++ })
        } }
        compose.onNodeWithContentDescription("Add a file").performScrollTo().performClick()
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
                onQuestionChange = { draft = it }, onSubmit = { submitted++ }, onPrepareModel = {}, onOpenAnswer = {})
        } }
        compose.onAllNodesWithText("Tap to try", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("A few places to start").performScrollTo()
        val heading = compose.onNodeWithText("A few places to start").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithText("Tap to try").fetchSemanticsNode().boundsInRoot
        assertEquals(heading.center.y, hint.center.y, 1f)
        assertTrue(heading.right <= hint.left)
        val first = compose.onNodeWithContentDescription("Use example: What role do chloroplasts play in photosynthesis?")
        first.performScrollTo()
        val a = first.fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithContentDescription("Use example: How do earthquake P waves differ from S waves?").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, b.top, 1f)
        assertTrue(a.right <= b.left)
        capture("suggestions-shared-hint-light")
        first.performClick()
        compose.runOnIdle { assertEquals("What role do chloroplasts play in photosynthesis?", draft); assertEquals(0, submitted) }
    }

    @Test fun sharedSuggestionHintFitsNarrowDarkLayoutAtDoubleTextSize() {
        var draft = ""
        var submitted = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ResearchScreen(ResearchUiState(), InferenceState.Ready, 1,
                        onQuestionChange = { draft = it }, onSubmit = { submitted++ },
                        onPrepareModel = {}, onOpenAnswer = {})
                }
            }
        } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("A few places to start"))
        compose.onAllNodesWithText("Tap to try", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Tap to try").assertIsDisplayed()
        val heading = compose.onNodeWithText("A few places to start").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithText("Tap to try").fetchSemanticsNode().boundsInRoot
        assertTrue(heading.right <= hint.left)
        assertEquals(heading.center.y, hint.center.y, 1f)
        capture("suggestions-shared-hint-dark-double-text")
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasContentDescription("Use example: How do earthquake P waves differ from S waves?"))
        compose.onNodeWithContentDescription("Use example: How do earthquake P waves differ from S waves?").performClick()
        compose.runOnIdle { assertEquals("How do earthquake P waves differ from S waves?", draft); assertEquals(0, submitted) }
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

    @Test fun quoteAndInlineCitationsKeepTheirSourceIndicesAndStayInTheLine() {
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
        // A mark inside a sentence keeps to the line it is on (WCAG 2.5.8's inline exception); the quote's source
        // row below keeps its full-size target.
        assertTrue(bounds.height <= 24 * density)
        val quoteSource = compose.onNodeWithText("Pump manual.txt").fetchSemanticsNode().boundsInRoot
        assertTrue(quoteSource.height >= 20 * density)
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
            FieldAtlasIcons.Globe, FieldAtlasIcons.MoreOptions, FieldAtlasIcons.Layers, FieldAtlasIcons.CollectionBook,
            FieldAtlasIcons.CollectionCross, FieldAtlasIcons.CollectionBowl, FieldAtlasIcons.CollectionPin,
            FieldAtlasIcons.CollectionLeaf)
        icons.forEach { assertTrue(it.name, it.root.size > 0) }
        assertEquals(32f, FieldAtlasIcons.Offline.viewportWidth)
    }

    @Test fun navigationKeepsAllFourTargetsVisibleAtDoubleTextSize() {
        lateinit var measurer: TextMeasurer
        lateinit var labelStyle: TextStyle
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                measurer = rememberTextMeasurer()
                // The tab label's own style (the navigation item in FieldAtlasApp), at its wider, selected weight.
                labelStyle = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    .copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
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
            // Each tab is named once, by its content description; the label under the icon is hidden from screen
            // readers, so its layout cannot be read through semantics. Measured in its own style instead, on one line it
            // must fit inside the tab (2 dp of padding each side), or it would be cut short with an ellipsis.
            val width = measurer.measure(label, labelStyle, maxLines = 1).size.width
            assertTrue("$label must remain fully readable ($width px in a ${bounds.width} px tab)", width <= bounds.width - 4f)
        }
        // At double text size the labels no longer fit four across, so the bar reflows to two rows of two.
        val first = compose.onNodeWithContentDescription("Research").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Library").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithContentDescription("History").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue("Large text navigation should reflow", third.top >= first.bottom)
        capture("navigation-double-text")
    }

    @Test fun historyTopicMenuOffersPinShareCopyAndDeleteWithFullTapTargets() {
        val question = "Which filter applies to serial 2345?"
        var continued = false
        var deleted = false
        val evidence = Evidence("attachment:pump", "chunk", "Helios_Pump_Revision_B.txt", "Attached file · text", "Use filter K-9.", 1.0)
        compose.setContent { FieldAtlasTheme {
            HistoryScreen(listOf(AnswerRecord(question, "For serial **2345**, use Revision B: filter K-9. [S1]",
                System.currentTimeMillis(), listOf(evidence.title), listOf(evidence))), onDelete = { deleted = true }, onContinue = { continued = true })
        } }
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val more = compose.onNodeWithContentDescription("More for $question").assertHasClickAction()
        val bounds = more.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= 40 * density - 1)
        more.performClick()
        listOf("Pin", "Share", "Copy", "Delete").forEach { compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed() }
        capture("history-menu-light")
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(deleted); assertFalse(continued) }
        compose.onNodeWithText(question).performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test fun historyPressedFeedbackStaysInsideRoundedCorners() {
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
        val card = compose.onNode(hasClickAction() and hasAnyDescendant(hasText(question)), useUnmergedTree = true)
        val bounds = card.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            card.performTouchInput { down(androidx.compose.ui.geometry.Offset(center.x, 24.dp.toPx())) }
            compose.mainClock.advanceTimeBy(400)
            val root = compose.onNode(isRoot() and hasAnyDescendant(hasText(question)))
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            val pixels = root.captureToImage().toPixelMap()
            val left = (bounds.left - rootBounds.left).toInt() + 2
            val right = (bounds.right - rootBounds.left).toInt() - 3
            val top = (bounds.top - rootBounds.top).toInt() + 2
            assertEquals("upper-left press highlight must be clipped", background, pixels[left, top].toArgb())
            assertEquals("upper-right press highlight must be clipped", background, pixels[right, top].toArgb())
        } finally {
            card.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun researchStopRemainsReachableWithLargeTextInDarkMode() {
        // While an answer is researched, Stop is the button in the answer page's question box (the Research home's
        // old progress card, with its own Stop, is gone). At twice the text size, in dark mode, on a small screen, it
        // stays on screen, enabled, at least 48 dp to touch, and it stops the run.
        var stops = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    AnswerScreen(ResearchUiState(question = "A long question about a saved document",
                        phase = ResearchPhase.Generating, startedAtNanos = System.nanoTime()),
                        onBack = {}, onAskAnother = {}, onCitation = {}, onStop = { stops++ })
                }
            }
        } }
        compose.onNodeWithText("Working on this answer · Stop ends it").assertExists()
        val stop = compose.onNodeWithContentDescription("Stop research").assertIsDisplayed().assertIsEnabled()
        // Drawn at 40 dp; Material's minimum interactive size makes it 48 dp to touch (1 px is 1 dp here).
        val touch = stop.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Stop must be at least 48 dp to touch", touch.width >= 48f && touch.height >= 48f)
        stop.performClick()
        assertEquals(1, stops)
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "ui-review")
        check(directory.isDirectory || directory.mkdirs())
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
