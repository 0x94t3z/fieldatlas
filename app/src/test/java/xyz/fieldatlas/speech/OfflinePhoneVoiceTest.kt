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
