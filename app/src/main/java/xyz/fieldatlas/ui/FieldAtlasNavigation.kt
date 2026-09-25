package xyz.fieldatlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class PrimaryDestination { Research, Library, History, More }

sealed interface DetailDestination {
    data object Answer : DetailDestination
    data class Source(val index: Int) : DetailDestination
}

@Stable
class FieldAtlasNavigationState internal constructor(
    initialPrimary: PrimaryDestination = PrimaryDestination.Research,
    initialDetail: DetailDestination? = null,
) {
    var primary by mutableStateOf(initialPrimary)
        private set

    var detail by mutableStateOf(initialDetail)
        private set

    fun select(destination: PrimaryDestination) {
        primary = destination
        detail = null
    }

    fun openAnswer() {
        detail = DetailDestination.Answer
    }

    fun openSource(index: Int): Boolean {
        if (index < 0) return false
        detail = DetailDestination.Source(index)
        return true
    }

    fun back(): Boolean = when (detail) {
        is DetailDestination.Source -> {
            detail = DetailDestination.Answer
            true
        }
        DetailDestination.Answer -> {
            detail = null
            true
        }
        null -> false
    }

    companion object {
        val Saver = listSaver<FieldAtlasNavigationState, String>(
            save = { state ->
                listOf(
                    state.primary.name,
                    when (state.detail) {
                        DetailDestination.Answer -> "answer"
                        is DetailDestination.Source -> "source"
                        null -> "none"
                    },
                    (state.detail as? DetailDestination.Source)?.index?.toString().orEmpty(),
                )
            },
            restore = { values ->
                val primary = PrimaryDestination.entries.firstOrNull { it.name == values[0] }
                    ?: PrimaryDestination.Research
                val detail = when (values[1]) {
                    "answer" -> DetailDestination.Answer
                    "source" -> values.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }
                        ?.let(DetailDestination::Source)
                    else -> null
                }
                FieldAtlasNavigationState(primary, detail)
            },
        )
    }
}

@Composable
fun rememberFieldAtlasNavigationState(): FieldAtlasNavigationState =
    rememberSaveable(saver = FieldAtlasNavigationState.Saver) { FieldAtlasNavigationState() }
