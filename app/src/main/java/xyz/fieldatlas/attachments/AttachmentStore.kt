package xyz.fieldatlas.attachments

import java.io.File
import java.io.InputStream
import java.util.UUID

/** Only owns direct children of the dedicated attachment directory. Never owns provider originals. */
class AttachmentStore(private val root: File) {
    fun stage(name: String, input: InputStream, checkCancelled: () -> Unit = {}): AttachmentInput {
        val bytes = AttachmentPolicy.readBounded(input, checkCancelled)
        val kind = AttachmentPolicy.kind(name, bytes)
        checkCancelled()
        check(root.mkdirs() || root.isDirectory)
        val id = UUID.randomUUID().toString()
        val file = File(root, id)
        try {
            file.writeBytes(bytes)
            checkCancelled()
            return AttachmentInput(id, attachmentDisplayName(name), file, kind)
        } catch (error: Throwable) { file.delete(); throw error }
    }
    fun remove(file: File) {
        if (file.canonicalFile.parentFile == root.canonicalFile && file.isFile) file.delete()
    }
    fun clearAbandoned() { root.listFiles()?.forEach(::remove) }
}
