package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.research.Evidence

class ResearchStepsTest {
    private val passage = Evidence("doc", "doc:0000", "Boat — Overview", "https://en.wikipedia.org/wiki/Boat", "A boat is a watercraft.", 0.0)

    @Test fun searchingShowsKeywordsAndLeavesLaterStepsPending() {
        val steps = researchSteps(ResearchUiState(phase = ResearchPhase.Searching, keywords = listOf("boat", "watercraft")))
        assertEquals(listOf(StepStatus.Active, StepStatus.Pending, StepStatus.Pending), steps.map { it.status })
        assertEquals("Looking for boat, watercraft", steps[0].detail)
        assertEquals("Read the sources", steps[1].title)
    }

    @Test fun aSourceLeadDoesNotCountAsTheModelWriting() {
        // The quoted lead fills the answer before any model token; the model is still reading.
        val steps = researchSteps(ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage),
            answer = "## From the saved reference\n\n> A boat is a watercraft.", promptRead = 300 to 1200))
        assertEquals(listOf(StepStatus.Done, StepStatus.Active, StepStatus.Pending), steps.map { it.status })
        assertEquals("1 passage found", steps[0].detail)
        assertEquals("25% read", steps[1].detail)
        assertEquals(0.25f, steps[1].progress!!, 0.001f)
    }

    @Test fun writingStartsWithTheFirstModelToken() {
        val steps = researchSteps(ResearchUiState(phase = ResearchPhase.Generating, sources = listOf(passage, passage),
            tokensWritten = 12, promptRead = 1200 to 1200))
        assertEquals(listOf(StepStatus.Done, StepStatus.Done, StepStatus.Active), steps.map { it.status })
        assertEquals("2 passages found", steps[0].detail)
        assertNull(steps[1].detail)
        assertEquals("12 tokens written", steps[2].detail)
    }

    @Test fun withoutSourcesTheMiddleStepPreparesTheModel() {
        val steps = researchSteps(ResearchUiState(phase = ResearchPhase.Generating))
        assertEquals("No matching passages", steps[0].detail)
        assertEquals("Prepare the model", steps[1].title)
    }
}
