package xyz.fieldatlas.attachments

import java.io.File
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Text from zip-based documents: Word (.docx), PowerPoint (.pptx), OpenDocument text and
 * slides (.odt, .odp) and EPUB books. Pages are slides for presentations and spine documents
 * (usually chapters) for EPUB; a Word or OpenDocument text file is one page.
 *
 * Archives are untrusted: entry count, each entry's size and the total inflated size are
 * capped, so a small "zip bomb" cannot exhaust the phone's memory.
 */
object DocumentTextReader {
    const val MAX_ENTRIES = 5_000
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    private val extensions = setOf("docx", "docm", "pptx", "pptm", "odt", "odp", "epub")

    fun supports(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

    fun label(name: String) = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "docx", "docm" -> "Word document"
        "pptx", "pptm" -> "Slides"
        "odt" -> "OpenDocument text"
        "odp" -> "OpenDocument slides"
        "epub" -> "EPUB book"
        else -> "Document"
    }

    /** What a page is called in citations for this format. */
    fun pageNoun(name: String) = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "pptx", "pptm", "odp" -> "slide"
        "epub" -> "chapter"
        else -> "part"
    }

    fun read(file: File, name: String): List<AttachmentPage> {
        val zip = try { ZipFile(file) } catch (_: ZipException) {
            throw AttachmentException("This document is damaged or not a supported format.")
        }
        zip.use { archive ->
            if (archive.size() > MAX_ENTRIES) throw AttachmentException("This document has too many internal parts to read safely.")
            val budget = Budget()
            fun text(path: String): String? {
                val entry = archive.getEntry(path) ?: return null
                if (entry.isDirectory) return null
                if (entry.size > MAX_ENTRY_BYTES) throw AttachmentException("This document is too large to read safely.")
                val bytes = archive.getInputStream(entry).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var entryTotal = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        entryTotal += count
                        // Declared sizes can lie; count what is actually inflated.
                        if (entryTotal > MAX_ENTRY_BYTES) throw AttachmentException("This document is too large to read safely.")
                        budget.take(count)
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                return String(bytes, Charsets.UTF_8)
            }
            val names = archive.entries().asSequence().map { it.name }.toList()
            val pages = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "docx", "docm" -> listOfNotNull(text("word/document.xml")?.let { AttachmentPage(1, docx(it)) })
                "pptx", "pptm" -> names.mapNotNull { entry ->
                    Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry)?.groupValues?.get(1)?.toInt()
                }.sorted().map { number -> AttachmentPage(number, pptx(text("ppt/slides/slide$number.xml").orEmpty())) }
                "odt" -> listOfNotNull(text("content.xml")?.let { AttachmentPage(1, odf(it)) })
                "odp" -> text("content.xml")?.let(::odpSlides).orEmpty()
                "epub" -> epub(::text)
                else -> emptyList()
            }
            if (pages.isEmpty()) throw AttachmentException("This document is damaged or not a supported format.")
            return pages.filter { it.text.isNotBlank() }
        }
    }

    fun docx(xml: String) = MarkupText.officeXml(xml,
        paragraph = setOf("w:p"), text = setOf("w:t"), tab = setOf("w:tab"), lineBreak = setOf("w:br", "w:cr"), cell = setOf("w:tc"), row = setOf("w:tr"))

    fun pptx(xml: String) = MarkupText.officeXml(xml,
        paragraph = setOf("a:p"), text = setOf("a:t"), tab = setOf("a:tab"), lineBreak = setOf("a:br"), cell = setOf("a:tc"), row = setOf("a:tr"))

    fun odf(xml: String) = MarkupText.officeXml(
        xml.substringAfter("<office:body", xml),
        paragraph = setOf("text:p", "text:h"), text = setOf("text:p", "text:h", "text:span", "text:a"),
        tab = setOf("text:tab"), lineBreak = setOf("text:line-break"), cell = setOf("table:table-cell"), row = setOf("table:table-row"), space = "text:s")

    fun odpSlides(xml: String): List<AttachmentPage> =
        Regex("<draw:page\\b.*?</draw:page>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .mapIndexed { index, match -> AttachmentPage(index + 1, odf(match.value)) }.toList()

    /** Spine order from the OPF package; falls back to no pages when the book has no spine. */
    fun epub(text: (String) -> String?): List<AttachmentPage> {
        val container = text("META-INF/container.xml") ?: return emptyList()
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: return emptyList()
        val opf = text(opfPath) ?: return emptyList()
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = Regex("<(?:\\w+:)?item\\b[^>]*>").findAll(opf).mapNotNull { item ->
            val attrs = item.value
            val id = Regex("\\bid\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
            val href = Regex("\\bhref\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
            if (id != null && href != null) id to href else null
        }.toMap()
        val spine = Regex("<(?:\\w+:)?itemref\\b[^>]*\\bidref\\s*=\\s*\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        return spine.mapNotNull(manifest::get).distinct().mapIndexedNotNull { index, href ->
            val path = normalize(base + java.net.URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
            text(path)?.let { AttachmentPage(index + 1, MarkupText.html(it)) }
        }.filter { it.text.isNotBlank() }.mapIndexed { index, page -> page.copy(number = index + 1) }
    }

    private fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> {}
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
        return parts.joinToString("/")
    }

    private class Budget {
        private var used = 0L
        fun take(count: Int) {
            used += count
            if (used > MAX_TOTAL_BYTES) throw AttachmentException("This document is too large to read safely.")
        }
    }
}
