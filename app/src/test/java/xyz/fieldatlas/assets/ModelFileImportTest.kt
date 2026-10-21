package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelFileImportTest {
    private val gemma = ModelOffers.largest
    private val twelve = 11_800_000_000L
    private val six = 5_867_972_000L

    @Test fun aFileIsAModelOnlyWhenItOpensWithTheGgufMark() {
        assertTrue(ModelFileImport.isGguf("GGUF\u0003\u0000".toByteArray()))
        assertFalse(ModelFileImport.isGguf("PK\u0003\u0004".toByteArray()))
        assertFalse(ModelFileImport.isGguf("GG".toByteArray()))
    }

    @Test fun theExactSizeOfATestedModelIsThatModelOnAPhoneWithItsMemory() {
        assertEquals(ModelFileKind.Tested(gemma), ModelFileImport.classify("anything.gguf", gemma.bytes, twelve))
        // The same file on a 6 GB phone is refused with the tested model's own memory rule.
        val refused = ModelFileImport.classify("gemma.gguf", gemma.bytes, six)
        assertTrue(refused is ModelFileKind.TooBig && refused.offer == gemma)
        val tooBig = ModelFileImport.refusal(refused, six)!!
        assertEquals("Too big for this phone", tooBig.headline)
        assertEquals("Gemma 4 26B needs a phone with 12 GB of memory; this one has 6 GB.", tooBig.message)
    }

    @Test fun aTestedModelsFileThatIsShorterIsAnIncompleteDownload() {
        val name = ModelFileImport.publishedName(gemma)
        assertEquals("gemma-4-26B-A4B-it-UD-Q2_K_XL.gguf", name)
        val kind = ModelFileImport.classify(name, 7_920_000_000L, twelve)
        assertEquals(ModelFileKind.Incomplete(gemma, 7_920_000_000L), kind)
        val incomplete = ModelFileImport.refusal(kind, twelve)!!
        assertEquals("Incomplete download", incomplete.headline)
        assertEquals("It has 7.92 of the 10.55 GB in the published Gemma 4 26B file. Download it again, then import it.", incomplete.message)
    }

    @Test fun anyOtherModelIsTheUsersOwnWhenItFitsAndRefusedWhenItDoesNot() {
        assertEquals(ModelFileKind.Own(4_920_000_000L), ModelFileImport.classify("llama-3.1-8b-instruct.Q4_K_M.gguf", 4_920_000_000L, twelve))
        assertNull(ModelFileImport.refusal(ModelFileKind.Own(4_920_000_000L), twelve))
        val big = ModelFileImport.classify("qwen3-32b-instruct.Q4_K_M.gguf", 19_800_000_000L, six)
        assertTrue(big is ModelFileKind.TooBig && big.offer == null)
        assertEquals("It needs about 21 GB of memory; a 6 GB phone can give one app about 5 GB.", ModelFileImport.refusal(big, six)!!.message)
        // What Android reports is less than the memory sold: the emulator's 12.53e9 bytes and the Redmi's 6.01e9.
        assertEquals("12 GB", ModelFileImport.phoneMemory(12_237_236L * 1024))
        assertEquals("6 GB", ModelFileImport.phoneMemory(5_867_972L * 1024))
        // 4.92 GB plus the runtime is more than 80% of a 6 GB phone.
        assertTrue(ModelFileImport.classify("llama-3.1-8b.gguf", 4_920_000_000L, six) is ModelFileKind.TooBig)
    }

    @Test fun anOwnModelMustHaveAReadableHeaderWeightsAndAChatFormat() {
        assertEquals("Not a model file", ModelFileImport.ownModelProblem(false, null, null)!!.headline)
        assertEquals("Not a model file", ModelFileImport.ownModelProblem(true, 0, "{{ messages }}")!!.headline)
        assertEquals("Not a chat model", ModelFileImport.ownModelProblem(true, 197, null)!!.headline)
        assertNull(ModelFileImport.ownModelProblem(true, 291, "{% for message in messages %}{{ message.content }}{% endfor %}"))
    }

    @Test fun theUsersOwnModelIsNamedByItsHeaderElseItsFile() {
        assertEquals("Llama 3.1 8B Instruct", ModelFileImport.ownTitle(" Llama 3.1 8B Instruct ", "x.gguf"))
        assertEquals("llama-3.1-8b-instruct.Q4_K_M", ModelFileImport.ownTitle(null, "llama-3.1-8b-instruct.Q4_K_M.gguf"))
        assertEquals("Your own model", ModelFileImport.ownTitle("", null))
        assertEquals("own-0123456789ab", ModelFileImport.ownId("0123456789ABCDEF"))
    }

    @Test fun anOwnModelIsCopiedHashedAndRegisteredUnderItsHash() = runBlocking {
        val root = Files.createTempDirectory("model-import").toFile()
        try {
            val registry = AssetRegistry(root)
            val bytes = "GGUF".toByteArray() + ByteArray(5_000) { (it % 251).toByte() }
            val progress = mutableListOf<Long>()
            val installed = ModelDownload(root, registry).installFromFile(ModelFileKind.Own(bytes.size.toLong()), ByteArrayInputStream(bytes),
                bytes.size.toLong(), "tiny.gguf", "Tiny model", null) { progress += it }
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(ModelFileImport.ownId(sha), installed.id)
            assertEquals(PackType.MODEL, installed.type)
            assertEquals("Tiny model", installed.title)
            assertTrue(File(installed.rootPath, "model.gguf").readBytes().contentEquals(bytes))
            assertEquals(bytes.size.toLong(), progress.last())
            assertTrue(registry.list().any { it.id == installed.id })
            // Nothing is left in staging.
            assertTrue(File(root, "model-downloads").listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun aModelThatWouldPassTheFiftyGigabyteLimitIsRefusedAsSuch() = runBlocking {
        val root = Files.createTempDirectory("model-import").toFile()
        try {
            // A sparse file stands for 49.99 GB already installed.
            java.io.RandomAccessFile(File(root, "held.bin"), "rw").use { it.setLength(49_990_000_000L) }
            val bytes = "GGUF".toByteArray() + ByteArray(20_000_000)
            try {
                ModelDownload(root, AssetRegistry(root)).installFromFile(ModelFileKind.Own(bytes.size.toLong()), ByteArrayInputStream(bytes),
                    bytes.size.toLong(), "own.gguf", "Own", null) {}
                fail("a model past the 50 GB limit must not install")
            } catch (refused: AssetImportException) {
                assertEquals(BudgetDecision.ExceedsGlobalLimit, refused.budgetDecision)
                assertTrue(xyz.fieldatlas.ui.setup.assetSetupError(refused, xyz.fieldatlas.ui.setup.AssetAction.PACK_IMPORT).contains("50 GB"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun aFileTheSizeOfATestedModelButWithOtherBytesIsRefused() = runBlocking {
        val root = Files.createTempDirectory("model-import").toFile()
        try {
            val bytes = ByteArray(1_000) { 7 }
            val fake = gemma.copy(bytes = bytes.size.toLong(), sha256 = "0".repeat(64))
            try {
                ModelDownload(root, AssetRegistry(root)).installFromFile(ModelFileKind.Tested(fake), ByteArrayInputStream(bytes),
                    bytes.size.toLong(), "gemma.gguf", fake.title, null) {}
                fail("a file with other contents must not install as the tested model")
            } catch (refused: ModelFileRefusal) {
                assertEquals("Not the published file", refused.headline)
                assertTrue(refused.message!!.startsWith("It's the size of the published Gemma 4 26B file, but its contents differ"))
            }
            assertTrue(AssetRegistry(root).list().isEmpty())
            assertTrue(File(root, "model-downloads").listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
