package xyz.fieldatlas.assets

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.fieldatlas.ui.AppContainer
import xyz.fieldatlas.ui.setup.AssetAction
import xyz.fieldatlas.ui.setup.assetSetupError

enum class AssetDownloadKind { MODEL, KNOWLEDGE }

data class AssetDownloadStatus(
    val kind: AssetDownloadKind? = null,
    val pack: KnowledgeCatalogEntry? = null,
    /** The model being downloaded, when [kind] is MODEL. */
    val model: ModelOffer? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorKind: AssetDownloadKind? = null,
    /** Which model's download failed, when [errorKind] is MODEL. */
    val errorModel: ModelOffer? = null,
    val error: String? = null,
    val offerKnowledge: Boolean = false,
) {
    val active: Boolean get() = kind != null
}

/** Application-owned transfer. Leaving Setup or Library does not cancel the user-started job. */
class BackgroundAssetDownloads(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val mutableState = MutableStateFlow(AssetDownloadStatus())
    val state: StateFlow<AssetDownloadStatus> = mutableState.asStateFlow()
    private var job: Job? = null
    private var keepPendingAfterCancel = false
    private val preferences = context.getSharedPreferences("asset-downloads", Context.MODE_PRIVATE)

    /** Called only when the user opens the app; Android forbids starting this service in the background. */
    fun resumeInterrupted() {
        if (mutableState.value.active) return
        val kind = preferences.getString(PENDING_KIND, null) ?: return
        when (kind) {
            AssetDownloadKind.MODEL.name -> {
                // Versions before the optional larger model saved no id; theirs was the recommended one.
                val id = preferences.getString(PENDING_MODEL, null)
                val offer = if (id == null) ModelOffers.recommended else ModelOffers.byId(id)
                if (offer == null) clearPending() else startModel(offer)
            }
            AssetDownloadKind.KNOWLEDGE.name -> {
                val pack = runCatching {
                    val saved = Json.decodeFromString<KnowledgeCatalogEntry>(
                        preferences.getString(PENDING_PACK, null) ?: error("Missing collection"))
                    KnowledgeCatalog.parse(Json.encodeToString(KnowledgeCatalog(1, listOf(saved))))
                        .packs.single()
                }.getOrNull()
                if (pack == null) clearPending() else startKnowledge(pack)
            }
            else -> clearPending()
        }
    }

    fun startModel(offer: ModelOffer = ModelOffers.recommended): Boolean =
        start(AssetDownloadKind.MODEL, null, offer, offer.bytes) { onProgress ->
            container.downloadModel(offer, onProgress)
        }

    fun startKnowledge(pack: KnowledgeCatalogEntry): Boolean = start(
        AssetDownloadKind.KNOWLEDGE, pack, null, pack.bytes,
    ) { onProgress -> container.downloadKnowledge(pack, onProgress) }

    private fun start(
        kind: AssetDownloadKind,
        pack: KnowledgeCatalogEntry?,
        model: ModelOffer?,
        totalBytes: Long,
        install: suspend ((Long) -> Unit) -> InstalledAsset,
    ): Boolean = synchronized(this) {
        if (mutableState.value.active) return@synchronized false
        mutableState.value = mutableState.value.copy(
            kind = kind, pack = pack, model = model, downloadedBytes = 0, totalBytes = totalBytes,
            errorKind = null, errorModel = null, error = null,
        )
        try {
            check(preferences.edit().putString(PENDING_KIND, kind.name)
                .putString(PENDING_PACK, pack?.let { Json.encodeToString(it) })
                .putString(PENDING_MODEL, model?.id).commit()) {
                "Could not save download state"
            }
            AssetDownloadService.start(context)
        } catch (error: Exception) {
            clearPending()
            container.errorBus.report("Background download", error)
            mutableState.value = mutableState.value.copy(
                kind = null, pack = null, model = null, errorKind = kind, errorModel = model,
                error = "Could not start the background download. Reopen the app and try again.",
            )
            return@synchronized false
        }
        job = scope.launch {
            try {
                install { bytes ->
                    mutableState.update { current ->
                        if (current.kind == kind) current.copy(downloadedBytes = bytes) else current
                    }
                }
                // Collections are offered after the phone's first model, not after a second one
                // added from Library.
                if (kind == AssetDownloadKind.MODEL && container.packs.value.count { it.type == PackType.MODEL } == 1) {
                    mutableState.update { it.copy(offerKnowledge = true) }
                }
                clearPending()
            } catch (error: CancellationException) {
                if (!keepPendingAfterCancel) clearPending()
                throw error
            } catch (error: Exception) {
                clearPending()
                container.errorBus.report(
                    if (kind == AssetDownloadKind.MODEL) "Model download" else "Collection download", error,
                )
                mutableState.update { it.copy(
                    errorKind = kind,
                    errorModel = model,
                    error = assetSetupError(error, if (kind == AssetDownloadKind.MODEL)
                        AssetAction.MODEL_DOWNLOAD else AssetAction.COLLECTION_DOWNLOAD),
                ) }
            } finally {
                mutableState.update { it.copy(kind = null, pack = null, model = null) }
                job = null
                keepPendingAfterCancel = false
            }
        }
        true
    }

    /** Pauses the transfer; verified partial bytes remain available for the next attempt. */
    fun cancel(keepPending: Boolean = false) {
        keepPendingAfterCancel = keepPending
        if (!keepPending) clearPending()
        job?.cancel()
    }

    private fun clearPending() {
        preferences.edit().remove(PENDING_KIND).remove(PENDING_PACK).remove(PENDING_MODEL).commit()
    }

    fun dismissKnowledgeOffer() {
        mutableState.update { it.copy(offerKnowledge = false) }
    }

    private companion object {
        const val PENDING_KIND = "kind"
        const val PENDING_PACK = "pack"
        const val PENDING_MODEL = "model"
    }
}
