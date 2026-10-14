package xyz.fieldatlas.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A broken file must not keep the reader busy: every pattern stays linear on unclosed markup. */
class MarkupTextSpeedTest {
    private fun timed(block: () -> Unit): Long { val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1_000_000 }

    @Test fun unclosedMarkupIsReadQuickly() {
        for (opener in listOf("<a ", "<!--", "<![CDATA[", "<!", "<?")) {
            val broken = "<p>Start</p>" + opener.repeat(40_000) + "end"
            val ms = timed { MarkupText.html(broken) }
            assertTrue("$opener took $ms ms", ms < 2_000)
        }
        val slides = "<draw:page a=\"1\">" .repeat(20_000)
        assertTrue(timed { DocumentTextReader.odpSlides(slides) } < 2_000)
    }

    @Test fun commentsAndCdataStillReadAsBefore() {
        assertEquals("Hello world", MarkupText.html("<p>Hello <!-- hidden --> <![CDATA[world]]></p>").replace(Regex("\\s+"), " ").trim())
    }
}
