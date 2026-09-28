package xyz.fieldatlas.attachments

import org.junit.Assert.*
import org.junit.Test

class PdfTextPolicyTest {
    @Test fun imageBodyDoesNotDisappearBehindEmbeddedHeader() {
        assertEquals("Annual report\nRevenue grew.", PdfTextPolicy.merge("Annual report", "Annual report\nRevenue grew."))
    }
    @Test fun emptyPagesHaveAnExplicitCoverageNotice() {
        val note = PdfTextPolicy.coverageNote(listOf(2, 4))!!
        assertTrue(note.contains("2, 4"))
        assertTrue(note.contains("unreadable", ignoreCase = true))
    }
}
