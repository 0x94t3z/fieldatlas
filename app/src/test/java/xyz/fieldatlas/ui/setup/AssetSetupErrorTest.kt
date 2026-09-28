package xyz.fieldatlas.ui.setup

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(message.contains("compatible .fapack"))
        assertFalse(message.contains("internal/path"))
    }
}
