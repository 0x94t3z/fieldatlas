package xyz.fieldatlas.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.MainActivity
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Real keyboard coverage: injected text alone does not show whether the IME covers the composer. */
class AnswerKeyboardUiTest {
    @get:Rule val compose = foregroundComposeRule()

    @Test fun mainActivityResizesInsteadOfPanningForTheKeyboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            activity.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
    }

    @Test fun keyboardKeepsInputVisibleAndTheWholeAnswerReachable() = checkKeyboard(fontScale = 1f)

    @Test fun keyboardKeepsInputVisibleAndTheWholeAnswerReachableAtLargeText() = checkKeyboard(fontScale = 2f)

    // Before Android 15 the app is not edge-to-edge (the Redmi 13C runs Android 13): the window itself resizes and
    // Compose sees no keyboard insets, so imePadding adds nothing. The same promises must hold on that path.
    @Test fun keyboardKeepsInputVisibleOnAWindowThatFitsTheSystemBars() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
        checkKeyboard(fontScale = 1f, edgeToEdge = false)
    }

    @Test fun keyboardKeepsInputVisibleOnAWindowThatFitsTheSystemBarsAtLargeText() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
        checkKeyboard(fontScale = 2f, edgeToEdge = false)
    }

    private fun checkKeyboard(fontScale: Float, edgeToEdge: Boolean = true) {
        lateinit var host: Activity
        var draft by mutableStateOf("")
        val sent = mutableListOf<String>()
        val question = "How do ocean tides work?"
        val state = ResearchUiState(question = question, phase = ResearchPhase.Complete,
            answer = (1..20).joinToString("\n\n") {
                "Passage $it. This long answer keeps the page scrollable while a follow-up is typed. [S1]"
            },
            sources = listOf(Evidence("tides", "tides:1", "Tides reference", "Saved source", "A saved passage about tides.", 1.0)),
            metrics = ResearchMetrics(10, 20, 4_000, 20, setOf("S1"), false))
        compose.setContent {
            host = LocalContext.current.activity()
            FieldAtlasTheme(darkTheme = fontScale > 1f) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    AnswerScreen(state, onBack = {}, onAskAnother = {}, onCitation = {},
                        followUpDraft = draft, onFollowUpDraftChange = { draft = it }, onFollowUp = { sent += it })
                }
            }
        }
        // The ComponentActivity fixture does not inherit MainActivity's manifest settings. Exercise the same
        // resize mode, with edge-to-edge insets as on Android 15+ or without, and check the actual manifest separately.
        compose.runOnUiThread {
            if (edgeToEdge) WindowCompat.setDecorFitsSystemWindows(host.window, false)
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.waitForIdle()
        val backTop = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInWindow.top
        val input = compose.onNodeWithContentDescription("Follow-up question")
        try {
            input.performClick()
            compose.runOnUiThread { WindowInsetsControllerCompat(host.window, host.window.decorView).show(WindowInsetsCompat.Type.ime()) }
            compose.waitUntil(10_000) { keyboardVisible(host) }
            input.performTextInput("Please explain\nthe main causes\nin simple terms.")
            compose.waitForIdle()
            assertAboveKeyboard(host, "Follow-up question")
            assertAboveKeyboard(host, "Ask follow-up")
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            assertEquals("The keyboard must not pan the top bar off screen", backTop,
                compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInWindow.top, 2f)

            val answer = compose.onNode(hasScrollToNodeAction())
            val scrollBefore = answer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            answer.performTouchInput { swipeUp() }
            compose.waitForIdle()
            assertTrue("A swipe on the answer must scroll while the composer has focus",
                answer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > scrollBefore)
            answer.performScrollToNode(hasText("Answer details"))
            compose.onNodeWithText("Answer details").assertIsDisplayed()
            assertTrue("Scrolling must leave the keyboard open", keyboardVisible(host))
            assertAboveKeyboard(host, "Follow-up question")
            answer.performScrollToIndex(0)
            compose.onNodeWithText(question).assertIsDisplayed()
            assertTrue("The top of the answer must be reachable while typing", keyboardVisible(host))
            assertAboveKeyboard(host, "Ask follow-up")
            capture("answer-keyboard-open-$fontScale-${if (edgeToEdge) "edge" else "fits"}")
            compose.onNodeWithContentDescription("Ask follow-up").performClick()
            compose.runOnIdle { assertEquals(listOf("Please explain\nthe main causes\nin simple terms."), sent) }

            compose.runOnUiThread { WindowInsetsControllerCompat(host.window, host.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
            compose.waitUntil(10_000) { !keyboardVisible(host) }
            input.assertTextContains("Please explain\nthe main causes\nin simple terms.")
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            answer.performScrollToNode(hasText("Answer details"))
            compose.onNodeWithText("Answer details").assertIsDisplayed()
        } finally {
            capture("answer-keyboard-$fontScale-${if (edgeToEdge) "edge" else "fits"}")
            compose.runOnUiThread { WindowInsetsControllerCompat(host.window, host.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
        }
    }

    private fun keyboardVisible(host: Activity): Boolean =
        ViewCompat.getRootWindowInsets(host.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun assertAboveKeyboard(host: Activity, description: String) {
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(host.window.decorView))
        assertTrue("A real keyboard must be visible", insets.isVisible(WindowInsetsCompat.Type.ime()))
        val keyboardTop = host.windowManager.currentWindowMetrics.bounds.height() - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val node = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("$description overlaps the keyboard: ${node.boundsInWindow.bottom} > $keyboardTop",
            node.boundsInWindow.bottom <= keyboardTop)
        assertTrue("$description must not be clipped", node.boundsInWindow.height >= node.size.height - 1)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "keyboard-check").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private tailrec fun Context.activity(): Activity = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> error("The keyboard fixture needs an Activity")
    }
}
