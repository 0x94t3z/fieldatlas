package xyz.fieldatlas.attachments

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PdfTextReader(private val ocr: ImageTextReader) {
    var usedOcr = false
        private set
    val uncertainPages = mutableListOf<Int>()
    suspend fun read(file: File): List<AttachmentPage> {
        val pages = mutableListOf<AttachmentPage>()
        var characters = 0
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = try { PdfRenderer(descriptor) } catch (_: SecurityException) {
                throw AttachmentException("This PDF is password-protected. Choose an unlocked copy.")
            }
            try {
                if (renderer.pageCount > AttachmentPolicy.MAX_PAGES) throw AttachmentException("Choose a PDF with 30 pages or fewer.")
                for (index in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    val page = renderer.openPage(index)
                    try {
                        var text = if (Build.VERSION.SDK_INT >= 35) page.textContents.joinToString("\n") { it.text } else ""
                        // imageContents may be empty for visible untagged raster images.
                        // Check every rendered page, even when it has an embedded header.
                        run {
                            usedOcr = true
                            val (w, h) = ImageTextReader.dimensions((page.width.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), (page.height.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val recognized = ocr.read(bitmap)
                                if (recognized.isBlank()) uncertainPages += index + 1
                                text = PdfTextPolicy.merge(text, recognized)
                            } finally { bitmap.recycle() }
                        }
                        characters += text.length
                        if (characters > AttachmentPolicy.MAX_CHARACTERS) throw AttachmentException("This document is too long. Choose a shorter section (up to 100,000 characters).")
                        if (text.isNotBlank()) pages += AttachmentPage(index + 1, text)
                    } finally { page.close() }
                }
            } finally { renderer.close() }
        } finally { descriptor.close() }
        return pages
    }
}
