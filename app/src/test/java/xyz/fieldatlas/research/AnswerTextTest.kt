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

    @Test fun `oversized and zero source numbers cannot crash or create dead citations`() {
        assertEquals(
            "Claim [S1]. Invalid and and.",
            AnswerText.finalized("Claim [1]. Invalid [S999999999999999999999] and [S0] and [0].", 1),
        )
    }

    @Test fun `citation audit matches finalized answer and flags removed markers`() {
        val raw = "<think>ignore [S9]</think>Answer [1], [s2], [S#], [S999999999999999999999]."
        assertEquals("Answer [S1], [S2].", AnswerText.finalized(raw, 2))
        assertEquals(
            AnswerText.CitationAudit(setOf("S1", "S2"), hasUnmappedCitation = true),
            AnswerText.citationAudit(raw, 2),
        )
    }
}
