package xyz.fieldatlas.attachments

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AttachmentPolicyTest {
    @Test fun codeAndConfigurationFilesUseStrictTextValidation() {
        for (name in listOf("main.kt", "script.py", "app.tsx", "config.yaml", "Dockerfile", "notes.txt", "diagram.svg")) {
            assertEquals(AttachmentKind.TEXT, AttachmentPolicy.kind(name, "val greeting = \"hello\"".toByteArray()))
            assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind(name, byteArrayOf(0, 1, 2)) }
        }
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("program.exe", "MZ".toByteArray()) }
    }
    @Test fun streamedLimitDoesNotTrustProviderSize() {
        assertEquals(20 * 1024 * 1024, AttachmentPolicy.readBounded(ByteArrayInputStream(ByteArray(20 * 1024 * 1024))).size)
        assertThrows(AttachmentException::class.java) {
            AttachmentPolicy.readBounded(ByteArrayInputStream(ByteArray(20 * 1024 * 1024 + 1)))
        }
    }
    @Test fun maximumThreeSelectedFiles() {
        AttachmentPolicy.checkCount(2)
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.checkCount(3) }
    }
    @Test fun unicodeAndBomTextSurvive() {
        assertEquals("Berlin — 日本", TextAttachmentDecoder.decode("Berlin — 日本".toByteArray()))
        assertEquals("Hello", TextAttachmentDecoder.decode(byteArrayOf(-1, -2) + "Hello".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Hello", TextAttachmentDecoder.decode(byteArrayOf(-2, -1) + "Hello".toByteArray(Charsets.UTF_16BE)))
    }
    @Test fun rejectsBinaryMalformedEmptyAndTooLongText() {
        for (bytes in listOf(byteArrayOf(0, 1, 2), byteArrayOf(-61, 40), byteArrayOf(), "a".repeat(100_001).toByteArray())) {
            assertThrows(AttachmentException::class.java) { TextAttachmentDecoder.decode(bytes) }
        }
    }
    @Test fun validatesSignaturesNotJustExtensions() {
        assertEquals(AttachmentKind.PDF, AttachmentPolicy.kind("notes.pdf", "%PDF-1.7".toByteArray()))
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("notes.pdf", "not a pdf".toByteArray()) }
        assertThrows(AttachmentException::class.java) { AttachmentPolicy.kind("notes.txt", byteArrayOf(0, 0, 2)) }
    }
    @Test fun ownedFilesUseGeneratedNamesAndFailedCopiesLeaveNoFiles() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = AttachmentStore(root)
            val input = store.stage("../../notes.txt", ByteArrayInputStream("safe".toByteArray()))
            assertEquals(root.canonicalFile, input.localFile.parentFile.canonicalFile)
            assertEquals("notes.txt", input.displayName)
            assertThrows(AttachmentException::class.java) { store.stage("bad.pdf", ByteArrayInputStream("bad".toByteArray())) }
            assertEquals(1, root.listFiles()!!.size)
            val outside = File(root.parentFile, "outside-${System.nanoTime()}")
            outside.writeText("original")
            try { store.remove(outside); assertTrue(outside.exists()) } finally { outside.delete() }
            store.remove(input.localFile)
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
