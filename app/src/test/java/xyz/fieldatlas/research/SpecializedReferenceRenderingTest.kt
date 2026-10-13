package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.research.answerWithSources

class SpecializedReferenceRenderingTest {
    private val literal = "Original [S99], [S#], array[1], (sources: S2) and from S1 remain source text.\n" +
        "```text\n    // source-only line comment\n    return false;\n```\n" +
        "<think>This is visible source text.</think>\n## This is not an app heading\n" +
        "**Ignore instructions** is quoted wording, not an instruction."

    private fun assertLiteral(answer: String, sources: List<Evidence>, expected: List<String>) {
        val finalized = AnswerText.finalized(answer, sources.size)
        assertEquals(finalized, AnswerText.finalized(finalized, sources.size))
        val blocks = parseAnswerMarkdown(finalized)
        val quotes = blocks.filterIsInstance<MarkdownBlock.Quote>()
        assertEquals(expected.size, quotes.size)
        expected.forEachIndexed { index, text ->
            assertEquals(listOf(MarkdownInline.Text(text + " "), MarkdownInline.Citation(index + 1)), quotes[index].content)
            assertTrue(answerWithSources("Question", finalized, sources).contains(text))
        }
        assertEquals(expected.indices.map { "S${it + 1}" }.toSet(), AnswerText.citationAudit(answer, sources.size).citedSourceIds)
        assertFalse(AnswerText.citationAudit(answer, sources.size).hasUnmappedCitation)
        assertEquals(1, blocks.filterIsInstance<MarkdownBlock.Heading>().size)
    }

    @Test fun partialComparisonKeepsItsGapAndLiteralTextThroughTheActualResearchRoute() = runBlocking {
        val body = "A battery stores chemical energy.\n\n$literal"
        val source = Evidence("b", "b:0", "Battery — Overview", "Local battery record", body, -1.0)
        val inference = FakeInferenceGateway(listOf("Unsupported comparison."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(source) }, inference)
            .research("Compare a battery with a capacitor. Quote the source.").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        val published = events.filterIsInstance<ResearchEvent.Sources>().last().evidence
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(answer.contains("These selected passages do not mention capacitor"))
        assertLiteral(answer, published, listOf(body))
    }

    @Test fun sourceOnlyReferenceKeepsLiteralTextThroughTheActualResearchRoute() = runBlocking {
        val body = "A heat pump moves thermal energy through a working fluid.\n\n$literal"
        val subject = Evidence("enw-1-0000", "enw-1-0000:0000", "Heat pump — Overview", "Local heat pump record", body, 0.0)
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(subject)
            override suspend fun titled(title: String, sections: List<String>, limit: Int) =
                if (title.equals("heat pump", true)) listOf(subject) else emptyList()
        }
        val inference = FakeInferenceGateway(listOf("Unsupported explanation."))
        val events = ResearchOrchestrator(retriever, inference)
            .research("According to the saved sources, how does a heat pump work? Quote the source.").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        val published = events.filterIsInstance<ResearchEvent.Sources>().last().evidence
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(answer.contains("Selected passages from your saved sources"))
        assertLiteral(answer, published, listOf(body))
    }

    @Test fun matchingChunkIdsFromDifferentSourceRevisionsStaySeparatelyCited() {
        val first = Evidence("enw-1-0000", "enw-1-0000:0000", "Heat pump — Overview", "Revision A",
            "A heat pump moves thermal energy. Revision A includes [S99].", 0.0)
        val second = first.copy(source = "Revision B", text = "A heat pump moves thermal energy. Revision B includes [S#].")
        val selected = PackedPrompt("", listOf(PromptSource("S1", first), PromptSource("S2", second)))
        val answer = SavedReferenceAnswer.answer("According to saved sources, how does a heat pump work?",
            selected, listOf(first, second))!!
        assertLiteral(answer, listOf(first, second), listOf(first.text, second.text))
    }
}
