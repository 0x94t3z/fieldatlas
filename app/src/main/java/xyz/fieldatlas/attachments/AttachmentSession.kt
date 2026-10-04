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
    fun staged(): List<AttachmentInput> = state.value.mapNotNull { inputs[it.id] }
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
                    input?.let { cleanup(it.localFile) }
                    inputs.remove(id)
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
