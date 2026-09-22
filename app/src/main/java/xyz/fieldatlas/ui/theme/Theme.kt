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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FieldAtlasColors {
    val LightBackground = Color(0xFFF3EFE5)
    val LightPrimary = Color(0xFF285D49)
    val DarkBackground = Color(0xFF111713)
    val DarkPrimary = Color(0xFFA8D7BD)
}

private val FieldNotebookColors = lightColorScheme(
    primary = FieldAtlasColors.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8DB),
    onPrimaryContainer = Color(0xFF173428),
    secondary = Color(0xFF765B23),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2B9),
    onSecondaryContainer = Color(0xFF30250C),
    tertiary = Color(0xFF5D655B),
    background = FieldAtlasColors.LightBackground,
    onBackground = Color(0xFF25312B),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF25312B),
    surfaceVariant = Color(0xFFE8E2D4),
    onSurfaceVariant = Color(0xFF555E57),
    outline = Color(0xFF817E72),
    outlineVariant = Color(0xFFD5CEBD),
    error = Color(0xFF984A36),
    onError = Color.White,
)

private val AtlasNightColors = darkColorScheme(
    primary = FieldAtlasColors.DarkPrimary,
    onPrimary = Color(0xFF173428),
    primaryContainer = Color(0xFF294D3D),
    onPrimaryContainer = Color(0xFFD7E6DA),
    secondary = Color(0xFFE2B56E),
    onSecondary = Color(0xFF3F2C05),
    secondaryContainer = Color(0xFF57431A),
    onSecondaryContainer = Color(0xFFFFDEA3),
    tertiary = Color(0xFFC0C9BE),
    background = FieldAtlasColors.DarkBackground,
    onBackground = Color(0xFFF2F0E8),
    surface = Color(0xFF1C251F),
    onSurface = Color(0xFFF2F0E8),
    surfaceVariant = Color(0xFF2A372F),
    onSurfaceVariant = Color(0xFFC5CCC5),
    outline = Color(0xFF8B958D),
    outlineVariant = Color(0xFF35463B),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF5F1509),
)

private val FieldAtlasTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)

private val FieldAtlasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun fieldAtlasColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) AtlasNightColors else FieldNotebookColors

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
