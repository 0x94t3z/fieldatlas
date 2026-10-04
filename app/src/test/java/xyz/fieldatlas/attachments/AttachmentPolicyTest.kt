package xyz.fieldatlas.attachments

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class AttachmentPolicyTest {
    @Test fun codeAndConfigurationFilesUseStrictTextValidation() {
        for (name in listOf("main.kt", "script.py", "app.tsx", "config.yaml", "Dockerfile", "notes.txt", "diagram.svg")) {
            assertEquals(AttachmentKind.TEXT, AttachmentPolicy.kind(name, "val greeting = \"hello\"".toByteArray()))
            assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind(name, byteArrayOf(0, 1, 2)) }
        }
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("program.exe", "MZ\u0090\u0000\u0003".toByteArray(Charsets.ISO_8859_1)) }
    }
    @Test fun streamedLimitDoesNotTrustProviderSize() {
        val limit = AttachmentPolicy.MAX_BYTES
        assertEquals(limit.toLong(), AttachmentPolicy.copyBounded(zeros(limit.toLong()), NullOutput))
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.copyBounded(zeros(limit + 1L), NullOutput) }
    }
    @Test fun maximumThreeSelectedFiles() {
        AttachmentPolicy.checkCount(2)
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.checkCount(3) }
    }
    @Test fun unicodeAndBomTextSurvive() {
        assertEquals("Berlin — 日本", TextAttachmentDecoder.decode("Berlin — 日本".toByteArray()))
        assertEquals("Hello", TextAttachmentDecoder.decode(byteArrayOf(-1, -2) + "Hello".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Hello", TextAttachmentDecoder.decode(byteArrayOf(-2, -1) + "Hello".toByteArray(Charsets.UTF_16BE)))
        assertEquals("Hello", TextAttachmentDecoder.decode(byteArrayOf(-17, -69, -65) + "Hello".toByteArray()))
    }
    @Test fun utf16WithoutByteOrderMarkIsDetected() {
        assertEquals("Field notes, day 2", TextAttachmentDecoder.decode("Field notes, day 2".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Field notes, day 2", TextAttachmentDecoder.decode("Field notes, day 2".toByteArray(Charsets.UTF_16BE)))
    }
    @Test fun windowsTextFallsBackToWindows1252OnlyForTextExtensions() {
        val bytes = "Café menu: crème brûlée €4".toByteArray(charset("windows-1252"))
        val decoded = TextAttachmentDecoder.decodeText(bytes)
        assertEquals("Café menu: crème brûlée €4", decoded.text)
        assertEquals("windows-1252", decoded.charset)
        assertEquals(AttachmentKind.TEXT, AttachmentPolicy.kind("menu.txt", bytes))
        // An unknown extension must be clean UTF-8/UTF-16, so arbitrary bytes are not "text".
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("menu.dat", bytes) }
        assertEquals(AttachmentKind.TEXT, AttachmentPolicy.kind("README", "Plain notes".toByteArray()))
    }
    @Test fun rejectsBinaryMalformedAndEmptyText() {
        for (bytes in listOf(byteArrayOf(0, 1, 2), byteArrayOf(), "   \n".toByteArray())) {
            assertThrows(AttachmentException::class.java) { TextAttachmentDecoder.decode(bytes) }
        }
    }
    @Test fun longTextIsReadUpToTheLimitAndMarkedTruncated() {
        val text = ("A line of field notes. ".repeat(40) + "\n").repeat(500)
        val decoded = TextAttachmentDecoder.decodeText(text.toByteArray())
        assertTrue(decoded.truncated)
        assertTrue(decoded.text.length <= AttachmentPolicy.MAX_CHARACTERS)
        assertTrue(decoded.text.length > AttachmentPolicy.MAX_CHARACTERS * 9 / 10)
        assertTrue("cut at a line end", decoded.text.endsWith("notes. "))
    }
    @Test fun aSampleCutInsideACharacterIsStillText() {
        val bytes = "日本の歴史".toByteArray()
        val sample = bytes.copyOf(bytes.size - 1)
        assertEquals(AttachmentKind.TEXT, AttachmentPolicy.kind("history.txt", sample, complete = false))
        assertTrue(TextAttachmentDecoder.decodeText(sample, inputTruncated = true).truncated)
    }
    @Test fun validatesSignaturesNotJustExtensions() {
        assertEquals(AttachmentKind.PDF, AttachmentPolicy.kind("notes.pdf", "%PDF-1.7".toByteArray()))
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("notes.pdf", byteArrayOf(0, 1, 2, 3)) }
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("notes.txt", byteArrayOf(0, 0, 2)) }
    }
    @Test fun recognisesDocumentAndPhotoFormatsAndExplainsUnsupportedOnes() {
        val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0, 0)
        assertEquals(AttachmentKind.DOCUMENT, AttachmentPolicy.kind("report.docx", zip))
        assertEquals(AttachmentKind.DOCUMENT, AttachmentPolicy.kind("deck.pptx", zip))
        assertEquals(AttachmentKind.DOCUMENT, AttachmentPolicy.kind("book.epub", zip))
        assertTrue(failure("budget.xlsx", zip).contains("CSV"))
        assertTrue(failure("photos.zip", zip).contains("archives"))
        assertTrue(failure("old.doc", byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0)).contains(".docx"))
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray() + ByteArray(4)
        assertEquals(AttachmentKind.IMAGE, AttachmentPolicy.kind("IMG_0001.HEIC", heic))
        assertEquals(AttachmentKind.IMAGE, AttachmentPolicy.kind("map.gif", "GIF89a....".toByteArray()))
        assertEquals(AttachmentKind.IMAGE, AttachmentPolicy.kind("scan.webp", "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
    }
    @Test fun ownedFilesUseGeneratedNamesAndFailedCopiesLeaveNoFiles() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = AttachmentStore(root)
            val input = store.stage("../../notes.txt", ByteArrayInputStream("safe".toByteArray()))
            assertEquals(root.canonicalFile, input.localFile.parentFile.canonicalFile)
            assertEquals("notes.txt", input.displayName)
            assertEquals("safe", input.localFile.readText())
            assertThrows(AttachmentException::class.java) { store.stage("bad.pdf", ByteArrayInputStream(byteArrayOf(0, 1, 2))) }
            assertEquals(1, root.listFiles()!!.size)
            val outside = File(root.parentFile, "outside-${System.nanoTime()}")
            outside.writeText("original")
            try { store.remove(outside); assertTrue(outside.exists()) } finally { outside.delete() }
            assertEquals(input.localFile.canonicalFile, store.file(input.id)?.canonicalFile)
            assertNull(store.file("../outside"))
            store.remove(input.localFile)
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun abandonedFilesAreClearedExceptRestoredOnes() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = AttachmentStore(root)
            val keep = store.stage("keep.txt", ByteArrayInputStream("keep".toByteArray()))
            val drop = store.stage("drop.txt", ByteArrayInputStream("drop".toByteArray()))
            store.clearAbandoned(setOf(keep.id))
            assertTrue(keep.localFile.exists())
            assertFalse(drop.localFile.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun storageErrorsAreExplained() {
        assertTrue(attachmentError(java.io.IOException("write failed: ENOSPC (No space left on device)")).contains("storage"))
        assertTrue(attachmentError(OutOfMemoryError()).contains("memory"))
    }

    private fun failure(name: String, bytes: ByteArray) =
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind(name, bytes) }.message.orEmpty()
    private fun zeros(count: Long): InputStream = object : InputStream() {
        var left = count
        override fun read(): Int = if (left-- > 0) 0 else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = minOf(len.toLong(), left).toInt(); left -= n; java.util.Arrays.fill(b, off, off + n, 0); return n
        }
    }
    private object NullOutput : java.io.OutputStream() { override fun write(b: Int) {}; override fun write(b: ByteArray, off: Int, len: Int) {} }
}
