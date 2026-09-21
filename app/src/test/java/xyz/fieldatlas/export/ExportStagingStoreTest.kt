package xyz.fieldatlas.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportStagingStoreTest {
    @Test fun stagedPayloadSurvivesStoreRecreationUntilTransferred() {
        val directory = Files.createTempDirectory("fieldatlas-export").toFile()
        try {
            val expected = "{\"results\":[1,2,3]}\n".toByteArray()
            ExportStagingStore(directory).stage("benchmark", expected)

            val output = ByteArrayOutputStream()
            assertTrue(ExportStagingStore(directory).transfer("benchmark", output))
            assertArrayEquals(expected, output.toByteArray())
            assertFalse(File(directory, "benchmark.pending").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun missingPayloadIsReportedWithoutCreatingOutput() {
        val directory = Files.createTempDirectory("fieldatlas-export").toFile()
        try {
            val output = ByteArrayOutputStream()
            assertFalse(ExportStagingStore(directory).transfer("benchmark", output))
            assertArrayEquals(byteArrayOf(), output.toByteArray())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun stagingAgainReplacesAStalePendingPayload() {
        val directory = Files.createTempDirectory("fieldatlas-export").toFile()
        try {
            val store = ExportStagingStore(directory)
            store.stage("benchmark", "stale".toByteArray())
            store.stage("benchmark", "current".toByteArray())

            val output = ByteArrayOutputStream()
            assertTrue(store.transfer("benchmark", output))
            assertArrayEquals("current".toByteArray(), output.toByteArray())
        } finally {
            directory.deleteRecursively()
        }
    }
}
