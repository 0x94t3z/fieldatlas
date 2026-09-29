package xyz.fieldatlas.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.RecommendedModel
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.ui.AppContainer

data class SetupUiState(
    val packs: List<InstalledAsset> = emptyList(),
    val importing: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val offerKnowledge: Boolean = false,
    val knowledgeDownloadKey: String? = null,
    val activeKnowledgeDownload: KnowledgeCatalogEntry? = null,
    val knowledgeDownloadedBytes: Long = 0,
    val knowledgeError: String? = null,
    val error: String? = null,
) {
    internal fun startImport() = copy(importing = true, error = null, knowledgeError = null)
    internal fun finishImport(hadModel: Boolean, installedType: xyz.fieldatlas.assets.PackType) =
        copy(importing = false, offerKnowledge = offerKnowledge || (!hadModel && installedType == xyz.fieldatlas.assets.PackType.MODEL))
    internal fun startKnowledgeDownload(key: String) = copy(knowledgeDownloadKey = key, knowledgeDownloadedBytes = 0, knowledgeError = null, error = null)
}

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()
    private var downloadJob: Job? = null
    private var knowledgeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { container.retireBundledReferenceIfPresent() }
                .onFailure { error ->
                    container.errorBus.report("Saved packs", error)
                    mutableState.value = mutableState.value.copy(
                        error = "Couldn't load saved files. Close and reopen the app, then try again.")
                }
            container.refreshPacks()
            container.packs.collect { packs -> mutableState.value = mutableState.value.copy(packs = packs) }
        }
    }

    fun importPack(uri: Uri) {
        if (mutableState.value.importing || mutableState.value.downloading ||
            mutableState.value.knowledgeDownloadKey != null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.startImport()
            mutableState.value = try {
                val hadModel = mutableState.value.packs.any { it.type == xyz.fieldatlas.assets.PackType.MODEL }
                val installed = container.importPack(uri)
                mutableState.value.finishImport(hadModel, installed.type)
            } catch (error: Exception) {
                container.errorBus.report("Pack import", error)
                mutableState.value.copy(importing = false,
                    error = assetSetupError(error, AssetAction.PACK_IMPORT))
            }
        }
    }

    fun downloadRecommendedModel() {
        if (mutableState.value.importing || mutableState.value.downloading ||
            mutableState.value.knowledgeDownloadKey != null ||
            mutableState.value.packs.any { it.id == RecommendedModel.id && it.version == RecommendedModel.version }
        ) return
        downloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(downloading = true, downloadedBytes = 0, error = null)
            try {
                container.downloadRecommendedModel { bytes ->
                    mutableState.value = mutableState.value.copy(downloadedBytes = bytes)
                }
                mutableState.value = mutableState.value.copy(offerKnowledge = true)
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    container.errorBus.report("Model download", error)
                    mutableState.value = mutableState.value.copy(
                        error = assetSetupError(error, AssetAction.MODEL_DOWNLOAD))
                }
            } finally {
                mutableState.value = mutableState.value.copy(downloading = false)
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun downloadKnowledge(pack: KnowledgeCatalogEntry) {
        if (mutableState.value.importing || mutableState.value.downloading ||
            mutableState.value.knowledgeDownloadKey != null ||
            mutableState.value.packs.any { it.id == pack.id && it.version == pack.version }
        ) return
        val key = "${pack.id}:${pack.version}"
        knowledgeJob = viewModelScope.launch {
            mutableState.value = mutableState.value.startKnowledgeDownload(key).copy(activeKnowledgeDownload = pack)
            try {
                container.downloadKnowledge(pack) { bytes ->
                    mutableState.value = mutableState.value.copy(knowledgeDownloadedBytes = bytes)
                }
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    container.errorBus.report("Collection download", error)
                    mutableState.value = mutableState.value.copy(
                        knowledgeError = assetSetupError(error, AssetAction.COLLECTION_DOWNLOAD),
                    )
                }
            } finally {
                mutableState.value = mutableState.value.copy(knowledgeDownloadKey = null, activeKnowledgeDownload = null)
            }
        }
    }

    fun cancelKnowledgeDownload() { knowledgeJob?.cancel() }

    fun dismissKnowledgeOffer() {
        mutableState.value = mutableState.value.copy(offerKnowledge = false)
    }
}
