package xyz.fieldatlas.attachments

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AttachmentNameTest {
    @Test fun longStagedNameKeepsBeginningAndExtension() {
        val root = Files.createTempDirectory("attachment-name").toFile()
        try {
            val name = "Trip-notes-" + "日".repeat(180) + "-Berlin-2026.txt"
            val input = AttachmentStore(root).stage(name, "Notes".byteInputStream())
            assertTrue(input.displayName.startsWith("Trip-notes-"))
            assertTrue(input.displayName.endsWith("-Berlin-2026.txt"))
            assertTrue(input.displayName.contains("…"))
            assertTrue(input.displayName.length <= 160)
        } finally { root.deleteRecursively() }
    }
}
