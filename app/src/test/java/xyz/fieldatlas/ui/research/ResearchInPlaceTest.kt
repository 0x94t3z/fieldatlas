package xyz.fieldatlas.ui.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.AnswerHistoryStore
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.research.Evidence

/** The answer page's progress says only what the app knows: the step, what that step measures, and the words so far. */
class ResearchInPlaceTest {
    private val passage = Evidence("doc", "doc:0", "Hagia Sophia — Overview", "https://simple.wikipedia.org/w/x", "Text", 1.0)

    @Test fun theStageFollowsTheRun() {
        assertEquals(0, researchStage(ResearchUiState(phase = ResearchPhase.Searching)))
        assertEquals(1, researchStage(ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage))))
        assertEquals(2, researchStage(ResearchUiState(phase = ResearchPhase.Generating, tokensWritten = 12)))
        assertEquals(2, researchStage(ResearchUiState(phase = ResearchPhase.Generating, checking = true)))
    }

    @Test fun theDinoMovesOnlyWithWhatIsMeasured() {
        val searching = ResearchUiState(phase = ResearchPhase.Searching)
        assertTrue(trailPosition(searching.copy(retrievalProgress = 0.5), null) > trailPosition(searching, null))
        val reading = ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage))
        assertTrue(trailPosition(reading, 0.6f) > trailPosition(reading, null))
        // Writing has no known length and is the last stop: more words or checking never move the dino.
        val writing = ResearchUiState(phase = ResearchPhase.Generating, tokensWritten = 40)
        assertEquals(trailPosition(writing, null), trailPosition(writing.copy(tokensWritten = 400), null), 0f)
        assertEquals(trailPosition(writing, null), trailPosition(writing.copy(checking = true), null), 0f)
        // Never at or past the next flag before that step begins, never past the end of the trail.
        assertTrue(trailPosition(searching.copy(retrievalProgress = 1.0), null) < trailPosition(reading, null))
        assertTrue(trailPosition(reading, 1f) < trailPosition(writing, null))
        assertTrue(trailPosition(writing, null) < 1f)
    }

    @Test fun theStatusSaysWhatIsHappeningInPlainWords() {
        assertEquals("Searching your collections", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Searching)))
        assertEquals("Finding better search words", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Planning)))
        assertEquals("Reading 2 passages", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage, passage))))
        assertEquals("Reading 1 passage", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage))))
        assertEquals("Preparing the model", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Generating)))
        assertEquals("Writing the answer", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Generating, tokensWritten = 5)))
        assertEquals("Checking the answer", researchStatusTitle(ResearchUiState(phase = ResearchPhase.Generating, tokensWritten = 5, checking = true)))
    }

    @Test fun wordsAreCountedRoughlyAndInTens() {
        assertEquals(200, wordsWritten(273))
        assertEquals(0, wordsWritten(12))
    }

    @Test fun theUsualTimeComesFromThisPhonesRecentResearchedAnswers() {
        fun record(at: Long, ms: Long?) = AnswerRecord("q", "a", at, durationMs = ms)
        assertNull("too few to say", typicalAnswerMillis(listOf(record(1, 180_000), record(2, 150_000))))
        assertNull("old answers have no duration", typicalAnswerMillis(List(5) { record(it.toLong(), null) }))
        // Instant replies (a guide, a sum) are left out; the median of the latest five is used.
        val records = listOf(record(10, 2_000), record(9, 200_000), record(8, 140_000), record(7, 170_000), record(6, 900),
            record(5, 160_000), record(4, 150_000), record(1, 600_000))
        assertEquals(160_000L, typicalAnswerMillis(records))
        // The range leaves out the quickest and slowest of the five; the status line rounds it to minutes.
        assertEquals(150_000L..170_000L, typicalAnswerRange(records))
        assertEquals("about 3 min", usualMinutes(150_000L..170_000L))
        assertEquals("3–4 min", usualMinutes(160_000L..250_000L))
        assertNull(typicalAnswerRange(listOf(record(1, 180_000), record(2, 150_000))))
    }

    @Test fun anAnswersDurationIsKeptInHistory() = runBlocking {
        val file = java.io.File(java.nio.file.Files.createTempDirectory("duration").toFile(), "answers.json")
        AnswerHistoryStore(file, synchronousWrites = true).record("q", "answer", emptyList(), durationMs = 165_000)
        val reloaded = AnswerHistoryStore(file, synchronousWrites = true)
        reloaded.ensureLoaded()
        assertEquals(165_000L, reloaded.records.value.single().durationMs)
    }

    @Test fun aReadingIsShownOnlyWhenItDiffersFromTheTitle() {
        assertNull("a question is searched as typed", researchReading(ResearchUiState(question = "tell me about the hagia sophia")))
        assertEquals("Read as" to "tell me about the hagia sophia: is it free to visit?", researchReading(ResearchUiState(
            question = "tell me about the hagia sophia: is it free to visit?", asked = "is it free to visit?")))
        assertEquals("Also searching for" to "tide, moon, gravity", researchReading(ResearchUiState(
            question = "why are there two tides a day", keywords = listOf("tide", "moon", "gravity"))))
    }
}
