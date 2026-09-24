package xyz.fieldatlas.ui.proof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.fieldatlas.benchmark.BenchmarkQuestion
import xyz.fieldatlas.benchmark.BenchmarkRecorder
import xyz.fieldatlas.benchmark.BenchmarkResult
import xyz.fieldatlas.benchmark.BenchmarkResultState
import xyz.fieldatlas.benchmark.BenchmarkRun
import xyz.fieldatlas.diagnostics.InstalledAssetSummary
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.AnswerText

data class BenchmarkUiState(
    val run: BenchmarkRun,
    val totalQuestions: Int,
    val nextQuestion: BenchmarkQuestion?,
    val running: Boolean = false,
    val currentAnswer: String = "",
    val error: String? = null,
)

class BenchmarkViewModel(
    private val questions: List<BenchmarkQuestion>,
    private val orchestrator: ResearchOrchestrator,
    artifacts: List<InstalledAssetSummary>,
    diagnosticsSha256: String,
    runId: () -> String = { UUID.randomUUID().toString() },
    now: () -> String = { Instant.now().toString() },
    launchScope: CoroutineScope? = null,
    private val onError: (String) -> Unit = {},
) : ViewModel() {
    private val scope = launchScope ?: viewModelScope
    private val recorder = BenchmarkRecorder(questions, runId(), now(), artifacts, diagnosticsSha256)
    private val mutableState = MutableStateFlow(
        BenchmarkUiState(recorder.snapshot(), questions.size, questions.firstOrNull()),
    )
    val state: StateFlow<BenchmarkUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var active: ActiveResult? = null

    fun runNext() {
        if (job?.isActive == true || mutableState.value.nextQuestion == null) return
        val question = mutableState.value.nextQuestion ?: return
        active = ActiveResult(question)
        mutableState.value = mutableState.value.copy(running = true, currentAnswer = "", error = null)
        job = scope.launch {
            try {
                orchestrator.research(question.prompt).collect { event ->
                    when (event) {
                        is ResearchEvent.Planning -> Unit
                        is ResearchEvent.Keywords -> Unit
                        is ResearchEvent.Searching -> Unit
                        is ResearchEvent.Sources -> {
                            active?.evidenceChunkIds = event.evidence.map { it.chunkId }
                        }
                        is ResearchEvent.Token -> {
                            active?.answer?.append(event.text)
                            mutableState.value = mutableState.value.copy(
                                currentAnswer = AnswerText.visible(active?.answer?.toString().orEmpty()),
                            )
                        }
                        is ResearchEvent.Complete -> {
                            active?.metrics = event.metrics
                            finishActive(BenchmarkResultState.COMPLETE)
                        }
                        is ResearchEvent.InsufficientEvidence -> {
                            active?.answer?.append("Insufficient evidence: ${event.reason}")
                            finishActive(BenchmarkResultState.INSUFFICIENT, event.reason)
                        }
                        is ResearchEvent.Failed -> finishActive(BenchmarkResultState.FAILED, event.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun stop() {
        if (active == null) return
        job?.cancel()
        job = null
        finishActive(BenchmarkResultState.CANCELLED)
    }

    @Synchronized
    private fun finishActive(state: BenchmarkResultState, error: String? = null) {
        error?.let(onError)
        val result = active ?: return
        active = null
        recorder.record(
            BenchmarkResult(
                questionId = result.question.id,
                prompt = result.question.prompt,
                state = state,
                answer = AnswerText.visible(result.answer.toString()),
                evidenceChunkIds = result.evidenceChunkIds,
                metrics = result.metrics,
                error = error,
            ),
        )
        val run = recorder.snapshot()
        mutableState.value = BenchmarkUiState(
            run = run,
            totalQuestions = questions.size,
            nextQuestion = questions.getOrNull(questions.indexOf(result.question) + 1),
            running = false,
            error = error,
        )
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private data class ActiveResult(
        val question: BenchmarkQuestion,
        val answer: StringBuilder = StringBuilder(),
        var evidenceChunkIds: List<String> = emptyList(),
        var metrics: ResearchMetrics? = null,
    )
}
