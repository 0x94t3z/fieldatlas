package xyz.fieldatlas.attachments

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DocumentTextReaderTest {
    private val dir = kotlin.io.path.createTempDirectory().toFile()
    @After fun cleanUp() { dir.deleteRecursively() }

    private fun zip(name: String, entries: Map<String, String>): File = File(dir, name).also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            for ((path, text) in entries) { out.putNextEntry(ZipEntry(path)); out.write(text.toByteArray()); out.closeEntry() }
        }
    }

    @Test fun wordDocumentKeepsParagraphsTabsAndTableCells() {
        val xml = """<?xml version="1.0"?><w:document xmlns:w="w"><w:body>
            <w:p><w:r><w:t>Revision B</w:t></w:r></w:p>
            <w:p><w:r><w:t xml:space="preserve">Filter: </w:t></w:r><w:r><w:t>K-9</w:t></w:r><w:r><w:tab/><w:t>M5</w:t></w:r></w:p>
            <w:p><w:r><w:delText>old text</w:delText><w:t>Interval &amp; hours: 600</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Serial</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>2000–2999</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            </w:body></w:document>"""
        val pages = DocumentTextReader.read(zip("pump.docx", mapOf("word/document.xml" to xml)), "pump.docx")
        val text = pages.single().text
        assertTrue(text, text.startsWith("Revision B\nFilter: K-9 M5\nInterval & hours: 600"))
        assertFalse("deleted tracked changes are not text", text.contains("old text"))
        assertTrue(text, text.contains("Serial") && text.contains("2000–2999"))
    }

    @Test fun slidesBecomePagesInNumericOrder() {
        fun slide(text: String) = """<p:sld><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>$text</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        val file = zip("deck.pptx", mapOf("ppt/slides/slide10.xml" to slide("Ten"), "ppt/slides/slide2.xml" to slide("Two"), "ppt/slides/slide1.xml" to slide("One")))
        val pages = DocumentTextReader.read(file, "deck.pptx")
        assertEquals(listOf(1 to "One", 2 to "Two", 10 to "Ten"), pages.map { it.number to it.text })
        assertEquals("slide", DocumentTextReader.pageNoun("deck.pptx"))
    }

    @Test fun openDocumentTextKeepsHeadingsAndSpaces() {
        val xml = """<office:document-content><office:body><office:text>
            <text:h>Camp rules</text:h><text:p>Quiet after<text:s text:c="2"/>22:00.<text:line-break/>No fires.</text:p>
            </office:text></office:body></office:document-content>"""
        val text = DocumentTextReader.read(zip("rules.odt", mapOf("content.xml" to xml)), "rules.odt").single().text
        assertEquals("Camp rules\nQuiet after 22:00.\nNo fires.", text)
    }

    @Test fun epubChaptersFollowTheSpineNotTheZipOrder() {
        val entries = mapOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<package><manifest>
                <item id="c2" href="text/ch%202.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="css" href="style.css"/></manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/text/ch 2.xhtml" to "<html><body><h1>Water</h1><p>Boil water for one minute.</p></body></html>",
            "OEBPS/text/ch1.xhtml" to "<html><head><title>x</title><style>p{}</style></head><body><h1>Shelter</h1><p>Stay dry.</p><script>alert(1)</script></body></html>",
        )
        val pages = DocumentTextReader.read(zip("guide.epub", entries), "guide.epub")
        assertEquals(listOf(1, 2), pages.map { it.number })
        assertEquals("Shelter\nStay dry.", pages[0].text)
        assertEquals("Water\nBoil water for one minute.", pages[1].text)
    }

    @Test fun damagedOrEmptyDocumentsGiveAClearError() {
        val notZip = File(dir, "broken.docx").apply { writeText("not a zip") }
        assertTrue(assertThrows(AttachmentException::class.java) { DocumentTextReader.read(notZip, "broken.docx") }.message!!.contains("damaged"))
        val noBody = zip("empty.docx", mapOf("[Content_Types].xml" to "<Types/>"))
        assertThrows(AttachmentException::class.java) { DocumentTextReader.read(noBody, "empty.docx") }
    }

    @Test fun zipBombIsRefusedWithoutInflatingItAll() {
        val file = File(dir, "bomb.docx")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("word/document.xml"))
            val block = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            repeat(20) { out.write(block) } // 20 MB of one letter compresses to a few KB.
            out.closeEntry()
        }
        assertTrue(file.length() < 1024 * 1024)
        assertTrue(assertThrows(AttachmentException::class.java) { DocumentTextReader.read(file, "bomb.docx") }.message!!.contains("too large"))
    }
}
