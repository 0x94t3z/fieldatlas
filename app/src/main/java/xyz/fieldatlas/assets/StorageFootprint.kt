package xyz.fieldatlas.assets

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes

/** Logical file bytes, including unregistered downloads and extraction staging.
 * No symlink traversal; unreadable paths fail closed rather than reporting zero.
 * This is a provisioning boundary, not an OS quota on future history/cache growth.
 */
class StorageFootprint(private val roots: () -> List<File>) {
    fun bytes(): Long {
        val seen = HashSet<java.nio.file.Path>()
        var total = 0L
        fun visit(file: File) {
            val path = file.toPath().toAbsolutePath().normalize()
            if (!seen.add(path)) return
            val attributes = try {
                Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: java.nio.file.NoSuchFileException) { return }
            if (attributes.isSymbolicLink) return
            if (attributes.isDirectory) {
                Files.newDirectoryStream(path).use { entries -> entries.forEach { visit(it.toFile()) } }
            } else if (attributes.isRegularFile) total = Math.addExact(total, attributes.size())
        }
        roots().forEach(::visit)
        return total
    }

    companion object {
        fun forApp(context: Context): StorageFootprint = StorageFootprint {
            val info = context.applicationInfo
            buildList {
                add(context.dataDir.canonicalFile)
                add(context.createDeviceProtectedStorageContext().dataDir.canonicalFile)
                add(File(info.sourceDir).canonicalFile)
                info.splitSourceDirs?.forEach { add(File(it).canonicalFile) }
                info.nativeLibraryDir?.let { add(File(it).canonicalFile) }
                context.getExternalFilesDirs(null).filterNotNull().forEach { add(it.canonicalFile) }
                context.externalCacheDirs.filterNotNull().forEach { add(it.canonicalFile) }
            }
        }
    }
}
