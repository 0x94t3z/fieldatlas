package xyz.fieldatlas.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Start research opens the answer page once per run; Back during the run is not undone by later progress. */
class AnswerOnStartTest {
    @Test fun aRunThatStartsOpensTheAnswerPageOnce() {
        assertTrue(opensAnswerOnStart(startedAtNanos = 100L, running = true, openedForRun = null))
        // Searching turns into reading and writing; the same run never opens the page again after Back.
        assertFalse(opensAnswerOnStart(startedAtNanos = 100L, running = true, openedForRun = 100L))
        // The next question or follow-up is a new run.
        assertTrue(opensAnswerOnStart(startedAtNanos = 200L, running = true, openedForRun = 100L))
    }

    @Test fun nothingRunningOpensNothing() {
        assertFalse(opensAnswerOnStart(startedAtNanos = 100L, running = false, openedForRun = null))
        assertFalse(opensAnswerOnStart(startedAtNanos = null, running = true, openedForRun = null))
    }
}
