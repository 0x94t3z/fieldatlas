package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnowledgeMirrorDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private val payload = "Offline source text.".encodeToByteArray()
    private fun archive(): ByteArray {
        val manifest = """{"schemaVersion":1,"id":"demo","version":"1","type":"KNOWLEDGE","title":"Demo","license":"CC0-1.0","sourceUrls":["https://original.test/source"],"artifacts":[{"path":"docs/content.txt","bytes":${payload.size},"sha256":"${Sha256.digest(payload)}"}]}""".encodeToByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in listOf("manifest.json" to manifest, "docs/content.txt" to payload)) {
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
    private fun entry(bytes: ByteArray) = KnowledgeCatalogEntry("demo", "1", "Demo", "Saved sources", "CC0-1.0",
        bytes.size.toLong(), Sha256.digest(bytes), "https://original.test/demo.fapack")

    @Test fun mirrorInstallsVerifiedArchiveWithoutReplacingCatalogMetadata() = runBlocking {
        val root = temp.newFolder("valid")
        val registry = AssetRegistry(root)
        val bytes = archive()
        val catalog = entry(bytes)
        val opened = mutableListOf<String>()
        val download = KnowledgePackDownload(root, registry, AssetImporter(root, registry),
            downloader = VerifiedResumableDownload { url, _ -> opened += url; Response(bytes) })
        val installed = download.install(catalog, "https://mirror.test/demo.fapack") {}
        assertEquals(listOf("https://mirror.test/demo.fapack"), opened)
        assertEquals(listOf(installed), registry.list())
        assertArrayEquals(payload, File(installed.rootPath, "docs/content.txt").readBytes())
        assertEquals("https://original.test/demo.fapack", catalog.url)
        assertEquals(Sha256.digest(bytes), catalog.sha256)
    }

    @Test fun wrongMirrorBytesCannotReplaceSavedPacks() = runBlocking {
        val root = temp.newFolder("wrong")
        val registry = AssetRegistry(root)
        val bytes = archive()
        val prior = AssetImporter(root, registry).import(ByteArrayInputStream(bytes), Long.MAX_VALUE)
        val newEntry = entry(bytes).copy(id = "next")
        val download = KnowledgePackDownload(root, registry, AssetImporter(root, registry),
            downloader = VerifiedResumableDownload { _, _ -> Response(ByteArray(bytes.size)) })
        assertThrows(IllegalStateException::class.java) {
            runBlocking { download.install(newEntry, "https://mirror.test/next.fapack") {} }
        }
        assertEquals(listOf(prior), registry.list())
        assertArrayEquals(payload, File(prior.rootPath, "docs/content.txt").readBytes())
        assertEquals(0L, download.resumableBytes(newEntry))
    }

    private class Response(private val bytes: ByteArray) : HttpURLConnection(URL("https://mirror.test/demo.fapack")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = null
    }
}
