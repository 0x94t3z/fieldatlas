package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import xyz.fieldatlas.inference.FakeInferenceGateway

class StudyApplicabilityTest {
    private val q = "Does research in mice prove that a treatment extends human lifespan?"
    private fun row(text: String) = Evidence("d", "d:0", "Understanding Medical Research", "fixture://study", text, 0.0)
    @Test fun applicabilityIsNotAnOrdinaryAnimalOrHumanStudyQuestion() {
        assertTrue(StudyApplicability.asks(q))
        assertTrue(StudyApplicability.asks("Do results in rats apply to humans?"))
        assertTrue(StudyApplicability.asks("Does longer lifespan in mice establish that the same treatment extends human lifespan?"))
        assertTrue(StudyApplicability.asks("Can a treatment in rats confirm longevity benefits in people?"))
        assertFalse(StudyApplicability.asks("What did this study find about lifespan in mice?"))
        assertFalse(StudyApplicability.asks("Compare mouse and human anatomy."))
    }
    @Test fun speciesCooccurrenceAloneIsInsufficient() {
        assertFalse(StudyApplicability.supports(row("Studies involve mice and humans. Treatment extends lifespan.")))
        assertFalse(StudyApplicability.supports(row("The trial studies humans. Animal findings may not transfer.")))
        assertFalse(StudyApplicability.supports(row("No meaningful conclusion.\nMeSH: Animals; Humans; translation")))
        assertFalse(StudyApplicability.supports(row("Neurons differ in size between mice and humans.")))
        assertFalse(StudyApplicability.supports(row("To assess whether these findings were translatable, we repeated the treatment in mouse and human fibroblasts.")))
    }
    @Test fun transferDiscoveryIsBoundedAndRequiresAnExplicitRelationship() = runBlocking {
        val good = row("Getting certain results in animals may not mean that you get those same results in humans.")
        val noise = row("Studies involve mice and humans. Treatment extends lifespan.").copy(chunkId = "noise")
        val calls = mutableListOf<String>()
        val retriever = Retriever { query, limit, _ ->
            calls += query
            assertTrue(limit <= 24)
            listOf(noise, good)
        }
        assertEquals(listOf(good), StudyApplicability.recover(q, listOf(noise), retriever, 30))
        assertEquals(1, calls.size)
        assertEquals(listOf(good), StudyApplicability.recover(q, listOf(good), retriever, 30))
        assertEquals(listOf(noise), StudyApplicability.recover("Describe mouse anatomy", listOf(noise), retriever, 30))
        assertEquals(1, calls.size)
        val packed = PromptBuilder.build(q, listOf(good.copy(text = "An unrelated introduction.\n\n".repeat(30) + good.text)), 2048)
        assertTrue(packed.sources.single().excerpt.contains(good.text))
    }
    @Test fun directLimitationSurvivesAndUnrelatedDiseaseEvidenceDoesNot() {
        val good = row("Getting certain results in animals may not mean that you get those same results in humans.")
        val bad = row("Research in mice found a treatment. Studies in humans found a different disease.")
        assertTrue(StudyApplicability.supports(good))
        assertFalse(StudyApplicability.supports(bad))
        assertFalse(EvidenceRelevance.keep(listOf(bad), FtsQuery.from(q)!!.terms, question=q).contains(bad))
    }
    @Test fun absentApplicabilityEvidenceDoesNotGenerateMedicalProofClaims() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Unsubstantiated treatment claim"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research(q).toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.filterIsInstance<ResearchEvent.Token>().last().text.contains("couldn't find supporting saved sources"))
        assertTrue(events.none { it is ResearchEvent.Sources })
    }
}
