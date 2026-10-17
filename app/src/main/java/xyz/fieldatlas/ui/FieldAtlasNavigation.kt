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
    initialReturnTo: PrimaryDestination? = null,
) {
    var primary by mutableStateOf(initialPrimary)
        private set

    var detail by mutableStateOf(initialDetail)
        private set

    // Where Back from the answer page goes when it was opened from another screen, such as a topic continued from
    // History; null goes to the Research home.
    var returnTo by mutableStateOf(initialReturnTo)
        private set

    fun select(destination: PrimaryDestination) {
        primary = destination
        detail = null
        returnTo = null
    }

    fun openAnswer(from: PrimaryDestination? = null) {
        // A continued topic's answer arrives as finished a moment after its page opens, and asks to open it again:
        // that keeps the way back the page was opened with.
        if (from != null) returnTo = from.takeIf { it != primary }
        else if (detail == null) returnTo = null
        detail = DetailDestination.Answer
    }

    /** The answer page now belongs to Research (a new question or follow-up started on it): Back goes home. */
    fun keepInResearch() {
        returnTo = null
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
            returnTo?.let { primary = it }
            returnTo = null
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
                    state.returnTo?.name.orEmpty(),
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
                val returnTo = PrimaryDestination.entries.firstOrNull { it.name == values.getOrNull(3) }
                FieldAtlasNavigationState(primary, detail, returnTo.takeIf { detail != null })
            },
        )
    }
}

@Composable
fun rememberFieldAtlasNavigationState(): FieldAtlasNavigationState =
    rememberSaveable(saver = FieldAtlasNavigationState.Saver) { FieldAtlasNavigationState() }
