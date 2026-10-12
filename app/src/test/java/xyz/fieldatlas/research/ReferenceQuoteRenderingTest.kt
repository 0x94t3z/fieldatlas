package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.text.MarkdownLiteral
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.research.answerWithSources

class ReferenceQuoteRenderingTest {
    private fun source(text: String) = PromptSource("S1",
        Evidence("rule", "rule:0", "QRS-42 — Specification", "Local source", text, 0.0), excerpt = text)

    private fun rendered(text: String): MarkdownBlock.Quote {
        val raw = PreciseReferenceAnswer.quote(source(text))
        assertEquals(setOf("S1"), AnswerText.citationAudit(raw, 2).citedSourceIds)
        assertFalse(AnswerText.citationAudit(raw, 2).hasUnmappedCitation)
        val finalized = AnswerText.finalized(raw, 2)
        assertEquals(finalized, AnswerText.finalized(finalized, 2))
        return parseAnswerMarkdown(AnswerText.visible(finalized)).single() as MarkdownBlock.Quote
    }

    @Test fun nativeFootnotesAndArrayIndicesAreTextNotAppCitations() {
        val body = "Use table[1]; see [99], [S2], [S#] and (sources: S2); from S2."
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
    }

    @Test fun literalModelDelimitersAndMarkdownSyntaxRemainVisible() {
        val body = "Literal <think>keep this</think>, `value_name`, **flags**, [link](https://example.invalid), \\path."
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
    }

    @Test fun existingCharacterReferencesAreNotDecodedTwice() {
        val body = "The literal spelling is &#91;S2&#93; and &amp;."
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
    }

    @Test fun meaningfulSpacingAndCommaPunctuationSurviveFinalization() {
        val body = "Two  spaces\tand a space before : plus commas,,."
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
    }

    @Test fun malformedOrInvalidCharacterReferencesRemainLiteral() {
        for (text in listOf("&#0;", "&#xD800;", "&#1114112;", "&#x110000;", "&#91", "&#no;")) {
            assertNull(MarkdownLiteral.at(text, 0))
        }
        assertEquals("😀" to 9, MarkdownLiteral.at("&#128512;", 0))
        assertEquals(listOf(MarkdownInline.Text("[S2]")),
            (parseAnswerMarkdown("&#91;S2&#93;").single() as MarkdownBlock.Paragraph).content)
    }

    @Test fun clipboardAndShareTextDecodeOnceWithoutChangingSourceTextOrLinks() {
        val body = "Use table[1], **flags** and literal &#91;S2&#93;."
        val item = source(body)
        val finalized = AnswerText.finalized(PreciseReferenceAnswer.quote(item), 1)
        assertEquals("Question\n\n> $body [S1]\n\nSources\n[S1] QRS-42 — Specification — Local source",
            answerWithSources("Question", finalized, listOf(item.evidence)))
        assertEquals(body, item.evidence.text)
    }

    @Test fun literalCodeLinesParagraphsAndIndentationRemainSeparate() {
        val body = "The readFlag rule is:\n    // guard condition\n    return false;\n\n" +
            "The following paragraph qualifies that rule."
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
        val quote = PreciseReferenceAnswer.quote(source(body))
        assertFalse("Literal line breaks must not enter ordinary Markdown quote joining", quote.contains('\n'))
        assertEquals("> $body [S1]", MarkdownLiteral.decode(AnswerText.finalized(quote, 1)))
        // Ordinary Markdown prose retains its existing line-joining behavior.
        assertEquals(listOf(MarkdownInline.Text("First prose line. Second prose line.")),
            (parseAnswerMarkdown("> First prose line.\n> Second prose line.").single() as MarkdownBlock.Quote).content)
    }

    @Test fun literalCarriageReturnsAndLineFeedsRoundTripWithoutNormalization() {
        val body = "First line.\r\nSecond line.\rThird line.\nFourth line."
        assertEquals(body, MarkdownLiteral.decode(MarkdownLiteral.encode(body)))
        assertEquals(listOf(MarkdownInline.Text(body + " "), MarkdownInline.Citation(1)), rendered(body).content)
    }

    @Test fun malformedReferencePrefixesDoNotConsumeOrSearchForLaterReferences() {
        val malformed = "&#no;".repeat(20_000)
        val text = malformed + "&#91;"
        assertNull(MarkdownLiteral.at(text, 0))
        assertEquals("[" to text.length, MarkdownLiteral.at(text, malformed.length))
        assertEquals(malformed + "[", MarkdownLiteral.decode(text))
        assertEquals(listOf(MarkdownInline.Text(malformed + "[")),
            (parseAnswerMarkdown(text).single() as MarkdownBlock.Paragraph).content)
    }
}
