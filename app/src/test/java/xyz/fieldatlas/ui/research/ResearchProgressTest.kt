package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchProgressTest {
    @Test fun readingProgressMovesBetweenBlockReportsWithoutPassingTheEngine() {
        // 256 tokens read in the first 6 s of a 1,024-token prompt.
        val samples = listOf(PrefillSample(256, 1024, 6_000))
        assertNull(estimatePrefill(0, emptyList(), 3_000))
        val atReport = estimatePrefill(0, samples, 6_000)!!
        assertEquals(0.25f, atReport.fraction, 0.001f)
        assertEquals(18L, atReport.secondsLeft)
        val later = estimatePrefill(0, samples, 9_000)!!
        assertTrue(later.fraction > 0.3f && later.fraction < 0.45f)
        // A slow block never carries the bar past what the engine has proved it read.
        val stalled = estimatePrefill(0, samples, 60_000)!!
        assertTrue(stalled.fraction < 0.5f)
    }

    @Test fun theLastBlockStopsAtTheEndAndTimeLeftIsCoarse() {
        val samples = listOf(PrefillSample(256, 300, 5_000), PrefillSample(300, 300, 6_000))
        val done = estimatePrefill(0, samples, 7_000)!!
        assertEquals(1f, done.fraction, 0.0001f)
        assertNull(done.secondsLeft)
        assertEquals("almost done", formatTimeLeft(2))
        assertEquals("about 20 s left", formatTimeLeft(17))
        assertEquals("about 2 min left", formatTimeLeft(95))
    }

    @Test fun theLiveDraftFollowsTheNewestTextFromALineBreak() {
        val opening = "Opening paragraph that is long enough. ".repeat(20)
        val answer = opening + "\n- Second point arrives\n- Newest point [S1] is here"
        val draft = draftAnswerPreview(answer, maxChars = 120)
        assertTrue(draft, draft.startsWith("…"))
        assertTrue(draft, draft.endsWith("Newest point is here"))
        assertEquals("Short answer.", draftAnswerPreview("Short answer. [S1]"))
    }
}
