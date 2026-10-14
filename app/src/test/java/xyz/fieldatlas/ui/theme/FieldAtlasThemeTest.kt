package xyz.fieldatlas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAtlasThemeTest {
    @Test fun answersAndSourcesShareACompactReadingAndDisclosureScale() {
        assertEquals(FieldAtlasEditorial, FieldAtlasReadingStyle.fontFamily)
        assertEquals(15.sp, FieldAtlasReadingStyle.fontSize)
        assertEquals(24.sp, FieldAtlasReadingStyle.lineHeight)
        assertEquals(FieldAtlasSans, FieldAtlasDisclosureStyle.fontFamily)
        assertEquals(13.sp, FieldAtlasDisclosureStyle.fontSize)
        assertEquals(21.sp, FieldAtlasDisclosureStyle.lineHeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, FieldAtlasDisclosureStyle.fontWeight)
    }

    @Test fun collectionStatusUsesTheCylinderDatabaseVectorNotTheBook() {
        val path = FieldAtlasIcons.Database.root[0] as androidx.compose.ui.graphics.vector.VectorPath
        assertTrue(path.pathData.any { it is androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo })
        assertEquals(24f, FieldAtlasIcons.Database.viewportWidth)
    }

    @Test fun theDinosaurWalksOnTheSameGridItStandsOn() {
        val frames = listOf(FieldAtlasIcons.Offline, FieldAtlasIcons.DinoWalkA, FieldAtlasIcons.DinoWalkB)
        frames.forEach { assertEquals(32f, it.viewportWidth); assertEquals(32f, it.viewportHeight) }
        val (a, b) = frames.drop(1).map { (it.root[0] as androidx.compose.ui.graphics.vector.VectorPath).pathData }
        // Two different strides, so the walk is visible.
        org.junit.Assert.assertNotEquals(a, b)
    }

    @Test fun restrainedShapeRolesKeepStatusPillsAndTheOriginalResearchControl() {
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(10.dp), FieldAtlasButtonShape)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(16.dp), FieldAtlasLayout.CardShape)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), FieldAtlasLayout.SheetShape)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(50), FieldAtlasChipShape)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(18.dp), FieldAtlasLayout.ResearchButtonShape)
        assertEquals(20.sp, FieldAtlasSheetTitleStyle.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontWeight(650), FieldAtlasSheetTitleStyle.fontWeight)
    }

    @Test fun modernPaletteKeepsTheApprovedDarkMintAndReadableActions() {
        assertEquals(Color(0xFFE8E9DE), fieldAtlasColorScheme(false).surfaceVariant)
        assertEquals(Color(0xFFA6D8B9), fieldAtlasColorScheme(true).primary)
        for (dark in listOf(false, true)) {
            val colors = fieldAtlasColorScheme(dark)
            assertTrue(contrastRatio(colors.primary, colors.onPrimary) >= 4.5f)
        }
    }

    @Test fun originalResearchButtonTypographyIsIndependentOfNewInterfaceFont() {
        assertEquals(androidx.compose.ui.text.font.FontFamily.Default, FieldAtlasResearchButtonStyle.fontFamily)
        assertEquals(androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp), FieldAtlasResearchButtonStyle.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, FieldAtlasResearchButtonStyle.fontWeight)
        assertEquals(androidx.compose.ui.unit.Dp(48f), FieldAtlasLayout.MinimumTouchTarget)
    }
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

    @Test
    fun containerSurfacesStayOnThePaperPalette() {
        // Dialogs, menus and switch tracks read these; baseline Material values are lavender.
        for (dark in listOf(false, true)) {
            val colors = fieldAtlasColorScheme(dark)
            listOf(colors.surfaceContainerLowest, colors.surfaceContainerLow, colors.surfaceContainer,
                colors.surfaceContainerHigh, colors.surfaceContainerHighest, colors.tertiaryContainer).forEach {
                assertTrue("$it leans purple", it.blue <= it.green + 0.01f)
            }
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
