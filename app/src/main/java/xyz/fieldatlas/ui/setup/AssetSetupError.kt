package xyz.fieldatlas.ui.setup

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Actionable copy for setup and Library. Raw failures belong in diagnostics, not primary UI. */
internal enum class AssetAction { MODEL_DOWNLOAD, COLLECTION_DOWNLOAD, PACK_IMPORT }

internal fun assetSetupError(error: Throwable, action: AssetAction): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    if (causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }) {
        return "Can't reach the download server. Turn on Wi-Fi or mobile data, then try again."
    }
    if (causes.any { it is SocketTimeoutException }) {
        return "The connection timed out. Check your internet connection and try again."
    }
    if (causes.any { it is SSLException }) {
        return "A secure connection couldn't be made. Check your connection and try again later."
    }
    val detail = causes.firstNotNullOfOrNull { it.message }.orEmpty().lowercase()
    if ("not enough" in detail && ("space" in detail || "storage" in detail)) {
        return "Not enough storage space on this phone. Free up space, then try again."
    }
    if ("checksum" in detail || "hash" in detail || "verification" in detail) {
        return "The file couldn't be verified, so nothing was installed. Try downloading it again."
    }
    if ("already installed" in detail) {
        return "This item is already on your phone. Find it in Library."
    }
    if ("http 404" in detail) {
        return "This download is unavailable right now. You can try again later or import a pack."
    }
    return when (action) {
        AssetAction.MODEL_DOWNLOAD -> "The model couldn't be downloaded. Check your connection and free space, then try again. You can also import a model pack."
        AssetAction.COLLECTION_DOWNLOAD -> "The collection couldn't be downloaded. Check your connection and free space, then try again."
        AssetAction.PACK_IMPORT -> "This pack couldn't be added. Choose a compatible .fapack file and try again."
    }
}
