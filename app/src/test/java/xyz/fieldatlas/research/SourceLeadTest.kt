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
        // A saved place-guide comparison retains its lead + model route; ordinary
        // encyclopedia explanations are now completed from original passages.
        val inference = FakeInferenceGateway(listOf("The day pass covers every line."))
        val events = ResearchOrchestrator(guideComparisonRetriever(), inference)
            .research(guideComparisonQuestion).toList()
        val leadIndex = events.indexOfFirst { it is ResearchEvent.Lead }
        val firstToken = events.indexOfFirst { it is ResearchEvent.Token }
        assertTrue(leadIndex >= 0 && leadIndex < firstToken)
        val lead = (events[leadIndex] as ResearchEvent.Lead).text
        val answer = events.filterIsInstance<ResearchEvent.Token>().last()
        assertTrue(answer.replace)
        assertTrue(answer.text.startsWith(lead))
        assertTrue(answer.text.contains("not verified against saved sources"))
        assertTrue(answer.text.contains("The day pass covers every line."))
        assertTrue((events.last() as ResearchEvent.Complete).metrics.generatedTokenCount > 0)
        val metrics = (events.last() as ResearchEvent.Complete).metrics
        assertEquals(setOf("S1"), metrics.citedSourceIds)
        assertFalse(metrics.hasUnmappedCitation)
    }

    @Test fun leadSurvivesAFailedModelExplanation() = runBlocking {
        val failing = object : InferenceGateway by FakeInferenceGateway() {
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> =
                flow { throw IllegalStateException("native decode failed") }
        }
        val events = ResearchOrchestrator(guideComparisonRetriever(), failing)
            .research(guideComparisonQuestion).toList()
        assertTrue(events.none { it is ResearchEvent.Failed })
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.startsWith("## ${SourceLead.HEADING}"))
        assertTrue(answer.contains("could not be completed"))
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }

    private val guideComparisonQuestion = "What is the difference between a single metro ticket and a day pass in Avelton?"

    private fun guideComparisonRetriever(): Retriever {
        val transit = Evidence("wvs-avelton", "wvs-avelton:0", "Avelton — Get around > By metro",
            "https://en.wikivoyage.org/w/index.php?oldid=1",
            "A single metro ticket costs 2 euros and is valid for 90 minutes. A day pass costs 8 euros and covers every line.", 0.0)
        return object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                if (phrase == "Avelton" && "Get around" in sections) listOf(transit) else emptyList()
        }
    }

    @Test fun withoutLeadAFailedGenerationStillFails() = runBlocking {
        val failing = object : InferenceGateway by FakeInferenceGateway() {
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> =
                flow { throw IllegalStateException("native decode failed") }
        }
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, failing)
            .research("What is friction?").toList()
        assertTrue(events.none { it is ResearchEvent.Lead })
        assertTrue(events.last() is ResearchEvent.Failed)
    }
    @Test fun anAbbreviationOpeningABracketContinuesTheSentence() {
        val split = SourceLead.sentences("Change money at a bank (e.g. Attijariwafa) for the best rate. Avoid the street changers.")
        assertEquals(2, split.size)
        assertTrue(split.first(), split.first().endsWith("(e.g. Attijariwafa) for the best rate."))
    }

    @Test fun anExtendedQuoteStopsBeforeAListLeadInAndTwoQuotesShareTheRoom() {
        val fares = Evidence("wvs-f", "wvs-f:0", "Paris — Get around > Fares", "https://en.wikivoyage.org/w/index.php?oldid=1",
            "The card can be loaded with fares. It cannot be loaded with credit.\nThe following cards are available:\n- Navigo Easy\n- Navigo Découverte\nA bank card can also be used.", 0.0)
        val extended = SourceLead.extend(SourceLead.Lead("S1", "The card can be loaded with fares."), listOf(PromptSource("S1", fares)))
        assertEquals("The card can be loaded with fares. It cannot be loaded with credit.", extended.text)
        val long = (1..40).joinToString(" ") { "Sentence number $it says a little more about the fares." }
        val source = PromptSource("S1", Evidence("wvs-l", "wvs-l:0", "Paris — Get around > Fares", "https://en.wikivoyage.org/w/index.php?oldid=1", long, 0.0))
        val alone = SourceLead.extend(SourceLead.Lead("S1", "Sentence number 1 says a little more about the fares."), listOf(source))
        val shared = SourceLead.extend(SourceLead.Lead("S1", "Sentence number 1 says a little more about the fares.", listOf("S1" to "Sentence number 30 says a little more about the fares.")), listOf(source))
        assertTrue(alone.text.length > 700 && alone.text.length <= 1_000)
        assertTrue(shared.text.length <= 700 && shared.more.single().second.length <= 700)
    }
}
