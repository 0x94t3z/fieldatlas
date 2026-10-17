package xyz.fieldatlas.assets

import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A mirror may change where bytes come from, never which bytes are trusted. */
data class DownloadArtifact(
    val kind: AssetDownloadKind,
    val id: String,
    val version: String,
    val title: String,
    val bytes: Long,
    val sha256: String,
    val defaultUrl: String,
) {
    // A changed edition, size or checksum must not inherit an earlier mirror.
    val key: String get() = "${kind.name}:$id:$version:$bytes:$sha256"
    /** What a source address must end in. The voice is several files, so its source is the folder holding them. */
    val extension: String get() = when (kind) {
        AssetDownloadKind.MODEL -> ".gguf"
        AssetDownloadKind.KNOWLEDGE -> ".fapack"
        AssetDownloadKind.VOICE -> "/"
    }

    companion object {
        fun model(offer: ModelOffer) = DownloadArtifact(AssetDownloadKind.MODEL, offer.id, offer.version,
            offer.name, offer.bytes, offer.sha256, offer.url)
        fun voice() = DownloadArtifact(AssetDownloadKind.VOICE, xyz.fieldatlas.speech.NaturalVoice.ID,
            xyz.fieldatlas.speech.NaturalVoice.REVISION, DownloadItem.Voice.title, xyz.fieldatlas.speech.NaturalVoice.BYTES,
            xyz.fieldatlas.speech.NaturalVoice.FINGERPRINT, xyz.fieldatlas.speech.NaturalVoice.DEFAULT_SOURCE)
        fun knowledge(pack: KnowledgeCatalogEntry) = DownloadArtifact(AssetDownloadKind.KNOWLEDGE, pack.id,
            pack.version, pack.title, pack.bytes, pack.sha256, pack.url)
    }
}

/** No networking, probing or catalog replacement. Used at save time AND transfer initiation. */
fun validatedDownloadUrl(value: String, extension: String): String {
    val address = value.trim()
    require(address.length in 1..2048 && address.none { it.isWhitespace() || it.isISOControl() }) {
        "Enter a complete HTTPS file URL."
    }
    val uri = runCatching { URI(address) }.getOrNull()
    require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535) &&
        uri.path?.endsWith(extension, ignoreCase = true) == true) {
        if (extension == "/") "Use an HTTPS folder URL ending in /, without a username, password or fragment."
        else "Use an HTTPS URL ending in $extension, without a username, password or fragment."
    }
    return address
}

/** Old jobs have no source snapshot: retain their original default, not newly edited settings. */
fun restoredDownloadUrl(artifact: DownloadArtifact, savedKey: String?, savedUrl: String?): String {
    if (savedKey == null && savedUrl == null) return artifact.defaultUrl
    require(savedKey == artifact.key && savedUrl != null) { "The saved download no longer matches this file. Start it again." }
    return validatedDownloadUrl(savedUrl, artifact.extension)
}

/** Inject persistence so validation and failed-write behavior can be tested without Android. */
class DownloadSources(
    initial: Map<String, String>,
    private val persist: (Map<String, String>) -> Boolean,
) {
    private val mutable = MutableStateFlow(initial.toMap())
    val state: StateFlow<Map<String, String>> = mutable.asStateFlow()

    fun urlFor(artifact: DownloadArtifact): String =
        mutable.value[artifact.key]?.let { validatedDownloadUrl(it, artifact.extension) } ?: artifact.defaultUrl

    @Synchronized
    fun save(artifact: DownloadArtifact, value: String) {
        val address = validatedDownloadUrl(value, artifact.extension)
        val next = mutable.value.toMutableMap().apply {
            if (address == artifact.defaultUrl) remove(artifact.key) else put(artifact.key, address)
        }.toMap()
        check(persist(next)) { "Couldn't save the source. Try again." }
        mutable.value = next
    }

    @Synchronized
    fun reset(artifact: DownloadArtifact) {
        val next = mutable.value - artifact.key
        check(persist(next)) { "Couldn't reset the source. Try again." }
        mutable.value = next
    }
}
