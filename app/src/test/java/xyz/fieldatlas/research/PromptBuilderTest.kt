package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test fun conceptualQuestionsPermitLabelledModelKnowledgeButVenueLookupsDoNot() {
        val fact = Evidence("d", "c", "Study", "Local", "Some relevant evidence.", -1.0)
        assertTrue(PromptBuilder.build("Explain mitosis", listOf(fact), 512).mixedAnswer)
        assertFalse(PromptBuilder.build("Which hotels are in Berlin?", listOf(fact), 512).mixedAnswer)
        assertFalse(PromptBuilder.build("Compare bars in London", listOf(fact), 512).mixedAnswer)
        assertFalse(PromptBuilder.build("Compare shops in London", listOf(fact), 512).mixedAnswer)
        assertFalse(PromptBuilder.build("Summarize my saved sources", listOf(fact), 512).mixedAnswer)
        assertTrue(PromptBuilder.buildModelOnly("Explain mitosis").mixedAnswer)
    }

    @Test fun scholarlyPromptUsesBoundedEvidenceWithoutChangingSavedPassages() {
        val evidence = (1..8).map { index ->
            Evidence("paper-$index", "chunk-$index", "Division study", "PubMed",
                "Mitosis and meiosis differ in study $index.\nMeSH: Cells; Humans", -index.toDouble())
        }
        val packed = PromptBuilder.build("Compare mitosis and meiosis", evidence, 2048)
        assertEquals(4, packed.sources.size)
        assertEquals(evidence.take(4), packed.sources.map { it.evidence })
        assertTrue(packed.prompt.contains("not claim this is a complete synthesis"))
    }

    @Test fun scholarlyLimitAlsoCoversUntaggedChunksFromTheSameCorpus() {
        val evidence = (1..8).map { index ->
            Evidence("paper-$index", "chunk-$index", "Division study", "PubMed",
                "Mitosis and meiosis differ in study $index." + if (index == 1) "\nMeSH: Cells" else "", -1.0)
        }
        assertEquals(4, PromptBuilder.build("Compare mitosis and meiosis", evidence, 2048).sources.size)
    }

    @Test fun indexingTagsAreNotSentAsEvidenceButOriginalSourceIsRetained() {
        val evidence = Evidence("paper", "chunk", "Cell division", "PubMed", "Mitosis and meiosis differ.\nMeSH: Humans; Growth", -1.0)
        val packed = PromptBuilder.build("Compare mitosis and meiosis", listOf(evidence), 512)
        assertFalse(packed.prompt.contains("MeSH:"))
        assertTrue(packed.prompt.contains("Mitosis and meiosis differ."))
        assertEquals(evidence, packed.sources.single().evidence)
    }

    private val first = Evidence("doc-a", "doc-a:0000", "Claim A", "Source A", "The limit is low.", 2.0)
    private val second = Evidence("doc-b", "doc-b:0000", "Claim B", "Source B", "The limit is high.", 1.0)

    @Test fun numbersEvidenceSeparatelyFromModelKnowledge() {
        val packed = PromptBuilder.build("Compare the limits", listOf(first, second), 512)
        assertTrue(packed.prompt.contains("[S1]"))
        assertTrue(packed.prompt.contains("[S2]"))
        assertTrue(packed.mixedAnswer)
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

        assertTrue(grounded.contains("name only places in the evidence"))
        assertTrue(grounded.contains("date the listing or source shows"))
        assertTrue(unsupported.contains("Do not invent place names"))
    }

    @Test fun travelPlaceRecommendationsNeedNamedEvidence() {
        val source = Evidence(
            "wv-place-museum", "wv-place-museum:0000", "Berlin — City Museum", "Wikivoyage",
            "Destination: Berlin\nCategory: See\nPlace: City Museum", -1.0,
        )
        val question = "Which museums are in Berlin?"

        assertTrue(PromptBuilder.build(question, listOf(source), 512).prompt
            .contains("name only places in the evidence"))
        assertTrue(PromptBuilder.buildModelOnly(question).prompt
            .contains("Do not invent place names"))
    }

    @Test fun truncationPreservesCitationMappingAndSurrogatePairs() {
        val huge = first.copy(text = "😀".repeat(2_000))
        val packed = PromptBuilder.build("Summarize", listOf(huge, second), 128)
        assertEquals("S1", packed.sources.first().citationId)
        assertEquals("doc-b:0000", packed.sources.first().evidence.chunkId)
        assertFalse(packed.prompt.last().isHighSurrogate())
        assertFalse(packed.prompt.any { it == '\uFFFD' })
        assertTrue(packed.prompt.length < huge.text.length)
    }

    @Test fun blankQuestionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptBuilder.build(" ", listOf(first), 512)
        }
    }

    @Test fun longEvidenceKeepsRelevantExcerptAndFollowingQualification() {
        val background = "Unrelated historical background is described here. ".repeat(100)
        val claim = "Mitosis preserves chromosome number while meiosis reduces it."
        val caveat = "However, these findings are limited to the studied organism."
        val source = first.copy(text = background + claim + " " + caveat)
        val packed = PromptBuilder.build("Compare mitosis and meiosis", listOf(source), 2048)
        assertTrue(packed.prompt.contains(claim))
        assertTrue(packed.prompt.contains(caveat))
        assertTrue(packed.prompt.length < 4000)
        assertEquals(source, packed.sources.single().evidence)
    }

    @Test fun longFirstSourceCannotConsumeSpaceForConflictingSource() {
        val a = first.copy(text = "The treatment improved survival. " + "Background observations. ".repeat(200))
        val b = second.copy(text = "The treatment did not improve survival in this trial.")
        val packed = PromptBuilder.build("Compare treatment survival findings", listOf(a, b), 2048)
        assertEquals(listOf(a, b), packed.sources.map { it.evidence })
        assertTrue(packed.prompt.contains("The treatment improved survival."))
        assertTrue(packed.prompt.contains(b.text))
    }

    @Test fun eightSourcesKeepClaimsTogetherWithTheirCaveats() {
        val claim = "Rapamycin improved survival in the experimental population and the authors reported a statistically significant treatment effect."
        val caveat = "However, this was a mouse study and does not establish that human lifespan is extended."
        val items = (1..8).map { index -> first.copy(documentId = "doc-$index", chunkId = "$index",
            title = "Study $index " + "long scientific title ".repeat(20),
            source = "https://example.test/" + "archive/".repeat(80),
            text = "Background observations. ".repeat(100) + claim + " " + caveat) }
        val packed = PromptBuilder.build("Rapamycin survival treatment effect", items, 2048)
        assertEquals(8, packed.sources.size)
        assertEquals(8, Regex(Regex.escape(caveat)).findAll(packed.prompt).count())
    }

    @Test fun neverClipSentenceBeforeItsTrailingNegation() {
        val source = first.copy(text = "The treatment " + "was evaluated in experimental populations ".repeat(30) +
            "but did not improve survival.")
        val packed = PromptBuilder.build("treatment survival", listOf(source), 2048)
        assertTrue(packed.prompt.contains(source.text) || packed.sources.isEmpty())
    }

    @Test fun wrappedLinesCannotHideTrailingNegation() {
        val text = "The treatment improved survival hypothesis was evaluated\n" +
            "in experimental populations with follow-up measurements\n".repeat(20) +
            "but did not improve survival."
        val packed = PromptBuilder.build("treatment survival", listOf(first.copy(text = text)), 2048)
        assertTrue(packed.sources.isEmpty() || packed.prompt.contains("but did not improve survival."))
    }
}
