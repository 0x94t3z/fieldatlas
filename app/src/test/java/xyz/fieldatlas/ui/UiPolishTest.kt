package xyz.fieldatlas.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.proof.ProofFact
import xyz.fieldatlas.proof.ProofOrigin
import xyz.fieldatlas.proof.ProofState
import xyz.fieldatlas.ui.markdown.MarkdownBlock
import xyz.fieldatlas.ui.markdown.MarkdownInline
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.markdown.shortSourceTitle
import xyz.fieldatlas.ui.more.phoneFacts
import xyz.fieldatlas.ui.presentation.displayVersion
import xyz.fieldatlas.ui.research.answerCardPreview
import xyz.fieldatlas.ui.research.readableHours

class UiPolishTest {
    @Test fun mapHoursReadAsPlainWords() {
        assertEquals("Daily 12:00–23:00", readableHours("Mo-Su 12:00-23:00"))
        assertEquals("Mon–Fri 09:00–19:00 · Sat 09:00–14:00", readableHours("Mo-Fr 09:00-19:00; Sa 09:00-14:00"))
        assertEquals("Sat–Sun, holidays 13:00–23:00", readableHours("Sa-Su,PH 13:00-23:00"))
        assertEquals("Sun closed", readableHours("Su off"))
        assertEquals("Open 24 hours", readableHours("24/7"))
        // An unfamiliar rule is still shown, not dropped.
        assertEquals("sunrise–sunset", readableHours("sunrise–sunset"))
    }

    @Test fun citationsFollowTheirSentencesPunctuation() {
        val inlines = (parseAnswerMarkdown("Keep the cord away from the worktop [S1]. Do not open the lid [S1][S2].").single()
            as MarkdownBlock.Paragraph).content
        assertEquals(listOf(
            MarkdownInline.Text("Keep the cord away from the worktop."), MarkdownInline.Citation(1),
            MarkdownInline.Text(" Do not open the lid."), MarkdownInline.Citation(1), MarkdownInline.Citation(2),
        ), inlines)
    }

    @Test fun punctuationAfterBoldTextAlsoMovesBeforeTheCitation() {
        val inlines = (parseAnswerMarkdown("Fire **112** [S1].").single() as MarkdownBlock.Paragraph).content
        assertEquals(listOf(MarkdownInline.Text("Fire "), MarkdownInline.Strong(listOf(MarkdownInline.Text("112"))),
            MarkdownInline.Text("."), MarkdownInline.Citation(1)), inlines)
        val adjacent = (parseAnswerMarkdown("Fire **112**[S1].").single() as MarkdownBlock.Paragraph).content
        assertEquals(listOf(MarkdownInline.Text("Fire "), MarkdownInline.Strong(listOf(MarkdownInline.Text("112"))),
            MarkdownInline.Text("."), MarkdownInline.Citation(1)), adjacent)
    }

    @Test fun sourceLabelsAndVersionsReadCleanly() {
        assertEquals("Japanese phrasebook · Basics", shortSourceTitle("Japanese phrasebook — Phrase list > Basics"))
        assertEquals("ui-check-kettle-manual.txt", shortSourceTitle("ui-check-kettle-manual.txt"))
        assertEquals("2026.10.12", displayVersion("2026.10.12-native-compressed-preview"))
        assertEquals("1.2.0", displayVersion("1.2.0"))
        assertEquals("preview", displayVersion("preview"))
    }

    @Test fun phoneDetailsLeadWithWhatPeopleRead() {
        fun fact(label: String, value: String, state: ProofState = ProofState.Info) = ProofFact(label, value, ProofOrigin.OsReport, state)
        val (everyday, internals) = phoneFacts(listOf(fact("Device", "Xiaomi 23108RN04Y"), fact("Android API", "33"),
            fact("ABIs", "arm64-v8a"), fact("Physical memory", "6.01 GB"), fact("PSS", "1.77 GB"),
            fact("RSS", "Not measured", ProofState.Missing)))
        assertEquals(listOf("Device", "Android", "Memory"), everyday.map { it.label })
        assertEquals("Android 13 (API 33)", everyday[1].value)
        assertEquals(listOf("ABIs", "PSS"), internals.map { it.label })
    }

    @Test fun aListAnswerPreviewNamesWhatFollows() {
        val preview = answerCardPreview("Nearest saved places to your current location:\n\n- **Apotek Sehat**, 27 km north-east [S1]")
        assertEquals("Nearest saved places to your current location: Apotek Sehat, 27 km north-east", preview)
    }
}
