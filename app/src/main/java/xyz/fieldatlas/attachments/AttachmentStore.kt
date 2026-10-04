package xyz.fieldatlas.attachments

import java.io.File
import java.io.InputStream
import java.util.UUID

/** Only owns direct children of the dedicated attachment directory. Never owns provider originals. */
class AttachmentStore(private val root: File) {
    /** Streams the file to disk (never whole into memory), then checks what it really is. */
    fun stage(name: String, input: InputStream, checkCancelled: () -> Unit = {}): AttachmentInput {
        check(root.mkdirs() || root.isDirectory)
        val id = UUID.randomUUID().toString()
        val file = File(root, id)
        try {
            file.outputStream().buffered().use { output -> AttachmentPolicy.copyBounded(input, output, checkCancelled) }
            checkCancelled()
            val (sample, complete) = AttachmentPolicy.sample(file)
            val kind = AttachmentPolicy.kind(name, sample, complete)
            checkCancelled()
            return AttachmentInput(id, attachmentDisplayName(name), file, kind)
        } catch (error: java.io.IOException) {
            file.delete(); throw AttachmentPolicy.storageError(error)
        } catch (error: Throwable) { file.delete(); throw error }
    }
    fun remove(file: File) {
        if (file.canonicalFile.parentFile == root.canonicalFile && file.isFile) file.delete()
    }
    /** Deletes staged files except [keep] (attachments restored after the app was closed). */
    fun clearAbandoned(keep: Set<String> = emptySet()) { root.listFiles()?.filter { it.name !in keep }?.forEach(::remove) }
    fun file(id: String): File? = File(root, id).takeIf { it.isFile && it.canonicalFile.parentFile == root.canonicalFile }
}
