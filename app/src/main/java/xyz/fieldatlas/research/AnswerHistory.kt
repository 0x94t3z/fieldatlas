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
)

/**
 * File-backed log of completed answers, newest first, capped so it cannot grow forever.
 * The state flow is the single source of truth for the UI; the JSON file (answers.json in
 * app storage) is rewritten whole on each entry — trivially small at the cap.
 */
class AnswerHistoryStore(private val file: File, private val synchronousWrites: Boolean = false) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var loaded = false
    private var initialLoadSucceeded = false
    private val mutableRecords = MutableStateFlow<List<AnswerRecord>>(emptyList())
    val records: StateFlow<List<AnswerRecord>> = mutableRecords.asStateFlow()

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
                mutableRecords.value = saved.sortedByDescending(AnswerRecord::createdAtEpochMs).take(MAX_ENTRIES)
                initialLoadSucceeded = true
            }
        }
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
            (listOf(entry) + mutableRecords.value).take(MAX_ENTRIES).also { mutableRecords.value = it }
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
            runCatching {
                synchronized(lock) {
                    file.parentFile?.mkdirs()
                    val pending = File(file.parentFile, "${file.name}.tmp")
                    pending.writeText(json.encodeToString(ListSerializer(AnswerRecord.serializer()), mutableRecords.value))
                    check(pending.renameTo(file))
                }
            }
        }
        if (synchronousWrites) write.run() else Thread(write).apply { isDaemon = true; start() }
    }

    companion object {
        private const val MAX_ENTRIES = 100
    }
}
