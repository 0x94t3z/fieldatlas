package xyz.fieldatlas.ui

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One reported failure: the pipeline area it came from plus a human-readable message. */
data class AppNotice(val id: Long, val area: String, val message: String)

/**
 * Single collection point for errors from anywhere in the app — research, voice,
 * packs, models and benchmarks all report here, and the UI renders the collected
 * notices in one read-only field that stays empty while everything is healthy.
 *
 * Notices persist until explicitly cleared so a failure that flashed by (e.g. a
 * background import) remains readable after the screen that triggered it is gone.
 */
class AppErrorBus(private val maxNotices: Int = 8) {
    private val ids = AtomicLong()
    private val _notices = MutableStateFlow<List<AppNotice>>(emptyList())
    val notices: StateFlow<List<AppNotice>> = _notices.asStateFlow()

    fun report(area: String, error: Throwable) {
        report(area, error.message ?: error.javaClass.simpleName)
    }

    fun report(area: String, message: String) {
        if (message.isBlank()) return
        val notice = AppNotice(ids.incrementAndGet(), area, message)
        _notices.update { (it + notice).takeLast(maxNotices) }
    }

    fun dismiss(id: Long) {
        _notices.update { current -> current.filterNot { it.id == id } }
    }

    fun clear() {
        _notices.value = emptyList()
    }

    /** Rendered form for the diagnostics field: one "[Area] message" line per notice. */
    fun renderText(): String = _notices.value.joinToString("\n") { "[${it.area}] ${it.message}" }
}
