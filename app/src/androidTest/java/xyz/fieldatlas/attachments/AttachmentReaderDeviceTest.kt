package xyz.fieldatlas.attachments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AttachmentReaderDeviceTest {
    @Test fun readsExifRotatedPhoto() = runBlocking {
        val file = File.createTempFile("rotated", ".jpg", context.cacheDir)
        val original = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        Canvas(original).apply {
            drawColor(Color.WHITE)
            drawText("Berlin vegan menu", 60f, 150f, Paint().apply { color = Color.BLACK; textSize = 60f; isAntiAlias = true })
        }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, android.graphics.Matrix().apply { postRotate(180f) }, true)
        try {
            file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            android.media.ExifInterface(file).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_180.toString())
                saveAttributes()
            }
            val result = AndroidAttachmentReader(context).read(AttachmentInput("rotated", "photo.jpg", file, AttachmentKind.IMAGE))
            assertTrue(result.pages.single().text.contains("Berlin", ignoreCase = true))
        } finally { original.recycle(); rotated.recycle(); file.delete() }
    }
    @Test fun mixedPdfReadsImageBodyAndReportsUnreadablePage() = runBlocking {
        val file = File.createTempFile("mixed", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        val body = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        Canvas(body).apply {
            drawColor(Color.WHITE)
            drawText("Vegan menu in Berlin", 40f, 140f, Paint().apply { textSize = 60f; isAntiAlias = true; color = Color.BLACK })
        }
        try {
            val first = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            first.canvas.drawText("Header", 40f, 60f, Paint().apply { textSize = 30f })
            first.canvas.drawBitmap(body, null, android.graphics.Rect(20, 100, 580, 324), null)
            pdf.finishPage(first)
            pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 2).create()))
            file.outputStream().use { pdf.writeTo(it) }
            val detectedImages = if (android.os.Build.VERSION.SDK_INT >= 35) {
                android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    renderer.openPage(0).use { it.imageContents.size }
                }
            } else -1
            val result = AndroidAttachmentReader(context).read(AttachmentInput("mixed", "mixed.pdf", file, AttachmentKind.PDF))
            assertTrue("Synthetic hybrid extraction (platform image count=$detectedImages): $result", result.pages.first().text.contains("Berlin", ignoreCase = true))
            assertTrue(result.coverageNote!!.contains("2"))
        } finally { pdf.close(); body.recycle(); file.delete() }
    }
    @Test fun rejectsMalformedPdf() = runBlocking {
        val file = File.createTempFile("broken", ".pdf", context.cacheDir)
        try {
            file.writeText("%PDF-1.7\nbroken")
            assertTrue(runCatching { AndroidAttachmentReader(context).read(AttachmentInput("broken", "broken.pdf", file, AttachmentKind.PDF)) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun rejectsTooManyPagesAndBlankImages() = runBlocking {
        val pdfFile = File.createTempFile("limit", ".pdf", context.cacheDir)
        val imageFile = File.createTempFile("blank", ".png", context.cacheDir)
        val pdf = PdfDocument()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            repeat(31) { pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(100, 100, it + 1).create())) }
            pdfFile.outputStream().use { pdf.writeTo(it) }
            bitmap.eraseColor(Color.WHITE)
            imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val reader = AndroidAttachmentReader(context)
            for ((file, kind) in listOf(pdfFile to AttachmentKind.PDF, imageFile to AttachmentKind.IMAGE)) {
                val failure = runCatching { reader.read(AttachmentInput("invalid", file.name, file, kind)) }.exceptionOrNull()
                assertTrue(failure is AttachmentException)
            }
        } finally { pdf.close(); bitmap.recycle(); pdfFile.delete(); imageFile.delete() }
    }
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun readsPrintedImageEntirelyLocally() = runBlocking {
        val file = File.createTempFile("ocr-test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("Berlin has vegan cafes", 60f, 150f, Paint().apply { color = Color.BLACK; textSize = 60f; isAntiAlias = true })
        }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = AndroidAttachmentReader(context).read(AttachmentInput("test", "page.png", file, AttachmentKind.IMAGE))
            assertTrue(result.pages.single().text.contains("Berlin", ignoreCase = true))
        } finally { bitmap.recycle(); file.delete() }
    }
    @Test fun readsPdfAndKeepsPageNumbers() = runBlocking {
        val file = File.createTempFile("pdf-test", ".pdf", context.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                for (n in 1..2) {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, n).create())
                    page.canvas.drawText("Berlin document page $n", 40f, 80f, Paint().apply { textSize = 30f })
                    pdf.finishPage(page)
                }
                file.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            val result = AndroidAttachmentReader(context).read(AttachmentInput("pdf", "notes.pdf", file, AttachmentKind.PDF))
            assertEquals(listOf(1, 2), result.pages.map { it.number })
            assertTrue(result.pages.all { it.text.contains("Berlin", ignoreCase = true) })
        } finally { file.delete() }
    }
}
