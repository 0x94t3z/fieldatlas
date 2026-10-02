package xyz.fieldatlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.R

object FieldAtlasColors {
    val PaperBackground = Color(0xFFF3EFE5)
    val ForestGreen = Color(0xFF285D49)
    val SageWash = Color(0xFFE8E9DE)
    val MenuSelection = Color(0xFF203D34)
    val OnMenuSelection = Color.White
}

/** Bundled so the notebook identity does not depend on an OEM's generic serif fallback. */
val FieldAtlasEditorial = FontFamily(
    Font(R.font.source_serif_4_variable, weight = FontWeight.Normal),
    Font(R.font.source_serif_4_variable, weight = FontWeight.Medium),
    Font(R.font.source_serif_4_variable, weight = FontWeight.SemiBold),
    Font(R.font.source_serif_4_variable, weight = FontWeight.Bold),
    Font(R.font.source_serif_4_italic_variable, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(R.font.source_serif_4_italic_variable, weight = FontWeight.SemiBold, style = FontStyle.Italic),
)

/**
 * Reading text and interface labels: Inter, a screen face with a tall x-height and open
 * letters that stays clear at small sizes. Bundled so answers read the same on every phone
 * rather than in an OEM default.
 */
@OptIn(ExperimentalTextApi::class)
val FieldAtlasSans = FontFamily(
    sansFont(FontWeight.Normal),
    sansFont(FontWeight.Medium),
    sansFont(FontWeight.SemiBold),
    sansFont(FontWeight.Bold),
    sansFont(FontWeight.Normal, FontStyle.Italic),
    sansFont(FontWeight.SemiBold, FontStyle.Italic),
)

// A variable file draws its default instance unless the wght axis is set for each weight.
@OptIn(ExperimentalTextApi::class)
private fun sansFont(weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    if (style == FontStyle.Italic) R.font.inter_italic_variable else R.font.inter_variable,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val FieldNotebookColors = lightColorScheme(
    primary = FieldAtlasColors.ForestGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8DB),
    onPrimaryContainer = Color(0xFF173428),
    secondary = Color(0xFF765B23),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2B9),
    onSecondaryContainer = Color(0xFF30250C),
    tertiary = Color(0xFF5D655B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E7DD),
    onTertiaryContainer = Color(0xFF1B231C),
    background = FieldAtlasColors.PaperBackground,
    onBackground = Color(0xFF25312B),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF25312B),
    surfaceVariant = Color(0xFFE8E2D4),
    onSurfaceVariant = Color(0xFF555E57),
    surfaceTint = FieldAtlasColors.ForestGreen,
    // Material derives dialogs, menus, sheets and switch tracks from these containers; left
    // unset they fall back to the baseline lavender palette and break the paper notebook.
    surfaceBright = Color(0xFFFFFDF7),
    surfaceDim = Color(0xFFE5DFD2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F1),
    surfaceContainer = Color(0xFFF7F3EA),
    surfaceContainerHigh = Color(0xFFF3EEE3),
    surfaceContainerHighest = Color(0xFFEAE4D7),
    inverseSurface = Color(0xFF2F3A33),
    inverseOnSurface = Color(0xFFEFEDE4),
    inversePrimary = Color(0xFFA6D8B9),
    outline = Color(0xFF817E72),
    outlineVariant = Color(0xFFD5CEBD),
    error = Color(0xFF984A36),
    onError = Color.White,
    errorContainer = Color(0xFFF6DED6),
    onErrorContainer = Color(0xFF3E130A),
)

private val FieldNotebookDarkColors = darkColorScheme(
    primary = Color(0xFFA6D8B9),
    onPrimary = Color(0xFF123526),
    primaryContainer = Color(0xFF244F3A),
    onPrimaryContainer = Color(0xFFD6EBDD),
    secondary = Color(0xFFE5C98A),
    onSecondary = Color(0xFF3A2D08),
    secondaryContainer = Color(0xFF55441A),
    onSecondaryContainer = Color(0xFFFFE4A7),
    tertiary = Color(0xFFC5CEC1),
    onTertiary = Color(0xFF2B322A),
    tertiaryContainer = Color(0xFF3A4339),
    onTertiaryContainer = Color(0xFFE1E9DC),
    background = Color(0xFF111A15),
    onBackground = Color(0xFFE7E9E1),
    surface = Color(0xFF16231C),
    onSurface = Color(0xFFE7E9E1),
    surfaceVariant = Color(0xFF2A3830),
    onSurfaceVariant = Color(0xFFC4CDC4),
    surfaceTint = Color(0xFFA6D8B9),
    surfaceBright = Color(0xFF2E3C33),
    surfaceDim = Color(0xFF0E1611),
    surfaceContainerLowest = Color(0xFF0B120E),
    surfaceContainerLow = Color(0xFF141F19),
    surfaceContainer = Color(0xFF18251E),
    surfaceContainerHigh = Color(0xFF1F2D25),
    surfaceContainerHighest = Color(0xFF29382F),
    inverseSurface = Color(0xFFE7E9E1),
    inverseOnSurface = Color(0xFF25312B),
    inversePrimary = FieldAtlasColors.ForestGreen,
    outline = Color(0xFF8B988D),
    outlineVariant = Color(0xFF3C4B41),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF5D1509),
    errorContainer = Color(0xFF6E2A1C),
    onErrorContainer = Color(0xFFFFDAD2),
)

// Material's defaults with every style set to a bundled family. Inter's tall x-height reads
// well a point below where a smaller-eyed face such as Source Sans would need to be.
private val MaterialDefaults = Typography()

private val FieldAtlasTypography = Typography(
    displayLarge = MaterialDefaults.displayLarge.copy(fontFamily = FieldAtlasEditorial),
    displayMedium = MaterialDefaults.displayMedium.copy(fontFamily = FieldAtlasEditorial),
    displaySmall = TextStyle(
        fontFamily = FieldAtlasEditorial,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FieldAtlasEditorial,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FieldAtlasEditorial,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    ),
    headlineSmall = MaterialDefaults.headlineSmall.copy(fontFamily = FieldAtlasEditorial, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FieldAtlasSans, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FieldAtlasSans, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FieldAtlasSans, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val FieldAtlasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun fieldAtlasColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) FieldNotebookDarkColors else FieldNotebookColors

@Composable
fun FieldAtlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = fieldAtlasColorScheme(darkTheme),
        typography = FieldAtlasTypography,
        shapes = FieldAtlasShapes,
        content = content,
    )
}
