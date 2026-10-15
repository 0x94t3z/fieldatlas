package xyz.fieldatlas.research

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerHistoryStorageTest {
    private fun withDir(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("history-storage").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun aLongSessionKeepsEveryAnswer() = withDir { dir ->
        val file = File(dir, "answers.json")
        val store = AnswerHistoryStore(file, synchronousWrites = true)
        runBlocking { store.ensureLoaded() }
        repeat(130) { store.record("question $it", "answer $it", emptyList()) }
        assertEquals(130, store.records.value.size)
        assertEquals("question 0", store.records.value.last().question)
        // Reopened from disk, the first answers are still there.
        val reopened = AnswerHistoryStore(file)
        runBlocking { assertEquals(130, reopened.diagnosticsEntryCount()) }
    }

    @Test fun lowFreeSpaceIsReported() = withDir { dir ->
        val store = AnswerHistoryStore(File(dir, "answers.json"), synchronousWrites = true, freeSpace = { 120L * 1024 * 1024 })
        runBlocking { store.ensureLoaded() }
        assertTrue(store.storage.value.low)
        assertEquals(120L * 1024 * 1024, store.storage.value.freeBytes)
    }

    @Test fun plentyOfSpaceIsNotLow() = withDir { dir ->
        val store = AnswerHistoryStore(File(dir, "answers.json"), synchronousWrites = true, freeSpace = { 8L * 1024 * 1024 * 1024 })
        runBlocking { store.ensureLoaded() }
        store.record("q", "a", emptyList())
        assertFalse(store.storage.value.low)
        assertFalse(store.storage.value.lastSaveFailed)
    }

    @Test fun aFailedSaveIsReportedAndTheAnswerStaysForThisSession() = withDir { dir ->
        // A regular file where the history folder should be: the write cannot succeed, as on a full disk.
        val blocker = File(dir, "history").apply { writeText("not a folder") }
        val store = AnswerHistoryStore(File(blocker, "answers.json"), synchronousWrites = true, freeSpace = { 8L * 1024 * 1024 * 1024 })
        runBlocking { store.ensureLoaded() }
        store.record("q", "a", emptyList())
        assertTrue(store.storage.value.lastSaveFailed)
        assertTrue(store.storage.value.low)
        assertEquals(1, store.records.value.size)
    }
}
