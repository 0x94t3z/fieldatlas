package xyz.fieldatlas

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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

    override fun onResume() {
        super.onResume()
        container.backgroundDownloads.resumeInterrupted()
    }

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
            ) { granted ->
                if (granted) researchViewModel.onMicClick()
                else researchViewModel.onMicrophonePermissionDenied()
            }
            // Asked only when a "near me" question is submitted. The question runs either way;
            // without permission it answers with how to enable location or name a city.
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { researchViewModel.submit() }
            var explainLocation by rememberSaveable { mutableStateOf(false) }
            val inferenceState by container.inference.state.collectAsStateWithLifecycle()
            val showBenchmark by benchmarkVisible
            LaunchedEffect(inferenceState) {
                (inferenceState as? InferenceState.Failed)?.let { failure ->
                    container.errorBus.report("Model", failure.message)
                }
            }
            val appNavigation = rememberFieldAtlasNavigationState()
            val scope = rememberCoroutineScope()
            var attachmentNotice by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
            val attachmentContainer = container
            fun attach(uri: Uri, name: String) {
                try {
                    researchViewModel.addAttachment(name) { attachmentContainer.stageAttachment(uri, name) }
                    attachmentNotice = null
                } catch (error: Exception) { attachmentNotice = xyz.fieldatlas.attachments.attachmentError(error) }
            }
            val exportStore = remember { ExportStagingStore(File(filesDir, "pending-exports")) }
            val proof = remember(setupState.packs, inferenceState, researchState.metrics, researchState.completion) {
                container.proof(researchState.metrics, researchState.completion)
            }
            val packPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) setupViewModel.importPack(uri) }
            val researchFilePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    attach(uri, attachmentName(uri))
                }
            }
            val researchPhotoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri ->
                if (uri != null) attach(uri, attachmentName(uri))
            }
            val researchCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { saved ->
                val file = pendingCameraPath?.let(::File)
                pendingCameraPath = null
                if (file != null && file.parentFile?.canonicalFile == File(cacheDir, "research-camera").canonicalFile) {
                    if (saved) {
                        try {
                            researchViewModel.addAttachment("Camera photo.jpg") {
                                try { attachmentContainer.stageAttachment(Uri.fromFile(file), "Camera photo.jpg") }
                                finally { file.delete() }
                            }
                            attachmentNotice = null
                        } catch (error: Exception) { file.delete(); attachmentNotice = xyz.fieldatlas.attachments.attachmentError(error) }
                    } else file.delete()
                }
            }
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
                        onPrepareModel = {
                            lifecycleScope.launch {
                                runCatching { container.loadModel() }
                                    .onFailure { reportUnlessCancelled("Model", it) }
                            }
                        },
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

            val catalogState by container.catalogState.collectAsStateWithLifecycle()
            FieldAtlasApp(
                packs = setupState.packs,
                importing = setupState.importing,
                importingName = setupState.importingName,
                importingPack = setupState.importingPack,
                downloading = setupState.downloading,
                downloadedBytes = setupState.downloadedBytes,
                offerKnowledge = setupState.offerKnowledge,
                availableKnowledge = xyz.fieldatlas.assets.catalogWithActiveDownload(
                    catalogState.packs, setupState.activeKnowledgeDownload),
                catalogRefreshing = catalogState.refreshing,
                catalogError = catalogState.error,
                onRefreshCatalog = { scope.launch { container.refreshKnowledgeCatalog() } },
                knowledgeDownloadKey = setupState.knowledgeDownloadKey,
                knowledgeDownloadedBytes = setupState.knowledgeDownloadedBytes,
                knowledgeDownloadError = setupState.knowledgeError,
                setupError = setupState.error,
                researchState = researchState,
                historyRecords = historyRecords,
                inferenceState = inferenceState,
                proof = proof,
                navigation = appNavigation,
                onImportPack = { packPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                onDownloadModel = setupViewModel::downloadRecommendedModel,
                resumableModelBytes = container.resumableModelBytes(),
                resumableKnowledgeBytes = container::resumableKnowledgeBytes,
                onCancelDownload = setupViewModel::cancelDownload,
                onDismissKnowledgeOffer = setupViewModel::dismissKnowledgeOffer,
                onDownloadKnowledge = setupViewModel::downloadKnowledge,
                onCancelKnowledgeDownload = setupViewModel::cancelKnowledgeDownload,
                onQuestionChange = researchViewModel::updateQuestion,
                onSubmit = {
                    if (xyz.fieldatlas.research.VenueLookup.isNearMe(researchState.question) &&
                        !container.deviceLocation.hasPermission()
                    ) {
                        explainLocation = true
                    } else {
                        researchViewModel.submit()
                    }
                },
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
                onCameraClick = {
                    try {
                        xyz.fieldatlas.attachments.AttachmentPolicy.checkCount(researchState.attachments.size)
                        val folder = File(cacheDir, "research-camera").apply { mkdirs() }
                        val file = File.createTempFile("capture-", ".jpg", folder)
                        pendingCameraPath = file.absolutePath
                        researchCamera.launch(androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.research-files", file))
                    } catch (_: Exception) {
                        pendingCameraPath?.let { File(it).delete() }
                        pendingCameraPath = null
                        attachmentNotice = "Could not open the camera. Check the attachment limit, or choose a photo instead."
                    }
                },
                onPhotosClick = { researchPhotoPicker.launch("image/*") },
                onFilesClick = {
                    // Providers often label code files as application/octet-stream. Validate bytes after selection.
                    researchFilePicker.launch(arrayOf("*/*"))
                },
                attachmentNotice = attachmentNotice,
                onRemoveAttachment = researchViewModel::removeAttachment,
                onRetryAttachment = researchViewModel::retryAttachment,
                diagnosticsText = diagnosticsNotices.joinToString("\n") { "[${it.area}] ${it.message}" },
                onClearDiagnostics = container.errorBus::clear,
                onAskAnotherQuestion = { researchViewModel.startNewQuestion() },
                onStop = researchViewModel::cancelResearch,
                onLoadModel = {
                    lifecycleScope.launch {
                        runCatching { container.loadModel() }
                            .onFailure { reportUnlessCancelled("Model", it) }
                    }
                },
                onUnloadModel = {
                    lifecycleScope.launch {
                        runCatching { container.unloadModel() }
                            .onFailure { reportUnlessCancelled("Model", it) }
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
                    lifecycleScope.launch {
                        runCatching { container.setPackEnabled(asset, enabled) }
                            .onFailure { reportUnlessCancelled("Library", it) }
                    }
                },
                onActivateModel = { asset ->
                    lifecycleScope.launch {
                        runCatching { container.setActiveModel(asset) }
                            .onFailure { reportUnlessCancelled("Model", it) }
                    }
                },
                onDeletePack = { asset ->
                    lifecycleScope.launch {
                        runCatching { container.deletePack(asset) }
                            .onFailure { reportUnlessCancelled("Library", it) }
                    }
                },
            )
            // FieldAtlasApp applies the theme inside itself; the sheet sits outside it.
            if (explainLocation) FieldAtlasTheme {
                xyz.fieldatlas.ui.research.LocationRationaleSheet(
                    onAllow = {
                        explainLocation = false
                        locationPermissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ))
                    },
                    // The question stays in the box so a city can be added to it.
                    onNameCity = { explainLocation = false },
                )
            }
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

    private fun reportUnlessCancelled(area: String, failure: Throwable) {
        if (failure !is kotlinx.coroutines.CancellationException) {
            container.errorBus.report(area, failure)
        }
    }

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

    private fun attachmentName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"

    private fun isResearchFileTypeSupported(name: String, mimeType: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return xyz.fieldatlas.attachments.TextFileTypes.supports(name) || mimeType.startsWith("text/") || mimeType.startsWith("image/") ||
            mimeType in setOf("application/pdf", "application/json", "application/xml") ||
            extension in setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "pdf", "png", "jpg", "jpeg", "webp", "gif")
    }

    private companion object {
        const val BENCHMARK_EXPORT = "benchmark"
        const val DIAGNOSTICS_EXPORT = "diagnostics"
    }
}
