package xyz.fieldatlas.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeCatalogTest {
    @Test fun replacementIdsMustBeSafeUniqueAndNotSelfReferences() {
        val entry = KnowledgeCatalogRepositoryTest.entry("compressed")
        for (replaces in listOf("[\"compressed\"]", "[\"../old\"]", "[\"old\",\"old\"]")) {
            assertThrows(IllegalArgumentException::class.java) {
                KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[${entry.dropLast(1)},"replaces":$replaces}]}""")
            }
        }
    }
    @Test fun compressedDownloadSpaceMatchesExpandedPayloadAndResume() {
        val pack = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText()).packs
            .single { it.id == "wikipedia-encyclopedia" }
        assertEquals(34_990_585_497L, pack.downloadGrowthBytes())
        assertEquals(35_502_585_497L, pack.downloadFreeBytes())
        assertEquals(pack.downloadFreeBytes() - 1_000_000_000L, pack.downloadFreeBytes(1_000_000_000L))
        assertEquals(pack.downloadFreeBytes(), pack.downloadFreeBytes(-1))
        assertEquals(24_859_052_005L, pack.downloadFreeBytes(Long.MAX_VALUE))
        val legacy = pack.copy(bytes = 1000, installedBytes = null)
        assertEquals(2000L + StorageBudget.METADATA_RESERVE_BYTES, legacy.downloadGrowthBytes())
    }
    @Test fun expandedSizeIsOptionalForOldCatalogsAndStrictWhenProvided() {
        val entry = KnowledgeCatalogRepositoryTest.entry("compressed")
        fun catalog(size: Long) = """{"schemaVersion":1,"packs":[${entry.dropLast(1)},"installedBytes":$size}]}"""
        assertEquals(24_346_003_429L, KnowledgeCatalog.parse(catalog(24_346_003_429L)).packs.single().installedBytes)
        for (size in listOf(0L, -1L, 50_000_000_001L)) {
            assertThrows(IllegalArgumentException::class.java) { KnowledgeCatalog.parse(catalog(size)) }
        }
        assertEquals(null, KnowledgeCatalog.parse(KnowledgeCatalogRepositoryTest.catalog("legacy")).packs.single().installedBytes)
    }
    @Test fun catalogRejectsDefaultsTheTransferWouldReject() {
        val valid = KnowledgeCatalogRepositoryTest.catalog("astronomy")
        for (invalid in listOf(valid.replace(".fapack", ".fapack#fragment"),
            valid.replace("https://", "https://name:password@"),
            valid.replace("https://example.org", " https://example.org"),
            valid.replace("https://example.org", "https://example.org:65536"))) {
            assertThrows(IllegalArgumentException::class.java) { KnowledgeCatalog.parse(invalid) }
        }
    }
    @Test fun rejectsMalformedEntriesAndUnsupportedSchema() {
        val valid = KnowledgeCatalogRepositoryTest.catalog("astronomy")
        for (invalid in listOf(valid.replace("edition-a", "../unsafe"),
            valid.replace("a".repeat(64), "not-a-hash"), valid.replace("https://", "http://"),
            valid.replace("schemaVersion\":1", "schemaVersion\":2"))) {
            assertThrows(IllegalArgumentException::class.java) { KnowledgeCatalog.parse(invalid) }
        }
    }
    @Test fun rejectsEmptyOrOversizedCatalogsAndAcceptsBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[]}""")
        }
        val entries = (1..500).joinToString(",") { KnowledgeCatalogRepositoryTest.entry("pack-$it") }
        assertEquals(500, KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[$entries]}""").packs.size)
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[$entries,${KnowledgeCatalogRepositoryTest.entry("extra")}]}""")
        }
    }

    @Test fun bundledCatalogContainsVerifiedBiologyPack() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "world-knowledge-biology" }
        assertEquals("world-knowledge-biology", pack.id)
        assertEquals("2026.10.18", pack.version)
        assertEquals(1_075_685_456L, pack.bytes)
        assertEquals(2_855_220_193L, pack.installedBytes)
        assertEquals("e12287e4ea0a208876131ecec9d73fc45da1322db123c8721a5e6033478e4f66", pack.sha256)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/7629d755538d1ed2296d5f90ca584fbfdd6ae908/world-knowledge-biology/2026.10.18/world-knowledge-biology-2026.10.18.fapack", pack.url)
        assertTrue(pack.recommended)
    }

    @Test fun travelDownloadMatchesPublishedArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "wikivoyage-places" }
        assertEquals("2026.09.1-leviathan", pack.version)
        assertEquals(215_077_879L, pack.bytes)
        assertEquals(650_272_768L, pack.installedBytes)
        assertEquals("c94daea43ae042048b7427fefc50abcee10a554a5a80fc359cc4fe9a273e8c63", pack.sha256)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/wikivoyage-places/2026.09.1-leviathan/wikivoyage-places-2026.09.1-leviathan.fapack", pack.url)
    }

    @Test fun veganPlacesDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "osm-vegan-places" }
        assertEquals("2026.10.05-leviathan", pack.version)
        assertEquals(24_183_118L, pack.bytes)
        assertEquals(76_947_456L, pack.installedBytes)
        assertEquals("ace29e1c28492f88ed2c5f8dad58260ba3bbf6ea00fb21606ec720475c9ede7d", pack.sha256)
        assertEquals("ODbL-1.0", pack.license)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/osm-vegan-places/2026.10.05-leviathan/osm-vegan-places-2026.10.05-leviathan.fapack", pack.url)
    }

    @Test fun essentialsDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "osm-essentials" }
        assertEquals("2026.10.05-leviathan", pack.version)
        assertEquals(986_143_236L, pack.bytes)
        assertEquals(3_620_782_080L, pack.installedBytes)
        assertEquals("f41811ed8fe4aa1883adb98a57cd75b60aaf30f918358eb3a5c391fc85f7e2d2", pack.sha256)
        assertEquals("ODbL-1.0", pack.license)
        assertEquals("Places", pack.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/osm-essentials/2026.10.05-leviathan/osm-essentials-2026.10.05-leviathan.fapack", pack.url)
    }

    @Test fun hostedDownloadsArePinnedToARevision() {
        // A branch name such as "main" could later serve different bytes; the checksum would then
        // reject every download instead of the catalog keeping a working copy.
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        catalog.packs.filter { "huggingface.co" in it.url }.forEach { pack ->
            assertTrue(pack.url, Regex("/resolve/[0-9a-f]{40}/").containsMatchIn(pack.url))
        }
    }

    @Test fun wikimediaDownloadsMatchBuiltArtifactsAndCategories() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val encyclopedia = catalog.packs.single { it.category == "Encyclopedia" }
        assertEquals("wikipedia-encyclopedia", encyclopedia.id)
        assertEquals("2026.10.12-native-compressed-preview", encyclopedia.version)
        assertEquals(10_643_533_492L, encyclopedia.bytes)
        assertEquals(24_346_003_429L, encyclopedia.installedBytes)
        assertEquals("7d41d7fbe0419d703b03d3788a55109217ef57e6fac7754165cc8ccc37654d59", encyclopedia.sha256)
        assertEquals("Encyclopedia", encyclopedia.category)
        assertTrue(encyclopedia.url.startsWith("https://huggingface.co/datasets/0x94t3z/fieldatlas-encyclopedia/resolve/"))
        assertTrue(encyclopedia.url.endsWith("/encyclopedia-2026.10.12.fapack"))
        assertTrue(catalog.packs.none { it.id in setOf("simplewiki", "wikipedia-en-vital") })
        val guides = catalog.packs.single { it.id == "wikivoyage-guides" }
        assertEquals(289_828_270L, guides.bytes)
        assertEquals(660_324_352L, guides.installedBytes)
        assertEquals("e67ab97828da0d73266e462b1bff08acd61125140cf797202806cd83234805de", guides.sha256)
        assertEquals("Travel", guides.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/wikivoyage-guides/2026.09.01-leviathan/wikivoyage-guides-2026.09.01-leviathan.fapack", guides.url)
        assertTrue(catalog.packs.all { it.category.isNotBlank() })
    }

    @Test fun cryptographyDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "crypto-ethereum" }
        assertEquals("2026.10.05-leviathan", pack.version)
        assertEquals(59_453_558L, pack.bytes)
        assertEquals(92_226_912L, pack.installedBytes)
        assertEquals("3457240f1c309da8dec2273d683eecf7480f3e3a638f8e6c7595a7800d1dd014", pack.sha256)
        assertEquals("Science", pack.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/crypto-ethereum/2026.10.05-leviathan/crypto-ethereum-2026.10.05-leviathan.fapack", pack.url)
    }

    @Test fun rejectsDuplicateAndInsecureEntries() {
        val valid = """{"id":"biology","version":"1","title":"Biology","description":"Notes","license":"CC0","bytes":100,"sha256":"${"a".repeat(64)}","url":"https://example.org/biology.fapack"}"""
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[$valid,$valid]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCatalog.parse("""{"schemaVersion":1,"packs":[${valid.replace("https://", "http://") }]}""")
        }
    }
}
