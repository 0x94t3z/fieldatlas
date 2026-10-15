package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    /** Words recognised so far while recording, already shown in the box being dictated into. */
    val liveText: String = "",
    /** Dictating into the follow-up box under an answer rather than the question box. */
    val forFollowUp: Boolean = false,
)

data class ResearchUiState(
    val attachments: List<AttachmentUiState> = emptyList(),
    /** The standalone question research runs on; for a follow-up, the reading built from the topic. */
    val question: String = "",
    /** A follow-up as it was typed ("which one is closest?"); blank for a question asked on its own. */
    val asked: String = "",
    /** Answered questions of the same topic, oldest first. */
    val earlier: List<xyz.fieldatlas.research.FollowUp.Turn> = emptyList(),
    /** Groups a question and its follow-ups in History. */
    val topic: Long? = null,
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
    /** The model's draft is being checked against the sources before it is shown. */
    val checking: Boolean = false,
    val metrics: ResearchMetrics? = null,
    val completion: ResearchCompletion? = null,
    val error: String? = null,
) {
    /** What the person asked, as they typed it. */
    val shownQuestion: String get() = asked.ifBlank { question }
    val canAddAttachment: Boolean get() = !isRunning && attachments.size < AttachmentPolicy.MAX_COUNT
    val attachmentsReady: Boolean get() = attachments.all { it.phase == AttachmentPhase.Ready }
    val canSubmit: Boolean get() = question.isNotBlank() && !isRunning && attachmentsReady
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
    /**
     * Where the next file goes. From the question box it starts a new question; from the follow-up box under an
     * answer it joins the topic and the answer stays.
     */
    private var attachForFollowUp = false
    fun selectAttachmentTarget(followUp: Boolean) { attachForFollowUp = followUp }
    fun addAttachment(name: String, load: suspend () -> AttachmentInput) {
        if (mutableUiState.value.isRunning) return
        if (mutableUiState.value.phase == ResearchPhase.Complete && !attachForFollowUp) {
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
    /** Where dictation writes: the question box, or the follow-up box under an answer. Chosen before the mic starts. */
    private var voiceForFollowUp = false
    fun selectVoiceTarget(followUp: Boolean) {
        if (mutableVoiceState.value.phase == VoicePhase.Idle) voiceForFollowUp = followUp
    }

    private val mutableFollowUpDraft = MutableStateFlow("")
    /** The follow-up being typed or dictated under an answer. */
    val followUpDraft: StateFlow<String> = mutableFollowUpDraft.asStateFlow()
    fun updateFollowUpDraft(text: String) {
        mutableFollowUpDraft.value = text
        if (mutableVoiceState.value.error != null && mutableVoiceState.value.phase == VoicePhase.Idle)
            mutableVoiceState.value = mutableVoiceState.value.copy(error = null)
    }

    // Each recording's number; leaving the follow-up box moves it on, so a recording still loading or transcribing
    // when the box left finds itself stale and writes nothing, instead of filling the question box over the answer.
    private var voiceSession = 0

    /** The follow-up box left the screen: a recording into it is dropped rather than left running. */
    fun leaveFollowUp() {
        if (!voiceForFollowUp) return
        voiceSession++
        activeTranscriber?.let { transcriber ->
            activeTranscriber = null
            scope.launch { runCatching { transcriber.cancel() } }
            // The abandoned recording's words go; what was typed before it stays.
            mutableFollowUpDraft.value = voiceBaseQuestion
        }
        voiceForFollowUp = false
        mutableVoiceState.value = VoiceUiState()
    }

    private fun writeDictation(text: String, forFollowUp: Boolean) = if (forFollowUp) updateFollowUpDraft(text) else updateQuestion(text)

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
            forFollowUp = voiceForFollowUp,
        )
    }

    private fun startVoiceCapture() {
        scope.launch {
            val forFollowUp = voiceForFollowUp
            val session = voiceSession
            mutableVoiceState.value = VoiceUiState(VoicePhase.Starting, forFollowUp = forFollowUp)
            val transcriber = runCatching { createTranscriber() }.getOrNull()
            if (session != voiceSession) {
                transcriber?.let { runCatching { it.cancel() } }
                return@launch
            }
            if (transcriber == null) {
                mutableVoiceState.value = VoiceUiState(error = "Voice input isn't available on this phone right now.", forFollowUp = forFollowUp)
                return@launch
            }
            activeTranscriber = transcriber
            // Live words are appended to what was already typed; stop() swaps in the final text.
            voiceBaseQuestion = (if (forFollowUp) mutableFollowUpDraft.value else mutableUiState.value.question).trim()
            val startFailure = runCatching {
                transcriber.start(
                    onLevel = { level ->
                        mutableVoiceState.value = mutableVoiceState.value.copy(level = level.coerceIn(0f, 1f))
                    },
                    onText = { live ->
                        scope.launch {
                            if (activeTranscriber !== transcriber || session != voiceSession) return@launch
                            mutableVoiceState.value = mutableVoiceState.value.copy(liveText = live)
                            writeDictation(joinQuestion(voiceBaseQuestion, live), forFollowUp)
                        }
                    },
                )
            }.exceptionOrNull()
            if (session != voiceSession) return@launch
            if (startFailure != null || activeTranscriber !== transcriber) {
                activeTranscriber = null
                runCatching { transcriber.cancel() }
                val reason = startFailure?.let { "Could not start recording: ${it.message ?: it.javaClass.simpleName}" }
                mutableVoiceState.value = VoiceUiState(error = reason, forFollowUp = forFollowUp)
                return@launch
            }
            mutableVoiceState.value = VoiceUiState(VoicePhase.Recording, forFollowUp = forFollowUp)
        }
    }

    private var voiceBaseQuestion = ""

    private fun joinQuestion(base: String, spoken: String) = listOf(base, spoken.trim()).filter(String::isNotBlank).joinToString(" ")

    private fun stopVoiceCapture() {
        scope.launch {
            val transcriber = activeTranscriber ?: return@launch
            val live = mutableVoiceState.value.liveText
            val forFollowUp = voiceForFollowUp
            val session = voiceSession
            mutableVoiceState.value = VoiceUiState(VoicePhase.Processing, forFollowUp = forFollowUp)
            val result = runCatching { transcriber.stop() }
            if (session != voiceSession) return@launch
            activeTranscriber = null
            // The final pass can correct the live guess; if it returns nothing, keep what was shown.
            val transcript = result.getOrDefault("").ifBlank { live }
            writeDictation(joinQuestion(voiceBaseQuestion, transcript), forFollowUp)
            val failure = result.exceptionOrNull()
            mutableVoiceState.value = VoiceUiState(
                forFollowUp = forFollowUp,
                error = failure?.let {
                    "The recording stopped early: ${it.message ?: it.javaClass.simpleName}"
                } ?: if (transcript.isBlank()) "No speech was detected. Try speaking again." else null,
            )
        }
    }

    fun updateQuestion(question: String) {
        savedStateHandle[QUESTION_KEY] = question
        // A dictation error describes the last recording, not a question typed since.
        if (mutableVoiceState.value.error != null && mutableVoiceState.value.phase == VoicePhase.Idle)
            mutableVoiceState.value = mutableVoiceState.value.copy(error = null)
        mutableUiState.update { state ->
            if (state.phase == ResearchPhase.Complete && question != state.question) {
                ResearchUiState(question = question, attachments = attachmentSession.state.value)
            } else {
                state.copy(question = question)
            }
        }
    }

    /** The last run cancelled, which the next one waits for. */
    private var stoppingJob: kotlinx.coroutines.Job? = null

    /** Cancels the running answer and remembers it, so the next run starts only after it has let go of the model. */
    private fun stopRunning() {
        researchJob?.let { job -> job.cancel(); stoppingJob = job }
        researchJob = null
    }

    fun submit() {
        val question = mutableUiState.value.question
        if (question.isBlank() || !attachmentSession.ready || researchJob?.isActive == true) return
        run(question, asked = "", earlier = emptyList(), topic = System.currentTimeMillis())
    }

    /**
     * Asks [text] about the answer on screen. The follow-up is read together with the question it
     * follows, and that reading is what research runs on and what the progress screen shows.
     */
    fun submitFollowUp(text: String) {
        val state = mutableUiState.value
        if (text.isBlank() || state.answer.isBlank() || state.isRunning || !attachmentSession.ready ||
            researchJob?.isActive == true) return
        val earlier = state.earlier + xyz.fieldatlas.research.FollowUp.Turn(state.shownQuestion, state.question, state.answer)
        val readAs = xyz.fieldatlas.research.FollowUp.readAs(state.question, text, root = earlier.first().readAs)
        savedStateHandle[QUESTION_KEY] = readAs
        mutableFollowUpDraft.value = ""
        attachForFollowUp = false
        run(readAs, asked = text.trim(), earlier = earlier, topic = state.topic ?: System.currentTimeMillis(),
            // An attached file's passages travel with the file itself, not as saved sources: after Continue the file
            // is no longer attached, and its words must not be quoted as the library's.
            carried = xyz.fieldatlas.research.TopicEvidence.Carried(text.trim(),
                state.sources.filterNot { it.documentId.startsWith("attachment:") }))
    }

    /**
     * The reading of a follow-up was wrong: stop, and put it in the question box to correct and
     * ask on its own.
     */
    fun editReadAs() {
        val state = mutableUiState.value
        if (state.asked.isBlank()) return
        stopRunning()
        rawAnswer.clear()
        savedStateHandle[QUESTION_KEY] = state.question
        mutableUiState.value = ResearchUiState(question = state.question, attachments = attachmentSession.state.value)
    }

    private fun run(question: String, asked: String, earlier: List<xyz.fieldatlas.research.FollowUp.Turn>, topic: Long,
                    carried: xyz.fieldatlas.research.TopicEvidence.Carried? = null) {
        rawAnswer.clear()
        mutableUiState.value = ResearchUiState(
            question = question,
            asked = asked,
            earlier = earlier,
            topic = topic,
            attachments = attachmentSession.state.value,
            startedAtNanos = System.nanoTime(),
            phase = ResearchPhase.Searching,
        )
        // A run cancelled a moment ago may still be letting go of the model and search; start after it,
        // or the new run fails with "Another local research run is active".
        val previous = stoppingJob
        researchJob = scope.launch {
            // Waited for even if this run is cancelled meanwhile, so a run after it also waits for the one before.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { previous?.join() }
            // A run cancelled while it waited never touches the model.
            ensureActive()
            orchestrator.research(question, attachments = attachmentSession.state.value.mapNotNull { it.extracted }, topic = carried).collect { event ->
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
                            checking = mutableUiState.value.checking && marker,
                        )
                    }
                    // Progress only: an unreviewed draft is never put on screen.
                    is ResearchEvent.Drafting -> mutableUiState.value.copy(
                        phase = ResearchPhase.Generating,
                        tokensWritten = event.tokens,
                        checking = event.checking,
                    )
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
                                question = state.shownQuestion,
                                answer = state.answer,
                                sources = state.sources.map { evidence -> evidence.title },
                                evidence = historyEvidence(state.sources),
                                topic = state.topic,
                                readAs = state.question.takeIf { state.asked.isNotBlank() },
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

    /**
     * Picks a topic up again from History: its latest answer is shown as finished, the questions before it fold above
     * it, and the next follow-up reads it like any other (with the passages saved alongside it). Returns false while an
     * answer is being written, which is never interrupted for this.
     */
    fun continueTopic(record: xyz.fieldatlas.research.AnswerRecord, history: List<xyz.fieldatlas.research.AnswerRecord>): Boolean {
        if (mutableUiState.value.isRunning) return false
        // An answer saved before topics existed starts its own: its follow-ups take its time as their topic.
        val key = record.topicKey
        val turns = history.filter { it.topicKey == key }
            .sortedBy { it.createdAtEpochMs }.ifEmpty { listOf(record) }
        val last = turns.last()
        stopRunning()
        rawAnswer.clear()
        attachmentSession.clear()
        mutableFollowUpDraft.value = ""
        attachForFollowUp = false
        val readAs = last.readAs ?: last.question
        savedStateHandle[QUESTION_KEY] = readAs
        rawAnswer.append(last.answer)
        mutableUiState.value = ResearchUiState(
            question = readAs,
            asked = if (last.readAs != null) last.question else "",
            earlier = turns.dropLast(1).map { xyz.fieldatlas.research.FollowUp.Turn(it.question, it.readAs ?: it.question, it.answer) },
            topic = last.topic ?: last.createdAtEpochMs,
            answer = last.answer,
            sources = last.evidence,
            phase = ResearchPhase.Complete,
            completion = ResearchCompletion.Complete,
        )
        return true
    }

    /** Clears the finished answer so the screen returns to a fresh-asking state. */
    fun startNewQuestion() {
        // A run in flight is kept in History as Cancel keeps it, not thrown away.
        if (mutableUiState.value.isRunning) cancelResearch()
        attachmentSession.clear()
        stopRunning()
        rawAnswer.clear()
        mutableFollowUpDraft.value = ""
        attachForFollowUp = false
        savedStateHandle[QUESTION_KEY] = ""
        mutableUiState.value = ResearchUiState()
    }

    fun cancelResearch() {
        val wasRunning = mutableUiState.value.isRunning
        stopRunning()
        if (wasRunning) {
            // Keep whatever the model managed to write: a half-answer the user walked away
            // from and cancelled is still worth finding in History later.
            val state = mutableUiState.value
            historyStore?.record(
                question = state.shownQuestion,
                answer = state.answer,
                sources = state.sources.map { evidence -> evidence.title },
                evidence = historyEvidence(state.sources),
                topic = state.topic,
                readAs = state.question.takeIf { state.asked.isNotBlank() },
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
