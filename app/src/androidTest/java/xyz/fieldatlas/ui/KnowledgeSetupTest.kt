package xyz.fieldatlas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        assertTrue(cancelled)
    }

    @Test fun catalogRefreshIsExplicitAndLoadingDisablesDuplicateClicks() {
        var calls = 0
        val refreshing = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), catalogRefreshing = refreshing.value,
                onRefreshCatalog = { calls++; refreshing.value = true }, online = true)
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Refreshing…").assertIsNotEnabled()
        compose.onNodeWithText("Import a saved pack").performScrollTo().assertIsEnabled()
    }

    @get:Rule val compose = createComposeRule()
    private val biology = KnowledgeCatalogEntry("biology", "1", "Biology & longevity", "Local biology sources",
        "Public data", 1567877980, "0".repeat(64), "https://example.org/biology.fapack", true)

    @Test fun biologyDownloadRequiresConfirmationAndUsesSelectedCatalogEntry() {
        var requested: KnowledgeCatalogEntry? = null
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), onDownloadKnowledge = { requested = it }, online = true)
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
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        assertTrue(cancelled)
    }

    private val model = xyz.fieldatlas.assets.InstalledAsset(
        xyz.fieldatlas.assets.RecommendedModel.id, "1", xyz.fieldatlas.assets.PackType.MODEL,
        xyz.fieldatlas.assets.RecommendedModel.title, "Apache 2.0", 100, "a".repeat(64), "/unused")

    @Test fun optionalDownloadDoesNotBlockResearchWithInstalledModel() {
        var continued = false
        compose.setContent { FieldAtlasTheme {
            SetupScreen(listOf(model), false, false, 0, null, {}, {}, {},
                knowledgeDownloadKey = "biology:1", onContinue = { continued = true }, online = true)
        } }
        compose.onNodeWithText("Start researching").assertIsEnabled().performClick()
        assertTrue(continued)
    }

    @Test fun importShowsFileNameAndExplainsWhyResearchMustWait() {
        compose.setContent { FieldAtlasTheme {
            SetupScreen(listOf(model), true, false, 0, null, {}, {}, {},
                importingName = "biology.fapack", online = false)
        } }
        compose.onNodeWithText("biology.fapack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Please wait while this pack is checked and installed.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
        compose.onNodeWithText("Your pack is importing. Please wait before starting research.").assertIsDisplayed()
    }

    @Test fun offlineCatalogUsesNeutralNoticeAndLeavesLocalImportAvailable() {
        compose.setContent { FieldAtlasTheme {
            SetupScreen(listOf(model), false, false, 0, null, {}, {}, {},
                availableKnowledge = listOf(biology), catalogError = "Refresh failed", online = false)
        } }
        compose.onNodeWithText("You’re offline. Import saved packs, or reconnect to download.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refresh failed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Download Biology & longevity").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import a saved pack").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Start researching").assertIsEnabled()
    }

    @Test fun modelImportReplacesDownloadAndShowsOneProgressCard() {
        compose.setContent { FieldAtlasTheme {
            SetupScreen(emptyList(), true, false, 0, null, {}, {}, {}, online = false,
                importingName = "qwen.fapack",
                importingPack = xyz.fieldatlas.assets.PackImportInfo(model.id, model.title, xyz.fieldatlas.assets.PackType.MODEL))
        } }
        compose.onNodeWithText("Qwen3.5 2B").assertIsDisplayed()
        compose.onNodeWithText("qwen.fapack").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download Qwen3.5 2B").assertDoesNotExist()
        compose.onAllNodesWithText("Please wait while this pack is checked and installed.").assertCountEquals(1)
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
        compose.onNodeWithText("Internet is needed for downloads.").assertDoesNotExist()
        val helper = compose.onNodeWithText("Your model is importing. Research will be ready when it finishes.")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val action = compose.onNodeWithText("Start researching").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Research action should remain at the bottom", root.bottom - action.bottom < 80.dp)
        assertTrue("Explanation should sit directly above the action", action.top - helper.bottom in 0.dp..24.dp)
    }

    @Test fun customKnowledgeImportDoesNotPretendToBeAModelImport() {
        compose.setContent { FieldAtlasTheme {
            SetupScreen(listOf(model), true, false, 0, null, {}, {}, {}, online = true,
                importingName = "local-history.fapack",
                importingPack = xyz.fieldatlas.assets.PackImportInfo("local", "Local history", xyz.fieldatlas.assets.PackType.KNOWLEDGE))
        } }
        compose.onNodeWithText("Local history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start researching").assertIsNotEnabled()
        compose.onNodeWithText("Your pack is importing. Please wait before starting research.").assertIsDisplayed()
        compose.onNodeWithText("Importing model").assertDoesNotExist()
    }
}
