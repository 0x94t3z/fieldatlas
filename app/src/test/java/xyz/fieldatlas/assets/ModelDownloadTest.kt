package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    private val body = "a small stand-in for model weights".encodeToByteArray()
    private fun offer(id: String) = ModelOffer(
        id = id, version = "1.0.0", name = "Test $id", title = "Test $id — Q4", license = "Apache-2.0",
        bytes = body.size.toLong(), sha256 = Sha256.digest(body), url = "https://example.test/$id.gguf",
        description = "For tests.",
    )

    private fun download(root: File, registry: AssetRegistry, opened: MutableList<String> = mutableListOf()) =
        ModelDownload(root, registry, downloader = VerifiedResumableDownload { url, _ ->
            opened += url
            OkConnection(body)
        })

    @Test fun eachOfferInstallsUnderItsOwnIdAndVersion() = runBlocking {
        val root = folder.newFolder("files")
        val registry = AssetRegistry(root)
        val opened = mutableListOf<String>()
        val installed = download(root, registry, opened).install(offer("larger-model")) {}
        assertEquals(listOf("https://example.test/larger-model.gguf"), opened)
        assertEquals("larger-model", installed.id)
        assertEquals("Test larger-model — Q4", installed.title)
        assertEquals(PackType.MODEL, installed.type)
        assertEquals(listOf(installed), registry.list())
        assertArrayEquals(body, File(installed.rootPath, "model.gguf").readBytes())
        assertTrue(File(installed.rootPath, "manifest.json").isFile)
    }

    @Test fun savedPartsBelongToOneModel() {
        val root = folder.newFolder("files")
        val saving = download(root, AssetRegistry(root))
        File(root, "model-downloads").mkdirs()
        File(root, "model-downloads/larger-model-1.0.0.part").writeBytes(body.copyOfRange(0, 9))
        assertEquals(9L, saving.resumableBytes(offer("larger-model")))
        assertEquals(0L, saving.resumableBytes(offer("compact-model")))
    }

    @Test fun anInstalledOfferIsNotFetchedAgain() = runBlocking {
        val root = folder.newFolder("files")
        val registry = AssetRegistry(root)
        val first = download(root, registry).install(offer("compact-model")) {}
        val offline = ModelDownload(root, registry, downloader = VerifiedResumableDownload { _, _ ->
            error("Network must not be opened")
        })
        assertEquals(first, offline.install(offer("compact-model")) {})
    }

    @Test fun mirrorInstallsTheSamePinnedFileAndRetainsOriginalProvenance() = runBlocking {
        val root = folder.newFolder("mirror")
        val registry = AssetRegistry(root)
        val opened = mutableListOf<String>()
        val spec = offer("compact-model")
        val url = "https://mirror.test/weights.gguf"
        val installed = download(root, registry, opened).install(spec, url) {}
        assertEquals(listOf(url), opened)
        assertArrayEquals(body, File(installed.rootPath, "model.gguf").readBytes())
        val manifest = PackManifestParser.parse(File(installed.rootPath, "manifest.json").readBytes())
        assertEquals(listOf(spec.url, url), manifest.sourceUrls)
        assertEquals(spec.sha256, manifest.artifacts.single().sha256)
    }

    @Test fun mirrorCannotChangeChecksumEvenWhenFileSizeMatches() = runBlocking {
        val root = folder.newFolder("wrong-mirror")
        val registry = AssetRegistry(root)
        val saving = ModelDownload(root, registry, downloader = VerifiedResumableDownload { _, _ ->
            OkConnection(ByteArray(body.size))
        })
        assertThrows(IllegalStateException::class.java) {
            runBlocking { saving.install(offer("compact-model"), "https://mirror.test/weights.gguf") {} }
        }
        assertTrue(registry.list().isEmpty())
        assertEquals(0L, saving.resumableBytes(offer("compact-model")))
    }

    @Test fun invalidMirrorDoesNotTouchInstalledOrPartialData() = runBlocking {
        val root = folder.newFolder("invalid-mirror")
        val registry = AssetRegistry(root)
        val partial = File(root, "model-downloads/compact-model-1.0.0.part").apply {
            parentFile!!.mkdirs(); writeBytes(body.copyOfRange(0, 9))
        }
        val saving = ModelDownload(root, registry, downloader = VerifiedResumableDownload { _, _ ->
            error("Must not contact this server")
        })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { saving.install(offer("compact-model"), "http://mirror.test/weights.gguf") {} }
        }
        assertArrayEquals(body.copyOfRange(0, 9), partial.readBytes())
        assertTrue(registry.list().isEmpty())
    }

    private class OkConnection(private val bytes: ByteArray) : HttpURLConnection(URL("https://example.test/model")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = null
    }
}
