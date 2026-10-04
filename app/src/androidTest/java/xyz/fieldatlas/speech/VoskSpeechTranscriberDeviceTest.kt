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
        // The app asks for the microphone at first use; the test grants it the same way a user would.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName,
            android.Manifest.permission.RECORD_AUDIO)
        val transcriber = VoskSpeechTranscriber(ApplicationProvider.getApplicationContext())
        try {
            transcriber.start { }
            Thread.sleep(500)
            assertNotNull(transcriber.stop())
        } finally {
            transcriber.cancel()
        }
    }

    /**
     * Stopping while the recogniser is still decoding a chunk once closed it from another thread,
     * and the recording thread then read freed memory (found under Android's malloc debug, the
     * same class of bug GrapheneOS's hardened allocator turns into a crash). Stop and cancel at
     * once, several times, so a regression shows under either allocator.
     */
    @Test fun stoppingOrCancellingMidDecodeNeverFreesTheRecogniserUnderIt() = runBlocking {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName,
            android.Manifest.permission.RECORD_AUDIO)
        val transcriber = VoskSpeechTranscriber(ApplicationProvider.getApplicationContext())
        repeat(3) {
            transcriber.start { }
            assertNotNull(transcriber.stop())
        }
        transcriber.start { }
        transcriber.cancel()
        // A second session right after a cancel must not touch the first one's recogniser.
        transcriber.start { }
        Thread.sleep(300)
        assertNotNull(transcriber.stop())
    }
}
