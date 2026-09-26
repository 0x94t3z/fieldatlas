package xyz.fieldatlas.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** A short real-device smoke test for the bundled, entirely offline voice path. */
@RunWith(AndroidJUnit4::class)
class VoskSpeechTranscriberDeviceTest {
    @Test fun startsAndStopsRecordingWithoutNetwork() = runBlocking {
        val transcriber = VoskSpeechTranscriber(ApplicationProvider.getApplicationContext())
        try {
            transcriber.start { }
            Thread.sleep(500)
            assertNotNull(transcriber.stop())
        } finally {
            transcriber.cancel()
        }
    }
}
