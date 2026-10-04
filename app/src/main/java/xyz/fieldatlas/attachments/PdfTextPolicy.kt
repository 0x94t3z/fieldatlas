package xyz.fieldatlas.attachments

object PdfTextPolicy {
    /** Fewer letters than this on a page's text layer means the page is probably an image. */
    const val MIN_LAYER_LETTERS = 120

    fun needsOcr(layer: String) = layer.count(Char::isLetter) < MIN_LAYER_LETTERS

    /**
     * Adds recognized lines the text layer does not already contain. Lines are compared on
     * letters and digits only, because recognition changes spacing, case and punctuation.
     */
    fun merge(embedded: String, recognized: String): String {
        val known = normalize(embedded)
        val additional = recognized.lineSequence().map { it.trim() }.filter { line ->
            val key = normalize(line)
            line.isNotBlank() && (key.isEmpty() || key !in known)
        }.toList()
        return (listOf(embedded.trim()).filter { it.isNotBlank() } + additional).joinToString("\n")
    }

    private fun normalize(text: String) = text.lowercase().filter(Char::isLetterOrDigit)

    fun coverageNote(pages: List<Int>): String? = coverageNote(pages, emptyList(), 0, 0)

    /** One plain sentence per kind of gap, or null when every page was read. */
    fun coverageNote(unreadable: List<Int>, skippedOcr: List<Int>, lastRead: Int, pageCount: Int): String? {
        val notes = buildList {
            if (unreadable.isNotEmpty()) add("Text may be missing or unreadable on pages ${ranges(unreadable)}. These pages may also be blank or contain only graphics.")
            if (skippedOcr.isNotEmpty()) add("Pages ${ranges(skippedOcr)} are scanned images and were not read (text recognition is limited to ${AttachmentPolicy.MAX_OCR_PAGES} pages).")
            if (pageCount > lastRead && lastRead > 0) add("Only pages 1–$lastRead of $pageCount were read.")
        }
        return notes.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** "2, 4–7, 9" */
    fun ranges(pages: List<Int>): String {
        val sorted = pages.distinct().sorted()
        val out = mutableListOf<String>()
        var start = sorted.firstOrNull() ?: return ""
        var previous = start
        for (page in sorted.drop(1) + Int.MIN_VALUE) {
            if (page == previous + 1) { previous = page; continue }
            out += if (start == previous) "$start" else "$start–$previous"
            start = page; previous = page
        }
        return out.joinToString(", ")
    }
}
