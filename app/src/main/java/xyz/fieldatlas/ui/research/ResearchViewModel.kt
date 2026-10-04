package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.AnswerHistoryStore
import xyz.fieldatlas.research.AnswerText
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.speech.SpeechTranscriber
import xyz.fieldatlas.attachments.*
import java.io.File

enum class ResearchPhase { Idle, Planning, Searching, Generating, Complete, Insufficient, Error }

enum class VoicePhase { Idle, Starting, Recording, Processing }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val level: Float = 0f,
    val error: String? = null,
    /** Words recognised so far while recording, already shown in the question box. */
    val liveText: String = "",
)

data class ResearchUiState(
    val attachments: List<AttachmentUiState> = emptyList(),
    val question: String = "",
    val startedAtNanos: Long? = null,
    val answer: String = "",
    val sources: List<Evidence> = emptyList(),
    val keywords: List<String> = emptyList(),
    val phase: ResearchPhase = ResearchPhase.Idle,
    val retrievalProgress: Double = 0.0,
    val retrievalVectorMatches: Int = 0,
    /** (tokensRead, tokensTotal) while the engine decodes the answer prompt; null otherwise. */
    val promptRead: Pair<Int, Int>? = null,
    val tokensWritten: Int = 0,
    val metrics: ResearchMetrics? = null,
    val completion: ResearchCompletion? = null,
    val error: String? = null,
) {
    val canAddAttachment: Boolean get() = !isRunning && attachments.size < AttachmentPolicy.MAX_COUNT
    val canSubmit: Boolean get() = question.isNotBlank() && !isRunning && attachments.all { it.phase == AttachmentPhase.Ready }
    val isRunning: Boolean get() = phase == ResearchPhase.Planning ||
        phase == ResearchPhase.Searching ||
        phase == ResearchPhase.Generating
}

class ResearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val orchestrator: ResearchOrchestrator,
    launchScope: CoroutineScope? = null,
    private val createTranscriber: () -> SpeechTranscriber = { error("Speech capture is unavailable") },
    private val onError: (String) -> Unit = {},
    private val historyStore: AnswerHistoryStore? = null,
    attachmentReader: AttachmentReader = AttachmentReader { throw AttachmentException("Attachments are unavailable.") },
    cleanupAttachment: (File) -> Unit = {},
    stagedAttachment: (String) -> File? = { null },
    clearAbandonedAttachments: (Set<String>) -> Unit = {},
) : ViewModel() {
    private val scope = launchScope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(
        ResearchUiState(question = savedStateHandle.get<String>(QUESTION_KEY).orEmpty()),
    )
    private val attachmentSession = AttachmentSession(scope, attachmentReader, cleanupAttachment)
    fun addAttachment(name: String, load: suspend () -> AttachmentInput) {
        if (mutableUiState.value.isRunning) return
        if (mutableUiState.value.phase == ResearchPhase.Complete) {
            mutableUiState.value = ResearchUiState(question = mutableUiState.value.question, attachments = attachmentSession.state.value)
        }
        attachmentSession.add(name, load)
    }
    fun removeAttachment(id: String) { if (!mutableUiState.value.isRunning) attachmentSession.remove(id) }
    fun retryAttachment(id: String) { if (!mutableUiState.value.isRunning) attachmentSession.retry(id) }
    val uiState: StateFlow<ResearchUiState> = mutableUiState.asStateFlow()
    private var researchJob: Job? = null
    private val rawAnswer = StringBuilder()
    private val mutableVoiceState = MutableStateFlow(VoiceUiState())
    val voiceState: StateFlow<VoiceUiState> = mutableVoiceState.asStateFlow()
    private var activeTranscriber: SpeechTranscriber? = null

    init {
        // Android may close the app while a picker or another app is in front (Xiaomi does this
        // within seconds). The question survives in saved state; bring its files back with it.
        val saved = savedStateHandle.get<ArrayList<String>>(ATTACHMENTS_KEY).orEmpty().mapNotNull { record ->
            val (id, kind, name) = record.split('\t', limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            val file = stagedAttachment(id) ?: return@mapNotNull null
            val type = AttachmentKind.entries.firstOrNull { it.name == kind } ?: return@mapNotNull null
            AttachmentInput(id, name, file, type)
        }
        clearAbandonedAttachments(saved.map { it.id }.toSet())
        if (saved.isNotEmpty()) attachmentSession.restore(saved)
        scope.launch {
            attachmentSession.state.collect { attachments ->
                mutableUiState.update { it.copy(attachments = attachments) }
                savedStateHandle[ATTACHMENTS_KEY] = ArrayList(attachmentSession.staged().map { "${it.id}\t${it.kind.name}\t${it.displayName}" })
            }
        }
        scope.launch {
            orchestrator.promptProgress.collect { progress: PromptProgress? ->
                mutableUiState.update { state ->
                    state.copy(
                        promptRead = progress?.let { it.processed to it.total },
                    )
                }
            }
        }
        scope.launch {
            // Retrieval milestones reach the orchestrator as a StateFlow, not flow events: the
            // retriever publishes them from its IO worker coroutine (see ResearchOrchestrator).
            orchestrator.searchProgress.collect { fraction ->
                mutableUiState.update { state ->
                    state.copy(retrievalProgress = fraction.coerceIn(0.0, 1.0))
                }
            }
        }
        scope.launch {
            orchestrator.vectorMatches.collect { count ->
                mutableUiState.update { state ->
                    state.copy(retrievalVectorMatches = count.coerceAtLeast(0))
                }
            }
        }
    }

    /**
     * Mic button entry point. Idle starts capture; Recording (or the brief
     * Starting phase while the model loads) stops it. Transcription is only
     * parked into the question box — the user still presses Start research.
     */
    fun onMicClick() {
        if (mutableUiState.value.isRunning) return
        when (mutableVoiceState.value.phase) {
            VoicePhase.Idle -> startVoiceCapture()
            VoicePhase.Recording -> stopVoiceCapture()
            VoicePhase.Starting, VoicePhase.Processing -> Unit
        }
    }

    fun onMicrophonePermissionDenied() {
        mutableVoiceState.value = VoiceUiState(
            error = "Allow microphone access in Android settings to use offline dictation.",
        )
    }

    private fun startVoiceCapture() {
        scope.launch {
            mutableVoiceState.value = VoiceUiState(VoicePhase.Starting)
            val transcriber = runCatching { createTranscriber() }.getOrNull()
            if (transcriber == null) {
                mutableVoiceState.value = VoiceUiState(error = "Voice input isn't available on this phone right now.")
                return@launch
            }
            activeTranscriber = transcriber
            // Live words are appended to what was already typed; stop() swaps in the final text.
            voiceBaseQuestion = mutableUiState.value.question.trim()
            val startFailure = runCatching {
                transcriber.start(
                    onLevel = { level ->
                        mutableVoiceState.value = mutableVoiceState.value.copy(level = level.coerceIn(0f, 1f))
                    },
                    onText = { live ->
                        scope.launch {
                            if (activeTranscriber !== transcriber) return@launch
                            mutableVoiceState.value = mutableVoiceState.value.copy(liveText = live)
                            updateQuestion(joinQuestion(voiceBaseQuestion, live))
                        }
                    },
                )
            }.exceptionOrNull()
            if (startFailure != null || activeTranscriber !== transcriber) {
                activeTranscriber = null
                runCatching { transcriber.cancel() }
                val reason = startFailure?.let { "Could not start recording: ${it.message ?: it.javaClass.simpleName}" }
                mutableVoiceState.value = VoiceUiState(error = reason)
                return@launch
            }
            mutableVoiceState.value = VoiceUiState(VoicePhase.Recording)
        }
    }

    private var voiceBaseQuestion = ""

    private fun joinQuestion(base: String, spoken: String) = listOf(base, spoken.trim()).filter(String::isNotBlank).joinToString(" ")

    private fun stopVoiceCapture() {
        scope.launch {
            val transcriber = activeTranscriber ?: return@launch
            val live = mutableVoiceState.value.liveText
            mutableVoiceState.value = VoiceUiState(VoicePhase.Processing)
            val result = runCatching { transcriber.stop() }
            activeTranscriber = null
            // The final pass can correct the live guess; if it returns nothing, keep what was shown.
            val transcript = result.getOrDefault("").ifBlank { live }
            updateQuestion(joinQuestion(voiceBaseQuestion, transcript))
            val failure = result.exceptionOrNull()
            mutableVoiceState.value = VoiceUiState(
                error = failure?.let {
                    "The recording stopped early: ${it.message ?: it.javaClass.simpleName}"
                } ?: if (transcript.isBlank()) "No speech was detected. Try speaking again." else null,
            )
        }
    }

    fun updateQuestion(question: String) {
        savedStateHandle[QUESTION_KEY] = question
        mutableUiState.update { state ->
            if (state.phase == ResearchPhase.Complete && question != state.question) {
                ResearchUiState(question = question, attachments = attachmentSession.state.value)
            } else {
                state.copy(question = question)
            }
        }
    }

    fun submit() {
        val question = mutableUiState.value.question
        if (question.isBlank() || !attachmentSession.ready || researchJob?.isActive == true) return
        rawAnswer.clear()
        mutableUiState.value = ResearchUiState(
            question = question,
            attachments = attachmentSession.state.value,
            startedAtNanos = System.nanoTime(),
            phase = ResearchPhase.Searching,
        )
        researchJob = scope.launch {
            orchestrator.research(question, attachments = attachmentSession.state.value.mapNotNull { it.extracted }).collect { event ->
                mutableUiState.value = when (event) {
                    is ResearchEvent.Planning -> mutableUiState.value.copy(
                        phase = ResearchPhase.Planning,
                        error = null,
                    )
                    is ResearchEvent.Keywords -> mutableUiState.value.copy(keywords = event.terms)
                    is ResearchEvent.Searching -> mutableUiState.value.copy(
                        phase = ResearchPhase.Searching,
                        retrievalProgress = 0.0,
                        retrievalVectorMatches = 0,
                        error = null,
                    )
                    is ResearchEvent.Sources -> mutableUiState.value.copy(sources = event.evidence)
                    is ResearchEvent.Lead -> {
                        // Shown while the model is still reading its prompt; tokensWritten
                        // stays at zero so the prefill progress label remains visible.
                        rawAnswer.clear()
                        rawAnswer.append(event.text)
                        mutableUiState.value.copy(
                            answer = AnswerText.visible(rawAnswer.toString()),
                            phase = ResearchPhase.Generating,
                        )
                    }
                    is ResearchEvent.Token -> {
                        // The orchestrator emits one empty marker token right after Sources to
                        // flip the phase while the prompt is still being READ; it is not an
                        // answer token and must not light up the written counter, or the
                        // read-progress label would be hidden from the first millisecond.
                        val marker = event.text.isEmpty()
                        if (event.replace) rawAnswer.clear()
                        rawAnswer.append(event.text)
                        mutableUiState.value.copy(
                            answer = AnswerText.visible(rawAnswer.toString()),
                            phase = ResearchPhase.Generating,
                            tokensWritten = mutableUiState.value.tokensWritten + if (marker) 0 else 1,
                        )
                    }
                    is ResearchEvent.Complete -> {
                        val finalAnswer = AnswerText.finalized(rawAnswer.toString(), mutableUiState.value.sources.size)
                        mutableUiState.value.copy(
                            answer = finalAnswer,
                            phase = ResearchPhase.Complete,
                            metrics = event.metrics,
                            completion = ResearchCompletion.Complete,
                        ).also {
                            val state = it
                            historyStore?.record(
                                question = state.question,
                                answer = state.answer,
                                sources = state.sources.map { evidence -> evidence.title },
                                evidence = historyEvidence(state.sources),
                            )
                        }
                    }
                    is ResearchEvent.InsufficientEvidence -> mutableUiState.value.copy(
                        phase = ResearchPhase.Insufficient,
                        error = event.reason,
                    )
                    is ResearchEvent.Failed -> mutableUiState.value.copy(
                        phase = ResearchPhase.Error,
                        error = event.message,
                    ).also { onError("Answer attempt failed: ${event.message}") }
                }
            }
        }
    }

    /** Clears the finished answer so the screen returns to a fresh-asking state. */
    fun startNewQuestion() {
        attachmentSession.clear()
        researchJob?.cancel()
        researchJob = null
        rawAnswer.clear()
        savedStateHandle[QUESTION_KEY] = ""
        mutableUiState.value = ResearchUiState()
    }

    fun cancelResearch() {
        val wasRunning = mutableUiState.value.isRunning
        researchJob?.cancel()
        researchJob = null
        if (wasRunning) {
            // Keep whatever the model managed to write: a half-answer the user walked away
            // from and cancelled is still worth finding in History later.
            val state = mutableUiState.value
            historyStore?.record(
                question = state.question,
                answer = state.answer,
                sources = state.sources.map { evidence -> evidence.title },
                evidence = historyEvidence(state.sources),
            )
            mutableUiState.value = state.copy(
                phase = ResearchPhase.Idle,
                completion = ResearchCompletion.Cancelled,
            )
        }
    }

    override fun onCleared() {
        attachmentSession.clear()
        researchJob?.cancel()
        activeTranscriber?.let { transcriber ->
            activeTranscriber = null
            // viewModelScope is already dead here, so release the microphone off-coroutine.
            Thread { runBlocking { runCatching { transcriber.cancel() } } }.apply { isDaemon = true; start() }
        }
        super.onCleared()
    }

    private companion object {
        const val QUESTION_KEY = "research.question"
        const val ATTACHMENTS_KEY = "research.attachments"
    }
}

/** Passages saved with a History answer so its sources stay readable after a collection is
 * removed; capped so a hundred answers stay a small file. */
internal fun historyEvidence(sources: List<xyz.fieldatlas.research.Evidence>): List<xyz.fieldatlas.research.Evidence> =
    sources.map { if (it.text.length <= HISTORY_PASSAGE_CHARS) it else it.copy(text = it.text.take(HISTORY_PASSAGE_CHARS).trimEnd() + "…") }

private const val HISTORY_PASSAGE_CHARS = 2_000
