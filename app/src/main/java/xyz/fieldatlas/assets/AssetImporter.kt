package xyz.fieldatlas.assets

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipMethod
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PackImportInfo(val id: String, val title: String, val type: PackType)

class AssetImportException(
    message: String,
    cause: Throwable? = null,
    val budgetDecision: BudgetDecision? = null,
) : Exception(message, cause)

/** Present only inside a pack folder moved into place but not yet registered. */
internal const val INSTALLING_MARKER = ".installing"

/**
 * Pack folders an install moved into place but never registered: the process died in between. Only folders
 * carrying the install's marker count, so a folder whose registry entry went missing some other way is kept.
 */
internal fun interruptedInstalls(packs: java.io.File, registeredRoots: Set<String>): List<java.io.File> =
    packs.listFiles().orEmpty().filter { it.isDirectory }.flatMap { id -> id.listFiles().orEmpty().toList() }
        .filter { it.isDirectory && it.canonicalPath !in registeredRoots && java.io.File(it, INSTALLING_MARKER).exists() }

class AssetImporter private constructor(
    private val storageRoot: File,
    private val registry: AssetRegistry,
    private val context: Context?,
    private val footprint: StorageFootprint,
) {
    constructor(storageRoot: File, registry: AssetRegistry = AssetRegistry(storageRoot)) :
        this(storageRoot, registry, null, StorageFootprint { listOf(storageRoot) })

    internal constructor(storageRoot: File, registry: AssetRegistry, footprint: StorageFootprint) :
        this(storageRoot, registry, null, footprint)

    constructor(context: Context) : this(context.filesDir, AssetRegistry(context.filesDir), context, StorageFootprint.forApp(context))

    /** Shares the app's registry: two instances on one file hold separate locks, so an import and
     * another registry write could interleave and one could overwrite the other's entry. */
    constructor(context: Context, registry: AssetRegistry) : this(context.filesDir, registry, context, StorageFootprint.forApp(context))

    private val importMutex = Mutex()

    /** Read only the bounded first entry for presentation; the real import still validates every artifact. */
    suspend fun inspect(uri: Uri): PackImportInfo? = withContext(Dispatchers.IO) {
        try {
            context?.contentResolver?.openInputStream(uri)?.use { inspect(it) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { null }
    }

    internal fun inspect(input: InputStream): PackImportInfo = ZipArchiveInputStream(input).use { zip ->
        val entry = zip.nextZipEntry ?: throw AssetImportException("Pack is empty")
        validateEntry(entry, "manifest.json")
        val manifest = PackManifestParser.parse(readManifest(zip, entry))
        PackImportInfo(manifest.id, manifest.title, manifest.type)
    }

    suspend fun import(uri: Uri): InstalledAsset = withContext(Dispatchers.IO) {
        val resolver = context?.contentResolver ?: throw AssetImportException("No ContentResolver configured")
        // Local document providers normally expose a seekable descriptor. Read the ZIP
        // in place instead of storing a second full archive before extraction.
        val direct = try { resolver.openFileDescriptor(uri, "r")?.let { ParcelFileDescriptor.AutoCloseInputStream(it) } }
            catch (_: java.io.IOException) { null }
        if (direct != null) {
            val size = try { direct.channel.position(0); direct.channel.size().takeIf { it > 0 } }
                catch (_: java.io.IOException) { null }
            if (size != null) return@withContext direct.use {
                importSeekable(it.channel, size, storageRoot.usableSpace)
            }
            direct.close() // Pipe/cloud providers retain the checked streaming fallback.
        }
        val input = resolver.openInputStream(uri) ?: throw AssetImportException("Unable to open pack URI")
        input.use { import(it, storageRoot.usableSpace) }
    }

    /** Caller owns the source; never delete it. It stays in the caller's storage (Downloads, a card), so only the
     * installed pack counts against the 50 GB limit: counting the source too refused a 24 GB pack that fits. */
    internal suspend fun importSeekable(channel: SeekableByteChannel, archiveBytes: Long, freeBytes: Long): InstalledAsset =
        withContext(Dispatchers.IO) {
            importMutex.withLock {
                requireImport(archiveBytes in 1..StorageBudget.MAX_TOTAL_BYTES && channel.size() == archiveBytes,
                    "Invalid seekable archive size")
                val session = createSession()
                try {
                    processArchive(null, session, freeBytes,
                        openZip = { ZipFile.builder().setSeekableByteChannel(channel).get() })
                } finally { session.deleteRecursively() }
            }
        }

    suspend fun import(input: InputStream, freeBytes: Long): InstalledAsset = withContext(Dispatchers.IO) {
        importOnIo(input, freeBytes)
    }

    /** Import a download already staged in app-private storage without copying the large ZIP again. */
    suspend fun importArchive(
        archive: File,
        expectedId: String,
        expectedVersion: String,
        expectedType: PackType,
        expectedInstalledBytes: Long? = null,
    ): InstalledAsset = withContext(Dispatchers.IO) {
        importMutex.withLock {
            requireImport(archive.isFile, "Downloaded pack is missing")
            val session = createSession()
            try {
                processArchive(archive, session, storageRoot.usableSpace,
                    Triple(expectedId, expectedVersion, expectedType), expectedInstalledBytes)
            } finally {
                session.deleteRecursively()
            }
        }
    }

    private suspend fun importOnIo(input: InputStream, freeBytes: Long): InstalledAsset = importMutex.withLock {
        if (freeBytes < 0) {
            throw AssetImportException("Invalid free-space value", budgetDecision = BudgetDecision.InvalidSize)
        }
        val session = createSession()
        try {
            val archive = File(session, "incoming.fapack")
            val archiveBytes = spoolArchive(input, archive, freeBytes)
            processArchive(archive, session, freeBytes - archiveBytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AssetImportException) {
            throw error
        } catch (error: Exception) {
            throw AssetImportException("Pack import failed", error)
        } finally {
            session.deleteRecursively()
        }
    }

    private fun createSession(): File {
        val stagingBase = File(storageRoot, "pack-staging").apply { mkdirs() }
        val session = File(stagingBase, UUID.randomUUID().toString())
        requireImport(session.mkdir(), "Unable to create staging directory")
        return session
    }

    private suspend fun processArchive(
        archive: File?,
        session: File,
        freeBytesForExtraction: Long,
        expected: Triple<String, String, PackType>? = null,
        expectedInstalledBytes: Long? = null,
        openZip: (() -> ZipFile)? = null,
    ): InstalledAsset {
        try {
            return (openZip?.invoke() ?: ZipFile.builder().setFile(requireNotNull(archive)).get()).use { zip ->
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
                if (expected != null) {
                    requireImport(
                        manifest.id == expected.first && manifest.version == expected.second &&
                            manifest.type == expected.third,
                        "Downloaded pack does not match its catalog entry",
                    )
                }
                val incomingBytes = checkedTotal(manifest.artifacts)
                if (expectedInstalledBytes != null) {
                    requireImport(incomingBytes == expectedInstalledBytes, "Installed size does not match catalog entry")
                }
                // Old catalogs reserve archive bytes for extraction. Schema 2 downloads
                // must declare their expanded size so admission never assumes a ZIP ratio.
                requireImport(expected == null || manifest.schemaVersion == 1 || expectedInstalledBytes != null,
                    "Compressed download requires installed size in catalog")
                val decision = StorageBudget.evaluate(
                    maxOf(footprint.bytes(), registry.totalInstalledBytes()),
                    Math.addExact(incomingBytes, StorageBudget.METADATA_RESERVE_BYTES),
                    freeBytesForExtraction,
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
                    currentCoroutineContext().ensureActive()
                    validateEntry(entry, allowDeflated = manifest.schemaVersion == 2)
                    requireImport(seen.add(entry.name), "Duplicate ZIP entry: ${entry.name}")
                    val artifact = declared[entry.name]
                        ?: throw AssetImportException("Undeclared ZIP entry: ${entry.name}")
                    zip.getInputStream(entry).use { payload ->
                        writeVerified(payload, entry, artifact, extracted)
                    }
                }
                requireImport(seen == declared.keys, "Pack is missing declared artifacts")

                // The archive and every extracted payload are already part of this footprint.
                // Recheck after a potentially long extraction: History, attachments or caches
                // may have grown since admission. Reserve only the pending registry metadata,
                // not another copy of the payload, before the atomic move publishes the pack.
                val finalDecision = StorageBudget.evaluate(
                    maxOf(footprint.bytes(), registry.totalInstalledBytes()),
                    StorageBudget.METADATA_RESERVE_BYTES,
                    storageRoot.usableSpace,
                )
                if (finalDecision != BudgetDecision.Allowed) {
                    throw AssetImportException(
                        "Storage changed during import: $finalDecision",
                        budgetDecision = finalDecision,
                    )
                }

                currentCoroutineContext().ensureActive()
                finalRoot.parentFile?.mkdirs()
                // Marks the folder as not yet registered, so a process that dies before registry.add leaves a
                // folder that startup recovery may delete. A folder without it is never deleted as an orphan.
                File(extracted, INSTALLING_MARKER).writeText("")
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
                    embedding = manifest.embedding.takeIf { manifest.type == PackType.KNOWLEDGE },
                )
                try {
                    registry.add(installed)
                    File(finalRoot, INSTALLING_MARKER).delete()
                } catch (error: Exception) {
                    finalRoot.deleteRecursively()
                    if (error is CancellationException) throw error
                    throw AssetImportException("Unable to persist asset registry", error)
                }
                installed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AssetImportException) {
            throw error
        } catch (error: Exception) {
            throw AssetImportException("Pack import failed", error)
        }
    }

    private suspend fun spoolArchive(input: InputStream, archive: File, freeBytes: Long): Long {
        var copied = 0L
        val initialBytes = footprint.bytes()
        FileOutputStream(archive).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                copied = try {
                    Math.addExact(copied, read.toLong())
                } catch (error: ArithmeticException) {
                    throw AssetImportException("Pack size overflow", error, BudgetDecision.InvalidSize)
                }
                val decision = StorageBudget.evaluate(initialBytes, copied, freeBytes)
                if (decision != BudgetDecision.Allowed) {
                    throw AssetImportException(
                        "Insufficient space to stage pack",
                        budgetDecision = decision,
                    )
                }
                output.write(buffer, 0, read)
            }
            output.fd.sync()
        }
        requireImport(copied > 0, "Pack is empty")
        return copied
    }

    private fun validateEntry(entry: ZipArchiveEntry, expectedName: String? = null, allowDeflated: Boolean = false) {
        requireImport(!entry.isDirectory, "Directories are not valid pack entries")
        requireImport(entry.method == ZipMethod.STORED.code || (allowDeflated && entry.method == ZipMethod.DEFLATED.code),
            if (allowDeflated) "Pack entries must use STORED or DEFLATED compression" else "Pack entries must use STORED compression")
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

    private suspend fun writeVerified(
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
                currentCoroutineContext().ensureActive()
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
