package xyz.fieldatlas.ui.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class ReadableLicenceTest {
    @Test fun proseWrapsNaturallyButParagraphsRemainSeparate() {
        assertEquals(listOf("Copyright 2026 Contributors", "This licence applies to every copy."),
            readableLicenceParagraphs("Copyright 2026\nContributors\n\nThis licence applies\nto every copy."))
    }

    @Test fun numberedListsAndRulesKeepTheirLineStructure() {
        assertEquals(listOf("1. Keep the notice.\n2. Include the licence.", "------\nTERMS\n------"),
            readableLicenceParagraphs("1. Keep the notice.\n2. Include the licence.\n\n------\nTERMS\n------"))
    }

    @Test fun reflowDoesNotLoseChangeOrReorderLegalWords() {
        val original = "  Copyright (c) 2026\r\nThe Authors\r\n\r\n Permission is granted,\r\nprovided that:\r\n\r\n1. Keep ALL notices.\r\n2. Keep this licence. "
        fun words(value: String) = value.trim().split(Regex("\\s+"))
        assertEquals(words(original), words(readableLicenceParagraphs(original).joinToString("\n\n")))
    }

    @Test fun headingsAndNumberedTermsDoNotInterpretLiteralMarkdown() {
        val blocks = licenceReadingBlocks("PREAMBLE\nText with *literal* and _names_.\n\n3) Keep ALL notices.\n    Continued terms.\n\n(a) Another condition.")
        assertEquals(LicenceReadingBlock.Heading("PREAMBLE"), blocks[0])
        assertEquals(LicenceReadingBlock.Paragraph("Text with *literal* and _names_."), blocks[1])
        assertEquals(LicenceReadingBlock.Item("3)", "Keep ALL notices. Continued terms."), blocks[2])
        assertEquals(LicenceReadingBlock.Item("(a)", "Another condition."), blocks[3])
    }

    @Test fun apacheInlineSectionKeepsTheFirstSentence() {
        assertEquals(listOf(LicenceReadingBlock.Heading("2. Grant of Copyright License."),
            LicenceReadingBlock.Paragraph("Subject to the terms and conditions of this License.")),
            licenceReadingBlocks("2. Grant of Copyright License. Subject to the terms and conditions of\nthis License."))
    }

    @Test fun completeBundledLicencesKeepEveryWordInOrder() {
        val files = listOf(File("src/main/assets/licenses"), File("src/main/assets/ocr/licenses"))
            .flatMap { it.listFiles().orEmpty().toList() }.filter { it.extension != "md" }
        assertTrue("Bundled licence fixtures must exist", files.size >= 12)
        fun words(value: String) = value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        files.forEach { file ->
            val original = file.readText()
            assertEquals("Changed legal text in ${file.name}", words(original),
                words(licenceReadingBlocks(original).joinToString("\n") { it.text }))
        }
    }

    @Test fun longAllCapsDisclaimerRemainsBodyText() {
        val text = "THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND,\nEXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO MERCHANTABILITY."
        assertTrue(licenceReadingBlocks(text).single() is LicenceReadingBlock.Paragraph)
        assertTrue(licenceReadingBlocks("").isEmpty())
    }
}
