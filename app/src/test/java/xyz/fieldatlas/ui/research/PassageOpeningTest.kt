package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageOpeningTest {
    @Test fun aShortPassageIsQuotedWhole() {
        assertEquals("“The tower is 330 metres tall.”", passageOpening("The tower is 330 metres tall."))
    }

    @Test fun aLongPassageIsCutWithAnEllipsisAndItsLineBreaksJoined() {
        val opening = passageOpening("It was built for the 1889 World's Fair\nto mark the centenary of the French Revolution, " +
            "and was at first criticised by some of France's leading artists.")!!
        assertTrue(opening, opening.endsWith("…”"))
        assertTrue(opening, "\n" !in opening)
        assertTrue(opening, opening.length <= 92)
    }

    @Test fun aPassageCutMidWordOpensAtItsFirstWholeWord() {
        assertEquals("\u201C\u2026parts on site.\u201D", passageOpening("e parts on site."))
    }

    @Test fun anEmptyPassageHasNoOpening() {
        assertNull(passageOpening("   "))
    }
}
