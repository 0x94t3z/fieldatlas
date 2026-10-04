package xyz.fieldatlas.attachments

import org.junit.Assert.*
import org.junit.Test

class MarkupTextTest {
    @Test fun htmlKeepsVisibleTextAndStructureOnly() {
        val html = """<!DOCTYPE html><html><head><title>Trail</title><style>body{color:red}</style>
            <script>var secret = "do not show";</script></head><body>
            <!-- hidden comment --><h1>Lost&nbsp;on a trail?</h1>
            <p>Stop, think, observe &amp; plan.</p><ul><li>Stay put</li><li>Use a whistle: 3 blasts</li></ul>
            <table><tr><th>Signal</th><th>Meaning</th></tr><tr><td>3 blasts</td><td>Help</td></tr></table>
            <p>Temp &#8805; 30&#x00B0;C</p></body></html>"""
        val text = MarkupText.html(html)
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("color:red"))
        assertFalse(text.contains("hidden comment"))
        assertTrue(text, text.contains("Lost on a trail?\nStop, think, observe & plan."))
        assertTrue(text, text.contains("• Stay put\n• Use a whistle: 3 blasts"))
        assertTrue(text, text.contains("Signal | Meaning"))
        assertTrue(text, text.contains("3 blasts | Help"))
        assertTrue(text, text.contains("Temp ≥ 30°C"))
    }
    @Test fun unknownEntitiesAndStrayBracketsSurvive() {
        assertEquals("a < b &unknown; c", MarkupText.html("a &lt; b &unknown; c"))
        assertEquals("x", MarkupText.html("<p>x"))
    }
}
