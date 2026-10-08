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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import xyz.fieldatlas.R

object FieldAtlasColors {
    val PaperBackground = Color(0xFFF3EFE5)
    val ForestGreen = Color(0xFF285D49)
    val SageWash = Color(0xFFE8E9DE)
    val MenuSelection = Color(0xFF203D34)
    val OnMenuSelection = Color.White
}

/** Bundled so the notebook identity does not depend on an OEM's generic serif fallback. */
@OptIn(ExperimentalTextApi::class)
val FieldAtlasEditorial = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.source_serif_4_variable, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    } + listOf(400, 600, 700).map { weight ->
        Font(R.font.source_serif_4_italic_variable, weight = FontWeight(weight), style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

/** Locally bundled interface type; never needs Google Play services or a font download. */
@OptIn(ExperimentalTextApi::class)
val FieldAtlasSans = FontFamily(listOf(400, 450, 500, 600, 650, 700, 750, 800).map { weight ->
    Font(R.font.manrope_variable, weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})

/** Preserve the original Start research control independently of interface typography. */
val FieldAtlasResearchButtonStyle = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
)

object FieldAtlasLayout {
    val ScreenPadding = 22.dp
    val MinimumTouchTarget = 48.dp
    val ControlShape = RoundedCornerShape(10.dp)
    val CardShape = RoundedCornerShape(16.dp)
    val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    // Kept separate: the user asked to preserve this control exactly.
    val ResearchButtonShape = RoundedCornerShape(18.dp)
}

val FieldAtlasSheetTitleStyle = TextStyle(
    fontFamily = FieldAtlasSans, fontWeight = FontWeight(650),
    fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.4f / 20).em,
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
    surfaceVariant = FieldAtlasColors.SageWash,
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

// Interface uses Manrope; reading surfaces explicitly use the editorial serif.
private val MaterialDefaults = Typography()

private val FieldAtlasTypography = Typography(
    displayLarge = MaterialDefaults.displayLarge.copy(fontFamily = FieldAtlasSans),
    displayMedium = MaterialDefaults.displayMedium.copy(fontFamily = FieldAtlasSans),
    displaySmall = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.Bold,
        fontSize = 39.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.8f / 39).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.4f / 34).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.9f / 27).em,
    ),
    headlineSmall = MaterialDefaults.headlineSmall.copy(fontFamily = FieldAtlasSans, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.7f / 22).em),
    titleLarge = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FieldAtlasSans, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FieldAtlasSans, fontSize = 13.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FieldAtlasSans, fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontFamily = FieldAtlasSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FieldAtlasSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 17.sp),
)

private val FieldAtlasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = FieldAtlasLayout.ControlShape,
    medium = FieldAtlasLayout.CardShape,
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
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
