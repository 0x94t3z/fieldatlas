package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchProgressHeadingTest {
    @Test fun readingSourcesUntilVisibleAnswerStarts() {
        assertEquals("Reading sources…", researchProgressHeading(ResearchPhase.Generating, "", true))
        assertEquals("Reading sources…", researchProgressHeading(ResearchPhase.Generating, "  ", true))
        assertEquals("Writing your answer…", researchProgressHeading(ResearchPhase.Generating, "Mitosis", true))
        assertEquals("Preparing your answer…", researchProgressHeading(ResearchPhase.Generating, "", false))
    }

    @Test fun retrievalAndPlanningKeepDistinctLabels() {
        assertEquals("Searching saved sources…", researchProgressHeading(ResearchPhase.Searching, "", true))
        assertEquals("Understanding your question…", researchProgressHeading(ResearchPhase.Planning, "", true))
    }
}
