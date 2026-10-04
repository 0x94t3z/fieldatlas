package xyz.fieldatlas.attachments

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAttachmentReader(private val context: Context) : AttachmentReader {
    private val mutex = Mutex()
    override suspend fun read(input: AttachmentInput): ExtractedAttachment = mutex.withLock {
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (input.localFile.length() > AttachmentPolicy.MAX_BYTES) throw AttachmentException("This file is too large. Choose a file smaller than ${AttachmentPolicy.MAX_BYTES / (1024 * 1024)} MB.")
            try { extract(input) } catch (_: OutOfMemoryError) {
                throw AttachmentException("This file is too large for this phone's memory. Try a smaller file or a section of it.")
            }
        }
    }

    private suspend fun extract(input: AttachmentInput): ExtractedAttachment {
        var usedOcr = false
        var coverageNote: String? = null
        var truncated = false
        val pages = when (input.kind) {
            AttachmentKind.TEXT -> {
                val decoded = readText(input.localFile, input.displayName)
                truncated = decoded.truncated
                listOf(AttachmentPage(1, decoded.text))
            }
            AttachmentKind.DOCUMENT -> {
                val (kept, cut) = AttachmentPages.cap(DocumentTextReader.read(input.localFile, input.displayName))
                truncated = cut
                kept
            }
            AttachmentKind.IMAGE -> ImageTextReader(context).use { ocr ->
                usedOcr = true
                listOf(AttachmentPage(1, ocr.read(input.localFile)))
            }
            AttachmentKind.PDF -> {
                if (!pdfBoxReady) { PDFBoxResourceLoader.init(context.applicationContext); pdfBoxReady = true }
                ImageTextReader(context).use { ocr ->
                    val pdf = PdfTextReader(ocr, File(context.cacheDir, "pdf-scratch").apply { mkdirs() })
                    pdf.read(input.localFile).also {
                        usedOcr = pdf.usedOcr
                        coverageNote = pdf.coverageNote
                        truncated = pdf.truncated
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (pages.all { it.text.isBlank() }) throw AttachmentException(
            if (input.kind == AttachmentKind.IMAGE || input.kind == AttachmentKind.PDF) "No readable text was found. Try a clearer image of printed English text."
            else "No readable text was found in this file.",
        )
        val note = listOfNotNull(coverageNote, AttachmentPages.truncationNote(truncated, input.kind, coverageNote)).joinToString(" ").ifBlank { null }
        return ExtractedAttachment(input.id, input.displayName, pages.filter { it.text.isNotBlank() }, usedOcr, note, input.kind, truncated)
    }

    /** Text files are read from their first bytes only, so a 50 MB log cannot fill memory. */
    private fun readText(file: File, name: String): DecodedText {
        val bytes = file.inputStream().use { it.readNBytesCompat(AttachmentPolicy.MAX_CHARACTERS * 4) }
        val decoded = TextAttachmentDecoder.decodeText(bytes, inputTruncated = file.length() > bytes.size,
            allowLegacy = TextFileTypes.supports(name))
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension in setOf("html", "htm", "xhtml")) {
            decoded.copy(text = MarkupText.html(decoded.text).ifBlank { decoded.text })
        } else decoded
    }

    private companion object {
        @Volatile var pdfBoxReady = false
    }
}

/** Keeps pages until the character limit; the page that crosses it is cut at a line or word. */
object AttachmentPages {
    fun cap(pages: List<AttachmentPage>, limit: Int = AttachmentPolicy.MAX_CHARACTERS): Pair<List<AttachmentPage>, Boolean> {
        val kept = mutableListOf<AttachmentPage>()
        var used = 0
        for (page in pages) {
            if (used + page.text.length <= limit) { kept += page; used += page.text.length; continue }
            val room = limit - used
            if (room > 500) kept += page.copy(text = TextAttachmentDecoder.truncate(page.text, room))
            return kept to true
        }
        return kept to false
    }

    /** A sentence for a file that was cut short, unless the PDF note already covers it. */
    fun truncationNote(truncated: Boolean, kind: AttachmentKind, existing: String?): String? {
        if (!truncated || existing?.contains("were read") == true) return null
        if (kind == AttachmentKind.PDF) return "This file is long; only its first part was read."
        return "This file is long; only its first ${String.format(Locale.ENGLISH, "%,d", AttachmentPolicy.MAX_CHARACTERS)} characters were read."
    }
}
