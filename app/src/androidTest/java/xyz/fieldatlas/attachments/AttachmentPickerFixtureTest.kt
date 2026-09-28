package xyz.fieldatlas.attachments

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/** Opt-in synthetic files for exercising the real release's system pickers. No personal files. */
class AttachmentPickerFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rows = listOf(
        Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "fieldatlas-offline-test.txt", "text/plain"),
        Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "fieldatlas-offline-test.pdf", "application/pdf"),
        Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "fieldatlas-offline-test.png", "image/png"),
    )
    @Test fun prepare() {
        if (InstrumentationRegistry.getArguments().getString("pickerFixtures") != "prepare") return
        cleanupOwnedRows()
        val lines = listOf("SYNTHETIC TEST - not real venues", "Green Lantern: fictional Berlin restaurant.", "Fully vegan menu. Price: 18 euros.", "Blue Market: fictional vegetarian restaurant.", "Vegan options have not been confirmed.")
        rows.forEach { (collection, name, mime) ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (mime == "image/png") "Pictures/FieldAtlas-test/" else "Download/FieldAtlas-test/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(context.contentResolver.insert(collection, values))
            try {
                context.contentResolver.openOutputStream(uri)!!.use { output ->
                    when (mime) {
                        "text/plain" -> output.write(lines.joinToString("\n").toByteArray())
                        "image/png" -> {
                            val bitmap = Bitmap.createBitmap(1400, 600, Bitmap.Config.ARGB_8888)
                            try {
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                lines.forEachIndexed { index, line -> canvas.drawText(line, 40f, 80f + index * 95f, Paint().apply { color = Color.BLACK; textSize = 42f; isAntiAlias = true }) }
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            } finally { bitmap.recycle() }
                        }
                        else -> {
                            val pdf = PdfDocument()
                            try {
                                val page = pdf.startPage(PdfDocument.PageInfo.Builder(700, 600, 1).create())
                                lines.forEachIndexed { index, line -> page.canvas.drawText(line, 20f, 50f + index * 60f, Paint().apply { textSize = 22f; isAntiAlias = true }) }
                                pdf.finishPage(page); pdf.writeTo(output)
                            } finally { pdf.close() }
                        }
                    }
                }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Throwable) { context.contentResolver.delete(uri, null, null); throw error }
        }
    }
    @Test fun cleanup() {
        if (InstrumentationRegistry.getArguments().getString("pickerFixtures") == "cleanup") cleanupOwnedRows()
    }
    private fun cleanupOwnedRows() {
        rows.forEach { (collection, name, mime) ->
            val path = if (mime == "image/png") "Pictures/FieldAtlas-test/" else "Download/FieldAtlas-test/"
            context.contentResolver.delete(collection, "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?", arrayOf(name, path, context.packageName))
        }
    }
}
