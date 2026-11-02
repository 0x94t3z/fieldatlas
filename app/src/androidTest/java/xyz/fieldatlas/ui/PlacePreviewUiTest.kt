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

    @Test fun practicalActionsHaveAccessibleNamesAndKeepTheFullSource() {
        var opened = -1
        compose.setContent { FieldAtlasTheme {
            SourcePreviewSheet(listOf(place(), place()), 0, true, 1, {}, { opened = it }, {})
        } }
        // A place in the answer's list is cited by being there; the sheet shows what it is, not a pill.
        compose.onNodeWithText("Cited in this answer").assertDoesNotExist()
        compose.onNodeWithText("Pharmacy").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy address").performScrollTo()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        // With an address to show, coordinates stay in the full source and in Open in maps.
        compose.onNodeWithContentDescription("Copy coordinates").assertDoesNotExist()
        compose.onNodeWithText("Open in maps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Call this place").assertDoesNotExist()
        compose.onNodeWithText("No phone number on record.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next source").assertDoesNotExist()
        compose.onNodeWithText("Full source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, opened) }
    }

    @Test fun darkLargeTextRetainsListingCloseAndOptionalPhoneAction() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SourcePreviewSheet(listOf(place("Phone: +62 123456")), 0, false, null, {}, null, {})
            }
        } }
        compose.onNodeWithText("Provided · not cited").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy address").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Call this place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close preview").assertIsDisplayed()
        compose.onNodeWithText("Example pharmacy").assertIsDisplayed()
        compose.onNodeWithText("Full source").assertDoesNotExist()
    }
}
