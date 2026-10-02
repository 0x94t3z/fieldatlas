package xyz.fieldatlas.research

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway

class SourceLeadTest {
    private val friction = "Friction is the force resisting the relative motion of solid surfaces, fluid layers, " +
        "and material elements sliding or grinding against each other. Types of friction include dry, fluid, " +
        "lubricated, skin, and internal – an incomplete list. The study of the processes involved is called " +
        "tribology, and has a history of more than 2,000 years.\n\nFriction can have dramatic consequences."

    private fun overview(subject: String, text: String, chunk: Int = 0) = Evidence(
        documentId = "gr-1-0000",
        chunkId = "gr-1-0000:%04d".format(chunk),
        title = "$subject — Overview",
        source = "https://en.wikipedia.org/w/index.php?oldid=1",
        text = text,
        score = -1.0,
    )

    private fun packed(vararg evidence: Evidence) =
        evidence.mapIndexed { index, item -> PromptSource("S${index + 1}", item) }

    @Test fun leadCopiesCompleteOpeningSentencesVerbatim() {
        val lead = SourceLead.select("What is friction?", packed(overview("Friction", friction)))!!
        assertEquals("S1", lead.citationId)
        assertTrue(lead.text.startsWith("Friction is the force resisting"))
        // Short opening sentences are joined up to the target length, never past a paragraph.
        assertTrue(lead.text.endsWith("more than 2,000 years."))
        // Verbatim by construction: the lead must occur unchanged in the saved passage.
        assertTrue(friction.replace(Regex("\\s+"), " ").contains(lead.text))
        assertFalse(lead.text.contains("dramatic consequences"))
        assertEquals("## ${SourceLead.HEADING}\n\n> ${lead.text} [S1]", lead.render())
    }

    @Test fun leadUsesTheCitationNumberOfThePackedSource() {
        val unrelated = Evidence("x", "x:0", "Tribology journal", "Local", "Friction was measured.", -1.0)
        val lead = SourceLead.select("Tell me about friction",
            packed(unrelated, overview("Friction", friction)))!!
        assertEquals("S2", lead.citationId)
    }

    @Test fun possessiveQuestionsAndPastTenseOpeningsGetTheArticleLead() {
        val japan = overview("History of Japan", "The history of Japan begins in prehistoric times, before any " +
            "written records were kept.\n\nAlso known as: Japanese history")
        val japanLead = SourceLead.select("Tell me about Japan's history", packed(japan))!!
        assertEquals("The history of Japan begins in prehistoric times, before any written records were kept.", japanLead.text)
        val revolution = overview("French Revolution", "The French Revolution was a period of political and " +
            "social change in France that began in 1789.")
        assertTrue(SourceLead.select("Tell me about the French Revolution", packed(revolution)) != null)
    }

    @Test fun laterChunksIncidentalMentionsAndOtherSubjectsGetNoLead() {
        assertNull(SourceLead.select("What is friction?", packed(overview("Friction", friction, chunk = 1))))
        assertNull(SourceLead.select("What is friction?", packed(overview("Friction",
            "The ancient Greeks studied surfaces. Many forces were later named.\n\nMore text."))))
        assertNull(SourceLead.select("What is encryption?", packed(overview("Friction", friction))))
        assertNull(SourceLead.select("Compare friction and drag.", packed(overview("Friction", friction))))
        // Source-only and venue questions keep their own evidence policies.
        assertNull(SourceLead.select("Summarize the saved sources about friction", packed(overview("Friction", friction))))
    }

    @Test fun domainQualifiedWikipediaOpeningQualifies() {
        val encryption = "In cryptography, encryption is the process of transforming information in a way that, " +
            "ideally, only authorized parties can decode. This process converts the original representation of " +
            "the information, known as plaintext, into an alternative form known as ciphertext."
        val lead = SourceLead.select("Tell me about encryption", packed(overview("Encryption", encryption)))!!
        assertTrue(lead.text.startsWith("In cryptography, encryption is the process"))
    }

    @Test fun indefiniteArticleQuestionsMatchTheSectionTitle() {
        val boat = "A boat is a watercraft of a large range of types and sizes, but generally smaller than a ship."
        for (question in listOf("What is a boat?", "What is an boat?", "Tell me about the boat")) {
            assertEquals(question, "S1", SourceLead.select(question, packed(overview("Boat", boat)))?.citationId)
        }
        // A word merely starting with "a"/"an" is not an article.
        assertNull(SourceLead.select("What is anboat?", packed(overview("Boat", boat))))
    }

    @Test fun abbreviationsAndInitialsDoNotEndASentence() {
        val text = "A boat is a watercraft, e.g. a dinghy, built by J. Smith in the U.S. long ago. " +
            "It floats. Boats vary widely in size and shape, and in how they are propelled through water by people."
        val lead = SourceLead.leadText(text)!!
        assertTrue(lead.startsWith("A boat is a watercraft, e.g. a dinghy, built by J. Smith in the U.S. long ago."))
    }

    @Test fun unterminatedOrOversizedOpeningsAreRejected() {
        assertNull(SourceLead.leadText("Friction is the force resisting motion and"))
        assertNull(SourceLead.leadText("Friction is " + "very ".repeat(200) + "old."))
    }

    @Test fun orchestratorShowsLeadBeforeGenerationAndKeepsItInTheFinalAnswer() = runBlocking {
        val source = overview("Friction", friction)
        val inference = FakeInferenceGateway(listOf("Friction converts motion into heat."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(source) }, inference)
            .research("What is friction?").toList()
        val leadIndex = events.indexOfFirst { it is ResearchEvent.Lead }
        val firstToken = events.indexOfFirst { it is ResearchEvent.Token }
        assertTrue(leadIndex >= 0 && leadIndex < firstToken)
        val lead = (events[leadIndex] as ResearchEvent.Lead).text
        val answer = events.filterIsInstance<ResearchEvent.Token>().last()
        assertTrue(answer.replace)
        assertTrue(answer.text.startsWith(lead))
        assertTrue(answer.text.contains("not verified against saved sources"))
        assertTrue(answer.text.contains("Friction converts motion into heat."))
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertFalse(metrics.hasUnmappedCitation)
    }

    @Test fun leadSurvivesAFailedModelExplanation() = runBlocking {
        val failing = object : InferenceGateway by FakeInferenceGateway() {
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> =
                flow { throw IllegalStateException("native decode failed") }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(overview("Friction", friction)) }, failing)
            .research("What is friction?").toList()
        assertTrue(events.none { it is ResearchEvent.Failed })
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.startsWith("## ${SourceLead.HEADING}"))
        assertTrue(answer.contains("could not be completed"))
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }

    @Test fun withoutLeadAFailedGenerationStillFails() = runBlocking {
        val failing = object : InferenceGateway by FakeInferenceGateway() {
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int): Flow<String> =
                flow { throw IllegalStateException("native decode failed") }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, failing)
            .research("What is friction?").toList()
        assertTrue(events.none { it is ResearchEvent.Lead })
        assertTrue(events.last() is ResearchEvent.Failed)
    }
}
