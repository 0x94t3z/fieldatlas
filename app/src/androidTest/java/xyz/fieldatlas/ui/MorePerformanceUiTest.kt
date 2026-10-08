package xyz.fieldatlas.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.benchmark.*
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.proof.*
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class MorePerformanceUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val question = BenchmarkQuestion(true, "factual", "fact-seasons", emptyList(), "What causes Earth's seasons?", listOf("axial tilt"), "Fixture")
    private val run = BenchmarkRun(runId = "fixture", startedAt = "2026-10-08T00:00:00Z", artifacts = emptyList(),
        diagnosticsSha256 = "a".repeat(64), results = emptyList())

    @Test fun preparingAndRunningKeepCallbacksAndBusyGuards() {
        val inference = mutableStateOf<InferenceState>(InferenceState.Idle)
        val state = mutableStateOf(BenchmarkUiState(run, 18, question))
        var prepares = 0; var runs = 0; var stops = 0; var backs = 0
        compose.setContent { FieldAtlasTheme {
            BenchmarkScreen(state.value, inference.value, { runs++ }, { stops++ }, { prepares++ }, {}, { backs++ })
        } }
        compose.onNodeWithText("Prepare model").performClick()
        compose.runOnIdle { assertEquals(1, prepares); inference.value = InferenceState.Loading }
        compose.onNodeWithText("Run next").assertIsNotEnabled()
        compose.runOnIdle { inference.value = InferenceState.Ready }
        compose.onNodeWithText("Run next").performClick()
        compose.runOnIdle { assertEquals(1, runs); state.value = state.value.copy(running = true, currentAnswer = "Reading the sources.") }
        compose.onNodeWithText("Stop").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun recordedResultsRetainActualStatesAndExportTheOriginalRun() {
        val recorded = run.copy(results = listOf(
            BenchmarkResult(question.id, question.prompt, BenchmarkResultState.COMPLETE, "An answer", listOf("S1", "S2"), null, null),
            BenchmarkResult("other", "Another question", BenchmarkResultState.FAILED, "", emptyList(), null, "Interrupted")))
        var exported: BenchmarkRun? = null
        compose.setContent { FieldAtlasTheme {
            BenchmarkScreen(BenchmarkUiState(recorded, 18, question, error = "Interrupted"), InferenceState.Ready,
                {}, {}, {}, { exported = it }, {})
        } }
        compose.onNodeWithText("2 of 18 recorded").assertExists()
        compose.onNodeWithText("Complete").performScrollTo().assertExists()
        compose.onNodeWithText("Failed").performScrollTo().assertExists()
        compose.onNodeWithText("2 evidence chunks").assertExists()
        compose.onNodeWithText("Export benchmark evidence").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(recorded, exported) }
    }
}
