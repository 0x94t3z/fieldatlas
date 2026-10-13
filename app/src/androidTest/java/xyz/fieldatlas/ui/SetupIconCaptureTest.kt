package xyz.fieldatlas.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.assets.KnowledgeCatalog
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** First-run setup as a new user sees it, for visual review; fixtures never download or import. */
class SetupIconCaptureTest {
    @get:Rule val compose = foregroundComposeRule()
    private val catalog = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("knowledge/catalog.json").bufferedReader().use { KnowledgeCatalog.parse(it.readText()).packs }

    @Test fun freshSetupLight() = walk(dark = false)
    @Test fun freshSetupDark() = walk(dark = true)

    private fun walk(dark: Boolean) {
        compose.setContent { FieldAtlasTheme(darkTheme = dark) {
            SetupScreen(emptyList(), false, false, 0, null, {}, {}, {},
                availableKnowledge = catalog, online = true)
        } }
        val theme = if (dark) "dark" else "light"
        capture("setup-$theme-1")
        catalog.forEachIndexed { index, entry ->
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(entry.title))
            if (index == 2 || index == catalog.lastIndex) capture("setup-$theme-${index + 2}")
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "setup-icon-review")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
