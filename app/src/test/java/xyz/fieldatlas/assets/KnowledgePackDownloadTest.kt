package xyz.fieldatlas.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnowledgePackDownloadTest {
    @Test fun expandedSizeIsReservedBeforeAnyNetworkTransfer() = kotlinx.coroutines.runBlocking {
        val root = temp.newFolder("compressed-preflight")
        val pack = essentials.copy(installedBytes = StorageBudget.MAX_TOTAL_BYTES)
        val partial = paused(root, pack)
        val downloader = KnowledgePackDownload(root, AssetRegistry(root), AssetImporter(root))
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { downloader.install(pack) { error("Must not download") } }
        }
        assertEquals(400L, partial.length())
        assertFalse(File(root, "pack-staging").exists())
    }
    @get:Rule val temp = TemporaryFolder()

    private val essentials = KnowledgeCatalogEntry("osm-essentials", "2026.10.05", "Essentials",
        "Pharmacies, hospitals and ATMs worldwide", "ODbL-1.0", 1_000, "a".repeat(64),
        "https://example.org/osm-essentials-2026.10.05.fapack")

    private fun paused(root: File, pack: KnowledgeCatalogEntry): File =
        File(root, "knowledge-downloads/${pack.id}-${pack.version}.part").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(400)) }

    @Test fun aPartialDownloadThatCanNoLongerFinishIsDiscarded() {
        val root = temp.newFolder("files")
        val pack = KnowledgeCatalogEntry("osm-vegan-places", "2026.10.02", "Vegan places", "Places", "ODbL-1.0", 1_000,
            "a".repeat(64), "https://example.org/osm-vegan-places-2026.10.02.fapack")
        val replacement = pack.copy(version = "2026.10.05", url = "https://example.org/osm-vegan-places-2026.10.05.fapack")
        val partial = paused(root, pack)
        val download = KnowledgePackDownload(root, AssetRegistry(File(root, "assets")), AssetImporter(root))
        assertEquals(400L, download.resumableBytes(pack))
        assertTrue(download.discardPartial(pack, listOf(replacement)))
        assertFalse(partial.exists())
        assertEquals(0L, download.resumableBytes(pack))
    }

    @Test fun aPartialDownloadSurvivesAnEntryThatOnlyChangedItsWording() {
        // A refresh or app update changed the paused entry's wording, category, recommended flag or
        // address but not its archive, so the saved bytes still verify.
        val root = temp.newFolder("reworded")
        val download = KnowledgePackDownload(root, AssetRegistry(File(root, "assets")), AssetImporter(root))
        val vegan = KnowledgeCatalogEntry("osm-vegan-places", "2026.10.05", "Vegan places", "Places", "ODbL-1.0", 1_000,
            "b".repeat(64), "https://example.org/osm-vegan-places-2026.10.05.fapack")
        for (listed in listOf(
            essentials.copy(description = "Pharmacies, hospitals, ATMs and toilets worldwide"),
            essentials.copy(title = "Essentials worldwide"),
            essentials.copy(recommended = true),
            essentials.copy(category = "Places"),
            essentials.copy(url = "https://example.org/rehosted/osm-essentials-2026.10.05.fapack"),
        )) {
            val partial = paused(root, essentials)
            assertFalse(download.discardPartial(essentials, listOf(vegan, listed)))
            assertTrue(partial.exists())
            assertEquals(400L, download.resumableBytes(listed))
        }
    }

    @Test fun aPartialDownloadOfAChangedArchiveUnderTheSameVersionIsDiscarded() {
        // Under a new size or checksum the saved bytes could never verify and would only hold the space.
        val root = temp.newFolder("rebuilt")
        val download = KnowledgePackDownload(root, AssetRegistry(File(root, "assets")), AssetImporter(root))
        for (listed in listOf(
            essentials.copy(sha256 = "c".repeat(64)),
            essentials.copy(bytes = 996, sha256 = "c".repeat(64)),
            essentials.copy(bytes = 996),
        )) {
            val partial = paused(root, essentials)
            assertTrue(download.discardPartial(essentials, listOf(listed)))
            assertFalse(partial.exists())
            assertEquals(0L, download.resumableBytes(listed))
        }
    }

    @Test fun pausedDownloadsOfReplacedVersionsAreRemoved() {
        val root = temp.newFolder("stale")
        val dir = File(root, "knowledge-downloads").apply { mkdirs() }
        val old = File(dir, "osm-vegan-places-2026.10.02.part").apply { writeBytes(ByteArray(10)) }
        val current = File(dir, "osm-vegan-places-2026.10.05.part").apply { writeBytes(ByteArray(10)) }
        val catalog = listOf(KnowledgeCatalogEntry("osm-vegan-places", "2026.10.05", "Vegan places", "Places", "ODbL-1.0", 1_000,
            "a".repeat(64), "https://example.org/osm-vegan-places-2026.10.05.fapack"))
        val download = KnowledgePackDownload(root, AssetRegistry(File(root, "assets")), AssetImporter(root))
        assertEquals(1, download.discardStale(catalog))
        assertFalse(old.exists())
        assertTrue(current.exists())
    }
}
