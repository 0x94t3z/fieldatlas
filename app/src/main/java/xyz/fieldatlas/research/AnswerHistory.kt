package xyz.fieldatlas.research

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import kotlin.collections.sortedByDescending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Bounded excerpts preserve attachment citations; older title-only records remain readable. */
@Serializable
data class AnswerRecord(
    val question: String,
    val answer: String,
    val createdAtEpochMs: Long,
    val sources: List<String> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    /** Shared by a question and its follow-ups; null for records saved before follow-ups existed. */
    val topic: Long? = null,
    /** For a follow-up, the standalone question it was read as. */
    val readAs: String? = null,
) {
    /** The topic this answer belongs to. One saved before topics existed is its own, keyed by when it was saved. */
    val topicKey: Long get() = topic ?: createdAtEpochMs
}

/**
 * What the History screen needs to know about the phone's storage: how much is free where answers are kept, and
 * whether the last save failed. Answers are never dropped to make room; when space runs low the reader is told.
 */
data class HistoryStorage(val freeBytes: Long = Long.MAX_VALUE, val lastSaveFailed: Boolean = false) {
    val low: Boolean get() = lastSaveFailed || freeBytes < LOW_STORAGE_BYTES

    companion object {
        /** Below this Android itself starts to struggle, and a collection update no longer fits. */
        const val LOW_STORAGE_BYTES = 300L * 1024 * 1024
    }
}

/**
 * File-backed log of completed answers, newest first. It keeps every answer until the reader deletes it: a long
 * session must not quietly lose its first questions. The state flow is the single source of truth for the UI; the
 * JSON file (answers.json in app storage) is rewritten whole on each entry, a few kilobytes per answer.
 */
class AnswerHistoryStore(
    private val file: File,
    private val synchronousWrites: Boolean = false,
    private val freeSpace: () -> Long = { (file.parentFile ?: file.absoluteFile.parentFile)?.usableSpace ?: Long.MAX_VALUE },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    // Writing a long History to disk takes time; record() runs on the main thread and must not wait for it.
    private val writeLock = Any()
    private var version = 0L
    private var writtenVersion = 0L
    private var loaded = false
    private var initialLoadSucceeded = false
    private val mutableRecords = MutableStateFlow<List<AnswerRecord>>(emptyList())
    val records: StateFlow<List<AnswerRecord>> = mutableRecords.asStateFlow()
    private val mutableStorage = MutableStateFlow(HistoryStorage())
    val storage: StateFlow<HistoryStorage> = mutableStorage.asStateFlow()

    /** Reads answers.json once, lazily, off the main thread. */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (loaded) return@withContext
            loaded = true
            runCatching {
                val attributes = try {
                    Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                } catch (_: NoSuchFileException) {
                    null // A genuinely missing file is an empty history, not a read failure.
                }
                if (attributes == null) {
                    initialLoadSucceeded = true
                    null
                } else {
                    check(attributes.isRegularFile) { "History is not a regular file" }
                    json.decodeFromString(ListSerializer(AnswerRecord.serializer()), file.readText())
                }
            }.getOrNull()?.let { saved ->
                mutableRecords.value = saved.sortedByDescending(AnswerRecord::createdAtEpochMs)
                initialLoadSucceeded = true
            }
            refreshStorage(lastSaveFailed = false)
        }
    }

    private fun refreshStorage(lastSaveFailed: Boolean) {
        mutableStorage.value = HistoryStorage(runCatching(freeSpace).getOrDefault(Long.MAX_VALUE), lastSaveFailed)
    }

    /** Read-only support metadata; unknown after a failed load, never an inferred empty history. */
    suspend fun diagnosticsEntryCount(): Int? {
        ensureLoaded()
        return synchronized(lock) {
            mutableRecords.value.size.takeIf { initialLoadSucceeded }
        }
    }

    fun record(question: String, answer: String, sources: List<String>, evidence: List<Evidence> = emptyList(),
               topic: Long? = null, readAs: String? = null) {
        if (answer.isBlank()) return
        val entry = AnswerRecord(
            question = question.trim(),
            answer = answer,
            createdAtEpochMs = System.currentTimeMillis(),
            sources = sources,
            evidence = evidence,
            topic = topic,
            readAs = readAs,
        )
        synchronized(lock) {
            mutableRecords.value = listOf(entry) + mutableRecords.value
        }
        persist()
    }

    /** Removes one answer, identified by when it was recorded. */
    fun delete(createdAtEpochMs: Long) {
        synchronized(lock) {
            val remaining = mutableRecords.value.filterNot { it.createdAtEpochMs == createdAtEpochMs }
            if (remaining.size == mutableRecords.value.size) return
            mutableRecords.value = remaining
        }
        persist()
    }

    private fun persist() {
        val write = Runnable {
            // A failed write (usually a full disk) is reported, not swallowed: the answer stays on screen this session,
            // and the History screen says it could not be kept.
            val (snapshotVersion, snapshot) = synchronized(lock) { ++version to mutableRecords.value }
            val saved = runCatching {
                synchronized(writeLock) {
                    // A later change's copy is already on disk; this older one must not replace it.
                    if (snapshotVersion <= writtenVersion) return@synchronized
                    file.parentFile?.mkdirs()
                    val pending = File(file.parentFile, "${file.name}.tmp")
                    pending.writeText(json.encodeToString(ListSerializer(AnswerRecord.serializer()), snapshot))
                    check(pending.renameTo(file))
                    writtenVersion = snapshotVersion
                }
            }.isSuccess
            refreshStorage(lastSaveFailed = !saved)
        }
        if (synchronousWrites) write.run() else Thread(write).apply { isDaemon = true; start() }
    }
}
