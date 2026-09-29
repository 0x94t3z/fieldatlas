package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AnswerTextTest {
    @Test fun `parenthesized references normalize into usable citations`() {
        assertEquals("Claim [S4]. More [S3][S8].", AnswerText.finalized("Claim (source: S4). More (S3, S8).", 8))
        assertEquals("Claim [S4].", AnswerText.finalized("Claim (s4).", 4))
        assertEquals(AnswerText.CitationAudit(setOf("S3", "S8"), true),
            AnswerText.citationAudit("Claim (S3, S8, S99).", 8))
        assertEquals("Claim [S3][S8].", AnswerText.finalized("Claim (S3, S8, S99).", 8))
    }

    @Test fun `ordinary parentheses are not source references`() {
        val text = "Population (2020). Sample (3, 8). Vitamin (S4 formulation)."
        assertEquals(text, AnswerText.finalized(text, 8))
    }

    @Test fun `parenthesized references cannot source unverified prose`() {
        val answer = AnswerText.mixed("Claim (source: S1). More (S1, S2).", listOf(passage, passage))
        assertTrue(answer.contains("Claim. More."))
        assertFalse(answer.contains("[S"))
        assertFalse(answer.contains("(source:"))
    }

    @Test fun `matching quote accepts parenthesized source and grouped references`() {
        val answer = AnswerText.mixed("From saved sources\n\"Mitosis produces two cells.\" (source: S1)", listOf(passage))
        assertTrue(answer.contains("\"Mitosis produces two cells.\" [S1]"))
        val grouped = AnswerText.mixed("From saved sources\n\"Mitosis produces two cells.\" (S1, S2)", listOf(passage, passage))
        assertTrue(grouped.contains("[S1][S2]"))
    }

    @Test fun `grouped references only link passages that contain the quote`() {
        val other = passage.copy(text = "Unrelated findings.")
        val answer = AnswerText.mixed("From saved sources\n\"Mitosis produces two cells.\" (S1, S2, S99)", listOf(passage, other))
        assertTrue(answer.contains("[S1]"))
        assertFalse(answer.contains("[S2]"))
        assertFalse(answer.contains("[S99]"))
        assertEquals(3, AnswerText.citationMarkerCount("(S1, S2, S99)"))
    }

    @Test fun `parenthesized citation inside quote cannot bypass attribution checks`() {
        val source = passage.copy(text = "See the result (source: S2) in this paper.")
        val answer = AnswerText.mixed("From saved sources\n\"See the result (source: S2) in this paper.\" (S1)", listOf(source, passage))
        assertFalse(answer.contains("[S"))
    }

    @Test fun `unverified citations cannot bypass guard by touching a word`() {
        assertFalse(AnswerText.mixed("Claim[S1].", listOf(passage)).contains("[S1]"))
        for (marker in listOf("[S+1]", "[s01]", "[S١]")) {
            val answer = AnswerText.mixed("Claim$marker.", listOf(passage))
            assertTrue(xyz.fieldatlas.ui.research.buildAnswerPresentation(answer, 1).availableCitations.isEmpty())
        }
    }

    @Test fun `citation syntax inside a quote cannot create extra source links`() {
        val source = passage.copy(text = "See the result[S2] in this paper.")
        val answer = AnswerText.mixed("From saved sources\n\"See the result[S2] in this paper.\" [S1]", listOf(source, passage))
        assertFalse(answer.contains("[S2]"))
    }

    private val passage = Evidence("doc", "chunk", "Study", "Local", "Mitosis produces two cells. This does not establish effects in humans.", -1.0)

    @Test fun `mixed answer keeps matching quotes but never cites model explanation`() {
        val answer = AnswerText.mixed("Model explanation\nBackground claim [S1].\nFrom saved sources\n\"Mitosis produces two cells.\" [S1]", listOf(passage))
        assertTrue(answer.contains("Model explanation—not verified against saved sources"))
        assertTrue(answer.contains("Background claim."))
        assertTrue(answer.contains("\"Mitosis produces two cells.\" [S1]"))
        assertEquals(1, Regex("\\[S1]").findAll(answer).count())
    }

    @Test fun `fabricated source claims are demoted without clickable citations`() {
        val answer = AnswerText.mixed("From saved sources\n\"Mitosis produces four cells.\" [S1]\nThis proves a treatment works [S1].", listOf(passage))
        assertFalse(answer.contains("[S1]"))
        assertFalse(answer.contains("## From saved sources"))
        assertTrue(answer.contains("not verified against saved sources"))
    }

    @Test fun `matching quote cannot validate extra prose in the same source line`() {
        val answer = AnswerText.mixed("From saved sources\n\"Mitosis produces two cells.\" [S1] This proves human lifespan extension.", listOf(passage))
        assertFalse(answer.contains("[S1]"))
    }

    @Test fun `unlabelled output and incomplete model citations default to unverified`() {
        val answer = AnswerText.mixed("Background [S1]. More [S", listOf(passage))
        assertTrue(answer.contains("not verified against saved sources"))
        assertFalse(answer.contains("[S"))
        assertEquals("", AnswerText.mixed("From saved sou", listOf(passage)))
    }

    @Test fun `quote must match the cited source not a different source`() {
        val other = passage.copy(documentId = "other", text = "Unrelated findings.")
        assertFalse(AnswerText.mixed("From saved sources\n\"Mitosis produces two cells.\" [S2]", listOf(passage, other)).contains("[S2]"))
    }

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
