package xyz.fieldatlas.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord

class HistorySearchTest {
    private val snake = AnswerRecord("I got bitten by a snake, what should I do?",
        "Keep still and call emergency services [S1].", 1L, sources = listOf("Venomous snakes — NIOSH"))
    private val water = AnswerRecord("Is stream water safe to drink?", "Boil it for one minute [S2].", 2L)

    @Test fun everyWordMustAppearInAnyOrderAndCase() {
        assertTrue(historyMatches(snake, "SNAKE bitten"))
        assertTrue(historyMatches(snake, "niosh"))
        assertTrue(historyMatches(water, "boil minute"))
        assertFalse(historyMatches(water, "snake"))
        assertFalse(historyMatches(snake, "snake boil"))
    }

    @Test fun anEmptyQueryShowsEverythingAndCitationMarksAreNotSearched() {
        assertTrue(historyMatches(water, "   "))
        assertFalse(historyMatches(water, "s2"))
    }
}
