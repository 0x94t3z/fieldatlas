package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerTextTest {
    @Test fun `final answer normalizes numeric citations`() {
        assertEquals("Claim [S2].", AnswerText.finalized("Claim [2].", sourceCount = 2))
    }

    @Test fun `final answer removes placeholders and unavailable citations`() {
        assertEquals(
            "First [S1]. Second. Third.",
            AnswerText.finalized("First [S1]. Second [S#]. Third [S9].", sourceCount = 2),
        )
    }

    @Test fun `final answer keeps markdown formatting`() {
        assertEquals("**Finding:** supported [S1]", AnswerText.finalized("**Finding:** supported [S1]", 1))
    }
}
