package xyz.fieldatlas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** The server is disclosed before any callback can start a download. No network is used. */
class DownloadConsentUiTest {
    @get:Rule val compose = createComposeRule()
    private val mirror = "https://mirror.test/download.fapack?private-query=hidden"
    private val pack = KnowledgeCatalogEntry("test-knowledge", "1", "Test knowledge", "Test sources",
        "CC0", 40_000_000, "0".repeat(64), "https://example.org/knowledge.fapack", true)

    private fun confirm(calls: () -> Int) {
        compose.runOnIdle { assertEquals(0, calls()) }
        compose.onNode(hasText("Source: mirror.test", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("private-query=hidden", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, calls()) }
    }

    @Test fun setupModelDisclosesSelectedServerBeforeDownload() {
        var calls = 0
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, { calls++ }, {},
                online = true, modelDownloadUrl = "https://mirror.test/model.gguf?private-query=hidden")
        } }
        compose.onNodeWithContentDescription("Download Qwen3.5 2B").performScrollTo().performClick()
        confirm { calls }
    }

    @Test fun setupKnowledgeDisclosesSelectedServerBeforeDownload() {
        var calls = 0
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {}, availableKnowledge = listOf(pack),
                online = true, onDownloadKnowledge = { calls++ }, knowledgeDownloadUrl = { mirror })
        } }
        compose.onNodeWithContentDescription("Download Test knowledge").performScrollTo().performClick()
        confirm { calls }
    }

    @Test fun libraryModelDisclosesSelectedServerBeforeDownload() {
        var calls = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            LibraryScreen(emptyList(), {}, modelOffers = listOf(ModelOffers.recommended),
                onDownloadModel = { calls++ }, modelDownloadUrl = { "https://mirror.test/model.gguf?private-query=hidden" })
        } }
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Download model, Qwen3.5 2B").performScrollTo().performClick()
        confirm { calls }
    }

    @Test fun libraryKnowledgeDisclosesSelectedServerBeforeDownload() {
        var calls = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            LibraryScreen(emptyList(), {}, availableKnowledge = listOf(pack),
                onDownloadKnowledge = { calls++ }, knowledgeDownloadUrl = { mirror })
        } }
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        compose.onNodeWithText("Download collection").performScrollTo().performClick()
        confirm { calls }
    }
}
