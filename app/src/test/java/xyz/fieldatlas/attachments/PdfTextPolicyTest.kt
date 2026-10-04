package xyz.fieldatlas.attachments

import org.junit.Assert.*
import org.junit.Test

class PdfTextPolicyTest {
    @Test fun imageBodyDoesNotDisappearBehindEmbeddedHeader() {
        assertEquals("Annual report\nRevenue grew.", PdfTextPolicy.merge("Annual report", "Annual report\nRevenue grew."))
    }
    @Test fun recognizedLinesAlreadyInTheTextLayerAreNotDuplicated() {
        val layer = "Service interval: 600 operating hours.\nUse filter K-9 and connector M5."
        // Recognition changes spacing, case and punctuation; the text must not be doubled.
        val merged = PdfTextPolicy.merge(layer, "SERVICE INTERVAL 600 operating hours\nUse filter K9 and connector M5\nSigned: J. Doe")
        assertEquals("$layer\nSigned: J. Doe", merged)
    }
    @Test fun onlyPagesWithoutARealTextLayerNeedRecognition() {
        assertTrue(PdfTextPolicy.needsOcr(""))
        assertTrue(PdfTextPolicy.needsOcr("Header\nPage 3"))
        assertFalse(PdfTextPolicy.needsOcr("The pump must be serviced every 600 operating hours. ".repeat(4)))
    }
    @Test fun emptyPagesHaveAnExplicitCoverageNotice() {
        val note = PdfTextPolicy.coverageNote(listOf(2, 4))!!
        assertTrue(note.contains("2, 4"))
        assertTrue(note.contains("unreadable", ignoreCase = true))
    }
    @Test fun pagesSkippedOrPastTheLimitAreNamed() {
        val note = PdfTextPolicy.coverageNote(unreadable = emptyList(), skippedOcr = listOf(31, 32, 33, 40), lastRead = 120, pageCount = 1200)!!
        assertTrue(note.contains("31–33, 40"))
        assertTrue(note.contains("Only pages 1–120 of 1200"))
        assertNull(PdfTextPolicy.coverageNote(emptyList(), emptyList(), 12, 12))
    }
}
