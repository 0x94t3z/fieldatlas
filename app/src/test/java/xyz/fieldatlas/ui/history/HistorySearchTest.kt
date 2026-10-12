package xyz.fieldatlas.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.text.MarkdownLiteral

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

    @Test fun literalSourceWordsAndNativeMarkersAreSearchableButAppCitationsAreNot() {
        val literal = "Standard flags use table[1] and a native [S2] marker."
        val answer = "> ${MarkdownLiteral.encode(literal)} [S1]"
        val record = AnswerRecord("Which rule applies?", answer, 3L)
        assertTrue(historyMatches(record, "STANDARD table[1]"))
        assertTrue(historyMatches(record, "[s2]"))
        assertFalse(historyMatches(record, "[s1]"))
        assertFalse(historyMatches(record, "&#83;"))
    }

    @Test fun literalSourceCharacterReferenceSpellingIsDecodedOnlyOnceForSearch() {
        val record = AnswerRecord("Which spelling is literal?",
            "> ${MarkdownLiteral.encode("The source spells it &#91;S2&#93;.")} [S1]", 4L)
        assertTrue(historyMatches(record, "&#91;s2&#93;"))
        assertFalse(historyMatches(record, "[s2]"))
    }
}
