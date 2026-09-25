package xyz.fieldatlas

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.benchmark.BenchmarkCodec
import xyz.fieldatlas.diagnostics.DiagnosticsProvider
import xyz.fieldatlas.export.ExportStagingStore
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.ui.FieldAtlasApp
import xyz.fieldatlas.ui.rememberFieldAtlasNavigationState
import xyz.fieldatlas.ui.proof.BenchmarkScreen
import xyz.fieldatlas.ui.proof.BenchmarkViewModel
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.KeepAliveService
import xyz.fieldatlas.ui.research.ResearchViewModel
import xyz.fieldatlas.ui.setup.SetupViewModel
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as FieldAtlasApplication).container
    private val setupViewModel: SetupViewModel by viewModels { container.setupViewModelFactory }
    private val researchViewModel: ResearchViewModel by viewModels { container.researchViewModelFactory }
    private val benchmarkViewModel: BenchmarkViewModel by viewModels { container.benchmarkViewModelFactory }
    private val benchmarkVisible = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (benchmarkVisible.value) {
                    benchmarkVisible.value = false
                    benchmarkViewModel.stop()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        setContent {
            val setupState by setupViewModel.state.collectAsStateWithLifecycle()
            val researchState by researchViewModel.uiState.collectAsStateWithLifecycle()
            val historyRecords by container.answerHistory.records.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { container.answerHistory.ensureLoaded() }
            // A run in flight outranks background process trimming: the service pins priority.
            LaunchedEffect(researchState.phase) {
                when (researchState.phase) {
                    ResearchPhase.Planning, ResearchPhase.Searching, ResearchPhase.Generating ->
                        KeepAliveService.start(this@MainActivity)
                    else -> KeepAliveService.stop(this@MainActivity)
                }
            }
            val voiceState by researchViewModel.voiceState.collectAsStateWithLifecycle()
            val diagnosticsNotices by container.errorBus.notices.collectAsStateWithLifecycle()
            val microphonePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> if (granted) researchViewModel.onMicClick() }
            val inferenceState by container.inference.state.collectAsStateWithLifecycle()
            val showBenchmark by benchmarkVisible
            LaunchedEffect(inferenceState) {
                (inferenceState as? InferenceState.Failed)?.let { failure ->
                    container.errorBus.report("Model", failure.message)
                }
            }
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
                        onBack = { benchmarkVisible.value = false },
                    )
                }
                return@setContent
            }

            FieldAtlasApp(
                packs = setupState.packs,
                importing = setupState.importing,
                setupError = setupState.error,
                researchState = researchState,
                historyRecords = historyRecords,
                inferenceState = inferenceState,
                proof = proof,
                navigation = appNavigation,
                onImportPack = { packPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                onQuestionChange = researchViewModel::updateQuestion,
                onSubmit = researchViewModel::submit,
                voiceState = voiceState,
                onMicClick = {
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        researchViewModel.onMicClick()
                    } else {
                        microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                diagnosticsText = diagnosticsNotices.joinToString("\n") { "[${it.area}] ${it.message}" },
                onClearDiagnostics = container.errorBus::clear,
                onAskAnotherQuestion = { researchViewModel.startNewQuestion() },
                onStop = researchViewModel::cancelResearch,
                onLoadModel = {
                    scope.launch {
                        runCatching { container.loadModel() }
                            .onFailure { container.errorBus.report("Model", it) }
                    }
                },
                onUnloadModel = {
                    scope.launch {
                        runCatching { container.unloadModel() }
                            .onFailure { container.errorBus.report("Model", it) }
                    }
                },
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
                    benchmarkVisible.value = true
                },
                onToggleResearch = { asset, enabled ->
                    scope.launch {
                        runCatching { container.setPackEnabled(asset, enabled) }
                            .onFailure { container.errorBus.report("Library", it) }
                    }
                },
                onActivateModel = { asset ->
                    scope.launch {
                        runCatching { container.setActiveModel(asset) }
                            .onFailure { container.errorBus.report("Model", it) }
                    }
                },
                onDeletePack = { asset ->
                    scope.launch {
                        runCatching { container.deletePack(asset) }
                            .onFailure { container.errorBus.report("Library", it) }
                    }
                },
            )
        }
    }

    override fun onStop() {
        // Research deliberately KEEPS RUNNING when the app goes to the background: a foreground
        // service (started while a run is active) keeps the process alive, the model stays
        // loaded, and the answer completes while the user does something else.
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
