package xyz.fieldatlas.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.research.SourcePreviewSheet
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Synthetic listings only: these tests never dial or launch another maps app. */
class PlacePreviewUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private fun place(phone: String = "") = Evidence("place", "place:0", "Saved listing", "openstreetmap.org",
        "Destination: Cirebon\nPlace: Example pharmacy\nType: pharmacy\nAddress: 45475 Majalengka\n" +
            "Latitude: -6.75490\nLongitude: 108.31648\n$phone", 1.0)

    @Test fun practicalActionsHaveAccessibleNamesAndKeepSourceNavigation() {
        var opened = -1
        var next = -1
        compose.setContent { FieldAtlasTheme {
            SourcePreviewSheet(listOf(place(), place()), 0, true, 1, {}, { opened = it }, { next = it })
        } }
        compose.onNodeWithText("Cited in this answer").assertIsDisplayed()
        listOf("Copy address", "Copy coordinates").forEach { label ->
            compose.onNodeWithContentDescription(label).performScrollTo()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Open in maps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Call this place").assertDoesNotExist()
        compose.onNodeWithText("Next source").performScrollTo().performClick()
        compose.onNodeWithText("Full source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, next); assertEquals(0, opened) }
    }

    @Test fun darkLargeTextRetainsListingCloseAndOptionalPhoneAction() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SourcePreviewSheet(listOf(place("Phone: +62 123456")), 0, false, null, {}, null, {})
            }
        } }
        compose.onNodeWithText("Provided · not cited").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy coordinates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Call this place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close preview").assertIsDisplayed()
        compose.onNodeWithText("Example pharmacy").assertIsDisplayed()
        compose.onNodeWithText("Full source").assertDoesNotExist()
    }
}
