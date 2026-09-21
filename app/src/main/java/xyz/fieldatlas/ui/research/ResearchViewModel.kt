package xyz.fieldatlas.ui.research

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.AnswerText
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.research.ResearchOrchestrator

enum class ResearchPhase { Idle, Searching, Generating, Complete, Insufficient, Error }

data class ResearchUiState(
    val question: String = "",
    val answer: String = "",
    val sources: List<Evidence> = emptyList(),
    val phase: ResearchPhase = ResearchPhase.Idle,
    val metrics: ResearchMetrics? = null,
    val completion: ResearchCompletion? = null,
    val error: String? = null,
) {
    val isRunning: Boolean get() = phase == ResearchPhase.Searching || phase == ResearchPhase.Generating
}

class ResearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val orchestrator: ResearchOrchestrator,
    launchScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = launchScope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(
        ResearchUiState(question = savedStateHandle.get<String>(QUESTION_KEY).orEmpty()),
    )
    val uiState: StateFlow<ResearchUiState> = mutableUiState.asStateFlow()
    private var researchJob: Job? = null
    private val rawAnswer = StringBuilder()

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
                    is ResearchEvent.Searching -> mutableUiState.value.copy(
                        phase = ResearchPhase.Searching,
                        error = null,
                    )
                    is ResearchEvent.Sources -> mutableUiState.value.copy(sources = event.evidence)
                    is ResearchEvent.Token -> {
                        rawAnswer.append(event.text)
                        mutableUiState.value.copy(
                            answer = AnswerText.visible(rawAnswer.toString()),
                            phase = ResearchPhase.Generating,
                        )
                    }
                    is ResearchEvent.Complete -> mutableUiState.value.copy(
                        phase = ResearchPhase.Complete,
                        metrics = event.metrics,
                        completion = ResearchCompletion.Complete,
                    )
                    is ResearchEvent.InsufficientEvidence -> mutableUiState.value.copy(
                        phase = ResearchPhase.Insufficient,
                        error = event.reason,
                    )
                    is ResearchEvent.Failed -> mutableUiState.value.copy(
                        phase = ResearchPhase.Error,
                        error = event.message,
                    )
                }
            }
        }
    }

    fun cancelResearch() {
        val wasRunning = mutableUiState.value.isRunning
        researchJob?.cancel()
        researchJob = null
        if (wasRunning) {
            mutableUiState.value = mutableUiState.value.copy(
                phase = ResearchPhase.Idle,
                completion = ResearchCompletion.Cancelled,
            )
        }
    }

    override fun onCleared() {
        researchJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val QUESTION_KEY = "research.question"
    }
}
