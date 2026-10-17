package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailObstacleTest {
    @Test fun eachCollectionCrossesFromTheAnswerEndToTheStartInTurn() {
        val (first, start) = obstacleAt(0, 3)!!
        assertEquals(0, first)
        assertTrue(start > 1f)
        val (_, end) = obstacleAt(3_199, 3)!!
        assertTrue(end < 0f)
        // A short gap, then the next collection; after the last, the first again.
        assertNull(obstacleAt(3_500, 3))
        assertEquals(1, obstacleAt(3_800, 3)!!.first)
        assertEquals(0, obstacleAt(3 * 3_800L, 3)!!.first)
    }

    @Test fun withNothingToShowNothingCrosses() {
        assertNull(obstacleAt(1_000, 0))
        assertNull(obstacleAt(-1, 3))
    }

    @Test fun theDinoHopsOnlyAsACollectionPassesIt() {
        assertEquals(1f, hopHeight(0.5f, 0.5f), 0f)
        assertTrue(hopHeight(0.53f, 0.5f) in 0.01f..0.99f)
        assertEquals(0f, hopHeight(0.7f, 0.5f), 0f)
    }
}
