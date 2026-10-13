package xyz.fieldatlas.assets

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackAttributionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun oldPacksWithoutNoticesRemainSupported() {
        assertNull(PackAttribution.read(temporary.root))
    }

    @Test fun offlineNoticeRetainsAttributionAndNewlines() {
        File(temporary.root, "ATTRIBUTION.txt").writeText("Creator: João\nLicence: CC BY 4.0\n")
        assertEquals("Creator: João\nLicence: CC BY 4.0", PackAttribution.read(temporary.root))
    }

    @Test fun oversizedAndMalformedNoticesAreRejected() {
        val file = File(temporary.root, "ATTRIBUTION.txt")
        file.writeBytes(ByteArray(262_145))
        assertThrows(IllegalArgumentException::class.java) { PackAttribution.read(temporary.root) }
        file.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { PackAttribution.read(temporary.root) }
    }

    @Test fun externalSymlinkIsRejected() {
        val root = temporary.newFolder("pack")
        val outside = temporary.newFile("outside.txt").apply { writeText("private") }
        Files.createSymbolicLink(File(root, "ATTRIBUTION.txt").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { PackAttribution.read(root) }
    }
}
