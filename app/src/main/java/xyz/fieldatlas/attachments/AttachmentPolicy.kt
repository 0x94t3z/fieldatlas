package xyz.fieldatlas.attachments

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

object AttachmentPolicy {
    const val MAX_BYTES = 50 * 1024 * 1024
    /** Text kept per file. Longer files are read up to here and the answer says so. */
    const val MAX_CHARACTERS = 300_000
    /** PDF pages read from their text layer. */
    const val MAX_PAGES = 1_000
    /** PDF pages without a text layer that are run through text recognition (slow). */
    const val MAX_OCR_PAGES = 30
    const val MAX_COUNT = 3
    /** Bytes sampled to decide whether a file is text; longer files are checked on this prefix. */
    const val TEXT_SAMPLE_BYTES = 1024 * 1024

    fun checkCount(existing: Int) {
        if (existing >= MAX_COUNT) throw AttachmentException("You can attach up to $MAX_COUNT files. Remove one to add another.")
    }

    /** Copies [input] to [output], refusing more than [MAX_BYTES] whatever size the provider claims. */
    fun copyBounded(input: InputStream, output: OutputStream, checkCancelled: () -> Unit = {}): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw AttachmentException("This file is too large. Choose a file smaller than ${MAX_BYTES / (1024 * 1024)} MB.")
            try { output.write(buffer, 0, count) } catch (error: IOException) { throw storageError(error) }
        }
        return total
    }

    /**
     * Decides the kind from the file's own bytes, not the provider's MIME type or the name alone.
     * [sample] is the start of the file; [complete] says whether it is the whole file.
     */
    fun kind(name: String, sample: ByteArray, complete: Boolean = true): AttachmentKind {
        val head = sample.copyOfRange(0, minOf(sample.size, 16))
        val ascii = head.toString(Charsets.ISO_8859_1)
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        fun startsWith(vararg bytes: Int) = head.size >= bytes.size && bytes.indices.all { head[it].toInt() and 0xff == bytes[it] }
        if (ascii.startsWith("%PDF-")) return AttachmentKind.PDF
        if (startsWith(0xff, 0xd8, 0xff) || startsWith(137, 80, 78, 71, 13, 10, 26, 10) ||
            (ascii.startsWith("RIFF") && ascii.substring(8, minOf(12, ascii.length)) == "WEBP") ||
            ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a") ||
            (ascii.startsWith("BM") && extension == "bmp") ||
            (ascii.length >= 12 && ascii.substring(4, 8) == "ftyp" && ascii.substring(8, 12) in heifBrands)) return AttachmentKind.IMAGE
        if (startsWith(0x50, 0x4b, 0x03, 0x04)) {
            if (DocumentTextReader.supports(name)) return AttachmentKind.DOCUMENT
            if (extension in setOf("xlsx", "xlsm", "ods")) throw AttachmentException("Spreadsheets aren’t supported yet. Export the sheet as CSV, then attach that.")
            throw AttachmentException("Compressed archives aren’t supported. Attach the document inside it instead.")
        }
        if (startsWith(0xd0, 0xcf, 0x11, 0xe0)) throw AttachmentException("This is an older Office format (.doc, .xls or .ppt). Save it as .docx, .pptx, PDF or CSV, then attach it again.")
        if (TextFileTypes.supports(name)) {
            TextAttachmentDecoder.decodeText(sample, inputTruncated = !complete)
            return AttachmentKind.TEXT
        }
        // A file with no or an unknown extension is accepted only when it is clean UTF-8/UTF-16.
        if (runCatching { TextAttachmentDecoder.decodeText(sample, inputTruncated = !complete, allowLegacy = false) }.isSuccess) return AttachmentKind.TEXT
        throw AttachmentException(UNSUPPORTED)
    }

    fun storageError(error: IOException): Exception =
        if (error.message.orEmpty().let { "ENOSPC" in it || "No space" in it }) AttachmentException("Not enough free storage to attach this file. Free up some space and try again.")
        else error

    fun sample(file: File): Pair<ByteArray, Boolean> = file.inputStream().use { input ->
        val bytes = input.readNBytesCompat(TEXT_SAMPLE_BYTES)
        bytes to (file.length() <= bytes.size)
    }

    const val UNSUPPORTED = "Choose a PDF, Word (.docx), PowerPoint (.pptx), OpenDocument or EPUB document, a text or code file, or a photo (JPG, PNG, WebP, HEIC, GIF)."
    private val heifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif")
}

internal fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(minOf(limit, 64 * 1024).coerceAtLeast(1))
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        out.write(buffer, 0, count)
        remaining -= count
    }
    return out.toByteArray()
}
