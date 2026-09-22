package xyz.fieldatlas

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.benchmark.BenchmarkCodec
import xyz.fieldatlas.diagnostics.DiagnosticsProvider
import xyz.fieldatlas.export.ExportStagingStore
import xyz.fieldatlas.ui.FieldAtlasApp
import xyz.fieldatlas.ui.rememberFieldAtlasNavigationState
import xyz.fieldatlas.ui.proof.BenchmarkScreen
import xyz.fieldatlas.ui.proof.BenchmarkViewModel
import xyz.fieldatlas.ui.research.ResearchViewModel
import xyz.fieldatlas.ui.setup.SetupViewModel
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as FieldAtlasApplication).container
    private val setupViewModel: SetupViewModel by viewModels { container.setupViewModelFactory }
    private val researchViewModel: ResearchViewModel by viewModels { container.researchViewModelFactory }
    private val benchmarkViewModel: BenchmarkViewModel by viewModels { container.benchmarkViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val setupState by setupViewModel.state.collectAsStateWithLifecycle()
            val researchState by researchViewModel.uiState.collectAsStateWithLifecycle()
            val inferenceState by container.inference.state.collectAsStateWithLifecycle()
            var showBenchmark by rememberSaveable { mutableStateOf(false) }
            val appNavigation = rememberFieldAtlasNavigationState()
            val scope = rememberCoroutineScope()
            val exportStore = remember { ExportStagingStore(File(filesDir, "pending-exports")) }
            val proof = remember(setupState.packs, inferenceState, researchState.metrics, researchState.completion) {
                container.proof(researchState.metrics, researchState.completion)
            }
            val packPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) setupViewModel.importPack(uri) }
            val diagnosticsExporter = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri != null) transferExport(
                    scope, exportStore, DIAGNOSTICS_EXPORT, uri, "Diagnostics exported",
                )
            }
            val benchmarkExporter = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri != null) transferExport(
                    scope, exportStore, BENCHMARK_EXPORT, uri, "Benchmark evidence exported",
                )
            }

            if (showBenchmark) {
                benchmarkWasOpened = true
                val benchmarkState by benchmarkViewModel.state.collectAsStateWithLifecycle()
                FieldAtlasTheme {
                    BenchmarkScreen(
                        state = benchmarkState,
                        inferenceState = inferenceState,
                        onRunNext = benchmarkViewModel::runNext,
                        onStop = benchmarkViewModel::stop,
                        onExport = { run ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    exportStore.stage(BENCHMARK_EXPORT, BenchmarkCodec.encode(run).toByteArray())
                                }
                                benchmarkExporter.launch("fieldatlas-benchmark-${safeTimestamp(run.startedAt)}.json")
                            }
                        },
                        onBack = { showBenchmark = false },
                    )
                }
                return@setContent
            }

            FieldAtlasApp(
                packs = setupState.packs,
                importing = setupState.importing,
                setupError = setupState.error,
                researchState = researchState,
                inferenceState = inferenceState,
                proof = proof,
                navigation = appNavigation,
                onImportPack = { packPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                onQuestionChange = researchViewModel::updateQuestion,
                onSubmit = researchViewModel::submit,
                onStop = researchViewModel::cancelResearch,
                onLoadModel = { scope.launch { runCatching { container.loadModel() } } },
                onUnloadModel = { scope.launch { runCatching { container.unloadModel() } } },
                onExportDiagnostics = { includeQuestion ->
                    val snapshot = container.diagnosticsSnapshot(
                        researchState.metrics,
                        researchState.completion,
                        researchState.question,
                        includeQuestion = includeQuestion,
                    )
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            exportStore.stage(
                                DIAGNOSTICS_EXPORT,
                                DiagnosticsProvider.toJson(snapshot).toByteArray(),
                            )
                        }
                        diagnosticsExporter.launch("fieldatlas-diagnostics.json")
                    }
                },
                onOpenBenchmark = {
                    benchmarkWasOpened = true
                    showBenchmark = true
                },
            )
        }
    }

    override fun onStop() {
        researchViewModel.cancelResearch()
        if (benchmarkWasOpened) benchmarkViewModel.stop()
        super.onStop()
    }

    private var benchmarkWasOpened = false

    private fun transferExport(
        scope: kotlinx.coroutines.CoroutineScope,
        store: ExportStagingStore,
        name: String,
        uri: android.net.Uri,
        successMessage: String,
    ) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val output = checkNotNull(contentResolver.openOutputStream(uri, "w")) {
                        "The selected document cannot be opened"
                    }
                    output.use { check(store.transfer(name, it)) { "The staged export is unavailable" } }
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) successMessage else "Export failed; try again",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun safeTimestamp(value: String): String = value.replace(Regex("[^0-9A-Za-z]+"), "-").trim('-')

    private companion object {
        const val BENCHMARK_EXPORT = "benchmark"
        const val DIAGNOSTICS_EXPORT = "diagnostics"
    }
}
