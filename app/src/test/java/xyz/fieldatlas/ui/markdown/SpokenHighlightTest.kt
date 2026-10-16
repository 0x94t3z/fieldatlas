package xyz.fieldatlas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenHighlightTest {
    @Test fun theSpokenSentenceIsFoundAcrossCitationMarksAndPunctuation() {
        val shown = "The Eiffel Tower is 324 meters tall,⁠[2] which includes an 18 cm lean.⁠[2] It was built in 1889."
        val range = spokenRange(shown, "which includes an 18 cm lean.")!!
        assertEquals("which includes an 18 cm lean", shown.substring(range.first, range.last + 1))
        assertEquals("It was built in 1889", shown.substring(spokenRange(shown, "It was built in 1889.")!!.let { it.first..it.last }))
    }

    @Test fun aParagraphWithoutTheSentenceIsNotMarked() {
        assertNull(spokenRange("Wash the eye with water.", "Call your local emergency number."))
        assertNull(spokenRange("Anything", ""))
    }
}
