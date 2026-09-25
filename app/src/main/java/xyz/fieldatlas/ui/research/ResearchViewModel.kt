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

enum class ResearchPhase { Idle, Planning, Searching, Generating, Complete, Insufficient, Error }

enum class VoicePhase { Idle, Starting, Recording, Processing }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val level: Float = 0f,
    val error: String? = null,
)

data class ResearchUiState(
    val question: String = "",
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
) : ViewModel() {
    private val scope = launchScope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(
        ResearchUiState(question = savedStateHandle.get<String>(QUESTION_KEY).orEmpty()),
    )
    val uiState: StateFlow<ResearchUiState> = mutableUiState.asStateFlow()
    private var researchJob: Job? = null
    private val rawAnswer = StringBuilder()
    private val mutableVoiceState = MutableStateFlow(VoiceUiState())
    val voiceState: StateFlow<VoiceUiState> = mutableVoiceState.asStateFlow()
    private var activeTranscriber: SpeechTranscriber? = null

    init {
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
        when (mutableVoiceState.value.phase) {
            VoicePhase.Idle -> startVoiceCapture()
            VoicePhase.Starting, VoicePhase.Recording -> stopVoiceCapture()
            VoicePhase.Processing -> Unit
        }
    }

    private fun startVoiceCapture() {
        scope.launch {
            mutableVoiceState.value = VoiceUiState(VoicePhase.Starting)
            val transcriber = runCatching { createTranscriber() }.getOrNull()
            if (transcriber == null) {
                mutableVoiceState.value = VoiceUiState(error = "Voice capture is not available on this build.")
                return@launch
            }
            activeTranscriber = transcriber
            val startFailure = runCatching {
                transcriber.start { level ->
                    mutableVoiceState.value = mutableVoiceState.value.copy(
                        level = level.coerceIn(0f, 1f),
                    )
                }
            }.exceptionOrNull()
            if (startFailure != null || activeTranscriber !== transcriber) {
                activeTranscriber = null
                val reason = startFailure?.let { "Could not start recording: ${it.message ?: it.javaClass.simpleName}" }
                reason?.let(onError)
                mutableVoiceState.value = VoiceUiState(error = reason)
                return@launch
            }
            mutableVoiceState.value = VoiceUiState(VoicePhase.Recording)
        }
    }

    private fun stopVoiceCapture() {
        scope.launch {
            val transcriber = activeTranscriber ?: return@launch
            mutableVoiceState.value = VoiceUiState(VoicePhase.Processing)
            val result = runCatching { transcriber.stop() }
            activeTranscriber = null
            val transcript = result.getOrDefault("")
            result.exceptionOrNull()?.let { failure ->
                onError("The recording stopped early: ${failure.message ?: failure.javaClass.simpleName}")
            }
            if (transcript.isNotBlank()) {
                val existing = mutableUiState.value.question.trim()
                updateQuestion(((if (existing.isEmpty()) "" else "$existing ") + transcript).trim())
            }
            mutableVoiceState.value = VoiceUiState(
                error = result.exceptionOrNull()?.let {
                    "The recording stopped early: ${it.message ?: it.javaClass.simpleName}"
                },
            )
        }
    }

    fun updateQuestion(question: String) {
        savedStateHandle[QUESTION_KEY] = question
        mutableUiState.value = mutableUiState.value.copy(question = question)
    }

    fun submit() {
        val question = mutableUiState.value.question
        if (question.isBlank() || researchJob?.isActive == true) return
        rawAnswer.clear()
        mutableUiState.value = ResearchUiState(
            question = question,
            phase = ResearchPhase.Searching,
        )
        researchJob = scope.launch {
            orchestrator.research(question).collect { event ->
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
                    is ResearchEvent.Token -> {
                        // The orchestrator emits one empty marker token right after Sources to
                        // flip the phase while the prompt is still being READ; it is not an
                        // answer token and must not light up the written counter, or the
                        // read-progress label would be hidden from the first millisecond.
                        val marker = event.text.isEmpty()
                        rawAnswer.append(event.text)
                        mutableUiState.value.copy(
                            answer = AnswerText.visible(rawAnswer.toString()),
                            phase = ResearchPhase.Generating,
                            tokensWritten = mutableUiState.value.tokensWritten + if (marker) 0 else 1,
                        )
                    }
                    is ResearchEvent.Complete -> mutableUiState.value.copy(
                        phase = ResearchPhase.Complete,
                        metrics = event.metrics,
                        completion = ResearchCompletion.Complete,
                    ).also {
                        val state = mutableUiState.value
                        historyStore?.record(
                            question = state.question,
                            answer = state.answer,
                            sources = state.sources.map { evidence -> evidence.title },
                        )
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
        researchJob?.cancel()
        researchJob = null
        rawAnswer.clear()
        mutableUiState.value = ResearchUiState(
            question = savedStateHandle.get<String>(QUESTION_KEY).orEmpty(),
        )
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
            )
            mutableUiState.value = state.copy(
                phase = ResearchPhase.Idle,
                completion = ResearchCompletion.Cancelled,
            )
        }
    }

    override fun onCleared() {
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
    }
}
