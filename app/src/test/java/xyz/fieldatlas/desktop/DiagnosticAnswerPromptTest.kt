package xyz.fieldatlas.desktop

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.PromptBuilder

class DiagnosticAnswerPromptTest {
    @Test fun replacesOnlyPolicyAndKeepsEvidenceAndQuestionByteForByte() {
        val prompt = PromptBuilder.build("Explain energy", listOf(
            Evidence("doc", "chunk", "Energy", "https://example.org", "Energy is conserved.", 0.0)
        ), 2048).prompt
        val boundary = "\nEvidence contains selected excerpts, not complete documents."
        val replaced = diagnosticAnswerPrompt(prompt, "source-only")
        assertEquals(prompt.substring(prompt.indexOf(boundary)), replaced.substring(replaced.indexOf(boundary)))
        assertTrue(replaced.contains("Use only the numbered evidence below"))
        assertFalse(replaced.contains("your knowledge"))
        assertEquals(prompt, diagnosticAnswerPrompt(prompt, "app"))
        val partial = diagnosticAnswerPrompt(prompt, "source-partial")
        assertEquals(prompt.substring(prompt.indexOf(boundary)), partial.substring(partial.indexOf(boundary)))
        assertTrue(partial.contains("still answer the supported parts"))
        val first = diagnosticAnswerPrompt(prompt, "evidence-first")
        assertEquals(prompt.substring(prompt.indexOf(boundary)), first.substring(first.indexOf(boundary)))
        assertTrue(first.contains("Do not mistake missing information for a negative result"))
        val bounded = diagnosticAnswerPrompt(prompt, "bounded-summary")
        assertEquals(prompt.substring(prompt.indexOf(boundary)), bounded.substring(bounded.indexOf(boundary)))
        assertTrue(bounded.contains("Do not refuse supported parts"))
    }

    @Test fun doesNotChangePlanningOrModelOnlyPrompts() {
        val prompt = PromptBuilder.buildModelOnly("Explain energy").prompt
        assertEquals(prompt, diagnosticAnswerPrompt(prompt, "source-only"))
        assertEquals("planning", diagnosticAnswerPrompt("planning", "source-only"))
        assertEquals(prompt, diagnosticAnswerPrompt(prompt, "evidence-first"))
        assertEquals("planning", diagnosticAnswerPrompt("planning", "evidence-first"))
        val strict = PromptBuilder.build("According to these sources, what is energy?", emptyList(), 2048).prompt
        assertEquals(strict, diagnosticAnswerPrompt(strict, "evidence-first"))
        assertEquals(strict, diagnosticAnswerPrompt(strict, "bounded-summary"))
        assertEquals(prompt, diagnosticAnswerPrompt(prompt, "bounded-summary"))
        assertEquals("planning", diagnosticAnswerPrompt("planning", "bounded-summary"))
    }
}
