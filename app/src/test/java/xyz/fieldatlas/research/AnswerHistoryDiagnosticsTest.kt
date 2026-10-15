package xyz.fieldatlas.research

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class AnswerHistoryDiagnosticsTest {
    private fun withFile(test: (File) -> Unit) {
        val root = Files.createTempDirectory("history-count-").toFile()
        try { test(File(root, "answers.json")) } finally { root.deleteRecursively() }
    }

    private fun records(count: Int): String = (1..count).joinToString(prefix = "[", postfix = "]") {
        """{"question":"private question $it","answer":"private answer $it","createdAtEpochMs":$it}"""
    }

    @Test fun diagnosticWaitsForPersistedHistoryWithoutAnEarlierEnsureLoaded() = withFile { file ->
        file.writeText(records(92))
        val store = AnswerHistoryStore(file)
        assertTrue(store.records.value.isEmpty())
        runBlocking {
            assertEquals(List(4) { 92 }, List(4) {
                async(Dispatchers.Default) { store.diagnosticsEntryCount() }
            }.awaitAll())
        }
        assertEquals((92L downTo 1L).toList(), store.records.value.map { it.createdAtEpochMs })
    }

    @Test fun missingFileIsZeroWithoutCreatingAFile() = withFile { file ->
        runBlocking { assertEquals(0, AnswerHistoryStore(file).diagnosticsEntryCount()) }
        assertFalse(file.exists())
    }

    @Test fun validEmptyFileIsZeroAndNotRewritten() = withFile { file ->
        file.writeText("[ ]\n")
        val before = file.readBytes()
        runBlocking { assertEquals(0, AnswerHistoryStore(file).diagnosticsEntryCount()) }
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun equalRecordsAreCountedIndividuallyRatherThanDeduplicated() = withFile { file ->
        val entry = """{"question":"same","answer":"same","createdAtEpochMs":1}"""
        file.writeText("[$entry,$entry,$entry]")
        runBlocking { assertEquals(3, AnswerHistoryStore(file).diagnosticsEntryCount()) }
    }

    @Test fun everySavedAnswerIsKeptNewestFirstWithNoCap() = withFile { file ->
        file.writeText(records(250))
        val original = file.readBytes()
        val store = AnswerHistoryStore(file)
        runBlocking { assertEquals(250, store.diagnosticsEntryCount()) }
        assertEquals((250L downTo 1L).toList(), store.records.value.map { it.createdAtEpochMs })
        assertTrue(original.contentEquals(file.readBytes()))
    }

    @Test fun countFollowsExistingAddDeleteBlankAndReopenSemantics() = withFile { file ->
        file.writeText(records(2))
        val store = AnswerHistoryStore(file, synchronousWrites = true)
        runBlocking {
            assertEquals(2, store.diagnosticsEntryCount())
            store.record("third", "answer", emptyList())
            assertEquals(3, store.diagnosticsEntryCount())
            store.record("blank", "  ", emptyList())
            store.delete(999)
            assertEquals(3, store.diagnosticsEntryCount())
            store.delete(1)
            assertEquals(2, store.diagnosticsEntryCount())
            val reopened = AnswerHistoryStore(file)
            assertEquals(2, reopened.diagnosticsEntryCount())
            assertEquals(store.records.value, reopened.records.value)
        }
    }

    @Test fun repeatedReadsDoNotTouchFileOrderingOrContents() = withFile { file ->
        file.writeText(records(5))
        val before = file.readBytes()
        val modified = Files.getLastModifiedTime(file.toPath())
        val store = AnswerHistoryStore(file)
        runBlocking {
            repeat(4) { assertEquals(5, store.diagnosticsEntryCount()) }
        }
        assertEquals((5L downTo 1L).toList(), store.records.value.map { it.createdAtEpochMs })
        assertEquals(modified, Files.getLastModifiedTime(file.toPath()))
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(listOf("answers.json"), requireNotNull(file.parentFile).list()!!.toList())
    }

    @Test fun corruptHistoryRemainsUnknownAndUnchanged() = withFile { file ->
        file.writeText("not valid JSON")
        val before = file.readBytes()
        val store = AnswerHistoryStore(file)
        runBlocking { repeat(2) { assertNull(store.diagnosticsEntryCount()) } }
        assertTrue(store.records.value.isEmpty())
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun directoryAtHistoryPathIsUnknownAndLeftIntact() = withFile { file ->
        assertTrue(file.mkdir())
        val sentinel = File(file, "keep.txt").apply { writeText("keep") }
        runBlocking { assertNull(AnswerHistoryStore(file).diagnosticsEntryCount()) }
        assertEquals("keep", sentinel.readText())
    }

    @Test fun unreadableRegularHistoryIsUnknownRatherThanZero() = withFile { file ->
        file.writeText(records(2))
        assertTrue(file.setReadable(false, false))
        try {
            assumeFalse("A privileged test account can still read this file", file.canRead())
            runBlocking { assertNull(AnswerHistoryStore(file).diagnosticsEntryCount()) }
        } finally { assertTrue(file.setReadable(true, false)) }
        assertEquals(records(2), file.readText())
    }

    @Test fun aNonRegularLinkIsUnknownWithoutFollowingOrRewritingItsTarget() = withFile { file ->
        val target = File(file.parentFile, "target.json").apply { writeText(records(2)) }
        Files.createSymbolicLink(file.toPath(), target.toPath())
        runBlocking { assertNull(AnswerHistoryStore(file).diagnosticsEntryCount()) }
        assertTrue(Files.isSymbolicLink(file.toPath()))
        assertEquals(records(2), target.readText())
    }

    @Test fun failedInitialLoadIsNotReclassifiedAsEmptyOnAnotherRead() = withFile { file ->
        file.writeText("broken")
        val store = AnswerHistoryStore(file)
        runBlocking {
            assertNull(store.diagnosticsEntryCount())
            file.writeText("[]")
            assertNull(store.diagnosticsEntryCount())
            assertEquals(0, AnswerHistoryStore(file).diagnosticsEntryCount())
        }
    }
}
