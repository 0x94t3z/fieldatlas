package xyz.fieldatlas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMarkdownTest {
    @Test
    fun parsesResearchAnswerStructure() {
        val markdown = """# Finding

Wetlands **slow water** and *spread peaks* [S1].

- Storage
- Habitat

> Evidence varies by site.

""" + "```text\npeak = inflow - outflow\n```"

        val blocks = parseAnswerMarkdown(markdown)

        assertEquals(5, blocks.size)
        assertEquals(
            MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("Finding"))),
            blocks[0],
        )
        assertTrue(blocks[1].toString().contains("Strong(content=[Text(value=slow water)]"))
        assertTrue(blocks[1].toString().contains("Emphasis(content=[Text(value=spread peaks)]"))
        assertTrue(blocks[1].toString().contains("Citation(number=1)"))
        assertEquals(
            MarkdownBlock.ListBlock(
                ordered = false,
                items = listOf(
                    listOf(MarkdownInline.Text("Storage")),
                    listOf(MarkdownInline.Text("Habitat")),
                ),
            ),
            blocks[2],
        )
        assertEquals(
            MarkdownBlock.Quote(listOf(MarkdownInline.Text("Evidence varies by site."))),
            blocks[3],
        )
        assertEquals(
            MarkdownBlock.CodeBlock("peak = inflow - outflow", "text"),
            blocks[4],
        )
    }

    @Test
    fun incompleteStreamingSyntaxRemainsReadable() {
        val blocks = parseAnswerMarkdown("## Result\n**partial answer [S")

        assertEquals("Result", blocks[0].plainText())
        assertEquals("partial answer [S", blocks[1].plainText())
    }

    @Test
    fun orderedListsRetainLargeCitationNumbers() {
        val blocks = parseAnswerMarkdown("1. First\n2. Second [S99]")

        assertEquals(
            MarkdownBlock.ListBlock(
                ordered = true,
                items = listOf(
                    listOf(MarkdownInline.Text("First")),
                    listOf(MarkdownInline.Text("Second "), MarkdownInline.Citation(99)),
                ),
            ),
            blocks.single(),
        )
    }

    @Test
    fun parsesInlineCodeAndAllSupportedUnorderedMarkers() {
        val blocks = parseAnswerMarkdown("* `alpha`\n+ beta\n- gamma")

        assertEquals(
            MarkdownBlock.ListBlock(
                ordered = false,
                items = listOf(
                    listOf(MarkdownInline.Code("alpha")),
                    listOf(MarkdownInline.Text("beta")),
                    listOf(MarkdownInline.Text("gamma")),
                ),
            ),
            blocks.single(),
        )
    }

    @Test
    fun multilineParagraphsBecomeOneReadableParagraph() {
        val blocks = parseAnswerMarkdown("First line\nsecond line")

        assertEquals("First line second line", blocks.single().plainText())
    }

    @Test
    fun unfinishedCodeFenceStillPreservesItsContent() {
        val blocks = parseAnswerMarkdown("```kotlin\nval answer = 42")

        assertEquals(MarkdownBlock.CodeBlock("val answer = 42", "kotlin"), blocks.single())
    }

    @Test
    fun citationsInsideEmphasisRemainNavigableNodes() {
        val block = parseAnswerMarkdown("**Supported [S1]** and *qualified [S2]*").single()

        assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    MarkdownInline.Strong(
                        listOf(MarkdownInline.Text("Supported "), MarkdownInline.Citation(1)),
                    ),
                    MarkdownInline.Text(" and "),
                    MarkdownInline.Emphasis(
                        listOf(MarkdownInline.Text("qualified "), MarkdownInline.Citation(2)),
                    ),
                ),
            ),
            block,
        )
    }
}
