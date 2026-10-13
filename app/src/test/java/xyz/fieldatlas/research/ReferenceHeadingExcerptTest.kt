package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import xyz.fieldatlas.inference.FakeInferenceGateway

class ReferenceHeadingExcerptTest {
    @Test fun comparisonRetainsProcessConditionsRatherThanDiscardingTheWholeSubject() {
        val text = "Assembly in the fictional sealed chamber requires dry fixtures. This requirement does not apply outside that chamber."
        val row = Evidence("fixture", "fixture:0", "Assembly", "fixture://assembly", text, 0.0)
        assertTrue(EvidenceRelevance.hasOverviewStatement(row, listOf("assembly")))
        assertEquals(text, PromptBuilder.excerptForKnowledge("Compare assembly and inspection.", row))
    }

    @Test fun missingComparisonDefinitionCanBeRecoveredFromItsTitleIndex() = runBlocking {
        val a = Evidence("a", "a:0", "Neral", "fixture://a", "Neral is a fictional solid test material.", 0.0)
        val b = a.copy(documentId = "b", chunkId = "b:0", title = "Velin", text = "Velin is a fictional liquid test material.")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(a)
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                if (phrase.equals("velin", true)) listOf(b) else emptyList()
        }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("must not invent")))
            .research("Using only saved sources, what is the difference between neral and velin?").toList()
        assertEquals(setOf("a", "b"), events.filterIsInstance<ResearchEvent.Sources>().last().evidence.map { it.documentId }.toSet())
        assertEquals(2, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount) // one repair for missing citations
        assertEquals(CitedExplanation.UNAVAILABLE, events.filterIsInstance<ResearchEvent.Token>().last().text)
    }

    @Test fun differenceBetweenQuestionDisclosesAnOmittedSubject() {
        val source = Evidence("fixture", "fixture:0", "Translation", "fixture://translation",
            "Translation is a process described in this fixture.", 0.0)
        val question = "Using only saved sources, what is the difference between transcription and translation?"
        val packed = PromptBuilder.build(question, listOf(source), 2048)
        val answer = PartialComparison.answer(question, packed)!!
        assertTrue(answer.contains("do not mention transcription"))
        assertTrue(answer.contains("do not support a complete comparison"))
    }

    @Test fun matchingQuestionHeadingKeepsItsAnswerAndQualification() {
        val original = "What are test widgets?\nTest widgets are fictional objects made for regression tests.\n\n" +
            "However, this fixture does not describe any real product.\n\n" + "Unrelated filler. ".repeat(150)
        val source = Evidence("fixture", "fixture:1", "Test widgets", "fixture://widgets", original, 0.0)
        val excerpt = PromptBuilder.excerptForKnowledge("What are test widgets?", source)
        assertTrue(excerpt.contains("Test widgets are fictional objects made for regression tests."))
        assertTrue(excerpt.contains("However, this fixture does not describe any real product."))
        assertTrue(original.contains(excerpt.removePrefix("… ").removeSuffix(" …")))
    }
}
