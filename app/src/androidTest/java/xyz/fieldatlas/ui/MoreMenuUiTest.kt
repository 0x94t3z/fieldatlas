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
    private val model = InstalledAsset("model", "1", PackType.MODEL, "Qwen3.5 2B — Q4_K_M Compact", "Apache-2.0", 1_400_000_000, "a".repeat(64), "/fixture")
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
            "Troubleshooting" to "Support report", "Privacy & offline use" to "Privacy & offline use",
            "Licences" to "Licences").forEach { (menu, title) ->
            compose.onNodeWithText(menu).performScrollTo().performClick()
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

    @Test fun modelDetailsKeepShortIdentityAndLibraryManagement() {
        var library = 0
        render(onLibrary = { library++ })
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsOn()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Qwen3.5 2B").assertIsDisplayed()
        compose.onNodeWithText("1.4 GB · version 1").assertExists()
        capture("after-model-details")
        compose.onNodeWithText("Model details and options").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, library) }
    }

    @Test fun expandedModelKeepsDetailsAtTheTrailingEdge() {
        render()
        compose.onNodeWithText("Answer model").performClick()
        val toggle = compose.onNodeWithContentDescription("Model loaded").fetchSemanticsNode().boundsInRoot
        val details = compose.onNodeWithText("Details").fetchSemanticsNode().boundsInRoot
        assertTrue("Details must not crowd the memory switch", details.left > toggle.right)
        assertEquals("Controls share a baseline", toggle.center.y, details.center.y, 2f)
        capture("after-model-expanded-alignment")
    }

    @Test fun expandedModelLargeTextKeepsBothControlsReachable() {
        render(dark = true, scale = 2f)
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Details").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close Answer model").assertIsDisplayed()
    }

    @Test fun modelSwitchReleasesOnlyWhenLoaded() {
        var released = 0; var prepared = 0
        render(onPrepare = { prepared++ }, onRelease = { released++ })
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").performClick()
        compose.runOnIdle { assertEquals(1, released); assertEquals(0, prepared) }
    }

    @Test fun modelSwitchPreparesWhenNotLoaded() {
        var prepared = 0
        render(inference = InferenceState.Idle, onPrepare = { prepared++ })
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsOff().performClick()
        compose.runOnIdle { assertEquals(1, prepared) }
    }

    @Test fun modelSwitchIsDisabledDuringResearch() {
        render(inference = InferenceState.Generating)
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test fun modelSwitchIsDisabledDuringPreparation() {
        render(inference = InferenceState.Loading)
        compose.onNodeWithText("Answer model").performClick()
        compose.onNodeWithContentDescription("Model loaded").assertIsOff().assertIsNotEnabled()
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
        compose.onNodeWithText("About Field Atlas").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to app and device").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close App & device").assertIsDisplayed()
    }

    @Test fun supportExportRequiresQuestionOptInAndHasAnEmptyState() {
        val exports = mutableListOf<Boolean>()
        render(onExport = { exports += it })
        compose.onNodeWithText("Troubleshooting").performScrollTo().performClick()
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
        compose.onNodeWithText("Troubleshooting").performScrollTo().performClick()
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
        compose.onNodeWithText("Licences").performScrollTo().performClick()
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
        listOf("Offline readiness" to "Offline readiness", "Troubleshooting" to "Support report",
            "Privacy & offline use" to "Privacy & offline use", "Licences" to "Licences").forEach { (menu, title) ->
            compose.onNodeWithText(menu).performScrollTo().performClick()
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("Close $title").assertIsDisplayed()
            capture("after-large-${title.replace(" ", "-").replace("&", "and")}")
            compose.onNodeWithContentDescription("Close $title").performClick()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "more-ui-review")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
