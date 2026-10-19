package xyz.fieldatlas.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.assets.DownloadFailure
import xyz.fieldatlas.assets.DownloadItem
import xyz.fieldatlas.assets.DownloadQueueState
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.PackType

class DownloadRowsTest {
    private val places = KnowledgeCatalogEntry("wikivoyage-places", "2026.09.1-leviathan", "Travel places", "", "CC-BY-SA-4.0",
        215_077_879, "0".repeat(64), "https://example.org/p.fapack")
    private val item = DownloadItem.Knowledge(places)
    private val other = DownloadItem.Voice

    @Test fun aRowSaysWhereItsDownloadStands() {
        val idle = DownloadQueueState()
        assertEquals(RowState.Get(0), rowState(item, idle, installed = false, saved = 0))
        assertEquals(RowState.Get(5_000_000), rowState(item, idle, installed = false, saved = 5_000_000))
        assertEquals(RowState.Done, rowState(item, idle.copy(active = item), installed = true, saved = 0))
        assertEquals(RowState.Waiting(5), rowState(item, idle.copy(active = item, waitingForWifi = true), false, 5))
        assertEquals(RowState.Checking, rowState(item, idle.copy(active = item, bytes = item.bytes), false, 0))
        val running = rowState(item, idle.copy(active = item, bytes = 107_538_939, queued = listOf(other)), false, 0) as RowState.Running
        assertEquals("107 of 216 MB", running.line)  // "then 1 more" belongs to the bar, not the row
        assertEquals(0.5f, running.fraction, 0.01f)
        assertEquals(RowState.Queued(0, "Supertonic 3"), rowState(item, idle.copy(active = other, queued = listOf(item)), false, 0))
        assertEquals(RowState.Failed("No connection", 3), rowState(item,
            idle.copy(failures = mapOf(item.key to DownloadFailure(item, "No connection"))), false, 3))
    }

    @Test fun anUpdateSaysWhatItBringsOnlyWhenThatIsPlain() {
        fun installed(version: String, bytes: Long) = InstalledAsset("wikivoyage-places", version, PackType.KNOWLEDGE, "Travel places",
            "CC-BY-SA-4.0", bytes, "0", "/x")
        assertEquals("Smaller · faster search", updateNote(places, installed("2026.09.1", 651_000_000)))
        assertEquals("Faster search", updateNote(places, installed("2026.09.1", 220_000_000)))
        assertNull(updateNote(places.copy(version = "2026.10.01"), installed("2026.09.1", 220_000_000)))
    }
}
