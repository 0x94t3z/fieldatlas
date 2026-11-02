package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerSentenceTest {
    private val election = "The 2016 United States presidential election was the 58th presidential election that was held on November 8, 2016. " +
        "Trump chose Indiana governor Mike Pence as his running mate. Trump received 304 electoral votes, 34 more votes than what was " +
        "needed to win, while Clinton received 227. Trump took office on January 20, 2017.\n\nAlso known as: 2016 U.S. Presidential election"

    @Test fun theSentenceThatAnswersIsPicked() {
        assertEquals("Trump received 304 electoral votes, 34 more votes than what was needed to win, while Clinton received 227.",
            AnswerSentence.pick("how many electoral votes did Trump have in 2016", election))
        assertEquals("The Strokes released their debut album, Is This It, in Australia in July 2001, and in the UK in August 2001.",
            AnswerSentence.pick("when did The Strokes release Is This It",
                "The band formed in 1998 in New York. The Strokes released their debut album, Is This It, in Australia in July 2001, and in the UK in August 2001. It sold well."))
        assertEquals("The Cowboys are often known as \"America's Team\".",
            AnswerSentence.pick("why are the Dallas Cowboys considered Americas team",
                "The Dallas Cowboys are a professional American Football team from the Dallas area. They have won 5 Super Bowls. The Cowboys are often known as \"America's Team\"."))
    }

    @Test fun aMeasureIsAFigureInDigitsAndAPronounCarriesTheSubject() {
        val everest = "Mount Everest is the highest mountain on Earth. Mount Everest is in the Himalayas, a tall mountain range in Asia. " +
            "It is about 8,848 metres (29,029 feet) high and one of the Seven Summits. Mount Everest is on the line between two countries: Nepal and China."
        assertEquals("It is about 8,848 metres (29,029 feet) high and one of the Seven Summits.", AnswerSentence.pick("how tall is mount everest", everest))
    }

    @Test fun nothingIsPickedWhenNoSentencePlainlyAnswers() {
        // An explanation, a passage without the asked kind of thing, or one that never names the question's words.
        assertNull(AnswerSentence.pick("how does a heat pump work", "A heat pump moves heat from a cold place to a warm one using a refrigerant cycle."))
        assertNull(AnswerSentence.pick("how many electoral votes did Trump have in 2016", "The election was held in the autumn and many candidates ran in it for a long time."))
        assertNull(AnswerSentence.pick("when did The Strokes release Is This It", "Rainfall in Lisbon peaks in the winter months, in 2001 as in other years."))
    }
}
