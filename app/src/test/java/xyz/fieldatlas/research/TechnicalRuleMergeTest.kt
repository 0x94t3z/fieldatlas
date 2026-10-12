package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class TechnicalRuleMergeTest {
    private val question = "Under QRS-42, what does readFlag return for 0xface?"
    private fun row(id: String, body: String, score: Double = -2.0, title: String = "QRS-42: Specification") =
        Evidence(id, "$id:0", title, "fixture://$id", body, score)
    private fun noise(id: Int) = row("noise-$id", "Under QRS-42, readFlag returns an implementation result.", -100.0 - id)
    private fun rule(id: String, result: String) = row(id, "The readFlag function returns $result for 0xface.")

    @Test fun `a rule in the last of fifty packs survives every bounded merge and final take`() {
        val exact = rule("exact", "false")
        val runs = (0 until 49).map { listOf(noise(it)) } + listOf(listOf(exact))
        for (ordered in listOf(runs, runs.reversed())) {
            val result = MultiKnowledgeRetriever.mergeRelevantEvidence(ordered, "$question flag value result", question, 24)
            assertEquals(24, result.size)
            assertEquals(exact, result.take(8).first())
            assertEquals(exact.score, result.first().score, 0.0)
        }
    }

    @Test fun `two contradictory exact passages survive pack round robin before summaries`() {
        val yes = rule("yes", "true")
        val no = rule("no", "false")
        val runs = (0 until 49).map { listOf(noise(it)) } + listOf(listOf(noise(50), yes, no))
        for (ordered in listOf(runs, runs.reversed())) {
            val result = MultiKnowledgeRetriever.mergeRelevantEvidence(ordered, question, question, 24).take(8)
            assertEquals(listOf(yes, no), result.take(2))
        }
    }

    @Test fun `foreign owner and near literal do not receive rule priority`() {
        val exact = rule("exact", "7")
        val foreign = row("foreign", "QRS-42 readFlag returns false for 0xface.", -900.0, "ABC-99: QRS-42 extension")
        val near = row("near", "readFlag returns false for 0xface0.", -900.0)
        val result = MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(foreign), listOf(near), listOf(exact)), question, question, 1)
        assertEquals(listOf(exact), result)
    }

    @Test fun `planner vocabulary cannot activate focus absent from original question`() {
        val original = "What is QRS-42?"
        val first = row("overview", "QRS-42 is a synthetic test standard.")
        val exact = rule("exact", "false")
        val result = MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(first), listOf(exact)), question, original, 1)
        assertEquals(listOf(first), result)
    }

    @Test fun `different source with shared chunk identity stays distinct`() {
        val one = rule("shared", "false")
        val two = one.copy(source = "fixture://other-version", text = "The readFlag function returns true for 0xface.")
        val result = MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(noise(0)), listOf(one), listOf(two)), question, question, 2)
        assertEquals(listOf(one, two), result)
    }
}
