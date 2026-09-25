package xyz.fieldatlas.research

import java.io.File
import kotlin.collections.sortedByDescending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One saved answer. Titles (not full evidence) are kept so the history can show provenance. */
@Serializable
data class AnswerRecord(
    val question: String,
    val answer: String,
    val createdAtEpochMs: Long,
    val sources: List<String> = emptyList(),
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
    private val mutableRecords = MutableStateFlow<List<AnswerRecord>>(emptyList())
    val records: StateFlow<List<AnswerRecord>> = mutableRecords.asStateFlow()

    /** Reads answers.json once, lazily, off the main thread. */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (loaded) return@withContext
            loaded = true
            runCatching {
                if (file.isFile) json.decodeFromString(ListSerializer(AnswerRecord.serializer()), file.readText()) else null
            }.getOrNull()?.let { saved ->
                mutableRecords.value = saved.sortedByDescending(AnswerRecord::createdAtEpochMs).take(MAX_ENTRIES)
            }
        }
    }

    fun record(question: String, answer: String, sources: List<String>) {
        if (answer.isBlank()) return
        val entry = AnswerRecord(
            question = question.trim(),
            answer = answer,
            createdAtEpochMs = System.currentTimeMillis(),
            sources = sources,
        )
        val updated = synchronized(lock) {
            (listOf(entry) + mutableRecords.value).take(MAX_ENTRIES).also { mutableRecords.value = it }
        }
        val write = Runnable {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(ListSerializer(AnswerRecord.serializer()), updated))
            }
        }
        if (synchronousWrites) write.run() else Thread(write).apply { isDaemon = true; start() }
    }

    companion object {
        private const val MAX_ENTRIES = 100
    }
}
