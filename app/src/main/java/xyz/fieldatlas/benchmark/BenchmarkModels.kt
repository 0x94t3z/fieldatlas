package xyz.fieldatlas.benchmark

import kotlinx.serialization.Serializable
import xyz.fieldatlas.diagnostics.InstalledAssetSummary
import xyz.fieldatlas.research.ResearchMetrics

@Serializable
data class BenchmarkQuestionSet(
    val questions: List<BenchmarkQuestion>,
    val schemaVersion: Int,
)

@Serializable
data class BenchmarkQuestion(
    val answerable: Boolean,
    val category: String,
    val id: String,
    val prohibitedClaims: List<String>,
    val prompt: String,
    val requiredEvidence: List<String>,
    val scoringNotes: String,
)

@Serializable
enum class BenchmarkResultState { COMPLETE, CANCELLED, FAILED, INSUFFICIENT }

@Serializable
data class BenchmarkResult(
    val questionId: String,
    val prompt: String,
    val state: BenchmarkResultState,
    val answer: String,
    val evidenceChunkIds: List<String>,
    val metrics: ResearchMetrics?,
    val error: String?,
)

@Serializable
data class BenchmarkRun(
    val schemaVersion: Int = 1,
    val runId: String,
    val startedAt: String,
    val artifacts: List<InstalledAssetSummary>,
    val diagnosticsSha256: String,
    val results: List<BenchmarkResult>,
)

class BenchmarkRecorder(
    private val questions: List<BenchmarkQuestion>,
    private val runId: String,
    private val startedAt: String,
    private val artifacts: List<InstalledAssetSummary>,
    private val diagnosticsSha256: String,
) {
    private val results = mutableListOf<BenchmarkResult>()
    private var lastQuestionIndex = -1

    fun record(result: BenchmarkResult) {
        val index = questions.indexOfFirst { it.id == result.questionId }
        require(index >= 0) { "Unknown benchmark question: ${result.questionId}" }
        require(index > lastQuestionIndex) { "Benchmark results must follow frozen question order" }
        require(result.prompt == questions[index].prompt) { "Result prompt does not match frozen question" }
        results += result
        lastQuestionIndex = index
    }

    fun snapshot(): BenchmarkRun = BenchmarkRun(
        runId = runId,
        startedAt = startedAt,
        artifacts = artifacts.sortedWith(compareBy(InstalledAssetSummary::id, InstalledAssetSummary::version)),
        diagnosticsSha256 = diagnosticsSha256,
        results = results.toList(),
    )
}
