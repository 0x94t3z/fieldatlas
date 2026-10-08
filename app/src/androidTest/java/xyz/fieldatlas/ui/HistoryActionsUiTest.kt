package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.ui.history.HistoryActions
import xyz.fieldatlas.ui.history.HistoryScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Isolated history fixtures; never share or delete the user's actual saved answers. */
class HistoryActionsUiTest {
    @get:Rule val compose = foregroundComposeRule()

    @Test fun compactActionsAlignAndKeepSeparateCallbacksAndFullTapTargets() {
        var asked = 0; var shared = 0; var deleted = 0
        compose.setContent { FieldAtlasTheme {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.width(300.dp)) { HistoryActions({ asked++ }, { shared++ }, { deleted++ }) }
            }
        } }
        val ask = compose.onNodeWithText("Ask again")
        val share = compose.onNodeWithContentDescription("Share answer")
        val delete = compose.onNodeWithContentDescription("Delete from History")
        listOf(ask, share, delete).forEach { it.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp) }
        val a = ask.fetchSemanticsNode().boundsInRoot
        val s = share.fetchSemanticsNode().boundsInRoot
        val d = delete.fetchSemanticsNode().boundsInRoot
        assertEquals(a.center.y, s.center.y, 2f)
        assertEquals(s.center.y, d.center.y, 2f)
        assertTrue(a.right <= s.left && s.right < d.left)
        ask.performClick(); share.performClick(); delete.performClick()
        compose.runOnIdle { assertEquals(1, asked); assertEquals(1, shared); assertEquals(1, deleted) }
    }

    @Test fun darkNarrowActionsReflowAtDoubleTextSize() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.width(240.dp)) { HistoryActions({}, {}, {}) }
                }
            }
        } }
        val ask = compose.onNodeWithText("Ask again").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val share = compose.onNodeWithContentDescription("Share answer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithContentDescription("Delete from History").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(share.top >= ask.bottom)
        assertTrue(delete.right <= ask.right)
    }

    @Test fun historyReusesTheExactQuestionAndRequiresDeleteConfirmation() {
        val question = "Which revision applies to serial 2345?"
        val answer = "The original saved answer stays unchanged."
        var draft = ""; var deleted: Long? = null
        val record = AnswerRecord(question, answer, 1234L)
        compose.setContent { FieldAtlasTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                HistoryScreen(listOf(record), { draft = it }, { deleted = it })
            }
        } }
        compose.onNodeWithText(question).performClick()
        compose.onNodeWithText("Ask again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(question, draft); assertNull(deleted) }
        compose.onNodeWithText(answer).assertExists()
        compose.onNodeWithContentDescription("Delete from History").performScrollTo().performClick()
        compose.onNodeWithText("Delete this answer?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithContentDescription("Delete from History").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1234L, deleted) }
    }
}
