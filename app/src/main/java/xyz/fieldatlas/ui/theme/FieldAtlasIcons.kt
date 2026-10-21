package xyz.fieldatlas.ui.theme

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Support

object FieldAtlasIcons {
    val Compare by prototypeIcon("Compare", "M2 6c2-2 4-2 6 0s4 2 6 0 4-2 8 0M2 12c2-2 4-2 6 0s4 2 6 0 4-2 8 0M2 18c2-2 4-2 6 0s4 2 6 0 4-2 8 0", false, 24f, PathFillType.NonZero)
    val Technology by prototypeIcon("Technology", "M9 17h6v-1c0-2 2-3 2-5a5 5 0 0 0-10 0c0 2 2 3 2 5v1ZM9 20h6M10 23h4M12 1v1M2.5 3.5l1 1M21.5 3.5l-1 1", false, 24f, PathFillType.NonZero)
    val Landmark by prototypeIcon("Landmark", "M3 9l9-6 9 6H3ZM5 10v8m5-8v8m4-8v8m5-8v8M3 18h18M2 21h20", false, 24f, PathFillType.NonZero)
    val Edit by prototypeIcon("Edit", "M4 16l-1 5 5-1L20 8l-4-4L4 16ZM13 7l4 4M16 4l2-2 4 4-2 2", false, 24f, PathFillType.NonZero)
    val ZoomIn by prototypeIcon("ZoomIn", "M16 10a6 6 0 1 1-12 0a6 6 0 1 1 12 0ZM15 15l6 6M7 10h6M10 7v6", false, 24f, PathFillType.NonZero)
    val ZoomOut by prototypeIcon("ZoomOut", "M16 10a6 6 0 1 1-12 0a6 6 0 1 1 12 0ZM15 15l6 6M7 10h6", false, 24f, PathFillType.NonZero)
    val Warning by prototypeIcon("Warning", "M12 3l10 18H2L12 3ZM12 9v5m0 3h.01", false, 24f, PathFillType.NonZero)
    val MoreOptions by prototypeIcon("MoreOptions", "M6.3 12a1.3 1.3 0 1 1-2.6 0a1.3 1.3 0 1 1 2.6 0Z M13.3 12a1.3 1.3 0 1 1-2.6 0a1.3 1.3 0 1 1 2.6 0Z M20.3 12a1.3 1.3 0 1 1-2.6 0a1.3 1.3 0 1 1 2.6 0Z", false, 24f, PathFillType.NonZero)
    val Back by prototypeIcon("Back", "M14 5l-7 7 7 7M7 12h14", false, 24f, PathFillType.NonZero)
    val ChevronRight by prototypeIcon("ChevronRight", "M9 5l7 7 -7 7", false, 24f, PathFillType.NonZero)
    val Research by prototypeIcon("Research", "M16 10a6 6 0 1 1-12 0a6 6 0 1 1 12 0Z M15 15l6 6", false, 24f, PathFillType.NonZero)
    val Library by prototypeIcon("Library", "M4.5 4H6.5a1.5 1.5 0 0 1 1.5 1.5V19.5a1.5 1.5 0 0 1-1.5 1.5H4.5a1.5 1.5 0 0 1-1.5-1.5V5.5a1.5 1.5 0 0 1 1.5-1.5Z M11 4v17m4-17 5 16M3 8h5", false, 24f, PathFillType.NonZero)
    val More by prototypeIcon("More", "M4 7h2m6 0h8M4 17h8m6 0h2 M12 7a3 3 0 1 1-6 0a3 3 0 1 1 6 0Z M18 17a3 3 0 1 1-6 0a3 3 0 1 1 6 0Z", false, 24f, PathFillType.NonZero)
    val History by prototypeIcon("History", "M3 11a9 9 0 1 1 2 7M3 4v7h7M12 7v5l3 2", false, 24f, PathFillType.NonZero)
    val Privacy by prototypeIcon("Privacy", "M12 2l8 3v6c0 5-8 11-8 11S4 16 4 11V5l8-3Z M8 11l3 3 5 -6", false, 24f, PathFillType.NonZero)
    val Performance by prototypeIcon("Performance", "M4 3v18h17M8 17v-5m4 5V7m4 10v-7m4 7V5", false, 24f, PathFillType.NonZero)
    val Diagnostics by prototypeIcon("Diagnostics", "M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5", false, 24f, PathFillType.NonZero)
    val Benchmark by prototypeIcon("Benchmark", "M4 3v18h17M8 17v-5m4 5V7m4 10v-7m4 7V5", false, 24f, PathFillType.NonZero)
    val AttachFile by prototypeIcon("AttachFile", "M8 14l7 -7a3 3 0 0 1 4 4l-9 9a5 5 0 0 1-7-7l9-9", false, 24f, PathFillType.NonZero)
    val Camera by prototypeIcon("Camera", "M8 6l2-3h4l2 3h4a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4Z M16 13a4 4 0 1 1-8 0a4 4 0 1 1 8 0Z", false, 24f, PathFillType.NonZero)
    val Photo by prototypeIcon("Photo", "M6 3H18a3 3 0 0 1 3 3V18a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z M10 8a2 2 0 1 1-4 0a2 2 0 1 1 4 0Z M3 17l5 -5 4 4 4 -7 5 8", false, 24f, PathFillType.NonZero)
    val Check by prototypeIcon("Check", "M4 12l5 5L20 6", false, 24f, PathFillType.NonZero)
    val Citation by prototypeIcon("Citation", "M10 5v3c-2 .3-3 1.4-3 3h2a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6c0-3.7 2.3-5.7 7-6Zm11 0v3c-2 .3-3 1.4-3 3h2a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-6c0-3.7 2.3-5.7 7-6Z", true, 24f, PathFillType.NonZero)
    val Offline by prototypeIcon("Offline", "M18 2h11v1h1v8h-7v2h5v2H18v3h3v2h-2v-1h-2v4h-2v3h2v2h-5v-4h-2v2h1v2H6v-4h2v-2H6v-2H4v-2H2v-6h2v3h2v2h2v-2h2v-2h2v-2h3V8h2V3h1V2Zm2 3v2h2V5h-2Z", true, 32f, PathFillType.EvenOdd)
    // The same dinosaur mid-stride, one foot lifted in each frame: it walks while the phone is online.
    val DinoWalkA by prototypeIcon("DinoWalkA", "M18 2h11v1h-11ZM17 3h13v2h-13ZM17 5h3v2h-3ZM22 5h8v2h-8ZM17 7h13v1h-13ZM15 8h15v3h-15ZM12 11h11v2h-11ZM2 12h2v3h-2ZM10 13h18v2h-18ZM2 15h4v2h-4ZM8 15h10v2h-10ZM2 17h16v1h-16ZM4 18h17v1h-17ZM4 19h13v1h-13ZM19 19h2v1h-2ZM6 20h11v2h-11ZM8 22h9v1h-9ZM8 23h7v1h-7ZM6 24h5v2h-5ZM12 24h3v2h-3ZM12 26h5v2h-5Z", true, 32f, PathFillType.NonZero)
    val DinoWalkB by prototypeIcon("DinoWalkB", "M18 2h11v1h-11ZM17 3h13v2h-13ZM17 5h3v2h-3ZM22 5h8v2h-8ZM17 7h13v1h-13ZM15 8h15v3h-15ZM12 11h11v2h-11ZM2 12h2v3h-2ZM10 13h18v2h-18ZM2 15h4v2h-4ZM8 15h10v2h-10ZM2 17h16v1h-16ZM4 18h17v1h-17ZM4 19h13v1h-13ZM19 19h2v1h-2ZM6 20h11v2h-11ZM8 22h9v1h-9ZM8 23h7v1h-7ZM6 24h4v2h-4ZM12 24h5v2h-5ZM6 26h5v2h-5Z", true, 32f, PathFillType.NonZero)
    val Model by prototypeIcon("Model", "M8 5H16a3 3 0 0 1 3 3V16a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Z M9 1v4m6-4v4M9 19v4m6-4v4M1 9h4m-4 6h4m14-6h4m-4 6h4M9 9h6v6H9z", false, 24f, PathFillType.NonZero)
    val Archive by prototypeIcon("Archive", "M3 3h18v5H3V3Zm2 7h14v11H5V10Zm4 4h6", false, 24f, PathFillType.NonZero)
    val Knowledge by prototypeIcon("Knowledge", "M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Zm0 0v15", false, 24f, PathFillType.NonZero)
    val Place by prototypeIcon("Place", "M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z M14.5 10a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0Z", false, 24f, PathFillType.NonZero)
    val Map by prototypeIcon("Map", "M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15", false, 24f, PathFillType.NonZero)
    // Knowledge collections as the website draws them: stacked layers.
    val Layers by prototypeIcon("Layers", "M3 7l9-5 9 5-9 5Z M3 12l9 5 9-5M3 17l9 5 9-5", false, 24f, PathFillType.NonZero)
    val Collection by prototypeIcon("Collection", "M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Zm0 0v15", false, 24f, PathFillType.NonZero)
    val Database by prototypeIcon("Database", "M20 5a8 3 0 1 1-16 0a8 3 0 1 1 16 0Z M4 5v14c0 1.66 3.58 3 8 3s8-1.34 8-3V5M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3", false, 24f, PathFillType.NonZero)
    val ResearchStart by icon("ResearchStart", "M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2L12 2z")
    val AutoAwesome by icon("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zM9 23l2.5-5.5L17 15l-5.5-2.5L9 7l-2.5 5.5L1 15l5.5 2.5L9 23zm10-8l1.25 2.75L23 19l-2.75 1.25L19 23l-1.25-2.75L15 19l2.75-1.25L19 15z")
    val ResearchSparkles by icon("ResearchSparkles", "M8 7l2.3 5.7L16 15l-5.7 2.3L8 23l-2.3-5.7L0 15l5.7-2.3L8 7zM16 1l1.2 3.8L21 6l-3.8 1.2L16 11l-1.2-3.8L11 6l3.8-1.2L16 1zM20 16l.65 2.35L23 19l-2.35.65L20 22l-.65-2.35L17 19l2.35-.65L20 16z")
    val Food by prototypeIcon("Food", "M5 3v7m3-7v7m-6-7v7h6M5 10v11m10-18v18m0-18c5 2 5 8 0 9", false, 24f, PathFillType.NonZero)
    val Document by prototypeIcon("Document", "M6 3h8l4 4v14H6V3Zm8 0v5h4M9 12h6M9 16h6", false, 24f, PathFillType.NonZero)
    val Share by prototypeIcon("Share", "M12 15V2m-5 5 5-5 5 5M5 12v9h14v-9", false, 24f, PathFillType.NonZero)
    val Copy by prototypeIcon("Copy", "M10 8H19a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z M16 8V3H3v13h5", false, 24f, PathFillType.NonZero)
    val Call by icon("Call", "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z")
    val Close by prototypeIcon("Close", "M6 6l12 12M18 6 6 18", false, 24f, PathFillType.NonZero)
    val OpenExternal by prototypeIcon("OpenExternal", "M6 18 18 6M6 6h12v12", false, 24f, PathFillType.NonZero)
    val Trash by prototypeIcon("Trash", "M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7", false, 24f, PathFillType.NonZero)
    val Sparkle by icon("Sparkle", "M12 2l1.7 5.3L19 9l-5.3 1.7L12 16l-1.7-5.3L5 9l5.3-1.7L12 2zm6 13 .9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9L18 15zM4 16l.6 1.4L6 18l-1.4.6L4 20l-.6-1.4L2 18l1.4-.6L4 16z")
    val Lock by prototypeIcon("Lock", "M7 10H17a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V12a2 2 0 0 1 2-2Z M8 10V7a4 4 0 0 1 8 0v3M12 15v2", false, 24f, PathFillType.NonZero)
    val Info by prototypeIcon("Info", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0Z M12 10v7m0-11h.01", false, 24f, PathFillType.NonZero)

    val Plus by prototypeIcon("Plus", "M12 4v16M4 12h16", false, 24f, PathFillType.NonZero)
    val Mic by prototypeIcon("Mic", "M12 3H12a3 3 0 0 1 3 3V12a3 3 0 0 1-3 3H12a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z M5 10v2a7 7 0 0 0 14 0v-2M12 19v3", false, 24f, PathFillType.NonZero)
    val Code by prototypeIcon("Code", "M8 7l-5 5 5 5m8-10 5 5-5 5m-3-14-2 18", false, 24f, PathFillType.NonZero)
    val Pdf by prototypeIcon("Pdf", "M6 3h8l4 4v14H6V3Zm8 0v5h4 M8 15h8", false, 24f, PathFillType.NonZero)
    val Download by prototypeIcon("Download", "M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5", false, 24f, PathFillType.NonZero)
    val Storage by prototypeIcon("Storage", "M5 4H19a2 2 0 0 1 2 2V8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z M5 14H19a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V16a2 2 0 0 1 2-2Z M17 7h.01M17 17h.01", false, 24f, PathFillType.NonZero)
    val Heart by prototypeIcon("Heart", "M12 21S1 14 2 7c1-5 7-5 10-1 3-4 9-4 10 1 1 7-10 14-10 14Z", false, 24f, PathFillType.NonZero)
    val Globe by prototypeIcon("Globe", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0Z M16 12a4 9 0 1 1-8 0a4 9 0 1 1 8 0Z M3 12h18", false, 24f, PathFillType.NonZero)

    // Collection subjects and utility actions come from Material Icons (already a dependency),
    // not hand-drawn paths.
    val Encyclopedia: ImageVector get() = Icons.Outlined.Public
    val Leaf: ImageVector get() = Icons.Outlined.Eco
    val Storefront: ImageVector get() = Icons.Outlined.Storefront
    val Compass: ImageVector get() = Icons.Outlined.Explore
    val Key: ImageVector get() = Icons.Outlined.Key
    val Microscope: ImageVector get() = Icons.Outlined.Biotech
    val Support: ImageVector get() = Icons.Outlined.Support
    val Import: ImageVector get() = Icons.Outlined.FileOpen

    private fun prototypeIcon(name: String, data: String, filled: Boolean, viewport: Float, rule: PathFillType) = lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = if (filled) 0f else 1.65f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathFillType = rule,
        ).build()
    }

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
