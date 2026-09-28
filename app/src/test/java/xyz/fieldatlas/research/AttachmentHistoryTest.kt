package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AttachmentHistoryTest {
    @Test fun savesCitedExcerptsAndReadsLegacyHistory() = runBlocking {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(root, "answers.json")
            file.writeText("""[{"question":"old","answer":"old answer","createdAtEpochMs":1,"sources":["Old source"]}]""")
            val store = AnswerHistoryStore(file, synchronousWrites = true)
            store.ensureLoaded()
            assertTrue(store.records.value.single().evidence.isEmpty())
            val source = Evidence("attachment:x", "x:p2:0", "menu.pdf", "Attached file · page 2", "Explicit vegan menu", 1.0)
            store.record("new", "Vegan [S1]", listOf("menu.pdf"), listOf(source))
            val reopened = AnswerHistoryStore(file, synchronousWrites = true)
            reopened.ensureLoaded()
            assertEquals(source, reopened.records.value.first().evidence.single())
            assertEquals(2, reopened.records.value.size)
        } finally { root.deleteRecursively() }
    }
}
