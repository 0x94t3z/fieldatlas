package xyz.fieldatlas.ui

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.attachments.AttachmentUiState
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.research.ResearchPhase

class AttachmentLimitTest {
    @Test fun threePendingFilesDisableAddingAndRemovalEnablesItAgain() {
        val full = ResearchUiState(attachments = (1..3).map { AttachmentUiState("$it", "$it.txt") })
        assertFalse(full.canAddAttachment)
        assertTrue(full.copy(attachments = full.attachments.dropLast(1)).canAddAttachment)
        assertTrue(ResearchUiState().canAddAttachment)
    }
    @Test fun runningResearchCannotAddEvenWithFreeSlots() {
        assertFalse(ResearchUiState(phase = ResearchPhase.Planning).canAddAttachment)
    }
}
