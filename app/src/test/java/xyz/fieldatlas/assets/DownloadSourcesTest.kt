package xyz.fieldatlas.assets

import org.junit.Assert.*
import org.junit.Test

class DownloadSourcesTest {
    private val model = DownloadArtifact.model(ModelOffers.recommended)
    private val pack = DownloadArtifact.knowledge(KnowledgeCatalogEntry("test", "1", "Test", "A collection",
        "CC0", 100, "a".repeat(64), "https://original.test/test.fapack"))
    private val mirror = "https://mirror.test/model.gguf"

    @Test fun defaultConstructionAndReadingDoNotPersistOrFetch() {
        val sources = DownloadSources(emptyMap()) { error("Should not write") }
        assertEquals(model.defaultUrl, sources.urlFor(model))
        assertEquals(pack.defaultUrl, sources.urlFor(pack))
    }

    @Test fun savingOnlyChangesTheServerAndSurvivesReopening() {
        var disk = emptyMap<String, String>()
        val sources = DownloadSources(disk) { disk = it; true }
        sources.save(model, " $mirror ")
        assertEquals(mirror, sources.urlFor(model))
        assertEquals(pack.defaultUrl, sources.urlFor(pack))
        assertEquals(mapOf(model.key to mirror), disk)
        assertEquals(mirror, DownloadSources(disk) { true }.urlFor(model))
        assertEquals(ModelOffers.recommended.sha256, model.sha256)
        assertEquals(ModelOffers.recommended.bytes, model.bytes)
    }

    @Test fun changedFileIdentityDoesNotInheritAMirror() {
        val sources = DownloadSources(mapOf(model.key to mirror)) { true }
        for (changed in listOf(model.copy(version = "2"), model.copy(bytes = model.bytes + 1),
            model.copy(sha256 = "b".repeat(64)), model.copy(id = "another"), model.copy(kind = AssetDownloadKind.KNOWLEDGE))) {
            assertEquals(changed.defaultUrl, sources.urlFor(changed))
        }
    }

    @Test fun resetAndSavingDefaultRemoveOnlyThatOverride() {
        var disk = emptyMap<String, String>()
        val sources = DownloadSources(disk) { disk = it; true }
        sources.save(model, mirror)
        sources.save(pack, "https://mirror.test/data.fapack")
        sources.reset(model)
        assertEquals(model.defaultUrl, sources.urlFor(model))
        assertEquals(1, disk.size)
        sources.save(pack, pack.defaultUrl)
        assertTrue(disk.isEmpty())
    }

    @Test fun failedPersistenceDoesNotChangeLiveSettings() {
        val sources = DownloadSources(mapOf(model.key to mirror)) { false }
        assertThrows(IllegalStateException::class.java) { sources.save(model, "https://second.test/model.gguf") }
        assertThrows(IllegalStateException::class.java) { sources.reset(model) }
        assertEquals(mirror, sources.urlFor(model))
    }

    @Test fun invalidAddressesNeverReachPersistence() {
        val sources = DownloadSources(emptyMap()) { error("Must not persist") }
        for (url in listOf("", "http://mirror.test/model.gguf", "file:///model.gguf", "https://u:p@mirror.test/model.gguf",
            "https://mirror.test/model.gguf#token", "https://mirror.test:0/model.gguf", "https://mirror.test:65536/model.gguf",
            "https://mirror.test/model.fapack", "https://mirror.test/a b.gguf", "https://mirror.test/" + "a".repeat(2050) + ".gguf")) {
            assertThrows(url, IllegalArgumentException::class.java) { sources.save(model, url) }
        }
        assertTrue(sources.state.value.isEmpty())
    }

    @Test fun queryParametersAndPortsAreSupportedWithoutChangingArtifactType() {
        assertEquals("https://mirror.test:8443/data.fapack?download=1",
            validatedDownloadUrl("https://mirror.test:8443/data.fapack?download=1", ".fapack"))
    }

    @Test fun resumeKeepsOriginalServerWhenSettingsChangeAndRejectsChangedPins() {
        val sources = DownloadSources(emptyMap()) { true }
        sources.save(model, mirror)
        val accepted = sources.urlFor(model)
        sources.save(model, "https://second.test/model.gguf")
        assertEquals(mirror, restoredDownloadUrl(model, model.key, accepted))
        assertEquals(model.defaultUrl, restoredDownloadUrl(model, null, null))
        assertThrows(IllegalArgumentException::class.java) { restoredDownloadUrl(model.copy(sha256 = "c".repeat(64)), model.key, accepted) }
        assertThrows(IllegalArgumentException::class.java) { restoredDownloadUrl(model, model.key, null) }
        assertThrows(IllegalArgumentException::class.java) { restoredDownloadUrl(model, null, accepted) }
        assertThrows(IllegalArgumentException::class.java) { restoredDownloadUrl(model, model.key, "http://mirror.test/model.gguf") }
    }
}
