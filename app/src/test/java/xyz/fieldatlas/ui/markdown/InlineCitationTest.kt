package xyz.fieldatlas.ui.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCitationTest {
    @Test fun citationStaysBesideItsClaimWithoutChangingLanguageCharacters() {
        val content = listOf(MarkdownInline.Text("Linienstraße 94. "), MarkdownInline.Citation(1),
            MarkdownInline.Text(" 日本語・café and family-owned."))
        assertEquals("Linienstraße 94.[1] 日本語・café and family-owned.",
            annotatedText(content, Color.Gray, Color.Green, 1).text)
    }
    @Test fun nestedCitationsKeepTheirPositionsAndUnavailableOnesAreNotActions() {
        val content = listOf(MarkdownInline.Strong(listOf(MarkdownInline.Text("First "), MarkdownInline.Citation(2))),
            MarkdownInline.Text(" then "), MarkdownInline.Citation(1), MarkdownInline.Citation(9))
        assertEquals("First[2] then[1]", annotatedText(content, Color.Gray, Color.Green, 2).text)
    }

    @Test fun aCitationAttachesToItsWordSoPunctuationIsNotPushedAway() {
        val content = listOf(MarkdownInline.Text("Boil it for one minute "), MarkdownInline.Citation(1), MarkdownInline.Text(". Then cool."))
        assertEquals("Boil it for one minute[1]. Then cool.", annotatedText(content, Color.Gray, Color.Green, 1).text)
        // An unavailable citation draws nothing, so the space before it stays.
        val unavailable = listOf(MarkdownInline.Text("Boil it "), MarkdownInline.Citation(4), MarkdownInline.Text("."))
        assertEquals("Boil it .", annotatedText(unavailable, Color.Gray, Color.Green, 1).text)
    }
}
