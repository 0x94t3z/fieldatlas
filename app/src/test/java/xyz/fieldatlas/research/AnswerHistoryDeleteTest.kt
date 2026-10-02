package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerHistoryDeleteTest {
    @Test fun deletingOneAnswerKeepsTheRestAndSurvivesReopening() = runBlocking {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(root, "answers.json")
            file.writeText("""[{"question":"a","answer":"A","createdAtEpochMs":1},{"question":"b","answer":"B","createdAtEpochMs":2}]""")
            val store = AnswerHistoryStore(file, synchronousWrites = true)
            store.ensureLoaded()
            store.delete(2)
            store.delete(99)
            assertEquals(listOf("a"), store.records.value.map { it.question })
            val reopened = AnswerHistoryStore(file, synchronousWrites = true)
            reopened.ensureLoaded()
            assertEquals(listOf("a"), reopened.records.value.map { it.question })
        } finally { root.deleteRecursively() }
    }
}

class HistoryEvidenceTest {
    @Test fun passagesAreSavedWithEveryAnswerButCapped() {
        val short = Evidence("d", "d:0", "Short", "https://example.org", "A short passage.", 0.0)
        val long = short.copy(title = "Long", text = "x".repeat(5_000))
        val saved = xyz.fieldatlas.ui.research.historyEvidence(listOf(short, long))
        assertEquals(short, saved[0])
        assertEquals(2_001, saved[1].text.length)
        assertEquals("Long", saved[1].title)
    }
}
