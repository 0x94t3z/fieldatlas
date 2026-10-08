package xyz.fieldatlas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.DownloadArtifact
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.ui.setup.DownloadSourcesSheet
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class DownloadSourcesUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val model = DownloadArtifact.model(ModelOffers.recommended)

    @Test fun firstRunCanReachAdvancedSourcesAndLocalImportWhileOffline() {
        var sources = 0
        var imports = 0
        compose.setContent { FieldAtlasTheme {
            SetupScreen(packs = emptyList(), importing = false, downloading = false, downloadedBytes = 0,
                error = null, onImportPack = { imports++ }, onDownloadModel = {}, onCancelDownload = {},
                online = false, onOpenDownloadSources = { sources++ })
        } }
        compose.onNodeWithText("Download sources").performScrollTo().performClick()
        compose.onNodeWithText("Import .fapack").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sources); assertEquals(1, imports) }
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
    }

    @Test fun editingRequiresExplicitSaveAndRejectsInsecureUrls() {
        var saves = 0
        compose.setContent { FieldAtlasTheme {
            DownloadSourcesSheet(listOf(model), emptyMap(), { _, _ -> saves++; null }, { null }, {})
        } }
        compose.onNodeWithText(model.title).performClick()
        compose.onNodeWithText("HTTPS file URL").performTextReplacement("http://mirror.test/model.gguf")
        compose.onNodeWithText("Save source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("Use an HTTPS URL ending in .gguf, without a username, password or fragment.").assertExists()
        compose.onNodeWithText("HTTPS file URL").performTextReplacement("https://mirror.test/model.gguf")
        compose.onNodeWithText("Downloads will contact mirror.test. That server can see your IP address, not your questions.").assertExists()
        compose.onNodeWithText("Save source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun failedSaveStaysInEditorAndCloseDoesNotSave() {
        var saves = 0
        var closed = 0
        compose.setContent { FieldAtlasTheme {
            DownloadSourcesSheet(listOf(model), emptyMap(), { _, _ -> saves++; "Couldn't save the source. Try again." },
                { null }, { closed++ })
        } }
        compose.onNodeWithText(model.title).performClick()
        compose.onNodeWithText("Save source").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't save the source. Try again.").assertExists()
        compose.onNodeWithContentDescription("Close download sources").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(1, closed) }
    }

    @Test fun resetOnlyCallsResetAndReturnsToSourceList() {
        var resets = 0
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            DownloadSourcesSheet(listOf(model), mapOf(model.key to "https://mirror.test/model.gguf"),
                { _, _ -> error("Must not save") }, { resets++; null }, {})
        } }
        compose.onNodeWithText("Custom · mirror.test").assertExists()
        compose.onNodeWithText(model.title).performClick()
        compose.onNodeWithText("Use default source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, resets) }
        compose.onNodeWithText("HTTPS file URL").assertDoesNotExist()
    }
}
