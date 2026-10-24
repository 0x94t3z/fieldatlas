package xyz.fieldatlas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.ui.library.DownloadControl
import xyz.fieldatlas.ui.library.RowActions
import xyz.fieldatlas.ui.library.RowState
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Library's download button is named by what it does, then the size its pill shows, as Setup's buttons are. */
class DownloadButtonLabelUiTest {
    @get:Rule val compose = createComposeRule()

    private fun actions(onGet: () -> Unit) = RowActions(get = onGet, pause = {}, resume = {}, retry = {},
        removeFromLine = {}, useMobileData = {}, dismissFailure = {})

    @Test fun downloadButtonSaysDownloadThenItsSizeAndKeepsItsTouchTarget() {
        var gets = 0
        compose.setContent { FieldAtlasTheme { DownloadControl(RowState.Get(0), "1.4 GB", "Qwen3.5 2B", actions { gets++ }) } }
        compose.onNodeWithContentDescription("Download Qwen3.5 2B, 1.4 GB").assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, gets) }
    }

    @Test fun updateButtonSaysUpdate() {
        var gets = 0
        compose.setContent { FieldAtlasTheme { DownloadControl(RowState.Get(0), "Update", "Encyclopedia", actions { gets++ }) } }
        compose.onNodeWithContentDescription("Update Encyclopedia").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, gets) }
    }
}
