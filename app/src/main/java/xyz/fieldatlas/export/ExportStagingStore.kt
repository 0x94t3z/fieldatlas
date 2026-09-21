package xyz.fieldatlas.export

import java.io.File
import java.io.OutputStream

class ExportStagingStore(private val directory: File) {
    fun stage(name: String, payload: ByteArray) {
        val destination = pendingFile(name)
        directory.mkdirs()
        val temporary = File(directory, "${destination.name}.tmp")
        temporary.outputStream().use { output ->
            output.write(payload)
            output.fd.sync()
        }
        if (destination.exists()) check(destination.delete()) { "Could not replace staged export" }
        check(temporary.renameTo(destination)) { "Could not stage export" }
    }

    fun transfer(name: String, destination: OutputStream): Boolean {
        val source = pendingFile(name)
        if (!source.isFile) return false
        source.inputStream().use { it.copyTo(destination) }
        destination.flush()
        check(source.delete()) { "Could not clear staged export" }
        return true
    }

    private fun pendingFile(name: String): File {
        require(name.matches(Regex("[a-z0-9-]+"))) { "Invalid export name" }
        return File(directory, "$name.pending")
    }
}
