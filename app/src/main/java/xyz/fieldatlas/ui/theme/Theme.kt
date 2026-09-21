package xyz.fieldatlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PaperLight = Color(0xFFF4F0E6)
private val InkLight = Color(0xFF20231E)
private val MossLight = Color(0xFF315C49)
private val AmberLight = Color(0xFF8A5B16)
private val RustLight = Color(0xFF984A36)

private val LightColors = lightColorScheme(
    primary = MossLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E6DA),
    onPrimaryContainer = Color(0xFF15251E),
    secondary = AmberLight,
    onSecondary = Color.White,
    background = PaperLight,
    onBackground = InkLight,
    surface = Color(0xFFFAF7EF),
    onSurface = InkLight,
    surfaceVariant = Color(0xFFE4DFD2),
    onSurfaceVariant = Color(0xFF55584F),
    outline = Color(0xFF77796E),
    error = RustLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CEB7),
    onPrimary = Color(0xFF17372A),
    primaryContainer = Color(0xFF294D3D),
    onPrimaryContainer = Color(0xFFD7E6DA),
    secondary = Color(0xFFE2B56E),
    background = Color(0xFF171B17),
    onBackground = Color(0xFFE8E4DA),
    surface = Color(0xFF1D221D),
    onSurface = Color(0xFFE8E4DA),
    surfaceVariant = Color(0xFF343A33),
    onSurfaceVariant = Color(0xFFC4C8BD),
    outline = Color(0xFF8C9186),
    error = Color(0xFFFFB4A5),
)

@Composable
fun FieldAtlasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
