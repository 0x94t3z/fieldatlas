package xyz.fieldatlas.attachments

import java.io.InputStream
import java.io.ByteArrayOutputStream

object AttachmentPolicy {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_CHARACTERS = 100_000
    const val MAX_PAGES = 30
    const val MAX_COUNT = 3
    fun checkCount(existing: Int) {
        if (existing >= MAX_COUNT) throw AttachmentException("You can attach up to 3 files. Remove one to add another.")
    }
    fun readBounded(input: InputStream, checkCancelled: () -> Unit = {}): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw AttachmentException("This file is too large. Choose a file smaller than 20 MB.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun kind(name: String, bytes: ByteArray): AttachmentKind {
        val head = bytes.take(12).toByteArray()
        val ascii = head.toString(Charsets.ISO_8859_1)
        if (ascii.startsWith("%PDF-")) return AttachmentKind.PDF
        if (head.take(3) == listOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) ||
            head.take(8) == listOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).map { it.toByte() } ||
            (ascii.startsWith("RIFF") && ascii.endsWith("WEBP"))) return AttachmentKind.IMAGE
        if (name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "markdown", "csv", "json")) {
            TextAttachmentDecoder.decode(bytes)
            return AttachmentKind.TEXT
        }
        throw AttachmentException("Choose a text file, PDF, or a JPG, PNG or WebP image.")
    }
}
