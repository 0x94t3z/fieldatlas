package xyz.fieldatlas.ui.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.speech.ReadAloud

/** Reading aloud offers a sample only of a voice that can play it. */
class ReadingSampleTest {
    @Test fun thePhoneVoiceSampleNeedsAnOfflineVoiceAndSupertonicsIsAlwaysThere() {
        assertTrue(sampleAvailable(ReadAloud.Choice.Phone, ReadAloud.PhoneVoice.Offline("en-us-x-iob-local")))
        assertFalse(sampleAvailable(ReadAloud.Choice.Phone, ReadAloud.PhoneVoice.Missing))
        assertFalse(sampleAvailable(ReadAloud.Choice.Phone, ReadAloud.PhoneVoice.OnlineOnly))
        assertFalse(sampleAvailable(ReadAloud.Choice.Phone, null))
        // A recording in the app: a preview before the download, whatever the phone has.
        assertTrue(sampleAvailable(ReadAloud.Choice.Natural, ReadAloud.PhoneVoice.Missing))
    }
}
