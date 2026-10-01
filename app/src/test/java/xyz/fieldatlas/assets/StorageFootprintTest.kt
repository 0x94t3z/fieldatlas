package xyz.fieldatlas.assets

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageFootprintTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun countsCacheStagingAndUnregisteredFilesWithoutDoubleCountingNestedRoots() {
        val root = temp.newFolder()
        val cache = File(root, "cache").apply { mkdir() }
        File(cache, "partial").writeBytes(ByteArray(31))
        File(root, "orphan.gguf").writeBytes(ByteArray(19))
        val apk = temp.newFile().apply { writeBytes(ByteArray(7)) }
        assertEquals(57L, StorageFootprint { listOf(root, cache, apk, apk) }.bytes())
    }

    @Test fun doesNotFollowSymlinkOrLoop() {
        val root = temp.newFolder()
        File(root, "real").writeBytes(ByteArray(13))
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        assertEquals(13L, StorageFootprint { listOf(root) }.bytes())
    }

    @Test fun missingOptionalRootIsEmpty() {
        assertEquals(0L, StorageFootprint { listOf(File(temp.root, "missing")) }.bytes())
    }

    @Test fun resumedBytesAreAlreadyIncludedInFootprint() {
        val root = temp.newFolder()
        File(root, "partial").writeBytes(ByteArray(40))
        val current = StorageFootprint { listOf(root) }.bytes()
        assertEquals(100L, current + (100 - 40))
        assertEquals(BudgetDecision.Allowed, StorageBudget.evaluate(current, 60, 60))
    }
}
