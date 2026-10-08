package xyz.fieldatlas.compliance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The licences the app shows under More are the repository's own files, unchanged. */
class LicenceAssetsTest {
    private val assets = File("src/main/assets/licenses")

    @Test fun shippedNoticesMatchTheRepository() {
        mapOf(
            "LICENSE.txt" to "../LICENSE",
            "NOTICE.txt" to "../NOTICE",
            "THIRD_PARTY_NOTICES.md" to "../THIRD_PARTY_NOTICES.md",
            "source-serif-4-OFL.txt" to "../docs/licenses/source-serif-4-OFL.txt",
            "manrope-OFL.txt" to "../docs/licenses/manrope-OFL.txt",
            "llama.cpp-LICENSE.txt" to "../third_party/llama.cpp/LICENSE",
            "bouncycastle-LICENSE.txt" to "../docs/licenses/bouncycastle-LICENSE.txt",
            "llvm-LICENSE.txt" to "../docs/licenses/llvm-LICENSE.txt",
        ).forEach { (asset, source) ->
            assertEquals("$asset differs from $source; copy it again", File(source).readText(), File(assets, asset).readText())
        }
    }

    @Test fun noticesReadAsOneLinePerComponentWithAddresses() {
        val text = xyz.fieldatlas.ui.more.readableNotices(File("../THIRD_PARTY_NOTICES.md").readText())
        assertTrue(text, text.contains("- **`ggml-org/llama.cpp`** — commit"))
        assertTrue(text, text.contains("(https://github.com/ggml-org/llama.cpp/blob/"))
        assertTrue(text.lines().none { it.trim().startsWith("|") })
    }

    @Test fun licenseIsTheVerbatimApacheText() {
        val text = File("../LICENSE").readText()
        assertTrue(text.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        assertTrue(text.contains("APPENDIX: How to apply the Apache License to your work."))
        assertEquals(202, text.lines().size - 1)
    }
}
