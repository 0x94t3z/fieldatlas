package xyz.fieldatlas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class KnowledgeSetupTest {
    @Test fun libraryRefreshFailureKeepsInstalledCollectionsAndImportAvailable() {
        val saved = xyz.fieldatlas.assets.InstalledAsset("mineralogy", "a", xyz.fieldatlas.assets.PackType.KNOWLEDGE,
            "Mineralogy", "CC0", 100, "a".repeat(64), "/unused")
        var refreshes = 0
        compose.setContent { FieldAtlasTheme {
            xyz.fieldatlas.ui.library.LibraryScreen(listOf(saved), {},
                availableKnowledge = emptyList(),
                catalogError = xyz.fieldatlas.assets.KnowledgeCatalogRepository.REFRESH_ERROR,
                onRefreshCatalog = { refreshes++ })
        } }
        compose.runOnIdle { assertEquals(0, refreshes) }
        compose.onNodeWithText("Mineralogy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Import a pack").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Refresh collections").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun refreshedCatalogShowsNewTopicAndPreservesActiveCancel() {
        val entries = androidx.compose.runtime.mutableStateOf(listOf(biology))
        var cancelled = false
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = xyz.fieldatlas.assets.catalogWithActiveDownload(entries.value, biology),
                knowledgeDownloadKey = "biology:1", knowledgeDownloadedBytes = 100,
                onCancelKnowledgeDownload = { cancelled = true })
        } }
        compose.runOnIdle { entries.value = listOf(biology.copy(id = "mineralogy", title = "Mineralogy")) }
        compose.onNodeWithText("Mineralogy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(cancelled)
    }

    @Test fun catalogRefreshIsExplicitAndLoadingDisablesDuplicateClicks() {
        var calls = 0
        val refreshing = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), catalogRefreshing = refreshing.value,
                onRefreshCatalog = { calls++; refreshing.value = true })
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Refresh collections").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Refreshing collections…").assertIsNotEnabled()
        compose.onNodeWithText("Import a saved pack").performScrollTo().assertIsEnabled()
    }

    @get:Rule val compose = createComposeRule()
    private val biology = KnowledgeCatalogEntry("biology", "1", "Biology & longevity", "Local biology sources",
        "Public data", 1567877980, "0".repeat(64), "https://example.org/biology.fapack", true)

    @Test fun biologyDownloadRequiresConfirmationAndUsesSelectedCatalogEntry() {
        var requested: KnowledgeCatalogEntry? = null
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), onDownloadKnowledge = { requested = it })
        } }
        compose.onNodeWithContentDescription("Download Biology & longevity").performScrollTo().performClick()
        assertEquals(null, requested)
        compose.onNodeWithText("Download Biology & longevity?").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        assertEquals(biology, requested)
    }

    @Test fun activeDownloadOffersCancelAndPreventsLeavingSetup() {
        var cancelled = false
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), knowledgeDownloadKey = "biology:1", knowledgeDownloadedBytes = 100,
                onCancelKnowledgeDownload = { cancelled = true })
        } }
        compose.onNodeWithText("Start researching").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Download Qwen3.5 2B").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(cancelled)
    }
}
