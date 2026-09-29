package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.assets.*
import xyz.fieldatlas.research.*

/** Uses desktop SQLite only; no model server, network, or downloaded packs. */
class NewTopicPackTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun unfamiliarTopicImportsSearchesDisablesAndRemovesWithoutCatalogEntry() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val db = File(temp.root, "fixture.sqlite")
        BundledSQLiteDriver().open(db.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            exec("INSERT INTO chunks_fts VALUES('quartz:1','quartz','Quartz reference','https://example.org/quartz','Quartz is a crystalline mineral composed of silicon and oxygen.')")
            exec("CREATE TABLE chunk_vectors (quant BLOB)")
            exec("INSERT INTO chunk_vectors(rowid,quant) VALUES(1,X'0000803F0100')")
        }
        val payload = db.readBytes()
        val manifest = """{"schemaVersion":1,"id":"mineralogy-reference","version":"edition-a","type":"KNOWLEDGE","title":"Mineralogy","license":"CC0","sourceUrls":["https://example.org/quartz"],"artifacts":[{"path":"content.sqlite","bytes":${payload.size},"sha256":"${Sha256.digest(payload)}"}]}""".toByteArray()
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in listOf("manifest.json" to manifest, "content.sqlite" to payload)) {
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED; size = content.size.toLong(); compressedSize = size
                    crc = CRC32().apply { update(content) }.value
                })
                zip.write(content); zip.closeEntry()
            }
        }
        val root = temp.newFolder("installed")
        val registry = AssetRegistry(root)
        val installed = AssetImporter(root, registry).import(ByteArrayInputStream(bytes.toByteArray()), Long.MAX_VALUE)
        var enabled = registry.list().filter { it.enabled }
        val opened = mutableListOf<KnowledgeDatabase>()
        val retriever = MultiKnowledgeRetriever({ enabled.map { File(it.rootPath, "content.sqlite") } },
            open = { KnowledgeDatabase.open(it).also(opened::add) })
        try {
            val evidence = retriever.search("What is quartz?", 5) {}
            assertEquals("https://example.org/quartz", evidence.single().source)
            assertTrue(evidence.single().text.contains("silicon and oxygen"))
            val prompt = PromptBuilder.build("According to saved sources, what is quartz?", evidence, 2048)
            assertEquals("S1", prompt.sources.single().citationId)
            assertEquals("https://example.org/quartz", prompt.sources.single().evidence.source)
            registry.setEnabled(installed.id, installed.version, false)
            enabled = registry.list().filter { it.enabled }
            assertTrue(retriever.search("What is quartz?", 5) {}.isEmpty())
            registry.setEnabled(installed.id, installed.version, true)
            enabled = registry.list().filter { it.enabled }
            assertEquals("quartz:1", retriever.search("What is quartz?", 5) {}.single().chunkId)
        } finally { opened.forEach { it.close() } }
        val expected = PackEmbedding("fixture", 2, "int8-symmetric-per-vector", true, "chunk_vectors", 1,
            0.5, "query: ", "encoder.gguf", "a".repeat(64), "https://example.org/encoder")
        val vectorDatabases = mutableListOf<KnowledgeDatabase>()
        var actualEncoder = expected
        var encoded = 0
        val vectorRetriever = MultiKnowledgeRetriever(
            { listOf(File(installed.rootPath, "content.sqlite")) },
            open = { KnowledgeDatabase.open(it).also(vectorDatabases::add) },
            packEmbeddings = { listOf(expected) },
            embedForPack = { metadata, text ->
                assertEquals(expected, metadata)
                assertTrue(text.startsWith("query: "))
                encodeWithCompatibleEncoder(metadata, actualEncoder) { encoded++; floatArrayOf(1f, 0f) }
            },
        )
        try {
            assertTrue(vectorRetriever.search("quartz", 5) {}.single().matchedBy.orEmpty().startsWith("concept match"))
            assertEquals(1, encoded)
            actualEncoder = expected.copy(encoderSha256 = "b".repeat(64))
            assertFalse(vectorRetriever.search("quartz", 5) {}.single().matchedBy.orEmpty().startsWith("concept match"))
            assertEquals(1, encoded)
        } finally { vectorDatabases.forEach { it.close() } }
        registry.remove(installed.id, installed.version)
        assertTrue(registry.list().isEmpty())
        assertFalse(File(installed.rootPath).exists())
    }
}
