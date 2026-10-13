package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class PassageContentTest {
    @Test fun numberedSectionHeadingIsNotEvidenceForItsArticleTitle() {
        val heading = Evidence("paper", "paper:3", "Autophagy and aging", "fixture://paper", "2.3 Lysosome target analysis", 0.0)
        assertTrue(PassageContent.isNumberedHeadingOnly(heading))
        assertTrue(EvidenceRelevance.keep(listOf(heading), listOf("autophagy"), question = "What is autophagy?").isEmpty())
        assertFalse(PassageContent.isNumberedHeadingOnly(heading, "What is the section heading?"))
        assertFalse(PassageContent.isNumberedHeadingOnly(heading.copy(text = heading.text + "\nThe experiment tested an assay.")))
        assertFalse(PassageContent.isNumberedHeadingOnly(heading.copy(text = "2.3 volts is the measured value.")))
    }
    private val empty = Evidence("article", "article:0", "Does autophagy underlie longevity", "Saved source",
        "September 2009 · https://example.org/article\nSeptember 4th, 2009\nDoes Autophagy Underlie Longevity?\n" +
            "Permalink (With Comments)\nPermalink (No Comments)\nPosted by Reason\nNo Comments Yet\nAdd a Comment", -1.0)

    @Test fun rejectsTitleAndNavigationWithoutAnArticle() {
        assertTrue(PassageContent.isEmptyWebPage(empty))
    }

    @Test fun preservesEvenAShortRealPassageAndItsOriginalText() {
        val substantive = empty.copy(text = empty.text + "\nAutophagy recycles cell components.")
        assertFalse(PassageContent.isEmptyWebPage(substantive))
        assertEquals(empty.text + "\nAutophagy recycles cell components.", substantive.text)
    }

    @Test fun explicitMetadataQuestionsCanStillReadThePageHeader() {
        assertFalse(PassageContent.isEmptyWebPage(empty, "What date was the article published?"))
        assertFalse(PassageContent.isEmptyWebPage(empty, "Who is the posted author?"))
    }

    @Test fun doesNotRejectTablesOrDocumentationAboutCommentButtons() {
        listOf("MAX_VALUE | 4096\nMIN_VALUE | 1", "Use Add a Comment to open the editor.",
            "Add a Comment\nPermalink\nThese buttons link to the discussion.")
            .forEach { assertFalse(PassageContent.isEmptyWebPage(empty.copy(text = it))) }
    }

    @Test fun sourceOnlyRequestsAndEmptyQueriesAlsoRejectEmptyPages() {
        val question = "Using only saved sources, explain autophagy and longevity."
        assertTrue(EvidenceRelevance.keep(listOf(empty), FtsQuery.from(question)!!.terms, question = question).isEmpty())
        assertTrue(EvidenceRelevance.keep(listOf(empty), emptyList()).isEmpty())
    }

    @Test fun linkedNewsletterHeadlinesCannotAnswerAnExplanationButRemainAvailableForContentsRequests() {
        val contents = empty.copy(title = "Research newsletter", text =
            "The Decline of Autophagy in Skin Aging\nReviewing Recent Research into Autophagy and Aging\n" +
                "Talking Up the Potential of the Longevity Industry\nhttps://example.org/article")
        assertTrue(PassageContent.isContentsOnly(contents, "Explain autophagy and longevity"))
        assertFalse(PassageContent.isContentsOnly(contents, "List the newsletter headlines"))
        assertFalse(PassageContent.isContentsOnly(contents.copy(text = contents.text +
            "\nAutophagy recycles damaged cell components."), "Explain autophagy and longevity"))
        assertFalse(PassageContent.isContentsOnly(contents.copy(title = "Research methods"), "Explain research methods"))
    }
}
