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
        "text" -> extension.takeIf { it in setOf("TXT", "MD", "CSV", "JSON") } ?: "Text"
        else -> extension.takeIf { it in setOf("TXT", "PDF", "MD", "CSV", "JSON", "JPG", "JPEG", "PNG", "WEBP", "HEIC") } ?: "File"
    }
    val page = if (type == "PDF") Regex("page (\\d+)", RegexOption.IGNORE_CASE).find(evidence.source)?.groupValues?.get(1) else null
    return SourcePresentation(true, "Your file · $type" + (page?.let { " · Page $it" } ?: ""), evidence.source.contains("recognized text", ignoreCase = true))
}
