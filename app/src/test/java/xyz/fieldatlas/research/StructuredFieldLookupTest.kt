package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class StructuredFieldLookupTest {
    private val question = "Using only saved sources, what does the Atlas why field say for VX?"
    private val ask = StructuredFieldLookup.ask(question)!!
    private fun record(value: String = "upstream", id: String = "VX") = Evidence(
        id, "$id:0", "Atlas records: $id", "Local fixture", "Dataset: Atlas records\nsymbol: $id\nwhy: $value\nnumber: 1", 0.0)

    @Test fun exactLookupPreservesValueAndCitation() {
        val result = StructuredFieldLookup.answer(ask, listOf(record()))!!
        assertTrue(result.answer.contains("why: upstream [S1]"))
        assertEquals(listOf(record()), result.evidence)
    }

    @Test fun interpretationAndAppendedRequestsDoNotMatch() {
        for (q in listOf("Does VX extend life?", "$question Explain why.",
            "What does the Atlas why field mean for VX?")) assertNull(StructuredFieldLookup.ask(q))
    }

    @Test fun multiwordDatasetIsNotMistakenForFieldName() {
        assertEquals(StructuredFieldLookup.Ask("Atlas human records", "why", "VX"),
            StructuredFieldLookup.ask("What does the Atlas human records why field say for VX?"))
    }

    @Test fun missingExactRecordContinuesNormalResearch() = runBlocking {
        var searches = 0
        val retriever = Retriever { _, _, _ -> searches++; emptyList() }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("No saved record.")))
            .research(question).toList()
        assertTrue(searches > 0)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(events.none { it is ResearchEvent.Sources })
    }

    @Test fun wrongRecordDatasetMissingFieldAndDuplicateFieldFallBack() {
        val row = record()
        for (bad in listOf(record(id = "VX2"), row.copy(text = row.text.replace("Atlas records", "Atlasology")),
            row.copy(text = row.text.replace("why:", "when:")), row.copy(text = row.text + "\nwhy: downstream"),
            record("[S99]"))) assertNull(StructuredFieldLookup.answer(ask, listOf(bad)))
    }

    @Test fun conflictsAndTruncatedCandidateListsFallBack() {
        assertNull(StructuredFieldLookup.answer(ask, listOf(record(), record("downstream"))))
        assertNull(StructuredFieldLookup.answer(ask, List(20) { record() }))
    }

    @Test fun orchestratorSkipsGeneralSearchAndGenerationForLiteralMatch() = runBlocking {
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence> =
                error("Exact field lookup should not search general prose")
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> {
                assertEquals("Atlas", phrase)
                assertEquals(listOf("VX"), sections)
                return listOf(record())
            }
        }
        val inference = FakeInferenceGateway(listOf("must not generate"))
        val events = ResearchOrchestrator(retriever, inference).research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertEquals(setOf("S1"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
        assertEquals(listOf(record()), events.filterIsInstance<ResearchEvent.Sources>().single().evidence)
    }
}
