package xyz.fieldatlas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object FieldAtlasIcons {
    val Back by icon("Back", "M20 11H7.83L13.42 5.41 12 4l-8 8 8 8 1.42-1.41L7.83 13H20v-2z")
    val Research by icon("Research", "M9.5 3a6.5 6.5 0 1 0 3.98 11.64L19.85 21 21 19.85l-6.36-6.37A6.5 6.5 0 0 0 9.5 3zm0 2a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z")
    val Library by icon("Library", "M4 4.5C4 3.67 4.67 3 5.5 3H11v15H6a2 2 0 0 0-2 2V4.5zM13 3h5.5c.83 0 1.5.67 1.5 1.5V20a2 2 0 0 0-2-2h-5V3z")
    val More by icon("More", "M5 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z")
    val Privacy by icon("Privacy", "M12 2 4 5v6c0 5.05 3.41 9.74 8 11 4.59-1.26 8-5.95 8-11V5l-8-3zm0 2.18L18 6.43V11c0 3.92-2.5 7.69-6 8.86C8.5 18.69 6 14.92 6 11V6.43l6-2.25zm-1 10.41-2.3-2.3-1.4 1.42L11 17.41l5.7-5.7-1.4-1.42-4.3 4.3z")
    val Performance by icon("Performance", "M12 4a9 9 0 0 0-9 9c0 2.39.93 4.68 2.59 6.36L7 17.95A6.97 6.97 0 0 1 5 13a7 7 0 1 1 12 4.95l1.41 1.41A9 9 0 0 0 12 4zm4.24 4.34-5.66 3.17a2 2 0 1 0 1.91 1.91l3.17-5.66-.42.58z")
    val Diagnostics by icon("Diagnostics", "M6 2h8l4 4v5h-2V7h-3V4H6v16h6v2H6a2 2 0 0 1-2-2V4c0-1.1.9-2 2-2zm11 11v4.17l1.59-1.58L20 17l-4 4-4-4 1.41-1.41L15 17.17V13h2z")
    val Benchmark by icon("Benchmark", "M4 19h16v2H2V3h2v16zm3-2H5v-5h2v5zm4 0H9V7h2v10zm4 0h-2V9h2v8zm4 0h-2V4h2v13z")
    val Information by icon("Information", "M11 10h2v8h-2v-8zm0-4h2v2h-2V6zm1-4a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16z")
    val Import by icon("Import", "M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3zM5 19h14v2H5v-2z")
    val Citation by icon("Citation", "M7.5 6A3.5 3.5 0 0 0 4 9.5V14a3 3 0 0 0 3 3h3v-6H6V9.5C6 8.67 6.67 8 7.5 8H10V6H7.5zm9 0A3.5 3.5 0 0 0 13 9.5V14a3 3 0 0 0 3 3h3v-6h-4V9.5c0-.83.67-1.5 1.5-1.5H19V6h-2.5z")

    private fun icon(name: String, pathData: String) = lazy {
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }
}
