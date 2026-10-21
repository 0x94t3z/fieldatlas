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
    /** The saved packs have been read once; before that, an empty list means "not read yet", not "none". */
    val packsLoaded: Boolean = false,
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
            runCatching { container.preloadBundledData() }
            runCatching { container.retireBundledReferenceIfPresent() }
                .onFailure { error ->
                    container.errorBus.report("Saved packs", error)
                    mutableState.value = mutableState.value.copy(
                        error = "Couldn't load saved files. Close and reopen the app, then try again.")
                }
            container.refreshPacks()
            container.packs.collect { packs -> mutableState.value = mutableState.value.copy(packs = packs, packsLoaded = true) }
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
            // A model file (.gguf) is what people download a model as; anything else is tried as a pack.
            val plan = try { container.inspectModelFile(uri) } catch (error: Exception) {
                container.errorBus.report("Model import", error)
                mutableState.value = mutableState.value.copy(importing = false, importingName = null, importingPack = null,
                    error = assetSetupError(error, AssetAction.PACK_IMPORT))
                return@launch
            }
            if (plan != null) { importModelFile(uri, plan); return@launch }
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

    private suspend fun importModelFile(uri: Uri, plan: xyz.fieldatlas.assets.ModelFilePlan) {
        xyz.fieldatlas.assets.ModelFileImport.refusal(plan.kind, container.deviceMemoryBytes)?.let { refusal ->
            mutableState.value = mutableState.value.copy(importing = false, importingName = null, importingPack = null,
                error = assetSetupError(refusal, AssetAction.PACK_IMPORT))
            return
        }
        val tested = (plan.kind as? xyz.fieldatlas.assets.ModelFileKind.Tested)?.offer
        val info = xyz.fieldatlas.assets.PackImportInfo(tested?.id ?: "own", tested?.name ?: plan.title,
            xyz.fieldatlas.assets.PackType.MODEL, totalBytes = plan.bytes, tested = tested != null)
        mutableState.value = mutableState.value.copy(importingPack = info)
        mutableState.value = try {
            val hadModel = mutableState.value.packs.any { it.type == xyz.fieldatlas.assets.PackType.MODEL }
            container.importModelFile(uri, plan) { copied ->
                mutableState.value.importingPack?.let { mutableState.value = mutableState.value.copy(importingPack = it.copy(copiedBytes = copied)) }
            }
            mutableState.value.finishImport(hadModel, xyz.fieldatlas.assets.PackType.MODEL)
        } catch (error: Exception) {
            container.errorBus.report("Model import", error)
            mutableState.value.copy(importing = false, importingName = null, importingPack = null,
                error = assetSetupError(error, AssetAction.PACK_IMPORT))
        }
    }

    fun downloadRecommendedModel() = downloadModel(ModelOffers.recommended)

    fun downloadModel(offer: ModelOffer) {
        if (mutableState.value.importing ||
            mutableState.value.packs.any { it.id == offer.id && it.version == offer.version } ||
            !offer.fits(container.deviceMemoryBytes)
        ) return
        mutableState.value = mutableState.value.copy(error = null, modelError = null, modelErrorId = null)
        // Setup's own screens confirm the data use and show no "waiting for Wi-Fi", so a tap there means go now.
        container.downloads.queue.enqueue(xyz.fieldatlas.assets.DownloadItem.Model(offer), allowMobileData = true)
    }

    fun cancelDownload() { container.downloads.queue.pause() }

    fun downloadKnowledge(pack: KnowledgeCatalogEntry) {
        if (mutableState.value.importing ||
            mutableState.value.packs.any { it.id == pack.id && it.version == pack.version }
        ) return
        mutableState.value = mutableState.value.copy(error = null, knowledgeError = null, modelError = null, modelErrorId = null)
        container.downloads.queue.enqueue(xyz.fieldatlas.assets.DownloadItem.Knowledge(pack), allowMobileData = true)
    }

    fun cancelKnowledgeDownload() { container.downloads.queue.pause() }

    fun dismissKnowledgeOffer() {
        mutableState.value = mutableState.value.copy(offerKnowledge = false)
        container.backgroundDownloads.dismissKnowledgeOffer()
    }
}
