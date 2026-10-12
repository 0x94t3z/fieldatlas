package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class ConditionalReferenceAnswerTest {
    private val question = "Why can a container leak even when its composite shell has a high pressure rating?"
    private val generic = Evidence("swp-container-0000", "swp-container-0000:0000", "Container — Overview", "Local revision 1",
        "A container holds materials. Containers may have a composite shell with a pressure rating.", -5.0)
    private val overview = Evidence("wp-shell-0000", "wp-shell-0000:0000", "Composite shell — Overview", "Local revision 2",
        "A composite shell is a structure made from multiple materials. It can resist applied pressure.", -1.0)
    private val detail = overview.copy(chunkId = "wp-shell-0000:0002", text =
        "However, containers can leak through joints. A shell pressure rating does not test the container's closures. " +
            "These observations apply only to the stated test conditions.")

    private fun retriever(includeDetail: Boolean) = object : Retriever {
        override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(generic, overview)
        override suspend fun titled(title: String, sections: List<String>, limit: Int) =
            when (title.lowercase()) { "container" -> listOf(generic); "composite shell" -> listOf(overview); else -> emptyList() }
        override suspend fun article(title: String, limit: Int) = when (title.lowercase()) {
            "container" -> listOf(generic)
            "composite shell" -> if (includeDetail) listOf(overview, detail) else listOf(overview)
            else -> emptyList()
        }
    }

    @Test fun conditionalExplanationReadsLaterUnitBeforeGenericSubjectAndNeverInventsCause() = runBlocking {
        val inference = FakeInferenceGateway(listOf("The stored material expands and forces itself through the shell."))
        var packed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever(true), inference, articleDepth = true,
            prefillSpeed = InMemoryPrefillSpeed(1.0), onEvidencePacked = { packed = it })
            .research(question).toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(0, inference.generateCalls)
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.contains("containers can leak through joints"))
        assertTrue(answer.contains("does not test the container's closures"))
        assertTrue(answer.contains("only to the stated test conditions"))
        assertFalse(answer.contains("forces itself"))
        assertEquals(detail, packed!!.sources.first().evidence)
        assertTrue(answer.contains("[S1]"))
    }

    @Test fun absenceOfTheConnectingDetailProducesScopedGapNotAnExplanationFromGenericWords() = runBlocking {
        val inference = FakeInferenceGateway(listOf("An unsupported cause."))
        val events = ResearchOrchestrator(retriever(false), inference, articleDepth = true).research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.contains("retrieved passages"))
        assertTrue(answer.contains("do not establish"))
        assertFalse(answer.contains("unsupported cause"))
    }

    @Test fun ordinaryExplanationPreservesPackedPolicyAndFinishesFromOriginals() = runBlocking {
        val inference = FakeInferenceGateway(listOf("Ordinary explanation."))
        var packed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever(true), inference, articleDepth = true,
            onEvidencePacked = { packed = it })
            .research("How does a composite shell resist pressure?").toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(0, inference.generateCalls)
        assertTrue(checkNotNull(packed).mixedAnswer)
        assertTrue(packed!!.sources.isNotEmpty())
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.contains("Original excerpts, not a generated explanation"))
        assertFalse(answer.contains("Ordinary explanation."))
        assertEquals(packed!!.sources.map { it.citationId }.toSet(), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
    }
}
