package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.*
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
        assertEquals("astronomy", repo.state.value.packs.single().id)
        assertNull(repo.state.value.error)
        val reopened = KnowledgeCatalogRepository(bundle, cache, { error("must stay offline") })
        assertEquals("astronomy", reopened.state.value.packs.single().id)
    }

    @Test fun failedRefreshPreservesCache() = runBlocking {
        cache.writeText(catalog("saved"))
        val before = cache.readBytes()
        for (body in listOf("broken", "{\"schemaVersion\":1,\"packs\":[]}", " ".repeat(1_048_577))) {
            val repo = KnowledgeCatalogRepository(bundle, cache, { body.toByteArray() })
            repo.refresh()
            assertEquals("saved", repo.state.value.packs.single().id)
            assertNotNull(repo.state.value.error)
            assertArrayEquals(before, cache.readBytes())
        }
    }

    @Test fun byteLimitAcceptsExactBoundary() = runBlocking {
        val body = catalog("boundary").padEnd(1_048_576).toByteArray()
        val repo = KnowledgeCatalogRepository(bundle, cache, { body })
        repo.refresh()
        assertEquals("boundary", repo.state.value.packs.single().id)
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
        val repo = KnowledgeCatalogRepository(bundle, cache, { catalog("replacement").toByteArray() })
        val entry = repo.selectForDownload(bundle.packs.single())
        val downloading = CompletableDeferred<KnowledgeCatalogEntry>()
        val finish = CompletableDeferred<Unit>()
        val transfer = async { downloading.complete(entry); finish.await(); entry }
        downloading.await()
        repo.refresh()
        finish.complete(Unit)
        assertEquals("https://example.org/original.fapack", transfer.await().url)
        assertEquals("a".repeat(64), entry.sha256)
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

    companion object {
        fun catalog(id: String) = """{"schemaVersion":1,"packs":[${entry(id)}]}"""
        fun entry(id: String) = """{"id":"$id","version":"edition-a","title":"Astronomy","description":"Saved reference","license":"CC0","bytes":100,"sha256":"${"a".repeat(64)}","url":"https://example.org/$id.fapack"}"""
    }
}
