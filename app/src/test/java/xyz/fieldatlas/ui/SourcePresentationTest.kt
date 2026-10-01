package xyz.fieldatlas.ui

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.research.Evidence

class SourcePresentationTest {
    @Test fun sourceCodeGetsAnAccurateLanguageLabel() {
        assertEquals("Your file · Kotlin", sourcePresentation(attachment("main.kt", "Attached file · text · page 1")).metadata)
    }
    @Test fun verifiedTypeOverridesMisleadingOrMissingExtension() {
        assertEquals("Your file · Image", sourcePresentation(attachment("scan.pdf", "Attached file · image · page 1 · recognized text")).metadata)
        assertEquals("Your file · PDF · Page 2", sourcePresentation(attachment("ticket", "Attached file · pdf · page 2")).metadata)
    }
    private fun attachment(name: String, source: String = "Attached file · page 1") =
        Evidence("attachment:one", "one:p1:0", name, source, "Original text", 1.0)

    @Test fun plainTextMetadataDoesNotPretendToBePaginatedOrScanned() {
        val info = sourcePresentation(attachment("notes.txt"))
        assertTrue(info.isAttachment)
        assertEquals("Your file · TXT", info.metadata)
        assertFalse(info.isRecognizedText)
    }
    @Test fun pdfKeepsPageAndOcrProvenance() {
        val info = sourcePresentation(attachment("tickets.PDF", "Attached file · page 3 · recognized text"))
        assertEquals("Your file · PDF · Page 3", info.metadata)
        assertTrue(info.isRecognizedText)
    }
    @Test fun photosDoNotShowInventedPageNumbers() {
        val info = sourcePresentation(attachment("menu.jpg", "Attached file · page 1 · recognized text"))
        assertEquals("Your file · JPG", info.metadata)
        assertTrue(info.isRecognizedText)
    }
    @Test fun libraryFileTitlesAreNotMistakenForAttachments() {
        val info = sourcePresentation(attachment("article.pdf").copy(documentId = "wiki-one", source = "https://www.example.org/article"))
        assertFalse(info.isAttachment)
        assertEquals("From example.org", info.metadata)
    }
}
