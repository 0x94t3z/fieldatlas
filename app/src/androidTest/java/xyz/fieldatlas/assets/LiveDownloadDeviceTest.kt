package xyz.fieldatlas.assets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicitly opt-in internet tests, not part of the ordinary offline test suite.
 * Run with -e fieldatlasLiveTransfers yes. No credentials or application data are used.
 * The small GGUF is a transport fixture (tokenizer vocabulary), NOT an inference model.
 * These component tests do not replace normal-release UI/setup or process-death tests.
 */
@RunWith(AndroidJUnit4::class)
class LiveDownloadDeviceTest {
    private lateinit var root: File

    @Before fun setUp() {
        assumeTrue("Requires explicit consent to live public HTTPS downloads",
            InstrumentationRegistry.getArguments().getString("fieldatlasLiveTransfers") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "live-download-${UUID.randomUUID()}")
        check(root.mkdir())
    }

    @After fun tearDown() {
        if (::root.isInitialized) check(root.deleteRecursively())
    }

    @Test fun actualHttpsTransferRecoversFromInterruptionWithFreshDownloader() = runBlocking {
        val partial = File(root, "vocabulary.part")
        var interrupted = false
        try {
            VerifiedResumableDownload().download(FIXTURE_URL, FIXTURE_BYTES, FIXTURE_HASH, partial) {
                if (it >= 64_000L) throw CancellationException("Intentional transfer interruption")
            }
        } catch (_: CancellationException) {
            interrupted = true
        }
        assertTrue("The transfer must really be interrupted", interrupted)
        val saved = partial.length()
        assertTrue(saved in 64_000L until FIXTURE_BYTES)
        val progress = mutableListOf<Long>()
        // A fresh instance must discover the partial on disk. The real server may honor Range
        // or legitimately restart with 200; either path must verify the complete pinned file.
        VerifiedResumableDownload().download(FIXTURE_URL, FIXTURE_BYTES, FIXTURE_HASH, partial) {
            progress += it
        }
        assertEquals(saved, progress.first())
        assertEquals(FIXTURE_BYTES, partial.length())
        assertEquals(FIXTURE_HASH, hash(partial))
    }

    @Test fun actualWrongChecksumCannotReplaceAnAlreadyInstalledModel() = runBlocking {
        val registry = AssetRegistry(root)
        val installer = ModelDownload(root, registry)
        val prior = installer.install(fixtureModel()) {}
        val priorFile = File(prior.rootPath, "model.gguf")
        assertEquals(FIXTURE_HASH, hash(priorFile))
        val bad = fixtureModel().copy(id = "incorrect-checksum-fixture", sha256 = "0".repeat(64))
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { installer.install(bad) {} }
        }
        assertTrue("Must reach checksum verification, not pass due to a network error",
            error.message.orEmpty().contains("checksum did not match"))
        assertEquals(listOf(prior), registry.list())
        assertEquals(FIXTURE_HASH, hash(priorFile))
        assertFalse(File(root, "packs/${bad.id}/${bad.version}").exists())
        assertEquals(0L, installer.resumableBytes(bad))
    }

    @Test fun realMirroredKnowledgePackRecoversAndVerifiesEveryArtifact() = runBlocking {
        val registry = AssetRegistry(root)
        val importer = AssetImporter(root, registry)
        val first = KnowledgePackDownload(root, registry, importer)
        var interrupted = false
        try {
            first.install(veganPack(), VEGAN_MIRROR) {
                if (it >= 1_000_000L) throw CancellationException("Intentional pack interruption")
            }
        } catch (_: CancellationException) {
            interrupted = true
        }
        assertTrue(interrupted)
        assertTrue(registry.list().isEmpty())
        val saved = first.resumableBytes(veganPack())
        assertTrue(saved in 1_000_000L until VEGAN_BYTES)
        val progress = mutableListOf<Long>()
        var manifestBytes: ByteArray? = null
        val installed = KnowledgePackDownload(root, registry, importer).install(veganPack(), VEGAN_MIRROR) {
            progress += it
            // The installer intentionally removes the verified archive after registration.
            // Inspect its bounded manifest while the completed archive is still in staging.
            if (it == VEGAN_BYTES) {
                val archive = File(root, "knowledge-downloads/osm-vegan-places-2026.10.05.part")
                check(hash(archive) == veganPack().sha256) // Never unzip unverified mirror bytes.
                ZipFile(archive).use { zip ->
                    val entry = checkNotNull(zip.getEntry("manifest.json"))
                    check(entry.size in 1..1_048_576)
                    manifestBytes = zip.getInputStream(entry).use { input -> input.readBytes() }
                }
            }
        }
        assertEquals(saved, progress.first())
        assertEquals("osm-vegan-places", installed.id)
        assertEquals("2026.10.05", installed.version)
        assertEquals(PackType.KNOWLEDGE, installed.type)
        assertTrue(installed.installedBytes > 0)
        assertEquals(listOf(installed), registry.list())
        val verifiedManifest = checkNotNull(manifestBytes)
        assertEquals(installed.manifestSha256, Sha256.digest(verifiedManifest))
        val manifest = PackManifestParser.parse(verifiedManifest)
        manifest.artifacts.forEach { artifact ->
            val file = File(installed.rootPath, artifact.path)
            assertEquals(artifact.path, artifact.bytes, file.length())
            assertEquals(artifact.path, artifact.sha256, hash(file))
        }
        assertEquals(0L, first.resumableBytes(veganPack()))
    }

    private fun hash(file: File): String = file.inputStream().use { Sha256.digest(it) }

    private fun fixtureModel() = ModelOffer(
        id = "live-transport-fixture", version = "1", name = "Transport fixture",
        title = "Transport fixture — not an answer model", license = "MIT",
        bytes = FIXTURE_BYTES, sha256 = FIXTURE_HASH, url = FIXTURE_URL, description = "Test-only vocabulary file",
    )

    private fun veganPack() = KnowledgeCatalogEntry(
        id = "osm-vegan-places", version = "2026.10.05-leviathan", title = "Vegan places", description = "Live transfer test",
        license = "ODbL-1.0", bytes = VEGAN_BYTES,
        sha256 = "ace29e1c28492f88ed2c5f8dad58260ba3bbf6ea00fb21606ec720475c9ede7d",
        url = "https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/osm-vegan-places/2026.10.05-leviathan/osm-vegan-places-2026.10.05-leviathan.fapack",
    )

    companion object {
        private const val FIXTURE_BYTES = 1_766_807L
        private const val FIXTURE_HASH = "cedc56ca6e2e89f63e781696d1fd76b4b1d49e6720dee86463e915f6e90016ac"
        private const val FIXTURE_URL = "https://raw.githubusercontent.com/ggml-org/llama.cpp/60081bb2b5b3294165a4d67c5cbeebe74c868014/models/ggml-vocab-gpt-2.gguf"
        private const val VEGAN_BYTES = 24_183_118L
        private const val VEGAN_MIRROR = "https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/resolve/a27528359563795169f6e0b818537642290efab5/osm-vegan-places/2026.10.05-leviathan/osm-vegan-places-2026.10.05-leviathan.fapack"
    }
}
