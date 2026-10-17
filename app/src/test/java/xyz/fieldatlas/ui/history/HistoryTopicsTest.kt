package xyz.fieldatlas.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.research.Evidence

class HistoryTopicsTest {
    private val hagia = AnswerRecord("tell me about the hagia sophia", "Hagia Sophia is in Istanbul [S1].", 1_000,
        sources = listOf("Hagia Sophia — Overview"), topic = 1_000)
    private val mosque = AnswerRecord("when was it turned back into a mosque?", "In July 2020 [S1].", 2_000,
        topic = 1_000, readAs = "tell me about the hagia sophia: when was it turned back into a mosque?")
    private val laos = AnswerRecord("what is the capital of laos?", "Vientiane [S1].", 3_000, sources = listOf("Laos — Overview"), topic = 3_000)
    private val free = AnswerRecord("is it free to visit?", "Tickets cost €25 [S1].", 4_000, topic = 1_000)
    // Saved before topics existed: its own conversation.
    private val old = AnswerRecord("how tall is the eiffel tower", "330 m [S1].", 500)

    @Test fun aQuestionAndItsFollowUpsAreOneConversation() {
        val topics = historyTopics(listOf(free, laos, mosque, hagia, old), "")
        assertEquals(listOf(listOf(hagia, mosque, free), listOf(laos), listOf(old)), topics.map { it.turns })
        // The conversation continued most recently comes first, whenever it began.
        assertEquals(listOf(1_000L, 3_000L, 500L), topics.map { it.key })
        assertEquals(free, topics.first().last)
    }

    @Test fun aSearchMatchingOneTurnShowsTheWholeConversation() {
        val topics = historyTopics(listOf(free, laos, mosque, hagia), "july 2020")
        assertEquals(listOf(listOf(hagia, mosque, free)), topics.map { it.turns })
        assertEquals(emptyList<HistoryTopic>(), historyTopics(listOf(free, laos, mosque, hagia), "tokyo"))
    }

    @Test fun theMetaLineCountsQuestionsSourcesAndTime() {
        val topic = historyTopics(listOf(hagia, mosque, free), "").single()
        assertEquals("3 questions · 1 source · 6:37 AM", historyMeta("6:37 AM", topic))
        assertEquals("1 source · 6:37 AM", historyMeta("6:37 AM", HistoryTopic(listOf(laos))))
        assertEquals("No sources · 6:37 AM", historyMeta("6:37 AM", HistoryTopic(listOf(old))))
        val guide = AnswerRecord("a snake bit my friend", "Call for help [S1].", 9_000, sources = listOf("Snakebite"),
            evidence = listOf(Evidence("emergency:snakebite", "emergency:snakebite:0", "Snakebite", "https://www.cdc.gov", "Call for help.", 0.0)))
        assertEquals("1 guide · 6:37 AM", historyMeta("6:37 AM", HistoryTopic(listOf(guide))))
    }

    @Test fun aTopicsKindComesFromWhereItsAnswersCameFrom() {
        fun record(id: String, question: String = "q") = AnswerRecord(question, "a", 1, evidence = listOf(Evidence(id, "$id:0", "t", "s", "x", 0.0)))
        assertEquals(TopicKind.FirstAid, topicKind(HistoryTopic(listOf(record("emergency:burns"), record("wp-1")))))
        assertEquals(TopicKind.Travel, topicKind(HistoryTopic(listOf(record("wvs-36215-0007")))))
        assertEquals(TopicKind.Travel, topicKind(HistoryTopic(listOf(record("osm-place-n1")))))
        assertEquals(TopicKind.Crypto, topicKind(HistoryTopic(listOf(record("eip-4844")))))
        assertEquals(TopicKind.Crypto, topicKind(HistoryTopic(listOf(record("wp-9", "what is a DAO in crypto")))))
        assertEquals(TopicKind.Knowledge, topicKind(HistoryTopic(listOf(record("swp-4329-0000", "what is the capital of japan")))))
    }

    @Test fun aSearchSaysWhereItMatched() {
        val topic = historyTopics(listOf(hagia, mosque, free), "").single()
        val match = historyMatch(topic, "july")!!
        assertEquals("In question 2 of 3", match.where)
        assertEquals("when was it turned back into a mosque?", match.lead)
        assertEquals("July", match.hit)
        assertEquals("In ", match.before)
        // The citation goes without leaving "2020 ." behind.
        assertEquals(" 2020.", match.after)
        val single = historyMatch(HistoryTopic(listOf(laos)), "vientiane")!!
        assertEquals("In the answer", single.where)
        assertEquals("Source · Laos — Overview", historyMatch(HistoryTopic(listOf(laos)), "overview")!!.where)
        assertEquals(null, historyMatch(topic, ""))
    }
}
