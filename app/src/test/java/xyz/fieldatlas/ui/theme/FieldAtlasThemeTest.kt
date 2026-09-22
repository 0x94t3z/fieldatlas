package xyz.fieldatlas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAtlasThemeTest {
    @Test
    fun lightFieldNotebookUsesPaperAndForest() {
        val colors = fieldAtlasColorScheme(darkTheme = false)

        assertEquals(Color(0xFFF3EFE5), colors.background)
        assertEquals(Color(0xFF285D49), colors.primary)
        assertTrue(colors.background.luminance() > 0.8f)
        assertTrue(colors.primary.green > colors.primary.red)
        assertTrue(colors.primary.green > colors.primary.blue)
        assertTrue(contrastRatio(colors.primary, colors.onPrimary) >= 4.5f)
    }

    @Test
    fun darkFieldNotebookUsesInkAndSage() {
        val colors = fieldAtlasColorScheme(darkTheme = true)

        assertTrue(colors.surface.luminance() < 0.03f)
        assertTrue(colors.primary.green > colors.primary.red)
        assertTrue(colors.primary.green > colors.primary.blue)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
