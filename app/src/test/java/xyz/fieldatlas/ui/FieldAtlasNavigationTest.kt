package xyz.fieldatlas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAtlasNavigationTest {
    @Test
    fun startsOnResearchWithoutADetailScreen() {
        val state = FieldAtlasNavigationState()

        assertEquals(PrimaryDestination.Research, state.primary)
        assertNull(state.detail)
    }

    @Test
    fun backTraversesSourceThenAnswerThenPrimaryScreen() {
        val state = FieldAtlasNavigationState()
        state.openAnswer()
        assertTrue(state.openSource(1))

        assertTrue(state.back())
        assertEquals(DetailDestination.Answer, state.detail)
        assertTrue(state.back())
        assertNull(state.detail)
        assertFalse(state.back())
    }

    @Test
    fun selectingAPrimaryDestinationClosesDetails() {
        val state = FieldAtlasNavigationState()
        state.openAnswer()

        state.select(PrimaryDestination.Library)

        assertEquals(PrimaryDestination.Library, state.primary)
        assertNull(state.detail)
    }

    @Test
    fun invalidSourceIndexIsRejectedWithoutChangingTheRoute() {
        val state = FieldAtlasNavigationState()
        state.openAnswer()

        assertFalse(state.openSource(-1))
        assertEquals(DetailDestination.Answer, state.detail)
    }

    @Test
    fun anAnswerOpenedFromHistoryGoesBackToHistory() {
        val state = FieldAtlasNavigationState(PrimaryDestination.History)
        state.select(PrimaryDestination.Research)
        state.openAnswer(from = PrimaryDestination.History)
        // The restored answer finishing opens the page again, without saying where from.
        state.openAnswer()
        assertTrue(state.openSource(0))

        assertTrue(state.back())
        assertEquals(DetailDestination.Answer, state.detail)
        assertTrue(state.back())
        assertEquals(PrimaryDestination.History, state.primary)
        assertNull(state.detail)
        // Once back, a later answer opened on Research goes home again.
        state.select(PrimaryDestination.Research)
        state.openAnswer()
        assertTrue(state.back())
        assertEquals(PrimaryDestination.Research, state.primary)
    }

    @Test
    fun aFollowUpOnAContinuedTopicKeepsTheAnswerInResearch() {
        val state = FieldAtlasNavigationState(PrimaryDestination.Research)
        state.openAnswer(from = PrimaryDestination.History)
        state.keepInResearch()

        assertTrue(state.back())
        assertEquals(PrimaryDestination.Research, state.primary)
    }

    @Test
    fun theReturnToHistorySurvivesARestore() {
        val state = FieldAtlasNavigationState(PrimaryDestination.Research)
        state.openAnswer(from = PrimaryDestination.History)
        val restored = with(FieldAtlasNavigationState.Saver) {
            val saved = androidx.compose.runtime.saveable.SaverScope { true }.save(state)!!
            restore(saved)!!
        }

        assertTrue(restored.back())
        assertEquals(PrimaryDestination.History, restored.primary)
    }
}
