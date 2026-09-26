package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val first = Evidence("doc-a", "doc-a:0000", "Claim A", "Source A", "The limit is low.", 2.0)
    private val second = Evidence("doc-b", "doc-b:0000", "Claim B", "Source B", "The limit is high.", 1.0)

    @Test fun numbersEvidenceAndForbidsExternalKnowledge() {
        val packed = PromptBuilder.build("Compare the limits", listOf(first, second), 512)
        assertTrue(packed.prompt.contains("[S1]"))
        assertTrue(packed.prompt.contains("[S2]"))
        assertTrue(packed.prompt.contains("Do not use external knowledge"))
        assertEquals(listOf("S1", "S2"), packed.sources.map { it.citationId })
    }

    @Test fun conflictingEvidenceStaysInSeparateBlocks() {
        val packed = PromptBuilder.build("Which is correct?", listOf(first, second), 512)
        val firstIndex = packed.prompt.indexOf("The limit is low.")
        val secondIndex = packed.prompt.indexOf("The limit is high.")
        assertTrue(firstIndex >= 0)
        assertTrue(secondIndex > firstIndex)
        assertTrue(packed.prompt.substring(firstIndex, secondIndex).contains("[S2]"))
    }

    @Test fun requestsEfficientNonThinkingGeneration() {
        val packed = PromptBuilder.build("Explain the claim", listOf(first), 512)
        assertTrue(packed.prompt.contains("/no_think"))
    }

    @Test fun modelOnlyPromptAllowsUncitedOfflineLookup() {
        val packed = PromptBuilder.buildModelOnly("Explain F1")

        assertTrue(packed.sources.isEmpty())
        assertTrue(packed.prompt.contains("offline knowledge only"))
        assertTrue(packed.prompt.contains("Do not invent citations"))
        assertTrue(packed.prompt.contains("under 150 words"))
        assertTrue(packed.prompt.contains("Do not add a generic disclaimer"))
        assertTrue(packed.prompt.contains("Explain F1"))
    }

    @Test fun venueRecommendationsRequireNamedEvidenceAndSourceDates() {
        val source = Evidence(
            "eat-a", "eat-a:0000", "Chiang Mai — Green Plate", "Wikivoyage",
            "Vegan dishes. Listing last checked: 2026-08-01", -1.0,
        )
        val question = "Best vegan restaurants in Chiang Mai?"
        val grounded = PromptBuilder.build(question, listOf(source), 512).prompt
        val unsupported = PromptBuilder.buildModelOnly(question).prompt

        assertTrue(grounded.contains("name only venues in the evidence"))
        assertTrue(grounded.contains("date the listing or source shows"))
        assertTrue(unsupported.contains("Do not invent venue names"))
    }

    @Test fun truncationPreservesCitationMappingAndSurrogatePairs() {
        val huge = first.copy(text = "😀".repeat(2_000))
        val packed = PromptBuilder.build("Summarize", listOf(huge, second), 128)
        assertEquals("S1", packed.sources.first().citationId)
        assertEquals("doc-a:0000", packed.sources.first().evidence.chunkId)
        assertFalse(packed.prompt.last().isHighSurrogate())
        assertFalse(packed.prompt.any { it == '\uFFFD' })
        assertTrue(packed.prompt.length < huge.text.length)
    }

    @Test fun blankQuestionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptBuilder.build(" ", listOf(first), 512)
        }
    }
}
