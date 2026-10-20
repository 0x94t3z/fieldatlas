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

    @Test fun theModelTextsBadgeSaysWhatWasChecked() {
        val head = "## ${AnswerText.MODEL_LABEL}\n\n"
        assertEquals(AnswerCheck(NOT_CHECKED, false), modelCheckBadge(head + "The wall stood 3.6 m tall."))
        assertEquals(AnswerCheck("1 figure matches your sources".replace("matches", "match"), true), modelCheckBadge(head + "The wall stood 3.6 m tall [S1]."))
        assertEquals(AnswerCheck("2 figures match your sources", true),
            modelCheckBadge(head + "- **Height:** 3.6 m [S1].\n- **Floors:** 163 [S2].\n\n_Written from the model's knowledge; a bracketed number marks a figure your saved passages state too._"))
        // A note under the answer: its own citation is a difference, not a match; quoted lines count for nothing.
        assertEquals(AnswerCheck("1 figure differs from your sources", false),
            modelCheckBadge(head + "It is 155 miles long.\n\n${xyz.fieldatlas.research.SavedFactCheck.HEADING} “168 km” [S1] _Check which is right._"))
        assertEquals(AnswerCheck("1 figure match, 1 figure differs", false),
            modelCheckBadge(head + "Built in 1961 [S1]. It is 155 miles long.\n\n${xyz.fieldatlas.research.SavedFactCheck.HEADING} “168 km” [S1] _Check which is right._"))
        assertEquals(AnswerCheck(NOT_CHECKED, false), modelCheckBadge(head + "Prose.\n\n## From saved sources\n\n- \"A quote\" [S1]"))
    }

    @Test fun trailingCitationsMoveToTheFooterWithoutTheSeparatingSpace() {
        val quote = parseAnswerMarkdown("> Buoyancy is an upward force. [S2] [S1]").single() as MarkdownBlock.Quote
        val (body, numbers) = quote.content.trailingCitations()
        assertEquals(listOf(2, 1), numbers)
        assertEquals("Buoyancy is an upward force.", MarkdownBlock.Quote(body).plainText())
    }

    @Test fun inlineCitationsAndUncitedQuotesAreLeftInPlace() {
        val inline = parseAnswerMarkdown("> Boats float [S1] because of buoyancy.").single() as MarkdownBlock.Quote
        assertEquals(inline.content to emptyList<Int>(), inline.content.trailingCitations())
        val plain = parseAnswerMarkdown("> No source here.").single() as MarkdownBlock.Quote
        assertEquals(plain.content to emptyList<Int>(), plain.content.trailingCitations())
    }
}
