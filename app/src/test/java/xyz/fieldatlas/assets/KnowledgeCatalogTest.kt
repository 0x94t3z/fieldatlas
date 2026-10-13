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
        assertEquals("2026.10.13", pack.version)
        assertEquals(8_183_535L, pack.bytes)
        assertEquals(27_291_455L, pack.installedBytes)
        assertEquals("06e49130ad80d75a83275b7c0c09ac1ba47fb6ea2e3f6c5786b0426caed2b376", pack.sha256)
        assertTrue(pack.recommended)
    }

    @Test fun travelDownloadMatchesPublishedArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "wikivoyage-places" }
        assertEquals("2026.09.1", pack.version)
        assertEquals(351_581_170L, pack.bytes)
        assertEquals("1aa075cb2f5dd57415589b3a8e083d701e63f9bb87498f3da97ccbda1c3567ac", pack.sha256)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/1c547f35c613e4984e5932a3173845b1cef3e76f/wikivoyage-places/2026.09.1/wikivoyage-places-2026.09.1.fapack", pack.url)
    }

    @Test fun veganPlacesDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "osm-vegan-places" }
        assertEquals("2026.10.05", pack.version)
        assertEquals(40_068_046L, pack.bytes)
        assertEquals("4c08ac74b221890cc0d7559c717fff8762252d9d61b04a9ffdf0d3d8daf660fd", pack.sha256)
        assertEquals("ODbL-1.0", pack.license)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/86064123736fc5c8ff966b44bfcbe6e07de30441/osm-vegan-places/2026.10.05/osm-vegan-places-2026.10.05.fapack", pack.url)
    }

    @Test fun essentialsDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "osm-essentials" }
        assertEquals("2026.10.05", pack.version)
        assertEquals(1_781_015_519L, pack.bytes)
        assertEquals("a8f85f91e9716d05fa08880cea5d5e512723b3a8d32870cc910906fe75eaf6d9", pack.sha256)
        assertEquals("ODbL-1.0", pack.license)
        assertEquals("Places", pack.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/e320ff8a3fe4dbcaa68c725c54aaffe93793f35c/osm-essentials/2026.10.05/osm-essentials-2026.10.05.fapack", pack.url)
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
        assertEquals(419_107_865L, guides.bytes)
        assertEquals("1de13456098ddbf307665257e88a6f5b98f646f7aa078b18d8a1a2a8e8032c83", guides.sha256)
        assertEquals("Travel", guides.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/5b72377fe68508ae00ea3856bdfdff40a19fb3f4/wikivoyage-guides/2026.09.01/wikivoyage-guides-2026.09.01.fapack", guides.url)
        assertTrue(catalog.packs.all { it.category.isNotBlank() })
    }

    @Test fun cryptographyDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "crypto-ethereum" }
        assertEquals("2026.10.05", pack.version)
        assertEquals(77_678_612L, pack.bytes)
        assertEquals("b34a374145eba8fd19e118a274b045a09361af596b1ffae2aba808481b62dcd7", pack.sha256)
        assertEquals("Science", pack.category)
        assertEquals("https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/6be16bab707c3ee9f6cc2b7b5694182492a43516/crypto-ethereum/2026.10.05/crypto-ethereum-2026.10.05.fapack", pack.url)
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
