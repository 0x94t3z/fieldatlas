package xyz.fieldatlas.speech

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Answers are read with a phone voice only when it is installed and needs no connection. */
class OfflinePhoneVoiceTest {
    private fun voice(name: String, locale: Locale = Locale.US, network: Boolean = false, installed: Boolean = true, quality: Int = 300) =
        EngineVoice(name, locale, network, installed, quality)

    @Test fun aNetworkVoiceIsNeverChosenEvenWhenItIsBetter() {
        val voices = listOf(voice("en-us-x-iob-network", network = true, quality = 500), voice("en-us-x-iob-local", quality = 400))
        assertEquals("en-us-x-iob-local", ReadAloud.offlineVoice(voices)?.name)
    }

    @Test fun onlyNetworkOrMissingVoicesMeanNoPhoneVoice() {
        assertNull(ReadAloud.offlineVoice(listOf(voice("en-us-x-sfg-network", network = true), voice("en-gb-x-gba-network", Locale.UK, network = true))))
        // A voice whose data is not downloaded cannot speak offline either.
        assertNull(ReadAloud.offlineVoice(listOf(voice("en-us-x-tpf-local", installed = false))))
        // An engine that lists no voices cannot be checked, so it is not used.
        assertNull(ReadAloud.offlineVoice(emptyList()))
    }

    @Test fun englishOnlyAmericanThenBritishThenAnyThenQuality() {
        val voices = listOf(
            voice("fr-fr-local", Locale.FRANCE, quality = 500),
            voice("en-in-local", Locale("en", "IN"), quality = 500),
            voice("en-gb-local", Locale.UK, quality = 300),
        )
        assertEquals("en-gb-local", ReadAloud.offlineVoice(voices)?.name)
        assertEquals("en-us-high", ReadAloud.offlineVoice(voices + voice("en-us-low", quality = 100) + voice("en-us-high", quality = 400))?.name)
        assertEquals("en-in-local", ReadAloud.offlineVoice(listOf(voice("fr-fr-local", Locale.FRANCE), voice("en-in-local", Locale("en", "IN"))))?.name)
        assertNull(ReadAloud.offlineVoice(listOf(voice("fr-fr-local", Locale.FRANCE))))
    }
}

/** The reading settings say what the phone's voice can do before anything is played. */
class PhoneVoiceWordingTest {
    private val offline = ReadAloud.PhoneVoice.Offline("en-us-x-iob-local")

    @Test fun anOfflineVoiceIsOfferedAndSaidToWorkWithNoSignal() {
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceNote(offline).contains("installed on this phone"))
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceChoice(offline).startsWith("Ready now"))
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.untilNaturalNote(offline).contains("the phone's own voice reads until it is"))
    }

    @Test fun aVoiceThatNeedsTheInternetIsSaidNotToBeUsed() {
        val online = ReadAloud.PhoneVoice.OnlineOnly
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceNote(online).contains("needs the internet, so answers are not read with it"))
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceChoice(online).contains("does not use it"))
        // Nobody reads meanwhile: the note must not promise the phone's voice.
        org.junit.Assert.assertFalse(xyz.fieldatlas.ui.research.untilNaturalNote(online).contains("reads until"))
    }

    @Test fun noEngineAndStillCheckingAreSaidAsSuch() {
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceNote(ReadAloud.PhoneVoice.Missing).startsWith("This phone has no speech voice"))
        org.junit.Assert.assertFalse(xyz.fieldatlas.ui.research.untilNaturalNote(ReadAloud.PhoneVoice.Missing).contains("reads until"))
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceNote(null).startsWith("Checking"))
        org.junit.Assert.assertTrue(xyz.fieldatlas.ui.research.phoneVoiceChoice(null).startsWith("Checking"))
    }
}
