package xyz.fieldatlas.assets

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Optional, checksum-verified-at-import offline notices. Never follow external paths. */
object PackAttribution {
    private const val MAX_BYTES = 262_144

    fun read(root: File): String? {
        val file = File(root, "ATTRIBUTION.txt")
        if (!file.exists()) return null
        require(file.canonicalFile.parentFile == root.canonicalFile && file.isFile) {
            "Invalid collection attribution path"
        }
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(MAX_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size <= MAX_BYTES) { "Collection attribution is too large" }
            buffer.copyOf(size)
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            .toString().trim().takeIf(String::isNotEmpty)
    }
}
