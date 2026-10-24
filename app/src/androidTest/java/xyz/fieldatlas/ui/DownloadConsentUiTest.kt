package xyz.fieldatlas.ui

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.FieldAtlasApplication
import xyz.fieldatlas.assets.AppDownloads
import xyz.fieldatlas.assets.DownloadItem
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.library.LocalDownloads
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/**
 * The server is disclosed before a download can start. Setup hands a confirmed download to its callback. Library hands
 * it to the app's download queue, so its tests use the app's own download coordinator on a device with no network:
 * there, with "Only on Wi-Fi" on, a confirmed item waits for Wi-Fi and no transfer starts.
 */
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

    /**
     * Library's consent sheet: nothing is queued while it is open or after "Not now", the server is named without the
     * address's private query, and "Download · <size>" queues exactly this item, which waits for Wi-Fi.
     */
    private fun confirmQueued(downloads: AppDownloads, item: DownloadItem) {
        // The row's button, "Download <title>, <size>"; the open sheet's close button ("Close Download <title>") has no comma.
        val button = hasContentDescription("Download ${item.title},", substring = true)
        fun assertNothingQueued() {
            compose.waitForIdle()
            val state = downloads.state.value
            assertNull("Nothing may be queued before the person agrees", state.active)
            assertTrue(state.queued.isEmpty())
        }
        compose.onNode(button).performScrollTo().performClick()
        assertNothingQueued()
        compose.onNode(hasText("Source: mirror.test", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("private-query=hidden", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("It waits for Wi-Fi, then downloads by itself").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        assertNothingQueued()
        compose.onNode(button).performScrollTo().performClick()
        compose.onNode(hasText("Download · ", substring = true) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        val state = downloads.state.value
        assertEquals(item.key, state.active?.key)
        assertTrue("Offline, the download waits for Wi-Fi", state.waitingForWifi)
        assertTrue(state.queued.isEmpty())
    }

    /**
     * Runs [block] with the app's download coordinator, on a device with no network and nothing downloading. Afterwards
     * the test's item leaves the queue and both download settings files are put back exactly as they were.
     */
    private fun withOfflineQueue(block: (AppDownloads) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as FieldAtlasApplication).container
        assertNull("Run with the device offline (airplane mode): a confirmed download must not start a transfer",
            context.getSystemService(ConnectivityManager::class.java).activeNetwork)
        // Built on the main thread, where the app builds it and where its queue runs.
        val downloads = compose.runOnUiThread { container.downloads }
        val saved = listOf("downloads", "asset-downloads").associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        assertFalse("Nothing may be downloading", container.backgroundDownloads.state.value.active)
        assertNull(downloads.state.value.active)
        assertTrue(downloads.state.value.queued.isEmpty())
        assertFalse(downloads.unmetered)
        val onlyOnWifi = downloads.state.value.onlyOnWifi
        if (!onlyOnWifi) compose.runOnUiThread { downloads.queue.setOnlyOnWifi(true) }
        try {
            block(downloads)
            assertFalse("A held item starts no transfer", container.backgroundDownloads.state.value.active)
        } finally {
            compose.runOnUiThread {
                // A held item never started; pausing lets it go with nothing kept to resume.
                if (downloads.state.value.active != null) downloads.queue.pause()
                downloads.state.value.queued.forEach(downloads.queue::remove)
                if (downloads.state.value.onlyOnWifi != onlyOnWifi) downloads.queue.setOnlyOnWifi(onlyOnWifi)
            }
            saved.forEach { (name, values) ->
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                assertTrue(editor.commit())
            }
        }
        assertNull("The test's item has left the queue", downloads.state.value.active)
        assertEquals(saved, listOf("downloads", "asset-downloads").associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() })
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

    @Test fun libraryModelDisclosesSelectedServerBeforeDownload() = withOfflineQueue { downloads ->
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDownloads provides downloads) {
                LibraryScreen(emptyList(), {}, modelOffers = listOf(ModelOffers.recommended),
                    modelDownloadUrl = { "https://mirror.test/model.gguf?private-query=hidden" })
            }
        } }
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        confirmQueued(downloads, DownloadItem.Model(ModelOffers.recommended))
    }

    @Test fun libraryKnowledgeDisclosesSelectedServerBeforeDownload() = withOfflineQueue { downloads ->
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDownloads provides downloads) {
                LibraryScreen(emptyList(), {}, availableKnowledge = listOf(pack), knowledgeDownloadUrl = { mirror })
            }
        } }
        compose.onNodeWithText("Discover").performScrollTo().performClick()
        confirmQueued(downloads, DownloadItem.Knowledge(pack))
    }
}
