package xyz.fieldatlas.attachments

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reads a PDF from its embedded text layer first (fast, exact) and runs text recognition only
 * on pages whose layer is empty or nearly so: scans, photos of pages, or a header over an image.
 * Recognition is slow on a phone, so at most [AttachmentPolicy.MAX_OCR_PAGES] pages get it;
 * anything not read is listed in [coverageNote] rather than silently dropped.
 */
class PdfTextReader(private val ocr: ImageTextReader, private val tempDir: File) {
    var usedOcr = false
        private set
    var coverageNote: String? = null
        private set
    var truncated = false
        private set

    suspend fun read(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<AttachmentPage> {
        val layer = embeddedText(file)
        val pageCount = layer?.size ?: pageCountFromRenderer(file)
        if (pageCount == 0) throw AttachmentException("This PDF has no pages.")
        val readable = minOf(pageCount, AttachmentPolicy.MAX_PAGES)
        val texts = MutableList(readable) { index -> layer?.getOrNull(index).orEmpty() }
        val needOcr = (0 until readable).filter { PdfTextPolicy.needsOcr(texts[it]) }
        val ocrPages = needOcr.take(AttachmentPolicy.MAX_OCR_PAGES)
        val unreadable = mutableListOf<Int>()
        if (ocrPages.isNotEmpty()) {
            usedOcr = true
            withRenderer(file) { renderer ->
                ocrPages.forEachIndexed { done, index ->
                    currentCoroutineContext().ensureActive()
                    onProgress(done, ocrPages.size)
                    val recognized = renderer.openPage(index).use { page ->
                        val (w, h) = ImageTextReader.dimensions(
                            (page.width.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            (page.height.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        )
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            ocr.read(bitmap)
                        } finally { bitmap.recycle() }
                    }
                    texts[index] = PdfTextPolicy.merge(texts[index], recognized)
                    if (texts[index].isBlank()) unreadable += index + 1
                }
            }
        }
        val pages = mutableListOf<AttachmentPage>()
        var characters = 0
        var lastRead = 0
        for (index in 0 until readable) {
            val text = texts[index]
            if (characters + text.length > AttachmentPolicy.MAX_CHARACTERS) {
                val room = AttachmentPolicy.MAX_CHARACTERS - characters
                if (room > 500) pages += AttachmentPage(index + 1, TextAttachmentDecoder.truncate(text, room))
                truncated = true
                lastRead = index + 1
                break
            }
            characters += text.length
            if (text.isNotBlank()) pages += AttachmentPage(index + 1, text)
            lastRead = index + 1
        }
        if (pageCount > lastRead) truncated = true
        coverageNote = PdfTextPolicy.coverageNote(
            unreadable = unreadable,
            skippedOcr = needOcr.drop(AttachmentPolicy.MAX_OCR_PAGES).map { it + 1 }.filter { it <= lastRead },
            lastRead = lastRead,
            pageCount = pageCount,
        )
        return pages
    }

    /** Page texts from the embedded layer, or null when the layer cannot be read at all. */
    private suspend fun embeddedText(file: File): List<String>? {
        val document = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir))
        } catch (_: InvalidPasswordException) {
            throw AttachmentException("This PDF is password-protected. Choose an unlocked copy.")
        } catch (_: IOException) {
            return null // Damaged structure; the platform renderer may still draw the pages.
        }
        return document.use { pdf ->
            if (!pdf.currentAccessPermission.canExtractContent()) return null
            val stripper = PDFTextStripper().apply { sortByPosition = true; lineSeparator = "\n" }
            val count = minOf(pdf.numberOfPages, AttachmentPolicy.MAX_PAGES)
            val texts = ArrayList<String>(pdf.numberOfPages)
            var total = 0
            for (page in 1..count) {
                currentCoroutineContext().ensureActive()
                // Past the character limit the rest is never used; stop extracting early.
                if (total > AttachmentPolicy.MAX_CHARACTERS) { texts += ""; continue }
                stripper.startPage = page
                stripper.endPage = page
                val text = try { MarkupText.tidy(stripper.getText(pdf)) } catch (_: IOException) { "" }
                total += text.length
                texts += text
            }
            repeat(pdf.numberOfPages - count) { texts += "" }
            texts
        }
    }

    /** Opens the platform renderer, closing it and its file descriptor afterwards. */
    private inline fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = try { PdfRenderer(descriptor) } catch (error: Throwable) { throw mapRendererError(error) }
            return renderer.use(block)
        } finally { descriptor.close() }
    }

    private fun pageCountFromRenderer(file: File): Int = withRenderer(file) { it.pageCount }

    private fun mapRendererError(error: Throwable): Throwable = when (error) {
        is SecurityException -> AttachmentException("This PDF is password-protected. Choose an unlocked copy.")
        is IOException -> AttachmentException("This PDF is damaged and could not be opened.")
        else -> error
    }
}
