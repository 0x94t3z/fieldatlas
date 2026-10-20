package xyz.fieldatlas.speech

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import xyz.fieldatlas.assets.StorageBudget

/** The voice counts against the 50 GB limit models and collections share, at its edge, without touching real data. */
class VoiceStorageLimitTest {
    private val limitMessage = "There isn't room within Field Atlas's 50 GB storage limit. Remove unused packs in Library, then try again."

    private fun attempt(held: Long, saved: Long = 0): Pair<NaturalVoice.State, File> {
        val root = Files.createTempDirectory("voice-limit").toFile()
        if (saved > 0) File(root, "voice-downloads/${NaturalVoice.ID}/${NaturalVoice.PARTS.first().name}.part")
            .apply { parentFile.mkdirs(); writeBytes(ByteArray(saved.toInt())) }
        val voice = NaturalVoice(root, scope = CoroutineScope(Dispatchers.IO), footprint = { held })
        // A source that cannot be read: past the limit check, the download itself fails at once.
        voice.download("file:///nonexistent-field-atlas-voice/")
        val ended = runBlocking { withTimeout(30_000) { voice.state.first { it is NaturalVoice.State.Failed || it == NaturalVoice.State.Ready } } }
        return ended to root
    }

    @Test fun oneByteOverTheLimitIsRefusedBeforeAnythingIsWritten() {
        val edge = StorageBudget.MAX_TOTAL_BYTES - NaturalVoice.BYTES - StorageBudget.METADATA_RESERVE_BYTES
        val (state, root) = attempt(held = edge + 1)
        assertEquals(limitMessage, (state as NaturalVoice.State.Failed).message)
        assertFalse(File(root, "voices/${NaturalVoice.ID}").exists())
        root.deleteRecursively()
    }

    @Test fun exactlyAtTheLimitItIsAllowedToStart() {
        val edge = StorageBudget.MAX_TOTAL_BYTES - NaturalVoice.BYTES - StorageBudget.METADATA_RESERVE_BYTES
        val (state, root) = attempt(held = edge)
        assertNotEquals(limitMessage, (state as NaturalVoice.State.Failed).message)
        root.deleteRecursively()
    }

    @Test fun theVoiceWaitsWhileAnotherDownloadOrImportClaimsStorage() {
        val root = Files.createTempDirectory("voice-lock").toFile()
        val provisioning = kotlinx.coroutines.sync.Mutex()
        var counted = 0
        val voice = NaturalVoice(root, scope = CoroutineScope(Dispatchers.IO), footprint = { counted++; StorageBudget.MAX_TOTAL_BYTES },
            provisioning = provisioning)
        runBlocking { provisioning.lock() }
        voice.download("file:///nonexistent-field-atlas-voice/")
        Thread.sleep(500)
        // Held elsewhere: nothing counted or decided yet.
        assertEquals(0, counted)
        assertEquals(true, voice.state.value is NaturalVoice.State.Downloading)
        provisioning.unlock()
        val ended = runBlocking { withTimeout(30_000) { voice.state.first { it is NaturalVoice.State.Failed } } }
        assertEquals(limitMessage, (ended as NaturalVoice.State.Failed).message)
        root.deleteRecursively()
    }

    @Test fun bytesAlreadySavedAreNotCountedTwice() {
        // The footprint already holds the saved part; only what is left to fetch is new.
        val saved = 1_000L
        val edge = StorageBudget.MAX_TOTAL_BYTES - (NaturalVoice.BYTES - saved) - StorageBudget.METADATA_RESERVE_BYTES
        val (allowed, root) = attempt(held = edge, saved = saved)
        assertNotEquals(limitMessage, (allowed as NaturalVoice.State.Failed).message)
        root.deleteRecursively()
        val (refused, again) = attempt(held = edge + 1, saved = saved)
        assertEquals(limitMessage, (refused as NaturalVoice.State.Failed).message)
        again.deleteRecursively()
    }
}
