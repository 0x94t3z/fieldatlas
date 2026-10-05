package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** From the 1.3.0 suite review: the Autophagy article was found but filtered out. */
class DefinitionQuestionRelevanceTest {
    private val autophagy = Evidence("swp-1", "swp-1:0000", "Autophagy — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
        "Autophagy (which means \"eating itself\"), is one of the basic cell mechanisms. It allows the controlled breaking down of cell parts which do not work properly.", 1.0)

    @Test fun explainXAndDistinguishAsksAboutX() {
        assertEquals(listOf("autophagy"), EvidenceRelevance.overviewSubjects(
            "Explain autophagy and distinguish a proposed role in aging from evidence that an intervention extends human life."))
        assertEquals(listOf("mitosis"), EvidenceRelevance.overviewSubjects("Explain mitosis and compare it with binary fission."))
    }

    @Test fun aDefinitionWithAParentheticalAndCommaCounts() {
        assertTrue(EvidenceRelevance.hasOverviewStatement(autophagy, listOf("autophagy")))
    }

    @Test fun theDefiningArticleSurvivesTheRelevanceGate() {
        val question = "Explain autophagy and distinguish a proposed role in aging from evidence that an intervention extends human life."
        val kept = EvidenceRelevance.keep(listOf(autophagy), FtsQuery.from(question)!!.terms, question = question)
        assertEquals(listOf("Autophagy — Overview"), kept.map { it.title })
    }

    private val japan = Evidence("swp-2", "swp-2:0000", "History of Japan — Overview", "https://simple.wikipedia.org/w/index.php?oldid=2",
        "The history of Japan begins in prehistoric times, before any written records were kept. Archaeologists have found proof " +
            "that people began living in Japan when the last Ice Age ended. The oldest texts we have from Japan were written in the 8th century AD.", 1.0)

    @Test fun everydayPhrasingsNameTheirTopic() {
        assertEquals(listOf("japan history", "history of japan"), EvidenceRelevance.overviewSubjects("tell me japan history"))
        assertEquals(listOf("Japan's history", "history of Japan"), EvidenceRelevance.overviewSubjects("Tell me Japan's history"))
        assertEquals(listOf("photosynthesis"), EvidenceRelevance.overviewSubjects("What do you know about photosynthesis?"))
        assertEquals(listOf("Roman Empire"), EvidenceRelevance.overviewSubjects("Give me an overview of the Roman Empire"))
        // A request for a procedure or a reason is not a topic overview.
        assertEquals(emptyList<String>(), EvidenceRelevance.overviewSubjects("Tell me how to purify water"))
        assertEquals(emptyList<String>(), EvidenceRelevance.overviewSubjects("Tell me why the sky is blue"))
    }

    @Test fun aHistoryQuestionGetsTheVerbatimOpeningOfItsArticle() {
        val question = "tell me japan history"
        assertTrue(EvidenceRelevance.isRequestedOverview(japan, question))
        val lead = SourceLead.select(question, listOf(PromptSource("S1", japan, japan.text)))!!
        assertEquals("S1", lead.citationId)
        assertTrue(lead.text, lead.text.startsWith("The history of Japan begins in prehistoric times"))
    }

    @Test fun aSubjectNoPassageDefinesKeepsThePassagesThatNameIt() {
        val question = "What is danksharding and how does it help Ethereum scale?"
        val upgrade = Evidence("wp-eth", "wp-eth:0040", "Ethereum — History > Upgrades in 2024", "https://en.wikipedia.org/w/index.php?oldid=3",
            "The 2024 upgrade added EIP-4844 (proto-danksharding), which gave Ethereum blobs: data kept only for a few weeks, " +
                "so rollups pay far less to publish their batches.", 1.0)
        val unrelated = Evidence("wp-gas", "wp-gas:0002", "Ethereum — Design > Gas", "https://en.wikipedia.org/w/index.php?oldid=4",
            "Gas is the unit that measures the work a transaction asks Ethereum to do.", 1.0)
        val kept = EvidenceRelevance.keep(listOf(unrelated, upgrade), FtsQuery.from(question)!!.terms, question = question)
        assertEquals(listOf("Ethereum — History > Upgrades in 2024"), kept.map { it.title })
        // A passage that defines the subject is still the only kind kept when one exists.
        val defined = Evidence("wp-dank", "wp-dank:0000", "Danksharding — Overview", "https://en.wikipedia.org/w/index.php?oldid=5",
            "Danksharding is a design in which Ethereum blocks carry large blobs of data for rollups.", 1.0)
        assertEquals(listOf("Danksharding — Overview"),
            EvidenceRelevance.keep(listOf(upgrade, defined), FtsQuery.from(question)!!.terms, question = question).map { it.title })
    }
}
