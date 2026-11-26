package xyz.fieldatlas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.research.AnswerText
import xyz.fieldatlas.research.SourceLead

class AnswerSectionTest {
    private fun heading(markdown: String) = parseAnswerMarkdown(markdown).single() as MarkdownBlock.Heading

    @Test fun appWrittenSectionsGetCompactTitlesAndProvenanceBadges() {
        assertEquals(AnswerSection("Model explanation", NOT_CHECKED, quoted = false),
            answerSection(heading("## ${AnswerText.MODEL_LABEL}")))
        assertEquals(AnswerSection("From the saved reference", "Quoted", quoted = true),
            answerSection(heading("## ${SourceLead.HEADING}")))
        assertEquals(AnswerSection("From saved sources", "Quoted", quoted = true),
            answerSection(heading("## From saved sources")))
        assertEquals(AnswerSection("From your saved sources", "Quoted", quoted = true),
            answerSection(heading("## ${xyz.fieldatlas.research.GroundedKnowledgeAnswer.HEADING}")))
        // Model-written headings keep their own text and get no badge.
        assertNull(answerSection(heading("## Key characteristics")))
    }

    @Test fun trailingCitationsMoveToTheFooterWithoutTheSeparatingSpace() {
        val quote = parseAnswerMarkdown("> Buoyancy is an upward force. [S2] [S1]").single() as MarkdownBlock.Quote
        val (body, numbers) = quote.content.trailingCitations()
        assertEquals(listOf(2, 1), numbers)
        assertEquals("Buoyancy is an upward force.", MarkdownBlock.Quote(body).plainText())
    }

    @Test fun aQuotedPassageShowsItsMathAsReadableText() {
        // As the app quotes a saved passage: its symbols encoded, the citation after it.
        val saved = "Write the numbers vertically so the decimal points line up. | \$\\begin{matrix} 3.7 \\\\ {+12.4}_{\\text{_____}} \\end{matrix}\$"
        val quote = parseAnswerMarkdown("> ${xyz.fieldatlas.text.MarkdownLiteral.encode(saved)} [S1]").single() as MarkdownBlock.Quote
        val (body, numbers) = quote.content.trailingCitations()
        assertEquals(listOf(1), numbers)
        assertEquals("Write the numbers vertically so the decimal points line up. | 3.7; +12.4", MarkdownBlock.Quote(body).plainText())
        // The answer itself still holds the passage word for word.
        assertEquals("> ${xyz.fieldatlas.text.MarkdownLiteral.encode(saved)} [S1]",
            AnswerText.visible("> ${xyz.fieldatlas.text.MarkdownLiteral.encode(saved)} [S1]"))
    }

    @Test fun inlineCitationsAndUncitedQuotesAreLeftInPlace() {
        val inline = parseAnswerMarkdown("> Boats float [S1] because of buoyancy.").single() as MarkdownBlock.Quote
        assertEquals(inline.content to emptyList<Int>(), inline.content.trailingCitations())
        val plain = parseAnswerMarkdown("> No source here.").single() as MarkdownBlock.Quote
        assertEquals(plain.content to emptyList<Int>(), plain.content.trailingCitations())
    }
}
