package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SourceOrderTest {
    private fun passage(title: String) = Evidence(title, "$title:0000", title, "https://en.wikipedia.org", "$title text.", 0.0)
    private val turkana = passage("Lake Turkana — Overview")
    private val humid = passage("African humid period — Overview")
    private val rift = passage("East African Rift — Overview")

    @Test fun anAnswerCitingOnlyItsSecondPassageNumbersItOne() {
        val (answer, sources) = SourceOrder.citedFirst("The lake is about 109 metres deep [S2].", listOf(turkana, humid))
        assertEquals("The lake is about 109 metres deep [S1].", answer)
        assertEquals(listOf(humid, turkana), sources)
    }

    @Test fun citedPassagesTakeTheOrderTheyAreFirstCitedInAndTheRestFollow() {
        val (answer, sources) = SourceOrder.citedFirst("First [S3]. Then [S1][S3]. Again [S3].", listOf(turkana, humid, rift))
        assertEquals("First [S1]. Then [S2][S1]. Again [S1].", answer)
        assertEquals(listOf(rift, turkana, humid), sources)
        // Applied again, nothing moves: reopening a saved answer keeps its numbers.
        assertEquals(answer to sources, SourceOrder.citedFirst(answer, sources))
    }

    @Test fun anAnswerAlreadyInOrderOrWithoutCitationsIsLeftAlone() {
        val sources = listOf(turkana, humid)
        val inOrder = "Deep [S1]. Wet [S2]."
        assertSame(sources, SourceOrder.citedFirst(inOrder, sources).second)
        assertEquals("No citation here.", SourceOrder.citedFirst("No citation here.", sources).first)
        assertSame(sources, SourceOrder.citedFirst("No citation here.", sources).second)
    }

    @Test fun aNumberPastTheListIsNotTouched() {
        val (answer, sources) = SourceOrder.citedFirst("Cited [S2] and [S9].", listOf(turkana, humid))
        assertEquals("Cited [S1] and [S9].", answer)
        assertEquals(listOf(humid, turkana), sources)
    }
}
