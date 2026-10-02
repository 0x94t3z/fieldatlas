package xyz.fieldatlas.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object FieldAtlasIcons {
    val Back by icon("Back", "M20 11H7.83L13.42 5.41 12 4l-8 8 8 8 1.42-1.41L7.83 13H20v-2z")
    val ChevronRight by icon("ChevronRight", "M9 5l7 7-7 7-1.41-1.41L13.17 12 7.59 6.41 9 5z")
    val Research by icon("Research", "M9.5 3a6.5 6.5 0 1 0 3.98 11.64L19.85 21 21 19.85l-6.36-6.37A6.5 6.5 0 0 0 9.5 3zm0 2a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z")
    val Library by icon("Library", "M4 4.5C4 3.67 4.67 3 5.5 3H11v15H6a2 2 0 0 0-2 2V4.5zM13 3h5.5c.83 0 1.5.67 1.5 1.5V20a2 2 0 0 0-2-2h-5V3z")
    val More by icon("More", "M5 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z")
    val History by icon("History", "M13 3a9 9 0 0 0-9 9H1l4 4 4-4H6a7 7 0 1 1 2.05 4.96l-1.44 1.42A9 9 0 1 0 13 3zm-1 5v6l5.25 3.15.75-1.23-4.5-2.67V8h-1.5z")
    val Privacy by icon("Privacy", "M12 2 4 5v6c0 5.05 3.41 9.74 8 11 4.59-1.26 8-5.95 8-11V5l-8-3zm0 2.18L18 6.43V11c0 3.92-2.5 7.69-6 8.86C8.5 18.69 6 14.92 6 11V6.43l6-2.25zm-1 10.41-2.3-2.3-1.4 1.42L11 17.41l5.7-5.7-1.4-1.42-4.3 4.3z")
    val Performance by icon("Performance", "M12 4a9 9 0 0 0-9 9c0 2.39.93 4.68 2.59 6.36L7 17.95A6.97 6.97 0 0 1 5 13a7 7 0 1 1 12 4.95l1.41 1.41A9 9 0 0 0 12 4zm4.24 4.34-5.66 3.17a2 2 0 1 0 1.91 1.91l3.17-5.66-.42.58z")
    val Diagnostics by icon("Diagnostics", "M6 2h8l4 4v5h-2V7h-3V4H6v16h6v2H6a2 2 0 0 1-2-2V4c0-1.1.9-2 2-2zm11 11v4.17l1.59-1.58L20 17l-4 4-4-4 1.41-1.41L15 17.17V13h2z")
    val Benchmark by icon("Benchmark", "M4 19h16v2H2V3h2v16zm3-2H5v-5h2v5zm4 0H9V7h2v10zm4 0h-2V9h2v8zm4 0h-2V4h2v13z")
    val Import by icon("Import", "M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3zM5 19h14v2H5v-2z")
    val AttachFile by icon("AttachFile", "M16.5 6.5 9.41 13.59a2 2 0 1 0 2.83 2.83l7.09-7.09a4 4 0 0 0-5.66-5.66l-8.5 8.5a6 6 0 0 0 8.49 8.49l7.08-7.08-1.41-1.42-7.08 7.09a4 4 0 0 1-5.66-5.66l8.5-8.5a2 2 0 1 1 2.83 2.83l-7.09 7.08a.01.01 0 0 1-.01 0 .01.01 0 0 1 0-.01l7.08-7.09 1.42 1.42z")
    val Camera by icon("Camera", "M9 4 7.2 6H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3.2L15 4H9zm3 5a4 4 0 1 1 0 8 4 4 0 0 1 0-8zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4z")
    val Photo by icon("Photo", "M4 3h16a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm0 2v14h16V5H4zm3.5 3a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm-2 9 4-4 2.5 2.5 3.5-4.5 4.5 6H5.5z")
    val Check by icon("Check", "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z")
    val Citation by icon("Citation", "M7.5 6A3.5 3.5 0 0 0 4 9.5V14a3 3 0 0 0 3 3h3v-6H6V9.5C6 8.67 6.67 8 7.5 8H10V6H7.5zm9 0A3.5 3.5 0 0 0 13 9.5V14a3 3 0 0 0 3 3h3v-6h-4V9.5c0-.83.67-1.5 1.5-1.5H19V6h-2.5z")
    // A crossed-out wifi reads as "no connection" at a glance; the earlier glyph did not.
    val Offline: ImageVector get() = Icons.Outlined.WifiOff
    val Model by icon("Model", "M12 2 2 7v10l10 5 10-5V7L12 2zm0 2.24L19.76 8 12 11.88 4.24 8 12 4.24zM4 9.62l7 3.5v6.26l-7-3.5V9.62zm9 9.76v-6.26l7-3.5v6.26l-7 3.5z")
    val Archive by icon("Archive", "M4 4h16v4H4V4zm1 6h14v10H5V10zm3 2v2h8v-2H8z")
    val Knowledge by icon("Knowledge", "M20.5 3.5C12 3.4 5 6 3.5 12.5 2.6 16.4 5.2 20 9 20c7.6 0 11.7-9.5 11.5-16.5zM9 18c-2.7 0-4.1-2.1-3.5-4.8C6.6 8.9 11.5 6.2 18 5.6c-1 6.4-4.2 12.4-9 12.4zm-.8-1.5c3-4.1 5.6-6.1 8.4-7.8-4.3 1.1-7.6 3.7-10 7.2l1.6.6z")
    val Place by icon("Place", "M12 2a8 8 0 0 0-8 8c0 5.4 8 12 8 12s8-6.6 8-12a8 8 0 0 0-8-8zm0 2a6 6 0 0 1 6 6c0 3.6-4 7.7-6 9.5C10 17.7 6 13.6 6 10a6 6 0 0 1 6-6zm0 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val Collection by icon("Collection", "M3 5.5 9 3l6 2.5L21 3v15.5L15 21l-6-2.5L3 21V5.5zm2 1.3v11.3l3-1.25V5.55L5 6.8zm5-1.25v11.3l4 1.67V7.22L10 5.55zm6 1.67v11.3l3-1.25V5.97l-3 1.25z")
    val Database by icon("Database", "M4 5c0-1.66 3.58-3 8-3s8 1.34 8 3v14c0 1.66-3.58 3-8 3s-8-1.34-8-3V5zm2 0c0 .55 2.24 1.5 6 1.5s6-.95 6-1.5-2.24-1.5-6-1.5S6 4.45 6 5zm0 4c0 .55 2.24 1.5 6 1.5s6-.95 6-1.5V7.3c-1.42.75-3.62 1.2-6 1.2s-4.58-.45-6-1.2V9zm0 5c0 .55 2.24 1.5 6 1.5s6-.95 6-1.5v-2c-1.42.75-3.62 1.2-6 1.2s-4.58-.45-6-1.2v2zm0 5c0 .55 2.24 1.5 6 1.5s6-.95 6-1.5v-2c-1.42.75-3.62 1.2-6 1.2s-4.58-.45-6-1.2v2z")
    val ResearchStart by icon("ResearchStart", "M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2L12 2z")
    val AutoAwesome by icon("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zM9 23l2.5-5.5L17 15l-5.5-2.5L9 7l-2.5 5.5L1 15l5.5 2.5L9 23zm10-8l1.25 2.75L23 19l-2.75 1.25L19 23l-1.25-2.75L15 19l2.75-1.25L19 15z")
    val ResearchSparkles by icon("ResearchSparkles", "M8 7l2.3 5.7L16 15l-5.7 2.3L8 23l-2.3-5.7L0 15l5.7-2.3L8 7zM16 1l1.2 3.8L21 6l-3.8 1.2L16 11l-1.2-3.8L11 6l3.8-1.2L16 1zM20 16l.65 2.35L23 19l-2.35.65L20 22l-.65-2.35L17 19l2.35-.65L20 16z")
    val Food by icon("Food", "M6 2v8c0 1.66 1.34 3 3 3v9h2v-9c1.66 0 3-1.34 3-3V2h-2v6H11V2H9v6H8V2H6zm11 0v20h2v-9h2V7c0-2.76-1.79-5-4-5z")
    val Document by icon("Document", "M5 2h9l5 5v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zm1 2v16h11V9h-5V4H6zm8 0v3h3l-3-3zM8 12h7v2H8v-2zm0 4h7v2H8v-2z")
    val Sparkle by icon("Sparkle", "M12 2l1.7 5.3L19 9l-5.3 1.7L12 16l-1.7-5.3L5 9l5.3-1.7L12 2zm6 13 .9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9L18 15zM4 16l.6 1.4L6 18l-1.4.6L4 20l-.6-1.4L2 18l1.4-.6L4 16z")
    val Lock by icon("Lock", "M17 9h-1V7a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zM10 7a2 2 0 0 1 4 0v2h-4V7zm7 13H7v-9h10v9zm-5-7a1.5 1.5 0 0 0-1 2.62V18h2v-2.38A1.5 1.5 0 0 0 12 13z")
    val Info by icon("Info", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16zm-1 6h2v7h-2v-7zm0-3h2v2h-2V7z")

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
