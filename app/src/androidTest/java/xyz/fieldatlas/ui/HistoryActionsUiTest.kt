package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.ui.history.HistoryScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Isolated history fixtures; never share or delete the user's actual saved answers. */
class HistoryActionsUiTest {
    @get:Rule val compose = foregroundComposeRule()

    private val question = "Which revision applies to serial 2345?"
    private val record = AnswerRecord(question, "The original saved answer stays unchanged.", 1234L)

    @Test fun tappingATopicContinuesIt() {
        var continued: AnswerRecord? = null
        compose.setContent { FieldAtlasTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HistoryScreen(listOf(record), onContinue = { continued = it })
        } } }
        compose.onNodeWithText(question).performClick()
        compose.runOnIdle { assertEquals(record, continued) }
    }

    @Test fun deleteWaitsForUndoAndUndoKeepsTheTopic() {
        var deleted: Long? = null
        compose.setContent { FieldAtlasTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HistoryScreen(listOf(record), onDelete = { deleted = it })
        } } }
        compose.onNodeWithContentDescription("More for $question").performClick()
        compose.onNodeWithText("Delete topic").performClick()
        compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText(question).assertExists()
    }
}
