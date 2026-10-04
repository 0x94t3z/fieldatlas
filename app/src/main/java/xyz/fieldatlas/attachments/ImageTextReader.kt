package xyz.fieldatlas.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

/** One instance per serialized extraction, never shared across worker threads. */
class ImageTextReader(private val context: Context) : Closeable {
    private var engine: TessBaseAPI? = null
    fun read(file: File): String {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val (w, h) = dimensions(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try { return read(bitmap) } finally { bitmap.recycle() }
    }
    fun read(bitmap: Bitmap): String {
        val tess = engine ?: createEngine().also { engine = it }
        try {
            tess.setImage(bitmap)
            return tess.utF8Text.orEmpty()
        } finally { tess.clear() }
    }
    private fun createEngine(): TessBaseAPI {
        val root = File(context.noBackupFilesDir, "ocr")
        val directory = File(root, "tessdata")
        check(directory.mkdirs() || directory.isDirectory)
        val data = File(directory, "eng.traineddata")
        // Hashing the 4 MB model costs time on every file; verify it once per process.
        if (!verified || !data.isFile) synchronized(Companion) {
            if (!data.isFile || sha256(data.readBytes()) != MODEL_SHA256) {
                val bytes = context.assets.open("ocr/eng.traineddata").use { it.readBytes() }
                check(sha256(bytes) == MODEL_SHA256)
                data.writeBytes(bytes)
            }
            verified = true
        }
        val tess = TessBaseAPI()
        try {
            if (!tess.init(root.absolutePath, "eng")) throw AttachmentException("Text recognition could not start. Please try again.")
            return tess
        } catch (error: Throwable) { tess.recycle(); throw error }
    }
    override fun close() { engine?.recycle(); engine = null }
    companion object {
        @Volatile private var verified = false
        const val MODEL_SHA256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
        fun dimensions(width: Int, height: Int): Pair<Int, Int> {
            require(width > 0 && height > 0)
            val scale = minOf(1.0, sqrt(4_000_000.0 / (width.toDouble() * height)), 4096.0 / maxOf(width, height))
            return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
        }
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
