package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream

class AssetImporterJvmTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun validPackInstallsPayloadAndRegistryAtomically() = runBlocking {
        val root = temporaryFolder.newFolder("valid")
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val payload = "offline knowledge".encodeToByteArray()

        val installed = importer.import(ByteArrayInputStream(pack(payload)), Long.MAX_VALUE)

        assertEquals("demo", installed.id)
        assertEquals(payload.size.toLong(), installed.installedBytes)
        assertEquals(CoverageLevel.DEMO, installed.discovery?.coverageLevel)
        assertTrue(File(installed.rootPath, "docs/content.txt").readBytes().contentEquals(payload))
        assertEquals(listOf(installed), registry.list())
        assertFalse(File(root, "pack-staging").walkTopDown().any { it.isFile })
    }

    @Test fun corruptPayloadLeavesPriorVersionAndRegistryUntouched() = runBlocking {
        val root = temporaryFolder.newFolder("corrupt")
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val good = "good".encodeToByteArray()
        val prior = importer.import(ByteArrayInputStream(pack(good, version = "1")), Long.MAX_VALUE)
        val corrupt = pack("bad".encodeToByteArray(), version = "2", declaredHash = Sha256.digest(good))

        assertThrows(AssetImportException::class.java) {
            runBlocking { importer.import(ByteArrayInputStream(corrupt), Long.MAX_VALUE) }
        }
        assertEquals(listOf(prior), registry.list())
        assertTrue(File(prior.rootPath, "docs/content.txt").isFile)
        assertFalse(File(root, "packs/demo/2").exists())
    }

    @Test fun undeclaredPayloadIsRejectedBeforeRegistryUpdate() = runBlocking {
        val root = temporaryFolder.newFolder("undeclared")
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)

        assertThrows(AssetImportException::class.java) {
            runBlocking {
                importer.import(
                    ByteArrayInputStream(pack("ok".encodeToByteArray(), extra = "surprise".encodeToByteArray())),
                    Long.MAX_VALUE,
                )
            }
        }
        assertTrue(registry.list().isEmpty())
        assertFalse(File(root, "packs/demo/1").exists())
    }

    @Test fun overBudgetPackIsRejectedBeforeCopy() = runBlocking {
        val root = temporaryFolder.newFolder("budget")
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val prior = importer.import(ByteArrayInputStream(pack("old".encodeToByteArray())), Long.MAX_VALUE)

        val error = assertThrows(AssetImportException::class.java) {
            runBlocking {
                importer.import(ByteArrayInputStream(pack("hello".encodeToByteArray(), version = "2")), 4)
            }
        }
        assertEquals(BudgetDecision.InsufficientFreeSpace, error.budgetDecision)
        assertEquals(listOf(prior), registry.list())
        assertTrue(File(prior.rootPath, "docs/content.txt").isFile)
    }

    @Test fun manifestMustBeFirst() = rejectsPack(
        pack("ok".encodeToByteArray(), manifestFirst = false),
        "first",
    )

    @Test fun compressedPayloadIsRejected() = rejectsPack(
        pack("ok".encodeToByteArray(), storedPayload = false),
        "STORED",
    )

    @Test fun interruptedPayloadDoesNotPublishPartialData() {
        val complete = pack(ByteArray(32_768) { 7 }, version = "2")
        rejectsPackPreservingPrior(complete.copyOf(complete.size / 2))
    }

    @Test fun traversalManifestDoesNotReplacePriorVersion() =
        rejectsPackPreservingPrior(pack("bad".encodeToByteArray(), version = "2", path = "../escape"))

    @Test fun duplicateZipEntryDoesNotReplacePriorVersion() =
        rejectsPackPreservingPrior(duplicatePack("bad".encodeToByteArray()))

    @Test fun symbolicLinkEntryDoesNotReplacePriorVersion() =
        rejectsPackPreservingPrior(symbolicLinkPack())

    @Test fun encryptedFlagDoesNotReplacePriorVersion() =
        rejectsPackPreservingPrior(withEncryptedFlags(pack("bad".encodeToByteArray(), version = "2")))

    private fun rejectsPack(pack: ByteArray, messageFragment: String?) = runBlocking {
        val root = temporaryFolder.newFolder("rejected-${UUID.randomUUID()}")
        val registry = AssetRegistry(root)
        val error = assertThrows(AssetImportException::class.java) {
            runBlocking { AssetImporter(root, registry).import(ByteArrayInputStream(pack), Long.MAX_VALUE) }
        }
        if (messageFragment != null) assertTrue(error.message.orEmpty().contains(messageFragment))
        assertTrue(registry.list().isEmpty())
        assertFalse(File(root, "packs").walkTopDown().any { it.isFile })
    }

    private fun rejectsPackPreservingPrior(invalidPack: ByteArray) = runBlocking {
        val root = temporaryFolder.newFolder("prior-${UUID.randomUUID()}")
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val prior = importer.import(ByteArrayInputStream(pack("old".encodeToByteArray())), Long.MAX_VALUE)
        assertThrows(AssetImportException::class.java) {
            runBlocking { importer.import(ByteArrayInputStream(invalidPack), Long.MAX_VALUE) }
        }
        assertEquals(listOf(prior), registry.list())
        assertTrue(File(prior.rootPath, "docs/content.txt").isFile)
        assertFalse(File(root, "packs/demo/2").exists())
    }

    private fun pack(
        payload: ByteArray,
        version: String = "1",
        declaredHash: String = Sha256.digest(payload),
        extra: ByteArray? = null,
        manifestFirst: Boolean = true,
        storedPayload: Boolean = true,
        path: String = "docs/content.txt",
    ): ByteArray {
        val manifest = """
            {"schemaVersion":1,"id":"demo","version":"$version","type":"KNOWLEDGE","title":"Demo","license":"CC0-1.0","sourceUrls":["https://example.invalid"],"discovery":{"coverageSummary":"Demo notes","exampleQuestions":["What is in the demo?"],"coverageLevel":"demo"},"artifacts":[{"path":"$path","bytes":${payload.size},"sha256":"$declaredHash"}]}
        """.trimIndent().encodeToByteArray()
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                if (!manifestFirst) zip.stored(path, payload)
                zip.stored("manifest.json", manifest)
                if (manifestFirst) {
                    if (storedPayload) zip.stored(path, payload)
                    else {
                        zip.putNextEntry(ZipEntry(path))
                        zip.write(payload)
                        zip.closeEntry()
                    }
                }
                if (extra != null) zip.stored("undeclared.bin", extra)
            }
            bytes.toByteArray()
        }
    }

    private fun duplicatePack(payload: ByteArray): ByteArray {
        val hash = Sha256.digest(payload)
        val manifest = """{"schemaVersion":1,"id":"demo","version":"2","type":"KNOWLEDGE","title":"Demo","license":"CC0-1.0","sourceUrls":["https://example.invalid"],"artifacts":[{"path":"docs/content.txt","bytes":${payload.size},"sha256":"$hash"}]}""".encodeToByteArray()
        return ByteArrayOutputStream().use { bytes ->
            ZipArchiveOutputStream(bytes).use { zip ->
                zip.storedArchive("manifest.json", manifest)
                zip.storedArchive("docs/content.txt", payload)
                zip.storedArchive("docs/content.txt", payload)
            }
            bytes.toByteArray()
        }
    }

    private fun symbolicLinkPack(): ByteArray {
        val payload = "target".encodeToByteArray()
        val hash = Sha256.digest(payload)
        val manifest = """{"schemaVersion":1,"id":"demo","version":"2","type":"KNOWLEDGE","title":"Demo","license":"CC0-1.0","sourceUrls":["https://example.invalid"],"artifacts":[{"path":"docs/content.txt","bytes":${payload.size},"sha256":"$hash"}]}""".encodeToByteArray()
        return ByteArrayOutputStream().use { bytes ->
            ZipArchiveOutputStream(bytes).use { zip ->
                zip.storedArchive("manifest.json", manifest)
                val checksum = CRC32().apply { update(payload) }
                val link = ZipArchiveEntry("docs/content.txt").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    crc = checksum.value
                    unixMode = 0b1010_000_000_000_000 or 0b1_111_111_111
                }
                zip.putArchiveEntry(link)
                zip.write(payload)
                zip.closeArchiveEntry()
            }
            bytes.toByteArray()
        }
    }

    private fun withEncryptedFlags(pack: ByteArray): ByteArray = pack.copyOf().also { bytes ->
        var index = 0
        while (index <= bytes.size - 10) {
            val localHeader = bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() &&
                bytes[index + 2] == 0x03.toByte() && bytes[index + 3] == 0x04.toByte()
            val centralHeader = bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() &&
                bytes[index + 2] == 0x01.toByte() && bytes[index + 3] == 0x02.toByte()
            if (localHeader) bytes[index + 6] = (bytes[index + 6].toInt() or 1).toByte()
            if (centralHeader) bytes[index + 8] = (bytes[index + 8].toInt() or 1).toByte()
            index++
        }
    }

    private fun ZipArchiveOutputStream.storedArchive(name: String, bytes: ByteArray) {
        val checksum = CRC32().apply { update(bytes) }
        val entry = ZipArchiveEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checksum.value
        }
        putArchiveEntry(entry)
        write(bytes)
        closeArchiveEntry()
    }

    private fun ZipOutputStream.stored(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        })
        write(bytes)
        closeEntry()
    }
}
