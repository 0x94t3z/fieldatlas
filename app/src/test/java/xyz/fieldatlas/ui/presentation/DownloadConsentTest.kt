package xyz.fieldatlas.ui.presentation

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.assets.ModelOffers

class DownloadConsentTest {
    @Test fun defaultModelShowsItsActualEndpoint() {
        assertEquals("Source: huggingface.co\nDownload servers can see your IP address, not your questions.",
            downloadSourceDisclosure(ModelOffers.recommended.url))
    }

    @Test fun mirrorsShowHostAndNondefaultPortButNeverPathOrSignedQueries() {
        val text = downloadSourceDisclosure("https://mirror.test:8443/private/model.gguf?token=secret-value")
        assertTrue(text.startsWith("Source: mirror.test:8443\n"))
        assertFalse(text.contains("private"))
        assertFalse(text.contains("token"))
        assertFalse(text.contains("secret-value"))
        assertTrue(downloadSourceDisclosure("https://mirror.test:443/data.fapack").startsWith("Source: mirror.test\n"))
    }

    @Test fun invalidStoredAddressesCannotLeakCredentialsOrRawInput() {
        for (url in listOf("broken secret-value", "http://secret.test/a.fapack", "https://user:password@mirror.test/a.fapack",
            "https://mirror.test/a.fapack#secret-value", "https://mirror.test:0/a.fapack")) {
            assertTrue(downloadSourceDisclosure(url).startsWith("Source: selected HTTPS server\n"))
            assertFalse(downloadSourceDisclosure(url).contains("password"))
            assertFalse(downloadSourceDisclosure(url).contains("secret-value"))
        }
    }
}
