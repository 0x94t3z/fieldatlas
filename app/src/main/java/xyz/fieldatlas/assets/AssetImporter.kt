package xyz.fieldatlas.assets

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipMethod

class AssetImportException(
    message: String,
    cause: Throwable? = null,
    val budgetDecision: BudgetDecision? = null,
) : Exception(message, cause)

class AssetImporter private constructor(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val context: Context?,
) {
    constructor(storageRoot: File, registry: AssetRegistry = AssetRegistry(storageRoot)) :
        this(storageRoot, registry, null)

    constructor(context: Context) : this(context.filesDir, AssetRegistry(context.filesDir), context)

    private val importMutex = Mutex()

    suspend fun import(uri: Uri): InstalledAsset {
        val resolver = context?.contentResolver ?: throw AssetImportException("No ContentResolver configured")
        val input = resolver.openInputStream(uri) ?: throw AssetImportException("Unable to open pack URI")
        return input.use { import(it, storageRoot.usableSpace) }
    }

    suspend fun import(input: InputStream, freeBytes: Long): InstalledAsset = importMutex.withLock {
        if (freeBytes < 0) {
            throw AssetImportException("Invalid free-space value", budgetDecision = BudgetDecision.InvalidSize)
        }
        val stagingBase = File(storageRoot, "pack-staging").apply { mkdirs() }
        val session = File(stagingBase, UUID.randomUUID().toString())
        try {
            requireImport(session.mkdir(), "Unable to create staging directory")
            val archive = File(session, "incoming.fapack")
            val archiveBytes = spoolArchive(input, archive, freeBytes)
            ZipFile.builder().setFile(archive).get().use { zip ->
                val entries = zip.entries.toBoundedList(MAX_ARCHIVE_ENTRIES)
                requireImport(entries.isNotEmpty(), "Pack is empty")
                val manifestEntry = entries.first()
                validateEntry(manifestEntry, "manifest.json")
                val manifestBytes = zip.getInputStream(manifestEntry).use { readManifest(it, manifestEntry) }
                val manifest = try {
                    PackManifestParser.parse(manifestBytes)
                } catch (error: InvalidPackManifestException) {
                    throw AssetImportException("Invalid manifest", error)
                }
                val incomingBytes = checkedTotal(manifest.artifacts)
                val decision = StorageBudget.evaluate(
                    registry.totalInstalledBytes(),
                    incomingBytes,
                    freeBytes - archiveBytes,
                )
                if (decision != BudgetDecision.Allowed) {
                    throw AssetImportException("Pack violates storage budget: $decision", budgetDecision = decision)
                }

                val finalRoot = File(storageRoot, "packs/${manifest.id}/${manifest.version}")
                requireImport(!finalRoot.exists(), "Asset version is already installed")
                val extracted = File(session, "extracted")
                requireImport(extracted.mkdir(), "Unable to create extraction directory")
                val declared = manifest.artifacts.associateBy { it.path }
                val seen = mutableSetOf<String>()
                entries.drop(1).forEach { entry ->
                    validateEntry(entry)
                    requireImport(seen.add(entry.name), "Duplicate ZIP entry: ${entry.name}")
                    val artifact = declared[entry.name]
                        ?: throw AssetImportException("Undeclared ZIP entry: ${entry.name}")
                    zip.getInputStream(entry).use { payload ->
                        writeVerified(payload, entry, artifact, extracted)
                    }
                }
                requireImport(seen == declared.keys, "Pack is missing declared artifacts")

                finalRoot.parentFile?.mkdirs()
                Files.move(extracted.toPath(), finalRoot.toPath(), StandardCopyOption.ATOMIC_MOVE)
                val installed = InstalledAsset(
                    id = manifest.id,
                    version = manifest.version,
                    type = manifest.type,
                    title = manifest.title,
                    license = manifest.license,
                    installedBytes = incomingBytes,
                    manifestSha256 = Sha256.digest(manifestBytes),
                    rootPath = finalRoot.absolutePath,
                    discovery = manifest.discovery,
                )
                try {
                    registry.add(installed)
                } catch (error: Exception) {
                    finalRoot.deleteRecursively()
                    throw AssetImportException("Unable to persist asset registry", error)
                }
                installed
            }
        } catch (error: AssetImportException) {
            throw error
        } catch (error: Exception) {
            throw AssetImportException("Pack import failed", error)
        } finally {
            session.deleteRecursively()
        }
    }

    private fun spoolArchive(input: InputStream, archive: File, freeBytes: Long): Long {
        var copied = 0L
        FileOutputStream(archive).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                copied = try {
                    Math.addExact(copied, read.toLong())
                } catch (error: ArithmeticException) {
                    throw AssetImportException("Pack size overflow", error, BudgetDecision.InvalidSize)
                }
                if (copied > freeBytes || copied > StorageBudget.MAX_TOTAL_BYTES) {
                    throw AssetImportException(
                        "Insufficient space to stage pack",
                        budgetDecision = if (copied > freeBytes) {
                            BudgetDecision.InsufficientFreeSpace
                        } else {
                            BudgetDecision.ExceedsGlobalLimit
                        },
                    )
                }
                output.write(buffer, 0, read)
            }
            output.fd.sync()
        }
        requireImport(copied > 0, "Pack is empty")
        return copied
    }

    private fun validateEntry(entry: ZipArchiveEntry, expectedName: String? = null) {
        requireImport(!entry.isDirectory, "Directories are not valid pack entries")
        requireImport(entry.method == ZipMethod.STORED.code, "Pack entries must use STORED compression")
        requireImport(!entry.generalPurposeBit.usesEncryption(), "Encrypted pack entries are not allowed")
        requireImport(!entry.isUnixSymlink, "Symbolic links are not allowed")
        if (expectedName != null) requireImport(entry.name == expectedName, "$expectedName must be first")
    }

    private fun readManifest(input: InputStream, entry: ZipArchiveEntry): ByteArray {
        requireImport(entry.size in 1..MAX_MANIFEST_BYTES, "Invalid manifest size")
        val output = ByteArrayOutputStream(entry.size.toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (copied < entry.size) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), entry.size - copied).toInt())
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read
        }
        requireImport(copied == entry.size && input.read() == -1, "Manifest size mismatch")
        return output.toByteArray()
    }

    private fun writeVerified(
        input: InputStream,
        entry: ZipArchiveEntry,
        artifact: PackArtifact,
        staging: File,
    ) {
        requireImport(entry.size == artifact.bytes, "Declared ZIP size mismatch for ${entry.name}")
        val outputFile = File(staging, artifact.path)
        requireImport(
            outputFile.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath()),
            "Artifact escapes staging directory",
        )
        outputFile.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (copied < artifact.bytes) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), artifact.bytes - copied).toInt())
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                copied = Math.addExact(copied, read.toLong())
            }
            output.fd.sync()
        }
        requireImport(copied == artifact.bytes && input.read() == -1, "Payload size mismatch for ${artifact.path}")
        val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        requireImport(actualHash == artifact.sha256, "SHA-256 mismatch for ${artifact.path}")
    }

    private fun checkedTotal(artifacts: List<PackArtifact>): Long = try {
        artifacts.fold(0L) { total, artifact -> Math.addExact(total, artifact.bytes) }
    } catch (error: ArithmeticException) {
        throw AssetImportException("Artifact sizes overflow", error, BudgetDecision.InvalidSize)
    }

    private fun <T> java.util.Enumeration<T>.toBoundedList(limit: Int): List<T> {
        val result = ArrayList<T>()
        while (hasMoreElements()) {
            requireImport(result.size < limit, "Pack contains too many entries")
            result += nextElement()
        }
        return result
    }

    private fun requireImport(condition: Boolean, message: String) {
        if (!condition) throw AssetImportException(message)
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 1024 * 1024
        const val MAX_MANIFEST_BYTES = 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES = 100_001
    }
}
