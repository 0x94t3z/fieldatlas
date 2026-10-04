package xyz.fieldatlas.attachments

import java.io.File

enum class AttachmentKind { TEXT, PDF, IMAGE, DOCUMENT }
data class AttachmentInput(val id: String, val displayName: String, val localFile: File, val kind: AttachmentKind)
data class AttachmentPage(val number: Int, val text: String)
/**
 * [coverageNote] tells the user what was not read (blank pages, pages past a limit, a cut-off
 * file). [truncated] is true when the file holds more text than was kept.
 */
data class ExtractedAttachment(
    val id: String,
    val displayName: String,
    val pages: List<AttachmentPage>,
    val fromOcr: Boolean = false,
    val coverageNote: String? = null,
    val kind: AttachmentKind? = null,
    val truncated: Boolean = false,
)
fun interface AttachmentReader { suspend fun read(input: AttachmentInput): ExtractedAttachment }
class AttachmentException(message: String) : Exception(message)

fun attachmentError(error: Throwable): String = when (error) {
    is AttachmentException -> error.message ?: "Could not read this file."
    is SecurityException -> "This file is no longer available. Please select it again."
    is OutOfMemoryError -> "This file is too large for this phone's memory. Try a smaller file or a section of it."
    is java.io.IOException -> if (error.message.orEmpty().let { "ENOSPC" in it || "No space" in it })
        "Not enough free storage to attach this file. Free up some space and try again."
        else "Could not read this file. Try another file saved on your device."
    else -> "Could not read this file. Try another file saved on your device."
}
