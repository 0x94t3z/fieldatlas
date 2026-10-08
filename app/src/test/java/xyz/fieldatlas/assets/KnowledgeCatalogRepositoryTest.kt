package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnowledgeCatalogRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val bundle get() = KnowledgeCatalog.parse(catalog("original"))
    private val cache get() = File(temp.root, "catalog.json")

    @Test fun constructionNeverFetchesAndCorruptCacheUsesBundle() {
        cache.writeText("broken")
        var calls = 0
        val repo = KnowledgeCatalogRepository(bundle, cache, { calls++; error("network") })
        assertEquals(0, calls)
        assertEquals("original", repo.state.value.packs.single().id)
    }

    @Test fun refreshPersistsNewTopic() = runBlocking {
        val repo = KnowledgeCatalogRepository(bundle, cache, { catalog("astronomy").toByteArray() })
        repo.refresh()
        assertEquals(listOf("original", "astronomy"), repo.state.value.packs.map { it.id })
        assertNull(repo.state.value.error)
        val reopened = KnowledgeCatalogRepository(bundle, cache, { error("must stay offline") })
        assertEquals(repo.state.value.packs, reopened.state.value.packs)
    }

    @Test fun failedRefreshPreservesCache() = runBlocking {
        cache.writeText(catalog("saved"))
        val before = cache.readBytes()
        for (body in listOf("broken", "{\"schemaVersion\":1,\"packs\":[]}", " ".repeat(1_048_577))) {
            val repo = KnowledgeCatalogRepository(bundle, cache, { body.toByteArray() })
            repo.refresh()
            assertEquals(listOf("original", "saved"), repo.state.value.packs.map { it.id })
            assertNotNull(repo.state.value.error)
            assertArrayEquals(before, cache.readBytes())
        }
    }

    @Test fun byteLimitAcceptsExactBoundary() = runBlocking {
        val body = catalog("boundary").padEnd(1_048_576).toByteArray()
        val repo = KnowledgeCatalogRepository(bundle, cache, { body })
        repo.refresh()
        assertEquals(listOf("original", "boundary"), repo.state.value.packs.map { it.id })
    }

    @Test fun duplicateRefreshIsIgnoredAndCancellationResetsLoading() = runBlocking {
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val repo = KnowledgeCatalogRepository(bundle, cache, {
            calls++; entered.complete(Unit); awaitCancellation()
        })
        val first = launch { repo.refresh() }
        entered.await()
        assertTrue(repo.state.value.refreshing)
        repo.refresh()
        assertEquals(1, calls)
        first.cancelAndJoin()
        assertFalse(repo.state.value.refreshing)
        assertNull(repo.state.value.error)
        assertEquals("original", repo.state.value.packs.single().id)
    }

    @Test fun cacheWriteFailureDoesNotReplaceVisibleList() = runBlocking {
        val parent = temp.newFile("not-a-directory")
        val repo = KnowledgeCatalogRepository(bundle, File(parent, "catalog.json"), { catalog("new").toByteArray() })
        repo.refresh()
        assertEquals("original", repo.state.value.packs.single().id)
        assertNotNull(repo.state.value.error)
    }

    @Test fun acceptedDownloadSnapshotSurvivesRefreshButStaleSelectionIsRejected() = runBlocking {
        val original = bundle.packs.single().copy(version = "2026.10.05")
        val replacement = original.copy(version = "2026.10.06", sha256 = "b".repeat(64),
            url = "https://example.org/replacement.fapack")
        val repo = KnowledgeCatalogRepository(KnowledgeCatalog(1, listOf(original)), cache, { bytes(replacement) })
        val entry = repo.selectForDownload(original)
        val downloading = CompletableDeferred<KnowledgeCatalogEntry>()
        val finish = CompletableDeferred<Unit>()
        val transfer = async { downloading.complete(entry); finish.await(); entry }
        downloading.await()
        repo.refresh()
        finish.complete(Unit)
        assertEquals("https://example.org/original.fapack", transfer.await().url)
        assertEquals("a".repeat(64), entry.sha256)
        assertEquals(listOf(replacement), repo.state.value.packs)
        assertThrows(IllegalArgumentException::class.java) { repo.selectForDownload(entry) }
        Unit
    }

    @Test fun activeTransferRemainsVisibleWhenCatalogRemovesOrReplacesIt() {
        val active = bundle.packs.single()
        val changed = active.copy(bytes = 500, sha256 = "b".repeat(64))
        assertEquals(listOf(active), catalogWithActiveDownload(emptyList(), active))
        assertEquals(listOf(active), catalogWithActiveDownload(listOf(changed), active))
        assertEquals(listOf(changed), catalogWithActiveDownload(listOf(changed), null))
    }

    @Test fun oldCacheCannotHideNewBundledTopicsOrDowngradeEditions() {
        val current = bundle.packs.single().copy(version = "2026.10.05", category = "Places")
        val old = current.copy(version = "2026.10.02", sha256 = "b".repeat(64))
        val added = current.copy(id = "new-topic")
        cache.writeBytes(bytes(old))
        var calls = 0
        val repo = KnowledgeCatalogRepository(KnowledgeCatalog(1, listOf(current, added)), cache,
            { calls++; error("offline") })
        assertEquals(listOf(current, added), repo.state.value.packs)
        assertEquals(0, calls)
        // Merely opening the app does not rewrite the previous cache or start a download.
        assertArrayEquals(bytes(old), cache.readBytes())
    }

    @Test fun staleRefreshCannotHideBundledTopicsOrDowngradeAfterRestart() = runBlocking {
        val current = bundle.packs.single().copy(version = "2026.10.05")
        val old = current.copy(version = "2026.10.02", sha256 = "b".repeat(64))
        val added = current.copy(id = "new-topic")
        val bundled = KnowledgeCatalog(1, listOf(current, added))
        val repo = KnowledgeCatalogRepository(bundled, cache, { bytes(old) }, { 42L })
        repo.refresh()
        assertEquals(listOf(current, added), repo.state.value.packs)
        assertEquals(42L, repo.state.value.lastCheckedMillis)
        assertNull(repo.state.value.error)
        assertEquals(repo.state.value.packs,
            KnowledgeCatalogRepository(bundled, cache, { error("offline") }).state.value.packs)
    }

    @Test fun newerFetchedEditionAndNewTopicSurviveServerRollbackAndRestart() = runBlocking {
        val original = bundle.packs.single().copy(version = "1.9.0")
        val newer = original.copy(version = "1.10.0", sha256 = "b".repeat(64))
        val added = original.copy(id = "new-topic")
        val bundled = KnowledgeCatalog(1, listOf(original))
        var response = bytes(newer, added)
        val repo = KnowledgeCatalogRepository(bundled, cache, { response })
        repo.refresh()
        assertEquals(listOf(newer, added), repo.state.value.packs)
        response = bytes(original)
        repo.refresh()
        assertEquals(listOf(newer, added), repo.state.value.packs)
        assertNull(repo.state.value.error)
        assertEquals(repo.state.value.packs,
            KnowledgeCatalogRepository(bundled, cache, { error("offline") }).state.value.packs)
    }

    @Test fun numericDatesUseComponentsNotLexicographicOrder() = runBlocking {
        val original = bundle.packs.single().copy(version = "2026.09.1")
        val newer = original.copy(version = "2026.10.05")
        val repo = KnowledgeCatalogRepository(KnowledgeCatalog(1, listOf(original)), cache, { bytes(newer) })
        repo.refresh()
        assertEquals(listOf(newer), repo.state.value.packs)
    }

    @Test fun opaqueEditionsAreNotGuessedToBeNewer() = runBlocking {
        val original = bundle.packs.single()
        val other = original.copy(version = "edition-b")
        val repo = KnowledgeCatalogRepository(bundle, cache, { bytes(other) })
        repo.refresh()
        assertEquals(listOf(original, other), repo.state.value.packs)
        assertNull(repo.state.value.error)
    }

    @Test fun identicalArtifactDoesNotLoseBundledMetadataToOldCache() {
        val original = bundle.packs.single().copy(category = "Science", recommended = true)
        cache.writeBytes(bytes(original.copy(category = "", recommended = false)))
        val repo = KnowledgeCatalogRepository(KnowledgeCatalog(1, listOf(original)), cache, { error("offline") })
        assertEquals(listOf(original), repo.state.value.packs)
    }

    @Test fun conflictingCachedPinFallsBackToBundle() {
        cache.writeBytes(bytes(bundle.packs.single().copy(sha256 = "b".repeat(64))))
        val repo = KnowledgeCatalogRepository(bundle, cache, { error("offline") })
        assertEquals(bundle.packs, repo.state.value.packs)
    }

    @Test fun refreshCannotChangeAnExistingEditionPin() = runBlocking {
        cache.writeBytes(bytes(*bundle.packs.toTypedArray()))
        val before = cache.readBytes()
        val original = bundle.packs.single()
        for (changed in listOf(original.copy(bytes = 101), original.copy(sha256 = "b".repeat(64)))) {
            val repo = KnowledgeCatalogRepository(bundle, cache, { bytes(changed) })
            repo.refresh()
            assertEquals(bundle.packs, repo.state.value.packs)
            assertNotNull(repo.state.value.error)
            assertArrayEquals(before, cache.readBytes())
        }
    }

    @Test fun mergedEntryLimitPreservesExistingCacheOnFailure() = runBlocking {
        cache.writeBytes(bytes(*bundle.packs.toTypedArray()))
        val before = cache.readBytes()
        val incoming = (1..500).map { bundle.packs.single().copy(id = "topic-$it") }
        val repo = KnowledgeCatalogRepository(bundle, cache, { bytes(*incoming.toTypedArray()) })
        repo.refresh()
        assertEquals(bundle.packs, repo.state.value.packs)
        assertNotNull(repo.state.value.error)
        assertArrayEquals(before, cache.readBytes())
    }

    @Test fun mergedByteLimitPreservesExistingCacheOnFailure() = runBlocking {
        val original = bundle.packs.single().copy(description = "a".repeat(600_000))
        val bundled = KnowledgeCatalog(1, listOf(original))
        cache.writeBytes(bytes(original))
        val before = cache.readBytes()
        val incoming = original.copy(id = "new-topic")
        val repo = KnowledgeCatalogRepository(bundled, cache, { bytes(incoming) })
        repo.refresh()
        assertEquals(listOf(original), repo.state.value.packs)
        assertNotNull(repo.state.value.error)
        assertArrayEquals(before, cache.readBytes())
    }

    @Test fun apkCatalogSurvivesOlderSixTopicCacheAndRefreshOffline() = runBlocking {
        val bundled = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())
        val old = bundled.packs.filterNot { it.id in setOf("wikipedia-en-vital", "crypto-ethereum") }
            .map { pack ->
                when (pack.id) {
                    "osm-essentials", "osm-vegan-places", "wikivoyage-guides" ->
                        pack.copy(version = "2025.01.01", sha256 = "b".repeat(64))
                    else -> pack
                }
            }
        assertEquals(6, old.size)
        cache.writeBytes(bytes(*old.toTypedArray()))
        val repo = KnowledgeCatalogRepository(bundled, cache, { bytes(*old.toTypedArray()) })
        assertEquals(bundled.packs, repo.state.value.packs)
        repo.refresh()
        assertNull(repo.state.value.error)
        assertEquals(bundled.packs, repo.state.value.packs)
        assertEquals(bundled.packs,
            KnowledgeCatalogRepository(bundled, cache, { error("must not fetch offline") }).state.value.packs)
    }

    @Test fun numericEditionComponentsCannotOverflow() = runBlocking {
        val original = bundle.packs.single().copy(version = "9".repeat(100) + ".1")
        val newer = original.copy(version = "9".repeat(100) + ".2")
        val repo = KnowledgeCatalogRepository(KnowledgeCatalog(1, listOf(original)), cache, { bytes(newer) })
        repo.refresh()
        assertEquals(listOf(newer), repo.state.value.packs)
        assertNull(repo.state.value.error)
    }

    companion object {
        private fun bytes(vararg entries: KnowledgeCatalogEntry): ByteArray =
            Json.encodeToString(KnowledgeCatalog(1, entries.toList())).encodeToByteArray()
        fun catalog(id: String) = """{"schemaVersion":1,"packs":[${entry(id)}]}"""
        fun entry(id: String) = """{"id":"$id","version":"edition-a","title":"Astronomy","description":"Saved reference","license":"CC0","bytes":100,"sha256":"${"a".repeat(64)}","url":"https://example.org/$id.fapack"}"""
    }
}
