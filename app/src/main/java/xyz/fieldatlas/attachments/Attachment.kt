package xyz.fieldatlas.attachments

import java.io.File

enum class AttachmentKind { TEXT, PDF, IMAGE }
data class AttachmentInput(val id: String, val displayName: String, val localFile: File, val kind: AttachmentKind)
data class AttachmentPage(val number: Int, val text: String)
data class ExtractedAttachment(val id: String, val displayName: String, val pages: List<AttachmentPage>, val fromOcr: Boolean = false, val coverageNote: String? = null, val kind: AttachmentKind? = null)
fun interface AttachmentReader { suspend fun read(input: AttachmentInput): ExtractedAttachment }
class AttachmentException(message: String) : Exception(message)

fun attachmentError(error: Throwable): String = when (error) {
    is AttachmentException -> error.message ?: "Could not read this file."
    is SecurityException -> "This file is no longer available. Please select it again."
    else -> "Could not read this file. Try another file saved on your device."
}
