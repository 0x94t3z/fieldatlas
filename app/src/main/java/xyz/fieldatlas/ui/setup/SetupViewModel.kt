package xyz.fieldatlas.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.ui.AppContainer

data class SetupUiState(
    val packs: List<InstalledAsset> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
)

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { container.installBundledKnowledgeIfNeeded() }
                .onFailure { error -> mutableState.value = mutableState.value.copy(error = error.message ?: "Built-in knowledge could not be installed") }
            container.refreshPacks()
            container.packs.collect { packs -> mutableState.value = mutableState.value.copy(packs = packs) }
        }
    }

    fun importPack(uri: Uri) {
        if (mutableState.value.importing) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(importing = true, error = null)
            mutableState.value = try {
                container.importPack(uri)
                mutableState.value.copy(importing = false)
            } catch (error: Exception) {
                mutableState.value.copy(importing = false, error = error.message ?: "Import failed")
            }
        }
    }
}
