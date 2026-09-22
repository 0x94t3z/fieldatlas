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
    val PaperBackground = Color(0xFFF3EFE5)
    val ForestGreen = Color(0xFF285D49)
    val SageWash = Color(0xFFE8E9DE)
}

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
    background = FieldAtlasColors.PaperBackground,
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
    background = Color(0xFF111A15),
    onBackground = Color(0xFFE7E9E1),
    surface = Color(0xFF16231C),
    onSurface = Color(0xFFE7E9E1),
    surfaceVariant = Color(0xFF2A3830),
    onSurfaceVariant = Color(0xFFC4CDC4),
    outline = Color(0xFF8B988D),
    outlineVariant = Color(0xFF3C4B41),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF5D1509),
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
