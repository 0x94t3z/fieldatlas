package xyz.fieldatlas.ui.setup

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.assets.PackType

class KnowledgeSetupStateTest {
    @Test fun importingKnowledgeKeepsOptionalStepOpenUntilUserContinues() {
        assertTrue(SetupUiState(offerKnowledge = true).finishImport(true, PackType.KNOWLEDGE).offerKnowledge)
        assertFalse(SetupUiState().finishImport(true, PackType.KNOWLEDGE).offerKnowledge)
        assertTrue(SetupUiState().finishImport(false, PackType.MODEL).offerKnowledge)
    }
    @Test fun switchingActionsClearsBothPreviousErrors() {
        val failed = SetupUiState(error = "Import failed", knowledgeError = "Download failed")
        for (state in listOf(failed.startImport(), failed.startKnowledgeDownload("biology:1"))) {
            assertNull(state.error)
            assertNull(state.knowledgeError)
        }
    }
}
