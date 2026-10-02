package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.ResearchMetrics

class ResearchPresentationTest {
    @Test fun modelWarningSurvivesTheReadyCardPreview() {
        val answer = "## Model explanation—not verified against saved sources\n\nGeneral background.\n\n## From saved sources\n\n- A quote [S1]"
        assertEquals("Model explanation (unverified): General background.", answerCardPreview(answer))
    }
    @Test fun readyCardCountsActualCitationSyntaxAndIgnoresUnavailableSources() {
        assertEquals(2, readyCitationCount("Sources [S1], [S2], [S2], [S9].", 4))
        assertEquals(0, readyCitationCount("No citations", 4))
    }
    @Test fun elapsedTimeIsADurationNotARelativeTimestamp() {
        assertEquals("0s", formatResearchElapsed(0))
        assertEquals("12s", formatResearchElapsed(12_400))
        assertEquals("1m 5s", formatResearchElapsed(65_000))
    }
    @Test fun formatsMeasuredRateWithoutInventingMissingValues() {
        val measured = formatResearchMetrics(metrics(totalMillis = 2_000, tokens = 10), sourceCount = 2)
        assertEquals("5.0 tok/s", measured.tokenRate)
        assertEquals("2 of 2 sources cited", measured.citationCoverage)

        val missing = formatResearchMetrics(metrics(totalMillis = 0, tokens = 10), sourceCount = 2)
        assertNull(missing.tokenRate)

        val modelOnly = formatResearchMetrics(metrics(totalMillis = 1_000, tokens = 10), sourceCount = 0)
        assertEquals("No local sources cited", modelOnly.citationCoverage)
        val sourceOnly = formatResearchMetrics(metrics(totalMillis = 1_000, tokens = 0), sourceCount = 1)
        assertEquals("No model generation", sourceOnly.tokenCount)
        assertNull(sourceOnly.tokenRate)
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

    @Test fun answerCardUsesRealBodyTextWithoutMarkdownOrCitationMarkers() {
        val answer = "# What the evidence says\n\n**Axial tilt** explains opposite seasons [S1]."
        assertEquals("Axial tilt explains opposite seasons.", answerCardPreview(answer))
        assertEquals("", answerCardPreview(""))
    }

    @Test fun leadAnswerPreviewShowsTheQuotedDefinitionWithoutLabelOrPronunciation() {
        val answer = "## From the saved reference\n\n> Buoyancy (/ˈbɔɪənsi, ˈbuːjənsi/), or upthrust, is the force exerted by a fluid. [S1]\n\n" +
            "## Model explanation—not verified against saved sources\n\nBuoyancy pushes up."
        assertEquals("Buoyancy, or upthrust, is the force exerted by a fluid.", answerCardPreview(answer))
    }

    @Test fun citationsNestedInMarkdownStylesRemainAvailable() {
        val model = buildAnswerPresentation(
            "**Strong [S1]** and *qualified [S2]* with [linked [S3]](offline://source)",
            sourceCount = 3,
        )

        assertEquals(setOf(1, 2, 3), model.availableCitations)
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
        assertEquals("Writing an offline answer", researchActivityLabel(ResearchPhase.Generating))
        assertEquals("Writing an offline answer", researchActivityLabel(ResearchPhase.Generating, hasSources = false))
        assertEquals(
            "Reading the question (123/2374 tokens read)",
            researchActivityLabel(ResearchPhase.Generating, promptRead = 123 to 2374, hasSources = false),
        )
        assertEquals("**Axial tilt** explains seasons.", draftAnswerPreview("**Axial tilt** [S1] explains seasons."))
        assertEquals(
            "Writing an offline answer (85 tokens written)",
            researchActivityLabel(ResearchPhase.Generating, tokensWritten = 85),
        )
        assertEquals(
            "Reading from sources (123/2374 tokens read)",
            researchActivityLabel(ResearchPhase.Generating, promptRead = 123 to 2374),
        )
        assertEquals(
            "Writing an offline answer (85 tokens written)",
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
