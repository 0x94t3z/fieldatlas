package xyz.fieldatlas.attachments

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AttachmentPhase { Reading, Ready, Error }
data class AttachmentUiState(
    val id: String, val displayName: String,
    val phase: AttachmentPhase = AttachmentPhase.Reading,
    val extracted: ExtractedAttachment? = null, val error: String? = null,
    val kind: AttachmentKind? = null, val previewFile: File? = null,
    val originalFile: File? = null,
)

/** A ready file whose staged bytes and extracted text move together between a session and a background answer. */
data class OwnedAttachment(val ui: AttachmentUiState, val input: AttachmentInput)

/** Called from the UI dispatcher; extraction/staging delegates move expensive work off it. */
class AttachmentSession(
    private val scope: CoroutineScope,
    private val reader: AttachmentReader,
    private val cleanup: (File) -> Unit,
) {
    private val mutableState = MutableStateFlow<List<AttachmentUiState>>(emptyList())
    val state = mutableState.asStateFlow()
    val ready get() = state.value.all { it.phase == AttachmentPhase.Ready }
    private val jobs = mutableMapOf<String, Job>()
    private val loaders = mutableMapOf<String, suspend () -> AttachmentInput>()
    private val inputs = mutableMapOf<String, AttachmentInput>()

    fun add(name: String, load: suspend () -> AttachmentInput) {
        AttachmentPolicy.checkCount(state.value.size)
        val id = UUID.randomUUID().toString()
        mutableState.value += AttachmentUiState(id, attachmentDisplayName(name))
        loaders[id] = load
        start(id)
    }
    /** Re-attaches files staged before the app was closed; they are read again from disk. */
    fun restore(staged: List<AttachmentInput>) {
        for (input in staged.take(AttachmentPolicy.MAX_COUNT - state.value.size)) {
            val id = UUID.randomUUID().toString()
            mutableState.value += AttachmentUiState(id, input.displayName, kind = input.kind, originalFile = input.localFile,
                previewFile = input.localFile.takeIf { input.kind == AttachmentKind.IMAGE })
            inputs[id] = input
            start(id)
        }
    }
    /** Staged files currently attached, in display order, for saving across process death. */
    fun staged(ids: Set<String>? = null): List<AttachmentInput> = state.value
        .filter { ids == null || it.id in ids }.mapNotNull { inputs[it.id] }

    /** Transfers ownership without deleting or re-reading the files. Only fully read, staged files may leave. */
    fun take(ids: Set<String>): List<OwnedAttachment> {
        val selected = state.value.filter { it.id in ids }
        require(selected.size == ids.size) { "Cannot transfer a file that is not in this session" }
        require(selected.all { it.phase == AttachmentPhase.Ready && it.extracted != null && inputs[it.id] != null }) {
            "Only ready staged files can be transferred"
        }
        val owned = selected.map { OwnedAttachment(it, inputs.getValue(it.id)) }
        // Remove ownership before publishing: a collector may resume the read coroutine's finally immediately.
        selected.forEach { inputs.remove(it.id); jobs.remove(it.id); loaders.remove(it.id) }
        mutableState.value = state.value.filterNot { it.id in ids }
        return owned
    }

    /** Takes back files transferred by [take], preserving their IDs, previews and extracted text. */
    fun restoreOwned(files: List<OwnedAttachment>) {
        if (files.isEmpty()) return
        val ids = files.map { it.ui.id }
        require(ids.distinct().size == ids.size && state.value.none { it.id in ids }) { "Duplicate transferred file" }
        require(files.all { it.ui.phase == AttachmentPhase.Ready && it.ui.extracted != null }) { "Transferred files must be ready" }
        AttachmentPolicy.checkCount(state.value.size + files.size - 1)
        files.forEach { inputs[it.ui.id] = it.input }
        mutableState.value += files.map { it.ui }
    }

    fun retry(id: String) {
        if (state.value.none { it.id == id && it.phase == AttachmentPhase.Error }) return
        update(id) { it.copy(phase = AttachmentPhase.Reading, error = null) }
        start(id)
    }
    private fun start(id: String) {
        jobs[id] = scope.launch {
            var input: AttachmentInput? = inputs[id]
            try {
                input = input ?: loaders.getValue(id).invoke()
                // A cancelled staging job may still return a file: keep ownership before checking cancellation.
                inputs[id] = input
                loaders.remove(id)
                ensureActive()
                update(id) { it.copy(displayName = input.displayName, kind = input.kind, originalFile = input.localFile,
                    previewFile = input.localFile.takeIf { input.kind == AttachmentKind.IMAGE }) }
                val extracted = reader.read(input)
                ensureActive()
                update(id) { it.copy(displayName = input.displayName, phase = AttachmentPhase.Ready, extracted = extracted, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                update(id) { it.copy(phase = AttachmentPhase.Error, error = attachmentError(error)) }
            } finally {
                if (state.value.none { it.id == id }) {
                    // take() may have handed a just-read file to a background answer. Only delete bytes still owned here.
                    inputs.remove(id)?.let { cleanup(it.localFile) }
                }
            }
        }
    }
    private fun update(id: String, change: (AttachmentUiState) -> AttachmentUiState) {
        mutableState.value = state.value.map { if (it.id == id) change(it) else it }
    }
    fun remove(id: String) {
        mutableState.value = state.value.filterNot { it.id == id }
        loaders.remove(id)
        val job = jobs.remove(id)
        if (job?.isActive == true) job.cancel() else inputs.remove(id)?.let { cleanup(it.localFile) }
    }
    fun clear() { state.value.map { it.id }.forEach(::remove) }
}
