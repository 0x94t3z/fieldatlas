package xyz.fieldatlas.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.ModelOffer
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.AssetDownloadKind
import xyz.fieldatlas.assets.AssetDownloadStatus
import xyz.fieldatlas.ui.AppContainer

data class SetupUiState(
    val packs: List<InstalledAsset> = emptyList(),
    val importing: Boolean = false,
    val importingName: String? = null,
    val importingPack: xyz.fieldatlas.assets.PackImportInfo? = null,
    /** True while any model downloads; [downloadingModelId] says which. */
    val downloading: Boolean = false,
    val downloadingModelId: String? = null,
    val downloadedBytes: Long = 0,
    val offerKnowledge: Boolean = false,
    val knowledgeDownloadKey: String? = null,
    val activeKnowledgeDownload: KnowledgeCatalogEntry? = null,
    val knowledgeDownloadedBytes: Long = 0,
    val knowledgeError: String? = null,
    val error: String? = null,
    /** A failed model download, kept apart from import errors so Library shows it on that model. */
    val modelError: String? = null,
    val modelErrorId: String? = null,
) {
    internal fun startImport() = copy(importing = true, importingName = null, importingPack = null, error = null,
        knowledgeError = null, modelError = null, modelErrorId = null)
    internal fun finishImport(hadModel: Boolean, installedType: xyz.fieldatlas.assets.PackType) =
        copy(importing = false, importingName = null, importingPack = null, offerKnowledge = offerKnowledge || (!hadModel && installedType == xyz.fieldatlas.assets.PackType.MODEL))
    internal fun startKnowledgeDownload(key: String) = copy(knowledgeDownloadKey = key, knowledgeDownloadedBytes = 0,
        knowledgeError = null, error = null, modelError = null, modelErrorId = null)
    internal fun withDownload(download: AssetDownloadStatus) = copy(
        downloading = download.kind == AssetDownloadKind.MODEL,
        downloadingModelId = download.model?.id?.takeIf { download.kind == AssetDownloadKind.MODEL },
        downloadedBytes = if (download.kind == AssetDownloadKind.MODEL) download.downloadedBytes else 0,
        knowledgeDownloadKey = download.pack?.let { "${it.id}:${it.version}" },
        activeKnowledgeDownload = download.pack,
        knowledgeDownloadedBytes = if (download.kind == AssetDownloadKind.KNOWLEDGE) download.downloadedBytes else 0,
        knowledgeError = if (download.errorKind == AssetDownloadKind.KNOWLEDGE) download.error else null,
        modelError = when {
            download.errorKind == AssetDownloadKind.MODEL -> download.error
            download.kind == AssetDownloadKind.MODEL -> null
            else -> modelError
        },
        modelErrorId = when {
            download.errorKind == AssetDownloadKind.MODEL -> download.errorModel?.id
            download.kind == AssetDownloadKind.MODEL -> null
            else -> modelErrorId
        },
        offerKnowledge = offerKnowledge || download.offerKnowledge,
    )
}

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            runCatching { container.recoverInterruptedInstalls() }
            runCatching { container.retireBundledReferenceIfPresent() }
                .onFailure { error ->
                    container.errorBus.report("Saved packs", error)
                    mutableState.value = mutableState.value.copy(
                        error = "Couldn't load saved files. Close and reopen the app, then try again.")
                }
            container.refreshPacks()
            container.packs.collect { packs -> mutableState.value = mutableState.value.copy(packs = packs) }
        }
        viewModelScope.launch {
            container.backgroundDownloads.state.collect { download ->
                mutableState.value = mutableState.value.withDownload(download)
            }
        }
    }

    fun importPack(uri: Uri) {
        if (mutableState.value.importing || container.backgroundDownloads.state.value.active) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.startImport()
            val name = container.importFileName(uri)
            mutableState.value = mutableState.value.copy(importingName = name)
            val packInfo = container.inspectImportPack(uri)
            mutableState.value = mutableState.value.copy(importingPack = packInfo)
            mutableState.value = try {
                val hadModel = mutableState.value.packs.any { it.type == xyz.fieldatlas.assets.PackType.MODEL }
                val installed = container.importPack(uri)
                mutableState.value.finishImport(hadModel, installed.type)
            } catch (error: Exception) {
                container.errorBus.report("Pack import", error)
                mutableState.value.copy(importing = false, importingName = null, importingPack = null,
                    error = assetSetupError(error, AssetAction.PACK_IMPORT))
            }
        }
    }

    fun downloadRecommendedModel() = downloadModel(ModelOffers.recommended)

    fun downloadModel(offer: ModelOffer) {
        if (mutableState.value.importing || container.backgroundDownloads.state.value.active ||
            mutableState.value.packs.any { it.id == offer.id && it.version == offer.version } ||
            !offer.fits(container.deviceMemoryBytes)
        ) return
        mutableState.value = mutableState.value.copy(error = null, modelError = null, modelErrorId = null)
        container.backgroundDownloads.startModel(offer)
    }

    fun cancelDownload() { container.backgroundDownloads.cancel() }

    fun downloadKnowledge(pack: KnowledgeCatalogEntry) {
        if (mutableState.value.importing || container.backgroundDownloads.state.value.active ||
            mutableState.value.packs.any { it.id == pack.id && it.version == pack.version }
        ) return
        mutableState.value = mutableState.value.copy(error = null, knowledgeError = null, modelError = null, modelErrorId = null)
        container.backgroundDownloads.startKnowledge(pack)
    }

    fun cancelKnowledgeDownload() { container.backgroundDownloads.cancel() }

    fun dismissKnowledgeOffer() {
        mutableState.value = mutableState.value.copy(offerKnowledge = false)
        container.backgroundDownloads.dismissKnowledgeOffer()
    }
}
