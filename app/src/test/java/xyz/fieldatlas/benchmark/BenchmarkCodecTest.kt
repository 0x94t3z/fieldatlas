package xyz.fieldatlas.benchmark

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.diagnostics.InstalledAssetSummary

class BenchmarkCodecTest {
    @Test fun resultsRemainBoundToQuestionIdsInFrozenOrder() {
        val recorder = BenchmarkRecorder(
            questions = listOf(question("q1"), question("q2")),
            runId = "run-1",
            startedAt = "2026-09-20T00:00:00Z",
            artifacts = emptyList(),
            diagnosticsSha256 = "a".repeat(64),
        )
        recorder.record(result("q2"))
        assertThrows(IllegalArgumentException::class.java) { recorder.record(result("q1")) }
        assertEquals(listOf("q2"), recorder.snapshot().results.map { it.questionId })
    }

    @Test fun encodingIsDeterministicAndSortsArtifactIdentity() {
        val first = run(listOf(asset("z"), asset("a")))
        val second = run(listOf(asset("a"), asset("z")))
        assertEquals(BenchmarkCodec.encode(first), BenchmarkCodec.encode(second))
    }

    @Test fun exporterRefusesAnExistingDestination() {
        val directory = Files.createTempDirectory("fieldatlas-benchmark").toFile()
        try {
            val destination = File(directory, "run.json").also { it.createNewFile() }
            assertThrows(IllegalStateException::class.java) { BenchmarkCodec.write(run(), destination) }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun run(artifacts: List<InstalledAssetSummary> = emptyList()) = BenchmarkRun(
        runId = "run-1",
        startedAt = "2026-09-20T00:00:00Z",
        artifacts = artifacts,
        diagnosticsSha256 = "b".repeat(64),
        results = listOf(result("q1")),
    )

    private fun question(id: String) = BenchmarkQuestion(
        id = id,
        category = "factual",
        prompt = "Prompt $id",
        answerable = true,
        requiredEvidence = listOf("fact"),
        prohibitedClaims = emptyList(),
        scoringNotes = "Fixture",
    )

    private fun result(id: String) = BenchmarkResult(
        questionId = id,
        prompt = "Prompt $id",
        state = BenchmarkResultState.COMPLETE,
        answer = "Answer [S1]",
        evidenceChunkIds = listOf("doc:0000"),
        metrics = null,
        error = null,
    )

    private fun asset(id: String) = InstalledAssetSummary(
        id = id,
        version = "1",
        type = PackType.KNOWLEDGE,
        installedBytes = 10,
        manifestSha256 = id.repeat(64).take(64),
    )
}
