package xyz.fieldatlas.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcerptRefusalTest {
    @Test
    fun repliesThatOnlySayTheExcerptsMissTheQuestionAreRefusals() {
        listOf(
            "No excerpt supports information about swallowed batteries, children's health emergencies, or first aid. " +
                "The provided text covers computer proofs and NASCAR racing history.",
            "The provided excerpts do not confirm that muscle loss occurs with age, nor do they state that exercise can reverse it.",
            "The provided excerpts do not explain what high blood pressure does to the body over time or define specific numbers.",
            "## Model explanation—not verified against saved sources\n\nThe provided excerpts do not state whether tap water in Mexico City is safe to drink.",
            "None of the excerpts mention the ferry timetable.",
            "Unfortunately, the selected sources do not cover this.",
            "There is no information in the excerpts about the exchange rate.",
            "Japan's Imperial Palace opens on January 2nd. Therefore, the question cannot be answered from the supplied evidence.",
        ).forEach { assertTrue(it, ExcerptRefusal.opensWith(it)) }
    }

    @Test
    fun answersThatNameAGapAfterAnsweringAreKept() {
        listOf(
            "Kilimanjaro is about 5,895 m high, the tallest mountain in Africa. The excerpts do not give the best season to climb.",
            "Resistance training builds muscle at any age, although the excerpts do not give a weekly amount.",
            "Rayleigh scattering makes the sky blue because shorter wavelengths scatter more.",
            "Plant sources do not contain vitamin B12, so vegans need a supplement.",
            "",
        ).forEach { assertFalse(it, ExcerptRefusal.opensWith(it)) }
    }

    @Test
    fun aLongAnswerThatDeclinesOnePartIsNotARefusal() {
        val answer = "Canberra became the capital as a compromise between Sydney and Melbourne. ".repeat(12) +
            "Whether Parliament sits this week cannot be answered from the provided excerpts."
        assertFalse(ExcerptRefusal.opensWith(answer))
    }
}
