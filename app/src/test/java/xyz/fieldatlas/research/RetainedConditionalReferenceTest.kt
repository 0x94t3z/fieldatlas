package xyz.fieldatlas.research

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in replay of a retained local source-review fixture. No private source text or case IDs
 * live in this test, and it neither searches the phone nor generates/records a new answer. */
class RetainedConditionalReferenceTest {
    @Test fun retainedLaterPassageSurvivesSelectionPackingAndVerbatimAnswering() {
        val caseFile = System.getenv("FIELDATLAS_REFERENCE_CASE_FILE")
        val caseId = System.getenv("FIELDATLAS_REFERENCE_CASE_ID")
        val captureDir = System.getenv("FIELDATLAS_REFERENCE_CAPTURE_DIR")
        assumeTrue("Requires an explicitly selected retained local fixture", caseFile != null && caseId != null && captureDir != null)
        val frozen = Json.parseToJsonElement(File(caseFile!!).readText()).jsonObject.getValue("cases").jsonArray
            .single { it.jsonObject.getValue("id").jsonPrimitive.content == caseId }.jsonObject
        val capture = Json.parseToJsonElement(File(captureDir!!, "result.json").readText()).jsonObject
        assertEquals(caseId, capture.getValue("caseId").jsonPrimitive.content)
        val question = frozen.getValue("question").jsonPrimitive.content
        val title = frozen.getValue("title").jsonPrimitive.content
        val source = frozen.getValue("source").jsonPrimitive.content
        val document = frozen.getValue("documentId").jsonPrimitive.content
        assertEquals(document, capture.getValue("documentId").jsonPrimitive.content)
        val sourceNumber = capture.getValue("sourceNumber").jsonPrimitive.content
        assertEquals("$sourceNumber · $title", capture.getValue("savedPassageTitle").jsonPrimitive.content)
        assertEquals(source, capture.getValue("originalURL").jsonPrimitive.content)

        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val nodes = factory.newDocumentBuilder().parse(File(captureDir, "source-page-00.xml")).getElementsByTagName("node")
        val overviewText = (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("text")?.nodeValue.orEmpty()
        }.maxBy { it.length }
        assertTrue(overviewText.length > 100)
        val overview = Evidence(document, capture.getValue("chunkId").jsonPrimitive.content, title, source, overviewText, 0.0)
        val gold = frozen.getValue("goldPassages").jsonArray.single().jsonObject
        val detail = Evidence(document, gold.getValue("chunkId").jsonPrimitive.content, title, source,
            gold.getValue("text").jsonPrimitive.content, 0.0)
        assertNotEquals(overview.chunkId, detail.chunkId)

        val subjects = ArticleDepth.supportingSubjects(question, listOf(overview), emptyList())
        assertEquals(listOf(overview), subjects)
        val focus = ConditionalReferenceFocus.from(question, subjects)!!
        assertFalse("The retained overview alone must not establish the connecting detail", focus.matches(overview, overview.text))
        val depth = ArticleDepth.pickForPrompt(question, subjects,
            mapOf(title.substringBefore(" — ") to listOf(overview, detail)), ArticleDepth.budget(10.0))
        assertEquals(listOf(detail), depth)
        val packed = PromptBuilder.buildKnowledge(question, listOf(overview), subjects, depth, 2048, 1.0)
        assertEquals(listOf(detail), packed.sources.map { it.evidence })
        assertTrue(focus.matches(detail, packed.sources.single().excerpt))
        val answer = PreciseReferenceAnswer.conditionalAnswer(question, packed, subjects)!!
        assertTrue(answer.contains(PreciseReferenceAnswer.quote(packed.sources.single())))
        assertTrue(answer.contains("not a generated causal explanation"))
        assertEquals(setOf("S1"), AnswerText.citationAudit(answer, packed.sources.size).citedSourceIds)
    }
}
