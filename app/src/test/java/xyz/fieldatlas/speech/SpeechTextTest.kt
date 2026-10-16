package xyz.fieldatlas.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyGuide
import xyz.fieldatlas.emergency.GuideSection
import xyz.fieldatlas.emergency.GuideSource

class SpeechTextTest {
    @Test fun numbersPeopleDialAreSaidDigitByDigit() {
        assertEquals("Call one one two or nine one one now.", SpeechText.spoken("Call 112 or 911 now."))
        assertEquals("Dial one one five for an ambulance.", SpeechText.spoken("Dial 115 for an ambulance."))
        // A quantity is still a number.
        assertEquals("The tower is 324 metres tall.", SpeechText.spoken("The tower is 324 m tall."))
    }

    @Test fun moneyTemperatureUnitsAndRangesAreSaidAsWords() {
        assertEquals("Tickets cost 25 euros, about 27 dollars.", SpeechText.spoken("Tickets cost €25 (about $27)."))
        assertEquals("It's 40 degrees Celsius, so drink 1.5 litres of water.", SpeechText.spoken("It's 40°C, so drink 1.5 L of water."))
        assertEquals("Take 5 to 10 milligrams.", SpeechText.spoken("Take 5–10 mg."))
        assertEquals("Fees fell 30 percent.", SpeechText.spoken("Fees fell 30%."))
        assertEquals("It is 1063 feet tall.", SpeechText.spoken("It is 1,063 ft tall."))
    }

    @Test fun timesReadTheWayPeopleSayThem() {
        assertEquals("It opens 9 30 to 5 pm.", SpeechText.spoken("It opens 9:30–17:00."))
        assertEquals("Back at 8 o'clock.", SpeechText.spoken("Back at 8:00."))
        assertEquals("Last entry 4 oh 5 pm.", SpeechText.spoken("Last entry 16:05."))
    }

    @Test fun citationsAndMarkdownAreNeverRead() {
        assertEquals("The Eiffel Tower is 324 metres tall.", SpeechText.spoken("**The Eiffel Tower** is 324 m tall [S1]."))
        assertEquals("E I P 1559 changed fees.", SpeechText.spoken("EIP-1559 changed fees.⁠[2]"))
    }

    @Test fun sentencesSplitAtFullStopsButNotAbbreviations() {
        assertEquals(listOf("Wash the eye, e.g. with tap water.", "Then get help."),
            SpeechText.sentences("Wash the eye, e.g. with tap water. Then get help."))
        assertEquals(listOf("Is it safe?", "Yes."), SpeechText.sentences("Is it safe? Yes."))
    }

    @Test fun longSentencesAreMadeClauseByClauseButShortListsStayWhole() {
        assertEquals(listOf("The Eiffel Tower has a total height of 324 metres,", "1063 feet, which includes a 24 metre,",
            "79 foot, aerial antenna on top."),
            SpeechText.clauses("The Eiffel Tower has a total height of 324 metres, 1063 feet, which includes a 24 metre, 79 foot, aerial antenna on top."))
        assertEquals(listOf("Bring water, food, a torch, and a radio."), SpeechText.clauses("Bring water, food, a torch, and a radio."))
        assertEquals(listOf("Call now."), SpeechText.clauses("Call now."))
    }

    @Test fun anAnswerIsReadWithoutItsLabelsOrSavedExcerpts() {
        val answer = """
            ## Model explanation—not verified against saved sources

            The tower is tall [S1]. It was built in 1889 [S2].

            ## Saved excerpts

            > The Eiffel Tower is a wrought-iron lattice tower.
        """.trimIndent()
        val read = SpeechText.answer(answer)
        assertEquals(listOf("The tower is tall.", "It was built in 1889."), read.map { it.shown })
        assertFalse(read.any { "Saved" in it.spoken || "wrought" in it.spoken })
    }

    @Test fun aGuideOpensWithTheCallLineAndKeepsItsOrder() {
        val guide = EmergencyGuide("eye", "Eye injuries", "medical", sections = listOf(
            GuideSection(heading = "Summary", paragraphs = listOf("Chemicals can burn your eyes. Wash out your eye right away.")),
            GuideSection(steps = listOf("Call 911.", "Keep the eye open under water.")),
        ), source = GuideSource(publisher = "NIH/NLM", title = "Eye", url = "https://medlineplus.gov/eyeinjuries.html", retrieved = "2026-10-16", license = "Public domain", sha256 = "0"))
        val read = SpeechText.guide(guide)
        assertEquals(SpeechText.CALL_FIRST, read.first().shown)
        assertTrue(read.first().spoken.contains("one one two"))
        assertEquals(listOf("Summary", "Chemicals can burn your eyes.", "Wash out your eye right away.", "Call 911.", "Keep the eye open under water."),
            read.drop(1).map { it.shown })
        assertEquals("Call nine one one.", read[4].spoken)
    }
}
