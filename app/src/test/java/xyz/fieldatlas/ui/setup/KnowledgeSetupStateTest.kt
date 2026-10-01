package xyz.fieldatlas.ui.setup

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.assets.AssetDownloadKind
import xyz.fieldatlas.assets.AssetDownloadStatus

class KnowledgeSetupStateTest {
    @Test fun importNameIsClearedForTheNextImportAndOnCompletion() {
        val state = SetupUiState(importing = true, importingName = "biology.fapack",
            importingPack = xyz.fieldatlas.assets.PackImportInfo("biology", "Biology", PackType.KNOWLEDGE))
        assertNull(state.startImport().importingName)
        assertNull(state.finishImport(true, PackType.KNOWLEDGE).importingName)
        assertNull(state.startImport().importingPack)
        assertNull(state.finishImport(true, PackType.KNOWLEDGE).importingPack)
    }
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
    @Test fun backgroundDownloadStateMapsIntoSetup() {
        val active = SetupUiState().withDownload(AssetDownloadStatus(
            kind = AssetDownloadKind.MODEL, downloadedBytes = 42, totalBytes = 100,
        ))
        assertTrue(active.downloading)
        assertEquals(42, active.downloadedBytes)
        assertFalse(active.withDownload(AssetDownloadStatus(offerKnowledge = true)).downloading)
        assertTrue(active.withDownload(AssetDownloadStatus(offerKnowledge = true)).offerKnowledge)
    }
    @Test fun completedBackgroundDownloadReportsItsError() {
        val failed = SetupUiState().withDownload(AssetDownloadStatus(
            errorKind = AssetDownloadKind.KNOWLEDGE, error = "Network interrupted",
        ))
        assertEquals("Network interrupted", failed.knowledgeError)
        assertNull(failed.knowledgeDownloadKey)
    }
}
