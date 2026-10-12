package xyz.fieldatlas.research

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.markdown.plainText

/** Opt-in replay of a previously retained local rule. Private questions, expected values,
 * and source IDs stay in the selected fixture, not this test or production code. No phone,
 * corpus search, native model generation, or history recording occurs here. */
class RetainedTechnicalReferenceTest {
    @Test fun retainedRuleSurvivesFocusPackingAndOriginalWordingAnswer() = runBlocking {
        val caseFile = System.getenv("FIELDATLAS_TECHNICAL_CASE_FILE")
        val caseId = System.getenv("FIELDATLAS_TECHNICAL_CASE_ID")
        assumeTrue("Requires an explicitly selected retained local fixture", caseFile != null && caseId != null)
        val selected = Json.parseToJsonElement(File(caseFile!!).readText()).jsonObject.getValue("cases").jsonArray
            .single { it.jsonObject.getValue("id").jsonPrimitive.content == caseId }.jsonObject
        val question = selected.getValue("question").jsonPrimitive.content
        val focus = TechnicalRuleFocus.from(question)!!
        val originals = selected.getValue("goldPassages").jsonArray.map { element ->
            val row = element.jsonObject
            Evidence(
                documentId = row.getValue("documentId").jsonPrimitive.content,
                chunkId = row.getValue("chunkId").jsonPrimitive.content,
                title = row.getValue("title").jsonPrimitive.content,
                source = row.getValue("source").jsonPrimitive.content,
                text = row.getValue("text").jsonPrimitive.content,
                score = -1.0,
            )
        }
        assertTrue(originals.isNotEmpty())
        assertEquals(selected.getValue("goldChunkIds").jsonArray.map { it.jsonPrimitive.content }, originals.map { it.chunkId })
        assertTrue(originals.all(focus::matches))
        val declaredSupport = selected.getValue("support").jsonPrimitive.content
        assertTrue(originals.any { it.text.contains(declaredSupport) })

        for (rate in listOf(null, 1.0, 200.0)) {
            val packed = PromptBuilder.buildKnowledge(question, originals, emptyList(), emptyList(), 2048, rate)
            assertEquals(originals, packed.sources.map { it.evidence })
            assertEquals(originals.map { EvidenceRelevance.passageText(it.text) }, packed.sources.map { it.excerpt })
            val answer = PreciseReferenceAnswer.answer(question, packed)!!
            assertTrue(parseAnswerMarkdown(AnswerText.finalized(answer, packed.sources.size))
                .joinToString("\n") { it.plainText() }.contains(declaredSupport))
            assertEquals(packed.sources.map { it.citationId }.toSet(), AnswerText.citationAudit(answer, packed.sources.size).citedSourceIds)
            assertFalse(AnswerText.citationAudit(answer, packed.sources.size).hasUnmappedCitation)
        }

        val inference = FakeInferenceGateway(listOf("An unsupported conclusion that must not be generated."))
        var observed: PackedPrompt? = null
        val events = ResearchOrchestrator(Retriever { _, _, _ -> originals }, inference,
            onEvidencePacked = { observed = it }).research(question).toList()
        assertEquals(0, inference.generateCalls)
        assertTrue(events.last() is ResearchEvent.Complete)
        assertEquals(originals, observed!!.sources.map { it.evidence })
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(parseAnswerMarkdown(AnswerText.finalized(answer, observed!!.sources.size))
            .joinToString("\n") { it.plainText() }.contains(declaredSupport))
        assertTrue(answer.contains("not a generated conclusion"))
        assertTrue(observed!!.sources.all { answer.contains(PreciseReferenceAnswer.quote(it)) })
        assertFalse((events.last() as ResearchEvent.Complete).metrics.hasUnmappedCitation)
    }
}
