package xyz.fieldatlas.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeCatalogTest {
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
