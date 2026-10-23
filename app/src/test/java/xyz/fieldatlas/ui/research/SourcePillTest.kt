package xyz.fieldatlas.ui.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePillTest {
    @Test fun aFromMemoryAnswerNamesItsSourcesOnlyWhenItCitedNotesItRead() {
        // The 4B read three notes for "who designed the Sydney Opera House" and cited [1]: the header says so.
        assertTrue(showsSourcePill(fromKnowledge = true, readSources = true, cited = 1))
        // Gemma from memory, with figures marked against the sources: the foot line covers it; the time stands alone.
        assertFalse(showsSourcePill(fromKnowledge = true, readSources = false, cited = 1))
        assertFalse(showsSourcePill(fromKnowledge = true, readSources = true, cited = 0))
        // Every other answer keeps its pill, cited or not.
        assertTrue(showsSourcePill(fromKnowledge = false, readSources = false, cited = 0))
        assertTrue(showsSourcePill(fromKnowledge = false, readSources = false, cited = 2))
    }
}
