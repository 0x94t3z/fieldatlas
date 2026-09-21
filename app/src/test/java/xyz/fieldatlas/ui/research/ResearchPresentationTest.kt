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
    }

    @Test fun exposesUnmappedCitationAsAWarningInsteadOfAConfidenceClaim() {
        val model = formatResearchMetrics(
            metrics(totalMillis = 1_000, tokens = 4, unmapped = true),
            sourceCount = 1,
        )
        assertTrue(model.hasUnmappedCitation)
        assertEquals("1 of 1 sources cited", model.citationCoverage)
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
