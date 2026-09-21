package xyz.fieldatlas.assets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetImporterTest {
    private lateinit var root: File

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "asset-import-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test fun importsStoredPackIntoAppPrivateStorage() = runBlocking {
        val payload = "device-local evidence".encodeToByteArray()
        val registry = AssetRegistry(root)
        val installed = AssetImporter(root, registry).import(
            ByteArrayInputStream(pack(payload)),
            root.usableSpace,
        )

        assertTrue(File(installed.rootPath, "docs/content.txt").isFile)
        assertEquals(payload.size.toLong(), installed.installedBytes)
        assertEquals(listOf(installed), registry.list())
    }

    @Test fun corruptPackDoesNotPublishDirectoryOrRegistryEntry() = runBlocking {
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val payload = "corrupt".encodeToByteArray()
        try {
            importer.import(
                ByteArrayInputStream(pack(payload, "0".repeat(64))),
                root.usableSpace,
            )
        } catch (_: AssetImportException) {
            // Expected.
        }

        assertTrue(registry.list().isEmpty())
        assertFalse(File(root, "packs/demo/1").exists())
    }

    private fun pack(payload: ByteArray, hash: String = Sha256.digest(payload)): ByteArray {
        val manifest = """
            {"schemaVersion":1,"id":"demo","version":"1","type":"KNOWLEDGE","title":"Demo","license":"CC0-1.0","sourceUrls":["https://example.invalid"],"artifacts":[{"path":"docs/content.txt","bytes":${payload.size},"sha256":"$hash"}]}
        """.trimIndent().encodeToByteArray()
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.stored("manifest.json", manifest)
                zip.stored("docs/content.txt", payload)
            }
            bytes.toByteArray()
        }
    }

    private fun ZipOutputStream.stored(name: String, bytes: ByteArray) {
        val checksum = CRC32().apply { update(bytes) }
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checksum.value
        })
        write(bytes)
        closeEntry()
    }
}
