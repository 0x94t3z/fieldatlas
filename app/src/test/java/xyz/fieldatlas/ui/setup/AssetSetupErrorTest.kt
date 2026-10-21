package xyz.fieldatlas.ui.setup

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.assets.AssetImportException
import xyz.fieldatlas.assets.BudgetDecision

class AssetSetupErrorTest {
    @Test fun offlineDownloadShowsActionInsteadOfHostname() {
        val message = assetSetupError(
            UnknownHostException("Unable to resolve host \"huggingface.co\""),
            AssetAction.MODEL_DOWNLOAD,
        )
        assertTrue(message.contains("Wi-Fi or mobile data"))
        assertFalse(message.contains("huggingface"))
    }

    @Test fun timeoutAndStorageHaveSpecificGuidance() {
        assertTrue(assetSetupError(SocketTimeoutException(), AssetAction.COLLECTION_DOWNLOAD)
            .contains("timed out"))
        assertTrue(assetSetupError(IllegalStateException("Not enough free space for this model"),
            AssetAction.MODEL_DOWNLOAD).contains("Free up space"))
    }

    @Test fun unknownFailureNeverShowsInternalMessage() {
        val message = assetSetupError(IllegalStateException("internal/path/private"), AssetAction.PACK_IMPORT)
        assertTrue(message, message.contains("a model file (.gguf) or a collection (.fapack)"))
        assertFalse(message.contains("internal/path"))
    }

    @Test fun importBudgetLimitHasLibraryGuidanceNotFileCompatibilityBlame() {
        for (action in listOf(AssetAction.PACK_IMPORT, AssetAction.COLLECTION_DOWNLOAD)) {
            val error = IllegalStateException("private/path/outer", AssetImportException(
                "Storage changed during import: internal/path", budgetDecision = BudgetDecision.ExceedsGlobalLimit))
            val message = assetSetupError(error, action)
            assertTrue(message.contains("50 GB storage limit"))
            assertTrue(message.contains("Library"))
            assertFalse(message.contains("compatible"))
            assertFalse(message.contains("private/path"))
            assertFalse(message.contains("internal/path"))
        }
    }

    @Test fun importFreeSpaceFailureHasPhoneStorageGuidance() {
        val message = assetSetupError(AssetImportException("Pack violates storage budget",
            budgetDecision = BudgetDecision.InsufficientFreeSpace), AssetAction.PACK_IMPORT)
        assertTrue(message.contains("storage space on this phone"))
        assertTrue(message.contains("Free up space"))
        assertFalse(message.contains("compatible"))
        assertFalse(message.contains("50 GB"))
    }

    @Test fun invalidSizesDoNotPretendThePhoneOrAppIsOutOfSpace() {
        val message = assetSetupError(AssetImportException("Invalid free-space value",
            budgetDecision = BudgetDecision.InvalidSize), AssetAction.PACK_IMPORT)
        assertTrue(message, message.contains("a model file (.gguf) or a collection (.fapack)"))
        assertFalse(message.contains("50 GB"))
        assertFalse(message.contains("Free up space"))
    }
}
