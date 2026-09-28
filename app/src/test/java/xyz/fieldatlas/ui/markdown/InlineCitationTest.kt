package xyz.fieldatlas.ui.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCitationTest {
    @Test fun citationStaysBesideItsClaimWithoutChangingLanguageCharacters() {
        val content = listOf(MarkdownInline.Text("Linienstraße 94. "), MarkdownInline.Citation(1),
            MarkdownInline.Text(" 日本語・café and family-owned."))
        assertEquals("Linienstraße 94. [1] 日本語・café and family-owned.",
            annotatedText(content, Color.Gray, Color.Green, 1).text)
    }
    @Test fun nestedCitationsKeepTheirPositionsAndUnavailableOnesAreNotActions() {
        val content = listOf(MarkdownInline.Strong(listOf(MarkdownInline.Text("First "), MarkdownInline.Citation(2))),
            MarkdownInline.Text(" then "), MarkdownInline.Citation(1), MarkdownInline.Citation(9))
        assertEquals("First [2] then [1]", annotatedText(content, Color.Gray, Color.Green, 2).text)
    }
}
