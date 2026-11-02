package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFactsTest {
    private val strokes = "The Strokes released their debut album, Is This It, in Australia in July 2001, and in Japan and the UK in August 2001 " +
        "before the release of a subsequent US version in October 2001."

    @Test fun aDateIsGivenOnlyAsPreciselyAsTheNotesGiveIt() {
        assertEquals("The Strokes released Is This It in October 2001.",
            NoteFacts.toNotedPrecision("The Strokes released Is This It on October 27, 2001.", listOf(strokes)))
        assertEquals("* **Release Date:** October 2001", NoteFacts.toNotedPrecision("* **Release Date:** 27 October 2001", listOf(strokes)))
        // A note with the day keeps it, and a date no note gives is the model's own and is left as it is.
        assertEquals("Opened on 20 October 1973.", NoteFacts.toNotedPrecision("Opened on 20 October 1973.", listOf("It opened on 20 October 1973.")))
        assertEquals("Founded on May 8, 1886.", NoteFacts.toNotedPrecision("Founded on May 8, 1886.", listOf(strokes)))
    }

    @Test fun aCitationStaysOnlyWherePassageStatesTheSentencesFigures() {
        val notes = mapOf(1 to "In late 2004, The Strokes revealed plans to release a live album.", 4 to strokes)
        assertEquals("Released in October 2001 [S4]. Label: Epitaph.",
            NoteFacts.withoutUnbackedCitations("Released in October 2001 [S4]. Label: Epitaph.") { notes[it] })
        assertEquals("* **Release Date:** October 2001", NoteFacts.withoutUnbackedCitations("* **Release Date:** October 2001 [S1]") { notes[it] })
        // A sentence with no figure keeps its citation; a number that opens no passage loses it.
        assertEquals("They are a rock band [S1].", NoteFacts.withoutUnbackedCitations("They are a rock band [S1].") { notes[it] })
        assertEquals("Released in 2001.", NoteFacts.withoutUnbackedCitations("Released in 2001 [S9].") { notes[it] })
    }
}
