package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.attachments.*

class AttachmentProvenanceTest {
    @Test fun detectsExplicitUnsourcedAnswerLabelsWithoutCallingAllOcrUnverified() {
        assertTrue(AttachmentProvenance.hasUnverifiedAnswerLabel("No linked local source – answered offline\nF1 stands for…"))
        assertFalse(AttachmentProvenance.hasUnverifiedAnswerLabel("A photo of a menu. Total 12.50."))
        assertFalse(AttachmentProvenance.needsWarning(Evidence("library:1", "1", "Book", "Local", "No linked local source", 1.0)))
    }
    @Test fun promptDistinguishesUploadsAndDoesNotClaimScreenshotVerification() {
        val attachments = listOf(
            ExtractedAttachment("screen", "answer.png", listOf(AttachmentPage(1, "No linked local source - answered offline\nAn earlier answer.")), true, kind = AttachmentKind.IMAGE),
            ExtractedAttachment("data", "diagnostics.json", listOf(AttachmentPage(1, "{\"version\":1}")), kind = AttachmentKind.TEXT),
        )
        val prompt = AttachmentEvidence.pack("Explain this", AttachmentEvidence.select("Explain this", attachments, 3), emptyList(), 4096).prompt
        assertTrue(prompt.contains("briefly distinguish each file"))
        assertTrue(prompt.contains("not independent verification"))
        assertTrue(prompt.contains("Contains an explicitly unsourced"))
    }
}
