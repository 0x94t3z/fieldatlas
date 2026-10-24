package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.ResearchMetrics

class ResearchPresentationTest {
    @Test fun modelWarningSurvivesTheReadyCardPreview() {
        val answer = "## Model explanation—not verified against saved sources\n\nGeneral background.\n\n## From saved sources\n\n- A quote [S1]"
        assertEquals("Model explanation (not checked against saved sources): General background.", answerCardPreview(answer))
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
        // 10 tokens written between the first word (250 ms) and the end (2 s).
        assertEquals("5.7 tok/s", measured.tokenRate)
        assertNull(measured.promptReading)
        val read = formatResearchMetrics(metrics(totalMillis = 2_000, tokens = 10).copy(promptTokens = 1_214, promptMillis = 66_000), 2)
        assertEquals("1,214 tokens in 66.0 s (18.4 tok/s)", read.promptReading)
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
        assertEquals("1 of 1 source cited", model.citationCoverage)
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

    @Test fun draftPreviewsDropCitationsAndKeepListItems() {
        assertEquals("**Axial tilt** explains seasons.", draftAnswerPreview("**Axial tilt** [S1] explains seasons."))
        // A long draft cut at a nested list item still draws that item as a list item.
        val excerpt = "First Aid: If a snake bites you\n\n" + "- Seek emergency medical attention as soon as possible.\n".repeat(12) +
            "  - Do not drive yourself to the hospital.\n- Keep calm."
        val preview = draftAnswerPreview(excerpt, maxChars = 120)
        assertTrue(preview, preview.startsWith("…\n\n- "))
        val items = xyz.fieldatlas.ui.markdown.parseAnswerMarkdown(preview).filterIsInstance<xyz.fieldatlas.ui.markdown.MarkdownBlock.ListBlock>()
        assertTrue(preview, items.isNotEmpty())
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

    @Test fun theStandInGuidePreviewsFromItsStartNotItsEnd() {
        // While a trusted model writes, the official guide stands in; its call line and first steps must stay in view.
        val answer = "**Call your local emergency number now** (112).\n\n" + xyz.fieldatlas.research.GuideStandIn.NOTE +
            "\n\n_CPR: shown as published by NIH/NLM [S1]._\n\n1. Check for breathing.\n2. Call for help.\n" + "3. Push hard and fast.\n".repeat(40) +
            "Take an accredited course and refresh every two years."
        val preview = draftAnswerPreview(answer, maxChars = 200)
        assertTrue(preview, preview.startsWith("**Call your local emergency number now**"))
        assertTrue(preview, preview.contains("Check for breathing"))
        assertTrue(preview, preview.trimEnd().endsWith("…"))
        assertFalse(preview, preview.contains("accredited course"))
    }
}
