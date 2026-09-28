package xyz.fieldatlas.attachments

import android.content.Context
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
            if (input.localFile.length() > AttachmentPolicy.MAX_BYTES) throw AttachmentException("This file is too large. Choose a file smaller than 20 MB.")
            var usedOcr = false
            var coverageNote: String? = null
            val pages = ImageTextReader(context).use { ocr ->
                when (input.kind) {
                    AttachmentKind.TEXT -> listOf(AttachmentPage(1, TextAttachmentDecoder.decode(input.localFile.readBytes())))
                    AttachmentKind.IMAGE -> { usedOcr = true; listOf(AttachmentPage(1, ocr.read(input.localFile))) }
                    AttachmentKind.PDF -> PdfTextReader(ocr).let { pdf -> pdf.read(input.localFile).also {
                        usedOcr = pdf.usedOcr
                        coverageNote = PdfTextPolicy.coverageNote(pdf.uncertainPages)
                    } }
                }
            }
            currentCoroutineContext().ensureActive()
            if (pages.sumOf { it.text.length } > AttachmentPolicy.MAX_CHARACTERS) throw AttachmentException("This document is too long. Choose a shorter section (up to 100,000 characters).")
            if (pages.all { it.text.isBlank() }) throw AttachmentException("No readable text was found. Try a clearer image of printed English text.")
            ExtractedAttachment(input.id, input.displayName, pages.filter { it.text.isNotBlank() }, usedOcr, coverageNote, input.kind)
        }
    }
}
