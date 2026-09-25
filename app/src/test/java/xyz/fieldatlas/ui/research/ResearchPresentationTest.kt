package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.ResearchMetrics

class ResearchPresentationTest {
    @Test fun formatsMeasuredRateWithoutInventingMissingValues() {
        val measured = formatResearchMetrics(metrics(totalMillis = 2_000, tokens = 10), sourceCount = 2)
        assertEquals("5.0 tok/s", measured.tokenRate)
        assertEquals("2 of 2 sources cited", measured.citationCoverage)

        val missing = formatResearchMetrics(metrics(totalMillis = 0, tokens = 10), sourceCount = 2)
        assertNull(missing.tokenRate)

        val modelOnly = formatResearchMetrics(metrics(totalMillis = 1_000, tokens = 10), sourceCount = 0)
        assertEquals("No local sources cited", modelOnly.citationCoverage)
    }

    @Test fun exposesUnmappedCitationAsAWarningInsteadOfAConfidenceClaim() {
        val model = formatResearchMetrics(
            metrics(totalMillis = 1_000, tokens = 4, unmapped = true),
            sourceCount = 1,
        )
        assertTrue(model.hasUnmappedCitation)
        assertEquals("1 of 1 sources cited", model.citationCoverage)
    }

    @Test fun citationTargetsOnlyExistingSources() {
        val model = buildAnswerPresentation("Result [S1], [S2], [S2], and [S8]", sourceCount = 2)

        assertEquals(setOf(1, 2), model.availableCitations)
        assertEquals(setOf(8), model.unavailableCitations)
    }

    @Test fun zeroSourcesMakesEveryCitationUnavailable() {
        val model = buildAnswerPresentation("Unsupported [S1]", sourceCount = 0)

        assertEquals(emptySet<Int>(), model.availableCitations)
        assertEquals(setOf(1), model.unavailableCitations)
    }

    @Test fun generationLabelsUsePlainResearchLanguage() {
        assertEquals("Ready for a question", researchActivityLabel(ResearchPhase.Idle))
        assertEquals("Searching your library", researchActivityLabel(ResearchPhase.Searching))
        assertEquals("Searching your library (47%)", researchActivityLabel(ResearchPhase.Searching, 0.47))
        assertEquals("Searching your library", researchActivityLabel(ResearchPhase.Searching, 0.004))
        assertEquals(
            "Searching your library (+3 concept matches, 42%)",
            researchActivityLabel(ResearchPhase.Searching, 0.42, vectorMatches = 3),
        )
        assertEquals("Writing from sources", researchActivityLabel(ResearchPhase.Generating))
        assertEquals(
            "Writing from sources (85 tokens written)",
            researchActivityLabel(ResearchPhase.Generating, tokensWritten = 85),
        )
        assertEquals(
            "Reading from sources (123/2374 tokens read)",
            researchActivityLabel(ResearchPhase.Generating, promptRead = 123 to 2374),
        )
        assertEquals(
            "Writing from sources (85 tokens written)",
            researchActivityLabel(ResearchPhase.Generating, promptRead = 2374 to 2374, tokensWritten = 85),
        )
        assertEquals(
            "Generating search keywords (12/58 tokens read)",
            researchActivityLabel(ResearchPhase.Planning, promptRead = 12 to 58),
        )
        assertEquals("Answer ready", researchActivityLabel(ResearchPhase.Complete))
        assertEquals("More evidence needed", researchActivityLabel(ResearchPhase.Insufficient))
        assertEquals("Research needs attention", researchActivityLabel(ResearchPhase.Error))
    }

    private fun metrics(
        totalMillis: Long,
        tokens: Int,
        unmapped: Boolean = false,
    ) = ResearchMetrics(
        retrievalMillis = 125,
        timeToFirstTokenMillis = 250,
        totalMillis = totalMillis,
        generatedTokenCount = tokens,
        citedSourceIds = setOf("S1", "S2"),
        hasUnmappedCitation = unmapped,
    )
}
