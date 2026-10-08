package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import xyz.fieldatlas.assets.KnowledgeCatalog
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Setup presentation only: fixtures never download, import, or alter the app's saved packs. */
class SetupPolishUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val catalog = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("knowledge/catalog.json").bufferedReader().use { KnowledgeCatalog.parse(it.readText()).packs }
    private val model = InstalledAsset(ModelOffers.recommended.id, "1", PackType.MODEL,
        ModelOffers.recommended.title, "Apache 2.0", 100, "a".repeat(64), "/fixture")
    private val saved = catalog.map {
        InstalledAsset(it.id, it.version, PackType.KNOWLEDGE, it.title, it.license,
            it.bytes, it.sha256, "/fixture")
    }

    @Test fun offlineInstalledRowsHaveOneStatusAndImportStillWorks() {
        var imports = 0
        var sources = 0
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            SetupScreen(saved, false, false, 0, null, { imports++ }, {}, {},
                availableKnowledge = catalog, online = false, onOpenDownloadSources = { sources++ })
        } }
        compose.onNodeWithText("You’re offline. Import saved packs, or reconnect to download.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download Qwen3.5 2B").assertIsNotEnabled()
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
        capture("setup-polished-top")
        for (entry in catalog) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(entry.title))
            compose.onAllNodesWithText(entry.title).onLast().assertIsDisplayed()
            assertReadable(entry.title)
        }
        capture("setup-polished-bottom")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Download sources"))
        compose.onNodeWithText("Download sources").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, sources) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Import .fapack"))
        compose.onNodeWithText("Import .fapack").performClick()
        compose.runOnIdle { assertEquals(1, imports) }
    }

    @Test fun installedCollectionHasOnlyOneTextualStatus() {
        val entry = catalog.first { it.title.contains("Cryptography") }
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            SetupScreen(saved, false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(entry), online = true)
        } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(entry.title))
        compose.onAllNodesWithText("Installed").assertCountEquals(1)
        compose.onNodeWithContentDescription("Download ${entry.title}").assertDoesNotExist()
        assertReadable(entry.title)
    }

    @Test fun freshSetupKeepsDownloadsBehindConfirmationAndFullTouchTargets() {
        var downloads = 0
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            SetupScreen(emptyList(), false, false, 0, null, {}, { downloads++ }, {},
                availableKnowledge = catalog, online = true)
        } }
        val action = compose.onNodeWithContentDescription("Download Qwen3.5 2B")
        action.assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture("setup-polished-fresh")
        action.performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
        compose.onNodeWithText("Download Qwen3.5 2B?").assertIsDisplayed()
        compose.onNode(hasText("Source: huggingface.co", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun darkSetupKeepsInstalledModelReadyWithoutRepeatedStatus() {
        var continued = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            SetupScreen(saved + model, false, false, 0, null, {}, {}, {},
                availableKnowledge = catalog, online = true, onContinue = { continued++ })
        } }
        capture("setup-polished-dark-top")
        compose.onNodeWithText("Start researching").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, continued) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(catalog.last().title))
        capture("setup-polished-dark-bottom")
    }

    @Test fun narrowLargeTextPreservesFullNamesImportAndDownloadActions() {
        val entry = catalog.first { it.title.contains("Cryptography") }
        var imports = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    SetupScreen(emptyList(), false, false, 0, null, { imports++ }, {}, {},
                        availableKnowledge = listOf(entry), online = true)
                }
            }
        } }
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasContentDescription("Download ${entry.title}"))
        compose.onNodeWithContentDescription("Download ${entry.title}")
            .assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        assertReadable(entry.title)
        capture("setup-polished-large-text")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Import .fapack"))
        compose.onNodeWithText("Import .fapack").performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        compose.onNodeWithText("Start researching").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun resumedDownloadKeepsPauseAndVerificationStates() {
        var resumed = 0
        var paused = 0
        val downloading = androidx.compose.runtime.mutableStateOf(false)
        val bytes = androidx.compose.runtime.mutableStateOf(64_000_000L)
        compose.setContent { FieldAtlasTheme(darkTheme = false) {
            SetupScreen(emptyList(), false, downloading.value, bytes.value, null, {},
                { resumed++; downloading.value = true }, { paused++ },
                online = true, resumableModelBytes = 64_000_000L)
        } }
        compose.onNodeWithContentDescription("Resume Qwen3.5 2B").performClick()
        compose.runOnIdle { assertEquals(0, resumed) }
        compose.onNode(hasText("Resume") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, resumed) }
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, paused); bytes.value = ModelOffers.recommended.bytes }
        compose.onNodeWithText("Verifying and installing…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pause").assertDoesNotExist()
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
    }

    private fun assertReadable(title: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(title).onLast()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The title must have a layout", layouts.isNotEmpty())
        val layout = layouts.single()
        // Compose can retain a paragraph wider than a shrink-wrapped Text node even when
        // its actual lines fit. Check rendered line bounds, not that paragraph box.
        assertFalse("No collection name may be truncated: $title", layout.multiParagraph.didExceedMaxLines)
        for (line in 0 until layout.lineCount) {
            assertTrue("Title line must fit horizontally: $title",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("Title line must fit vertically: $title", layout.getLineBottom(line) <= layout.size.height + 1f)
        }
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "setup-ui-review")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create setup screenshot directory" }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
