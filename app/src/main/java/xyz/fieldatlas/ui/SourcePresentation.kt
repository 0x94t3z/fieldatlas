package xyz.fieldatlas.ui

import java.net.URI
import xyz.fieldatlas.research.Evidence

internal fun sourceDisplayName(source: String): String =
    runCatching { URI(source).host?.removePrefix("www.") }.getOrNull()
        ?.takeIf(String::isNotBlank) ?: source

internal data class SourcePresentation(val isAttachment: Boolean, val metadata: String, val isRecognizedText: Boolean)

internal fun sourcePresentation(evidence: Evidence): SourcePresentation {
    if (!evidence.documentId.startsWith("attachment:")) return SourcePresentation(false, "From ${sourceDisplayName(evidence.source)}", false)
    val extension = evidence.title.substringAfterLast('.', "").uppercase(java.util.Locale.ROOT)
    val verifiedKind = evidence.source.split(" · ").getOrNull(1)
    val type = when (verifiedKind) {
        "pdf" -> "PDF"
        "image" -> "Image"
        "text" -> if (xyz.fieldatlas.attachments.TextFileTypes.supports(evidence.title)) xyz.fieldatlas.attachments.TextFileTypes.label(evidence.title) else "Text"
        "document" -> xyz.fieldatlas.attachments.DocumentTextReader.label(evidence.title)
        else -> extension.takeIf { it in setOf("TXT", "PDF", "MD", "CSV", "JSON", "JPG", "JPEG", "PNG", "WEBP", "HEIC") } ?: "File"
    }
    val unit = when {
        type == "PDF" -> "page"
        verifiedKind == "document" -> Regex("\\b(slide|chapter|part) \\d+").find(evidence.source)?.groupValues?.get(1)
        else -> null
    }
    val page = unit?.let { Regex("$it (\\d+)", RegexOption.IGNORE_CASE).find(evidence.source)?.groupValues?.get(1) }
    val where = page?.let { " · ${unit.replaceFirstChar(Char::uppercaseChar)} $it" } ?: ""
    return SourcePresentation(true, "Your file · $type$where", evidence.source.contains("recognized text", ignoreCase = true))
}
