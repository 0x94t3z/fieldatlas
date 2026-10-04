package xyz.fieldatlas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class NestedListMarkdownTest {
    @Test fun indentedItemsKeepTheirDepth() {
        val blocks = parseAnswerMarkdown("- Apply first aid while waiting.\n  - Lay or sit down.\n  - Remove rings.\n- Keep calm.")
        val list = blocks.single() as MarkdownBlock.ListBlock
        assertEquals(4, list.items.size)
        assertEquals(listOf(0, 1, 1, 0), list.depths)
    }
    @Test fun aNestedBulletUnderANumberedStepStaysInTheList() {
        val list = parseAnswerMarkdown("1. Call for help.\n  - Use 112.\n2. Start CPR.").single() as MarkdownBlock.ListBlock
        assertEquals(listOf(0, 1, 0), list.depths)
    }

    @Test fun finalizingAnAnswerKeepsNestedIndentation() {
        val text = xyz.fieldatlas.research.AnswerText.finalized("- Apply first aid.\n  - Lay or sit down.\n- Keep  calm [S1].", 1)
        val list = parseAnswerMarkdown(text).single() as MarkdownBlock.ListBlock
        assertEquals(listOf(0, 1, 0), list.depths)
        assertEquals("- Apply first aid.\n  - Lay or sit down.\n- Keep calm [S1].", text)
    }
}
