package xyz.fieldatlas.ui.proof

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.text.MarkdownLiteral

class BenchmarkAnswerPreviewTest {
    @Test fun protectedSourceTextDisplaysAsReadableLiteralText() {
        val original = "Standard table[1]\n    // qualification\n    return false;"
        val stored = "> ${MarkdownLiteral.encode(original)} [S1]"
        assertEquals("> $original [S1]", benchmarkAnswerPreview(stored))
        assertEquals("> ${MarkdownLiteral.encode(original)} [S1]", stored)
    }

    @Test fun existingEntitySpellingDecodesOnlyOnce() {
        val original = "Literal &#91;S2&#93; and <think>source syntax</think>."
        assertEquals(original, benchmarkAnswerPreview(MarkdownLiteral.encode(original)))
    }

    @Test fun ordinaryUnencodedAnswerRemainsUnchanged() {
        val original = "A normal **answer** [S1]."
        assertEquals(original, benchmarkAnswerPreview(original))
    }
}
