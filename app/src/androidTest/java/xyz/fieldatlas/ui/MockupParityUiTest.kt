package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.*

/** Isolated presentation fixtures: no downloads or changes to the user's real app data. */
class MockupParityUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val model = InstalledAsset("model", "1", PackType.MODEL, "Qwen3.5 2B", "Apache-2.0",
        1_400_000_000, "a".repeat(64), "/fixture")
    private val knowledge = model.copy(id = "knowledge", type = PackType.KNOWLEDGE,
        title = "Saved knowledge", installedBytes = 900_000_000)

    @Test fun compactArtworkRetainsSwitchSemanticsAndTheWholeTouchTarget() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val compact = mutableStateOf(false)
        compose.setContent { FieldAtlasTheme {
            FieldAtlasSwitch(checked.value, { checked.value = it },
                Modifier.semantics { contentDescription = "Use collection" }, enabled.value, compact.value)
        } }
        val target = compose.onNodeWithContentDescription("Use collection")
        target.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).assertIsOff()
        compose.onNodeWithTag("compact-switch-track", useUnmergedTree = true)
            .assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(26.dp)
        compose.onNodeWithTag("compact-switch-thumb", useUnmergedTree = true).assertWidthIsEqualTo(18.dp)
        // Outside the artwork, but inside the target: this must still toggle.
        target.performTouchInput { click(Offset(2f, 2f)) }
        target.assertIsOn()
        compose.runOnIdle { enabled.value = false }
        target.assertIsNotEnabled().performClick()
        compose.runOnIdle { assertTrue(checked.value); compact.value = true }
        compose.onNodeWithTag("compact-switch-track", useUnmergedTree = true)
            .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(22.dp)
        target.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test fun switchThumbFollowsRtlReadingDirection() {
        val checked = mutableStateOf(false)
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FieldAtlasSwitch(checked.value, { checked.value = it }, Modifier.semantics { contentDescription = "RTL switch" })
            }
        } }
        val thumb = compose.onNodeWithTag("compact-switch-thumb", useUnmergedTree = true)
        val before = thumb.fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithContentDescription("RTL switch").performClick()
        compose.waitForIdle()
        assertTrue(thumb.fetchSemanticsNode().boundsInRoot.left < before)
    }

    @Test fun setupHasTheApprovedOrderAndDistinctBadgeWashes() {
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {}, online = true)
        } }
        compose.onNodeWithText("Field Atlas").assertIsDisplayed()
        compose.onNodeWithText("Download sources").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val offline = compose.onNodeWithText("Offline").fetchSemanticsNode().boundsInRoot
        val cited = compose.onNodeWithText("Cited").fetchSemanticsNode().boundsInRoot
        val private = compose.onNodeWithText("Private").fetchSemanticsNode().boundsInRoot
        assertTrue(offline.left < cited.left && cited.left < private.left)
        compose.onNodeWithText("Import .fapack").assertIsDisplayed()
        compose.onNodeWithText("Model or knowledge pack").assertIsDisplayed()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        fun wash(rect: androidx.compose.ui.geometry.Rect) = pixels[
            (rect.center.x - root.left).toInt(), (rect.top - root.top - 3).toInt()]
        assertEquals(Color(0xFFDCE8DB), wash(offline))
        assertEquals(Color(0xFFF1E2B9), wash(cited))
        assertEquals(Color(0xFFF2DED2), wash(private))
        capture("setup-approved-colors")
    }

    @Test fun setupFailureKeepsTheActualErrorSavedProgressAndOfflineRetryState() {
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, "Connection interrupted", {}, {}, {}, online = false,
                resumableModelBytes = 64_000_000, modelDownloadError = "Connection interrupted")
        } }
        compose.onNodeWithText("Download interrupted").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Connection interrupted").assertExists()
        compose.onNodeWithText("64 MB saved. Your progress is kept.").assertExists()
        compose.onNodeWithText("Go online to retry.").assertExists()
        compose.onNodeWithContentDescription("Retry download Qwen3.5 2B").assertIsNotEnabled()
        capture("setup-interrupted-offline")
    }

    @Test fun libraryDiscoverReachesTheSameSourcesAndStorageUsesRealBytes() {
        var sources = 0
        var toggled: Boolean? = null
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            LibraryScreen(listOf(model, knowledge), {}, onToggleResearch = { _, value -> toggled = value },
                onOpenDownloadSources = { sources++ })
        } }
        compose.onNodeWithContentDescription("Use Saved knowledge in research").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, toggled) }
        capture("library-approved-switches")
        compose.onNodeWithText("Storage").performScrollTo().performClick()
        compose.onNodeWithText("2.3 GB on this phone").assertIsDisplayed()
        compose.onNodeWithText("2 packs").assertIsDisplayed()
        capture("storage-actual-fixture-bytes")
        compose.onNodeWithContentDescription("Close Storage").performClick()
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        compose.onNodeWithText("Download sources").performClick()
        compose.runOnIdle { assertEquals(1, sources) }
        capture("library-discover-sources")
    }

    @Test fun readyAnswerStaysReadableAtLargeTextAndKeepsAllExistingActions() {
        var opened = 0
        var another = 0
        var submitted = 0
        val evidence = Evidence("attachment:pump", "chunk", "Pump manual.txt", "Attached file · text", "Use filter K-9.", 1.0)
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                androidx.compose.material3.Surface(Modifier.width(320.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    ResearchScreen(ResearchUiState(question = "Which filter applies?", phase = ResearchPhase.Complete,
                        answer = "Use **Revision B**, filter K-9. [S1]", sources = listOf(evidence),
                        metrics = ResearchMetrics(10, 20, 4000, 20, setOf("S1"), false)),
                        InferenceState.Ready, 1, onQuestionChange = {}, onSubmit = { submitted++ },
                        onStop = {}, onPrepareModel = {}, onOpenAnswer = { opened++ },
                        onAskAnotherQuestion = { another++ })
                }
            }
        } }
        compose.onNodeWithText("Answer ready").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 local source cited.").assertExists()
        compose.onNodeWithText("Took 4s").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Read answer"))
        // A 200% text card is taller than a normal one. Scroll it into the safe tap area,
        // not just partially into view above Android's navigation bar.
        compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText("Read answer").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("ready-answer-dark-large-before-open")
        compose.onNodeWithText("Read answer").performClick()
        compose.runOnIdle { assertEquals(1, opened); assertEquals(0, submitted) }
        capture("ready-answer-dark-large")
        compose.onNodeWithText("Ask another question").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, another); assertEquals(0, submitted) }
    }

    @Test fun libraryBareImportKeepsItsTapTargetAndTransferGuards() {
        val transfer = mutableStateOf(0)
        var imports = 0
        compose.setContent { FieldAtlasTheme {
            LibraryScreen(listOf(model), { imports++ }, importing = transfer.value == 1,
                modelDownloading = transfer.value == 2,
                knowledgeDownloadKey = if (transfer.value == 3) "knowledge" else null)
        } }
        val button = compose.onNodeWithContentDescription("Import a pack")
        button.assertIsEnabled().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        for (state in 1..3) {
            compose.runOnIdle { transfer.value = state }
            button.assertIsNotEnabled().performClick()
            compose.runOnIdle { assertEquals(1, imports) }
        }
        compose.runOnIdle { transfer.value = 0 }
        button.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, imports) }
    }

    @Test fun bareUtilityButtonNeverPaintsAWhitePressBackground() {
        var clicks = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            androidx.compose.material3.Surface(color = Color(0xFF111A15)) {
                FieldAtlasBareIconButton({ clicks++ }, "Copy", FieldAtlasIcons.Copy)
            }
        } }
        val button = compose.onNodeWithContentDescription("Copy")
        button.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(Color(0xFF111A15), pixels[8, 8])
        button.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun modelActionsKeepDetailsBusyProtectionAndDeleteConfirmation() {
        val other = model.copy(id = "other", title = "Other model")
        val busy = mutableStateOf(true)
        var activated: InstalledAsset? = null
        var deleted = false
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            LibraryScreen(listOf(model, other), {}, modelBusy = busy.value,
                onActivateModel = { activated = it }, onDeletePack = { deleted = true })
        } }
        compose.onNodeWithContentDescription("More options for Other model").performScrollTo().performClick()
        compose.onNodeWithText("Use this model").assertIsNotEnabled()
        compose.runOnIdle { assertNull(activated); busy.value = false }
        compose.onNodeWithText("Use this model").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(other, activated) }
        compose.onNodeWithContentDescription("More options for Qwen3.5 2B").performScrollTo().performClick()
        compose.onNodeWithText("Use this model").assertDoesNotExist()
        compose.onNodeWithText("Delete model from phone").assertIsDisplayed()
        capture("model-actions-approved")
        compose.onNodeWithContentDescription("Model details").performClick()
        compose.onNodeWithText("Apache 2.0").assertExists()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("More options for Qwen3.5 2B").performClick()
        compose.onNodeWithText("Delete model from phone").performClick()
        compose.onNodeWithText("Delete Qwen3.5 2B?").assertIsDisplayed()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertFalse(deleted) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "mockup-parity-review")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            // Capture the composed phone, including modal windows, not just one Compose root.
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
