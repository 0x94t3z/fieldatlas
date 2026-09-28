package xyz.fieldatlas.attachments

object PdfTextPolicy {
    fun merge(embedded: String, recognized: String): String {
        val existing = embedded.lineSequence().map { it.trim().lowercase() }.toSet()
        val additional = recognized.lineSequence().filter { it.isNotBlank() && it.trim().lowercase() !in existing }.toList()
        return (listOf(embedded.trim()).filter { it.isNotBlank() } + additional).joinToString("\n")
    }
    fun coverageNote(pages: List<Int>): String? = pages.takeIf { it.isNotEmpty() }?.let {
        "Text may be missing or unreadable on pages ${it.joinToString(", ")}. These pages may also be blank or contain only graphics."
    }
}
