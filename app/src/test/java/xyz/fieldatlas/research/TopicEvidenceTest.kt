package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicEvidenceTest {
    private fun ev(id: String, title: String, text: String) = Evidence("doc-$id", "doc-$id:0", title, "Source", text, 1.0)
    private val overview = ev("o", "Hagia Sophia — Overview", "Hagia Sophia was a church, then a mosque after the conquest of 1453.")
    private val details = ev("d", "Hagia Sophia — Details",
        "It became a museum in 1935. In July 2020 the government ordered it turned back into a mosque.")
    private val other = ev("x", "Mosque — Overview", "A mosque is a place where Muslims worship.")

    @Test fun theEarlierPassageThatSharesTheFollowUpsWordsLeads() {
        val merged = TopicEvidence.merge(listOf(overview, other), TopicEvidence.Carried("when was it turned back into a mosque?", listOf(overview, details)), 8)
        assertEquals(listOf(details, overview, other), merged)
    }

    @Test fun earlierPassagesWithoutTheWordsOnlyFillEmptySlots() {
        val unrelated = ev("u", "Hagia Sophia — Architecture", "The dome rises 55 metres.")
        val merged = TopicEvidence.merge(listOf(overview), TopicEvidence.Carried("is it free to visit?", listOf(unrelated)), 8)
        assertEquals(listOf(overview, unrelated), merged)
        assertEquals(listOf(overview), TopicEvidence.merge(listOf(overview), TopicEvidence.Carried("is it free?", listOf(unrelated)), 1))
    }

    @Test fun nothingCarriedLeavesTheSearchAsItWas() {
        assertEquals(listOf(overview, other), TopicEvidence.merge(listOf(overview, other), null, 8))
        assertEquals(listOf(overview, other), TopicEvidence.merge(listOf(overview, other), TopicEvidence.Carried("why?", emptyList()), 8))
    }

    @Test fun atMostThreeEarlierPassagesLead() {
        val earlier = (1..5).map { ev("m$it", "Mosque $it", "turned back into a mosque, number $it") }
        val found = listOf(ev("n", "New", "fresh result"))
        val merged = TopicEvidence.merge(found, TopicEvidence.Carried("when was it turned back into a mosque", earlier), 8)
        assertEquals("New", merged[TopicEvidence.MAX_LEADING].title)
        assertEquals(6, merged.size)
    }
}
