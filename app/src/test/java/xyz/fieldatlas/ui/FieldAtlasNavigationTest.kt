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
}
