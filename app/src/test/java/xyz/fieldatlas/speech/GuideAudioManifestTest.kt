package xyz.fieldatlas.speech

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyData

/**
 * The recordings must say what the app shows: a guide changed without rebuilding its recording would mark the wrong
 * sentence while it plays. Rebuild with tools/packs/build_guide_audio.py when this fails.
 */
class GuideAudioManifestTest {
    @Test fun everyGuideHasARecordingThatMatchesItsScript() {
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString(GuideAudioManifest.serializer(),
            File("src/main/assets/emergency/audio/manifest.json").readText())
        val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
        assertEquals(guides.map { it.id }.sorted(), manifest.guides.keys.sorted())
        for (guide in guides) {
            val entry = manifest.guides.getValue(guide.id)
            assertEquals("${guide.id} recording is out of date", SpeechText.guide(guide).map { it.shown }, entry.segments.map { it.shown })
            assertTrue(File("src/main/assets/emergency/audio/${entry.file}").isFile)
            assertTrue(entry.segments.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
        }
    }
}
