package xyz.fieldatlas.ui.proof

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.benchmark.BenchmarkQuestion
import xyz.fieldatlas.benchmark.BenchmarkResultState
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

class BenchmarkViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun tearDown() = scope.cancel()

    @Test fun stopPreservesEvidenceAndPartialAnswerAndMarksCurrentCancelled() {
        val cancelled = AtomicBoolean(false)
        val gateway = object : InferenceGateway {
            override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
                try {
                    emit("partial")
                    awaitCancellation()
                } finally {
                    cancelled.set(true)
                }
            }
            override suspend fun unload() = Unit
        }
        val evidence = Evidence("doc", "doc:0000", "Title", "Source", "Fact", 1.0)
        val viewModel = BenchmarkViewModel(
            questions = listOf(question("q1"), question("q2")),
            orchestrator = ResearchOrchestrator(Retriever { _, _ -> listOf(evidence) }, gateway),
            artifacts = emptyList(),
            diagnosticsSha256 = "a".repeat(64),
            runId = { "run-1" },
            now = { "2026-09-20T00:00:00Z" },
            launchScope = scope,
        )

        viewModel.runNext()
        viewModel.stop()

        val result = viewModel.state.value.run.results.single()
        assertTrue(cancelled.get())
        assertEquals(BenchmarkResultState.CANCELLED, result.state)
        assertEquals("partial", result.answer)
        assertEquals(listOf("doc:0000"), result.evidenceChunkIds)
        assertEquals("q2", viewModel.state.value.nextQuestion?.id)
    }

    @Test fun exportedBenchmarkAnswerOmitsModelThinkingMarkers() {
        val gateway = object : InferenceGateway {
            override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
                emit("<thi")
                emit("nk>private reasoning</think>\n\nVisible answer [S1]")
            }
            override suspend fun unload() = Unit
        }
        val evidence = Evidence("doc", "doc:0000", "Title", "Source", "Fact", 1.0)
        val viewModel = BenchmarkViewModel(
            questions = listOf(question("q1")),
            orchestrator = ResearchOrchestrator(Retriever { _, _ -> listOf(evidence) }, gateway),
            artifacts = emptyList(),
            diagnosticsSha256 = "a".repeat(64),
            runId = { "run-1" },
            now = { "2026-09-20T00:00:00Z" },
            launchScope = scope,
        )

        viewModel.runNext()

        assertEquals("Visible answer [S1]", viewModel.state.value.run.results.single().answer)
    }

    private fun question(id: String) = BenchmarkQuestion(
        id = id,
        category = "factual",
        prompt = "Prompt $id",
        answerable = true,
        requiredEvidence = listOf("fact"),
        prohibitedClaims = emptyList(),
        scoringNotes = "Fixture",
    )
}
