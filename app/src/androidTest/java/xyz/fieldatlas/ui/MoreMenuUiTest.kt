package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.*
import xyz.fieldatlas.ui.more.MoreScreen
import xyz.fieldatlas.ui.more.licenceLabel
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Every More destination and its inner actions, isolated from the user's saved data. */
class MoreMenuUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val model = InstalledAsset("qwen3.5-2b-q4-k-m", "1", PackType.MODEL, "Qwen3.5 2B — Q4_K_M Compact", "Apache-2.0", 1_400_000_000, "a".repeat(64), "/fixture")
    private val knowledge = model.copy(id = "knowledge", type = PackType.KNOWLEDGE, title = "Encyclopedia")
    private val proof = ProofModel(
        listOf(ProofFact("Internet permission", "Present · downloads only", ProofOrigin.ManifestAudit, ProofState.Warning)) +
            (1..25).map { ProofFact("Verified pack $it", "knowledge 1 · ${"a".repeat(12)}…", ProofOrigin.ManifestAudit, ProofState.Pass) },
        listOf(ProofFact("Device", "Redmi fixture", ProofOrigin.OsReport, ProofState.Info),
            ProofFact("Physical memory", "4 GB", ProofOrigin.OsReport, ProofState.Info)),
        listOf(ProofFact("Total", "4000 ms", ProofOrigin.MeasuredQuery, ProofState.Info)))

    private fun render(dark: Boolean = false, scale: Float = 1f, logs: String = "",
        onExport: (Boolean) -> Unit = {}, onClear: () -> Unit = {}, onLibrary: () -> Unit = {},
        onSources: () -> Unit = {}, onBenchmark: () -> Unit = {},
        inference: InferenceState = InferenceState.Ready, onPrepare: () -> Unit = {}, onRelease: () -> Unit = {}) {
        compose.setContent { FieldAtlasTheme(darkTheme = dark) {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MoreScreen(proof, inference, onPrepare, onRelease, onExport, onBenchmark,
                        diagnosticsText = logs, onClearDiagnostics = onClear, packs = listOf(model, knowledge),
                        onOpenLibrary = onLibrary, onOpenDownloadSources = onSources)
                }
            }
        } }
    }

    @Test fun allInformationDestinationsOpenDirectlyAndClose() {
        render()
        listOf("Offline readiness" to "Offline readiness", "App & device" to "App & device",
            "Support report" to "Support report", "Privacy & offline use" to "Privacy & offline use",
            "Licences" to "Licences").forEach { (menu, title) ->
            tapRow(menu)
            compose.onNodeWithContentDescription("Close $title").assertIsDisplayed()
            capture("after-${title.replace(" ", "-").replace("&", "and")}")
            compose.onNodeWithContentDescription("Close $title").performClick()
        }
        compose.onNodeWithText("Preview support report").assertDoesNotExist()
        compose.onNodeWithText("View data and licences").assertDoesNotExist()
    }

    @Test fun sourcePerformanceAndLibraryActionsKeepTheirCallbacks() {
        var sources = 0; var benchmark = 0; var library = 0
        render(onSources = { sources++ }, onBenchmark = { benchmark++ }, onLibrary = { library++ })
        compose.onNodeWithText("Download sources").performScrollTo().performClick()
        compose.onNodeWithText("Performance check").performScrollTo().performClick()
        compose.onNodeWithText("Offline readiness").performScrollTo().performClick()
        compose.onNodeWithText("Manage in Library").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sources); assertEquals(1, benchmark); assertEquals(1, library) }
    }

    @Test fun modelRowShowsAndHidesTheFastStartSetting() {
        var library = 0
        render(onLibrary = { library++ })
        compose.onNodeWithText("Start answers faster").assertDoesNotExist()
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithText("Start answers faster").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start answers faster").assertIsDisplayed().assertIsOn()
        compose.onNodeWithText("Licence").assertDoesNotExist()
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithText("Start answers faster").assertDoesNotExist()
        // Showing the setting never leaves More.
        compose.runOnIdle { assertEquals(0, library) }
    }

    @Test fun fastStartSwitchSitsAtTheEdgeBelowTheModelRow() {
        render()
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        val model = compose.onNodeWithText("Qwen3.5 2B", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Start answers faster", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithContentDescription("Start answers faster").fetchSemanticsNode().boundsInRoot
        assertEquals("Rows share a text column", model.left, label.left, 2f)
        assertTrue("The switch sits after its label", toggle.left > label.right)
        assertTrue("The setting is below the model row", label.top > model.bottom)
        capture("after-model-rows")
    }

    @Test fun expandedModelLargeTextKeepsBothControlsReachable() {
        render(dark = true, scale = 2f)
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Qwen3.5 2B").performScrollTo().assertIsDisplayed()
    }

    @Test fun modelSwitchReleasesOnlyWhenLoaded() {
        var released = 0; var prepared = 0
        render(onPrepare = { prepared++ }, onRelease = { released++ })
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").performClick()
        compose.runOnIdle { assertEquals(1, released); assertEquals(0, prepared) }
    }

    @Test fun modelSwitchPreparesWhenNotLoaded() {
        var prepared = 0
        render(inference = InferenceState.Idle, onPrepare = { prepared++ })
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").assertIsOff().performClick()
        compose.runOnIdle { assertEquals(1, prepared) }
    }

    @Test fun modelSwitchIsDisabledDuringResearch() {
        render(inference = InferenceState.Generating)
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Qwen3.5 2B").assertIsDisplayed()
    }

    @Test fun modelSwitchIsDisabledDuringPreparation() {
        render(inference = InferenceState.Loading)
        compose.onNodeWithText("Qwen3.5 2B").performClick()
        compose.onNodeWithContentDescription("Start answers faster").assertIsOff().assertIsNotEnabled()
    }

    @Test fun longTechnicalDetailsKeepPinnedCloseAndAboutBackNavigation() {
        render()
        compose.onNodeWithText("App & device").performScrollTo().performClick()
        compose.onNodeWithText("Redmi fixture").assertIsDisplayed()
        compose.onNodeWithText("Verified pack 25").assertDoesNotExist()
        compose.onNodeWithText("Technical details").performScrollTo().performClick()
        compose.onNodeWithText("Verified pack 25").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close App & device").assertIsDisplayed()
        capture("after-device-scrolled")
        compose.onNodeWithContentDescription("Back to app and device").assertIsDisplayed().performClick()
        compose.onNodeWithText("Storage").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close App & device").assertIsDisplayed()
    }

    @Test fun supportExportRequiresQuestionOptInAndHasAnEmptyState() {
        val exports = mutableListOf<Boolean>()
        render(onExport = { exports += it })
        compose.onNodeWithText("Support report").performScrollTo().performClick()
        compose.onNodeWithText("No recent issues recorded.").assertExists()
        compose.onNodeWithContentDescription("Include latest question").assertIsOff()
        compose.onNodeWithText("Save support report").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Include latest question").performScrollTo().performClick()
        compose.onNodeWithText("Save support report").performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), exports) }
    }

    @Test fun clearingIssuesNeedsConfirmationAndDoesNotClearOnKeep() {
        var cleared = 0
        render(logs = (1..40).joinToString("\n") { "[Model] Earlier issue $it" }, onClear = { cleared++ })
        compose.onNodeWithText("Support report").performScrollTo().performClick()
        compose.onNodeWithText("Clear details").assertDoesNotExist()
        compose.onNodeWithText("Recent technical details").performScrollTo().performClick()
        compose.onNodeWithText("Clear details").performScrollTo().performClick()
        compose.onNodeWithText("Clear recent details?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertEquals(0, cleared) }
        compose.onNodeWithText("Clear details").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun everyBundledLicenceOpensAndKeepsBackAndCloseVisibleAfterScrolling() {
        render()
        tapRow("Licences")
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val paths = assets.list("licenses").orEmpty().filter { it.endsWith(".txt") && it != "NOTICE.txt" }.sorted().map { "licenses/$it" } +
            assets.list("ocr/licenses").orEmpty().sorted().map { "ocr/licenses/$it" }
        compose.waitUntil(5000) { compose.onAllNodesWithText(licenceLabel(paths.first())).fetchSemanticsNodes().isNotEmpty() }
        (listOf("App notices" to "licenses/NOTICE.txt", "Third-party notices" to "licenses/THIRD_PARTY_NOTICES.md") + paths.map { licenceLabel(it) to it }).forEach { (label, path) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Loading licence…").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Back to licences").assertIsDisplayed()
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("Close Licences").assertIsDisplayed()
            if (path == "licenses/manrope-OFL.txt") capture("after-licence-scrolled")
            compose.onNodeWithContentDescription("Back to licences").performClick()
        }
    }

    @Test fun largeTextDarkSheetsKeepCloseVisibleAndActionsReachable() {
        render(dark = true, scale = 2f)
        listOf("Offline readiness" to "Offline readiness", "Support report" to "Support report",
            "Privacy & offline use" to "Privacy & offline use", "Licences" to "Licences").forEach { (menu, title) ->
            tapRow(menu)
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("Close $title").assertIsDisplayed()
            capture("after-large-${title.replace(" ", "-").replace("&", "and")}")
            compose.onNodeWithContentDescription("Close $title").performClick()
        }
    }

    /**
     * Taps a More row only once it is laid out on screen. The list builds the next row ahead of time without placing
     * it, and the scroll actions read such a row as already in view, so a tap would land on whatever is drawn there
     * (seen at 200% text: the model card opened instead of Privacy & offline use).
     */
    private fun tapRow(text: String) {
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText(text))
        var swipes = 0
        while (!compose.onNodeWithText(text).fetchSemanticsNode().layoutInfo.isPlaced) {
            check(swipes < 16) { "$text never came on screen" }
            // The rows these tests open lie further down; if a row is ever behind instead, scroll back.
            list.performTouchInput { if (swipes < 8) swipeUp() else swipeDown() }
            swipes++
        }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "more-ui-review")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
