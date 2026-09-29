package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.ui.research.ResearchQuestionPanel
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class ResearchQuestionPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun questionKeepsItsWidthWhenEditingBecomesAvailable() {
        val ready = mutableStateOf(false)
        val question = "Compare the history and culture of Japan with neighbouring countries."
        var edited = false
        compose.setContent {
            FieldAtlasTheme {
                Box(Modifier.width(320.dp)) {
                    ResearchQuestionPanel(question, if (ready.value) ({ edited = true }) else null)
                }
            }
        }
        compose.onNodeWithText("Your question").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit question").assertDoesNotExist()
        val before = compose.onNodeWithText(question).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { ready.value = true }
        val after = compose.onNodeWithText(question).fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithContentDescription("Edit question").performClick()
        compose.runOnIdle { assertEquals(true, edited) }
    }
}
