package xyz.fieldatlas.speech

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.ui.research.readingSummary

class NaturalVoiceTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test fun everyFileIsPinnedOnce() {
        assertEquals(NaturalVoice.PARTS.size, NaturalVoice.PARTS.map { it.name }.toSet().size)
        assertTrue(NaturalVoice.PARTS.all { it.sha256.matches(Regex("[0-9a-f]{64}")) && it.bytes > 0 })
        // The seven files the sherpa-onnx Supertonic config names: about 145 MB in all.
        assertEquals(145_295_768L, NaturalVoice.BYTES)
    }

    @Test fun readyOnlyWhenEveryFileIsWholeAndTheInstallFinished() {
        val root = Files.createTempDirectory("voice").toFile()
        val dir = File(root, "voices/${NaturalVoice.ID}").apply { mkdirs() }
        // Sparse files of the right length stand in for the real ones.
        fun fill(skip: String? = null) = NaturalVoice.PARTS.filter { it.name != skip }
            .forEach { part -> java.io.RandomAccessFile(File(dir, part.name), "rw").use { it.setLength(part.bytes) } }
        fill()
        assertEquals(NaturalVoice.State.Missing(NaturalVoice.BYTES), NaturalVoice(root, scope = scope).state.value)
        File(dir, "installed").writeText(NaturalVoice.REVISION)
        assertEquals(NaturalVoice.State.Ready, NaturalVoice(root, scope = scope).state.value)
        File(dir, "vocoder.int8.onnx").delete()
        val partial = File(root, "voice-downloads/${NaturalVoice.ID}/vocoder.int8.onnx.part").apply { parentFile.mkdirs(); writeBytes(ByteArray(1000)) }
        val voice = NaturalVoice(root, scope = scope)
        assertEquals(NaturalVoice.State.Missing(NaturalVoice.BYTES - 25_991_073 + 1000), voice.state.value)
        assertTrue(partial.isFile)
        root.deleteRecursively()
    }

    @Test fun playingStartsOnlyWhenTheRestWillArriveInTime() {
        // A fast phone (speech made in a third of its length): the next sentence's making time is all it needs.
        assertEquals(3_000L, ReadAloud.headStartMs(remainingChars = 100, nextChars = 50, madeChars = 50, madeAudioMs = 5_000, makingMs = 2_000))
        // The Redmi 13C: 120 characters made in 15.4 s gave 12.2 s of speech; 178 characters remain, the next is 109.
        assertEquals(15_022L, ReadAloud.headStartMs(178, 109, 120, 12_167, 15_438))
        // Nothing left to make: play at once.
        assertEquals(0L, ReadAloud.headStartMs(0, 0, 298, 29_000, 45_000))
    }

    @Test fun headingsGetTheLongerPauseLikeTheRecordings() {
        assertEquals(0.8f, ReadAloud.pauseAfter("What to do"))
        assertEquals(0.45f, ReadAloud.pauseAfter("Rinse the eye for 15 minutes."))
        assertEquals(0.45f, ReadAloud.pauseAfter("Is it safe?"))
    }

    @Test fun moreSummarisesTheVoiceAndSpeed() {
        assertEquals("Phone voice · normal speed", readingSummary(ReadAloud.Settings(), naturalReady = false))
        assertEquals("Natural voice · 1.25×", readingSummary(ReadAloud.Settings(ReadAloud.Choice.Natural, 1.25f), naturalReady = true))
        assertEquals("Natural voice, not downloaded · normal speed", readingSummary(ReadAloud.Settings(ReadAloud.Choice.Natural), naturalReady = false))
    }
}
