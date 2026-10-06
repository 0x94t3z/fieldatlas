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
    @Test fun switchingActionsClearsEveryPreviousError() {
        val failed = SetupUiState(error = "Import failed", knowledgeError = "Download failed",
            modelError = "Network interrupted", modelErrorId = xyz.fieldatlas.assets.ModelOffers.recommended.id)
        for (state in listOf(failed.startImport(), failed.startKnowledgeDownload("biology:1"))) {
            assertNull(state.error)
            assertNull(state.knowledgeError)
            // Importing a model after its download failed must not leave that failure on Setup.
            assertNull(state.modelError)
            assertNull(state.modelErrorId)
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
    @Test fun aModelDownloadSaysWhichModelAndKeepsItsErrorApartFromImports() {
        val larger = xyz.fieldatlas.assets.ModelOffers.larger
        val active = SetupUiState(error = "Import failed").withDownload(AssetDownloadStatus(
            kind = AssetDownloadKind.MODEL, model = larger, downloadedBytes = 7, totalBytes = larger.bytes,
        ))
        assertTrue(active.downloading)
        assertEquals(larger.id, active.downloadingModelId)
        val failed = active.withDownload(AssetDownloadStatus(
            errorKind = AssetDownloadKind.MODEL, errorModel = larger, error = "Network interrupted",
        ))
        assertFalse(failed.downloading)
        assertNull(failed.downloadingModelId)
        assertEquals("Network interrupted", failed.modelError)
        assertEquals(larger.id, failed.modelErrorId)
        assertEquals("Import failed", failed.error)
        // A collection download leaves the model's error on its card; retrying the model clears it.
        assertEquals("Network interrupted", failed.withDownload(AssetDownloadStatus(kind = AssetDownloadKind.KNOWLEDGE)).modelError)
        val retry = failed.withDownload(AssetDownloadStatus(kind = AssetDownloadKind.MODEL, model = larger))
        assertNull(retry.modelError)
        assertNull(retry.modelErrorId)
    }
    @Test fun completedBackgroundDownloadReportsItsError() {
        val failed = SetupUiState().withDownload(AssetDownloadStatus(
            errorKind = AssetDownloadKind.KNOWLEDGE, error = "Network interrupted",
        ))
        assertEquals("Network interrupted", failed.knowledgeError)
        assertNull(failed.knowledgeDownloadKey)
    }
}
