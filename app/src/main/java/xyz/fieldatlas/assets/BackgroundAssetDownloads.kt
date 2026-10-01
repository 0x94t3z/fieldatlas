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
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorKind: AssetDownloadKind? = null,
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
            AssetDownloadKind.MODEL.name -> startModel()
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

    fun startModel(): Boolean = start(AssetDownloadKind.MODEL, null, RecommendedModel.bytes) { onProgress ->
        container.downloadRecommendedModel(onProgress)
    }

    fun startKnowledge(pack: KnowledgeCatalogEntry): Boolean = start(
        AssetDownloadKind.KNOWLEDGE, pack, pack.bytes,
    ) { onProgress -> container.downloadKnowledge(pack, onProgress) }

    private fun start(
        kind: AssetDownloadKind,
        pack: KnowledgeCatalogEntry?,
        totalBytes: Long,
        install: suspend ((Long) -> Unit) -> InstalledAsset,
    ): Boolean = synchronized(this) {
        if (mutableState.value.active) return@synchronized false
        mutableState.value = mutableState.value.copy(
            kind = kind, pack = pack, downloadedBytes = 0, totalBytes = totalBytes,
            errorKind = null, error = null,
        )
        try {
            check(preferences.edit().putString(PENDING_KIND, kind.name)
                .putString(PENDING_PACK, pack?.let { Json.encodeToString(it) }).commit()) {
                "Could not save download state"
            }
            AssetDownloadService.start(context)
        } catch (error: Exception) {
            clearPending()
            container.errorBus.report("Background download", error)
            mutableState.value = mutableState.value.copy(
                kind = null, pack = null, errorKind = kind,
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
                if (kind == AssetDownloadKind.MODEL) {
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
                    error = assetSetupError(error, if (kind == AssetDownloadKind.MODEL)
                        AssetAction.MODEL_DOWNLOAD else AssetAction.COLLECTION_DOWNLOAD),
                ) }
            } finally {
                mutableState.update { it.copy(kind = null, pack = null) }
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
        preferences.edit().remove(PENDING_KIND).remove(PENDING_PACK).commit()
    }

    fun dismissKnowledgeOffer() {
        mutableState.update { it.copy(offerKnowledge = false) }
    }

    private companion object {
        const val PENDING_KIND = "kind"
        const val PENDING_PACK = "pack"
    }
}
