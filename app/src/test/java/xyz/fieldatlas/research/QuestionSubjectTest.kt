package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSubjectTest {
    @Test fun namedTitlesRequireCompleteContiguousWordsRatherThanSharedTerms() {
        assertTrue(QuestionSubject.containsMultiwordTitle("Why can a sealing-membrane fail?", "Sealing membrane"))
        assertTrue(QuestionSubject.containsMultiwordTitle("Why can a SEALING MEMBRANE fail?", "Sealing membrane"))
        for (question in listOf("Why can sealing membranes fail?", "Why can membrane sealing fail?",
            "Why can sealing fail? Membrane damage was seen.")) {
            assertFalse(question, QuestionSubject.containsMultiwordTitle(question, "Sealing membrane"))
        }
        assertFalse(QuestionSubject.containsMultiwordTitle("Why can water leak?", "Water"))
    }

    @Test fun onlyAnExplicitLeadingSourceScopeIsRemoved() {
        for (prefix in listOf("According to the saved sources, ", "Based on my notes: ",
            "According to local sources ", "According to the saved travel guides; ",
            "  ACCORDING TO THE PROVIDED DOCUMENTS, ")) {
            assertEquals(prefix, "what causes tides?", QuestionSubject.text(prefix + "what causes tides?"))
        }
        for (question in listOf("What are sources of information?", "According sources of information",
            "According to Einstein, why is time relative?", "What do notes say according to local sources?",
            "According to the sources of the Nile", "Based on my notes")) {
            assertEquals(question, QuestionSubject.text(question))
        }
    }

    @Test fun discoveryNormalizationDoesNotEnableModelOnlyAnswers() {
        val question = "According to the saved sources, what causes tides?"
        assertFalse(PromptBuilder.allowsModelExplanation(question))
        val prompt = PromptBuilder.build(question, emptyList(), 2048)
        assertFalse(prompt.mixedAnswer)
        assertEquals(question, prompt.prompt.substringAfter("QUESTION:\n").substringBefore("\n\nANSWER:"))
    }

    @Test fun leadingUsingSourceScopePreservesTheQuestionAndStrictPolicy() {
        val plain = "where was the 16th Summit of the Non-Aligned Movement held?"
        for (prefix in listOf("Using only my saved sources, ", "Using local documents: ",
            "USING ONLY THE PROVIDED NOTES; ", "Using these sources ")) {
            val question = prefix + plain
            assertEquals(prefix, plain, QuestionSubject.text(question))
            assertFalse(PromptBuilder.allowsModelExplanation(question))
            val prompt = PromptBuilder.build(question, emptyList(), 2048)
            assertEquals(question, prompt.prompt.substringAfter("QUESTION:\n").substringBefore("\n\nANSWER:"))
        }
        for (question in listOf("What can be learned using local sources?", "Using source control, what changed?",
            "Using only sources of the Nile", "Using my notes", "Using a telescope, where is Mars?")) {
            assertEquals(question, QuestionSubject.text(question))
        }
    }
}
