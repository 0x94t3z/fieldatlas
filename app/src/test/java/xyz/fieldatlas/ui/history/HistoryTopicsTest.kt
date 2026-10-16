package xyz.fieldatlas.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.research.AnswerRecord

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

    @Test fun theMetaLineCountsFollowUpsOrASingleAnswersSources() {
        assertEquals("6:37 AM · 2 follow-ups", historyMeta("6:37 AM", listOf(hagia, mosque, free)))
        assertEquals("6:37 AM · 1 follow-up", historyMeta("6:37 AM", listOf(hagia, mosque)))
        assertEquals("6:37 AM · 1 source", historyMeta("6:37 AM", listOf(laos)))
        assertEquals("6:37 AM · No sources", historyMeta("6:37 AM", listOf(old)))
    }
}
