package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class SavedEvidenceAppendixTest {
    @Test fun citationsAttachOnlyToVerbatimEvidenceNotGeneratedClaims() {
        val evidence = Evidence("manual", "m1", "Manual", "Saved file", "The device has a steel casing.", -1.0)
        val packed = PromptBuilder.build("Does the device have a casing?", listOf(evidence), 2048)
        val answer = "## Model explanation\n\nThe device is waterproof."
        val result = SavedEvidenceAppendix.append(answer, packed.copy(mixedAnswer = true))
        assertTrue(result.startsWith(answer + "\n\n"))
        assertFalse(result.contains("waterproof. [S1]"))
        assertTrue(result.contains("steel casing."))
        assertTrue(result.contains("[S1]"))
        assertTrue(result.contains("not verification"))
        assertEquals(result, SavedEvidenceAppendix.append(result, packed.copy(mixedAnswer = true)))
        assertEquals(answer, SavedEvidenceAppendix.append(answer, packed.copy(mixedAnswer = false)))
    }

    @Test fun anAnswerFromTheModelsOwnKnowledgeSaysTheExcerptsWereNotItsSource() {
        val answer = "## Model explanation\n\nRain is liquid water falling from clouds."
        val evidence = Evidence("library", "r1", "Rain", "Saved collection", "Rain is liquid water in droplets.", -1.0)
        val packed = PromptBuilder.build("What is rain?", listOf(evidence), 2048).copy(mixedAnswer = true)
        val written = SavedEvidenceAppendix.append(answer, packed, writtenWithout = true)
        assertTrue(written.endsWith(SavedEvidenceAppendix.NOT_USED))
        assertFalse(SavedEvidenceAppendix.NOT_VERIFICATION in written)
        assertTrue(SavedEvidenceAppendix.append(answer, packed).endsWith(SavedEvidenceAppendix.NOT_VERIFICATION))
    }
}
