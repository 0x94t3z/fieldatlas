package xyz.fieldatlas.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeCatalogTest {
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
        assertEquals("1.2.0", pack.version)
        assertEquals(1_567_877_980L, pack.bytes)
        assertEquals("0cc4cfddc2eb6660707e3388e5e2a6f687c334cd1bb74c4f0621d4becb091d01", pack.sha256)
        assertTrue(pack.recommended)
    }

    @Test fun travelDownloadMatchesPublishedArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "wikivoyage-places" }
        assertEquals("2026.09.1", pack.version)
        assertEquals(351_581_170L, pack.bytes)
        assertEquals("1aa075cb2f5dd57415589b3a8e083d701e63f9bb87498f3da97ccbda1c3567ac", pack.sha256)
        assertTrue(pack.url.endsWith("/knowledge-travel-2026.09.1/wikivoyage-places-2026.09.1.fapack"))
    }

    @Test fun veganPlacesDownloadMatchesBuiltArtifact() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val pack = catalog.packs.single { it.id == "osm-vegan-places" }
        assertEquals("2026.10.02", pack.version)
        assertEquals(40_088_517L, pack.bytes)
        assertEquals("6cc944185c18de577060833645ee91a4da511c2c32df77ffc3b4dbfaf2dd8f7c", pack.sha256)
        assertEquals("ODbL-1.0", pack.license)
        assertTrue(pack.url.endsWith("/knowledge-vegan-places-2026.10.02/osm-vegan-places-2026.10.02.fapack"))
    }

    @Test fun wikimediaDownloadsMatchBuiltArtifactsAndCategories() {
        val catalog = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val encyclopedia = catalog.packs.single { it.id == "simplewiki" }
        assertEquals(316_396_538L, encyclopedia.bytes)
        assertEquals("add1984a60ff3cfd2298668e7e40b061a8581aeb24023edf3bf5370eef39a82a", encyclopedia.sha256)
        assertEquals("Encyclopedia", encyclopedia.category)
        assertTrue(encyclopedia.url.endsWith("/knowledge-simplewiki-2025.12.29/simplewiki-2025.12.29.fapack"))
        val guides = catalog.packs.single { it.id == "wikivoyage-guides" }
        assertEquals(374_600_710L, guides.bytes)
        assertEquals("2684b5b664aedb15aafb905a2dffccdd059dc589d10a382fdddec17c8bef60b2", guides.sha256)
        assertEquals("Travel", guides.category)
        assertTrue(guides.url.endsWith("/knowledge-wikivoyage-guides-2025.12.29/wikivoyage-guides-2025.12.29.fapack"))
        assertTrue(catalog.packs.all { it.category.isNotBlank() })
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
