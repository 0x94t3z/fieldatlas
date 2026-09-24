package xyz.fieldatlas.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.fieldatlas.assets.AssetImporter
import xyz.fieldatlas.assets.AssetRegistry
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.benchmark.BenchmarkQuestionSet
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.inference.LlamaInferenceGateway
import xyz.fieldatlas.diagnostics.AndroidDiagnosticsCollector
import xyz.fieldatlas.diagnostics.DiagnosticsSnapshot
import xyz.fieldatlas.diagnostics.DiagnosticsProvider
import xyz.fieldatlas.diagnostics.InstalledAssetSummary
import xyz.fieldatlas.proof.ProofMapper
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.research.FtsRetriever
import xyz.fieldatlas.research.KnowledgeDatabase
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.research.ResearchCompletion
import xyz.fieldatlas.ui.research.ResearchViewModel
import xyz.fieldatlas.ui.proof.BenchmarkViewModel
import xyz.fieldatlas.ui.setup.SetupViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val registry = AssetRegistry(appContext.filesDir)
    private val importer = AssetImporter(appContext)
    private val mutablePacks = MutableStateFlow<List<InstalledAsset>>(emptyList())
    private val benchmarkQuestions = appContext.assets.open("benchmark/questions-v1.json")
        .bufferedReader(Charsets.UTF_8).use { reader ->
            Json.decodeFromString<BenchmarkQuestionSet>(reader.readText()).also {
                require(it.schemaVersion == 1) { "Unsupported bundled benchmark schema" }
                require(it.questions.map { question -> question.id }.distinct().size == it.questions.size) {
                    "Bundled benchmark contains duplicate question ids"
                }
            }.questions
        }
    val packs: StateFlow<List<InstalledAsset>> = mutablePacks.asStateFlow()
    val inference: InferenceGateway = LlamaInferenceGateway(appContext)

    suspend fun refreshPacks() {
        mutablePacks.value = registry.list()
    }

    suspend fun installBundledKnowledgeIfNeeded() {
        val installed = registry.list()
        if (installed.any { it.id == BUNDLED_KNOWLEDGE_ID && it.version == BUNDLED_KNOWLEDGE_VERSION }) return
        installed.filter { it.id == LEGACY_STARTER_ID }.forEach { legacy ->
            registry.remove(legacy.id, legacy.version)
        }
        appContext.assets.open(BUNDLED_KNOWLEDGE_ASSET).use { bundled ->
            importer.import(bundled, appContext.filesDir.usableSpace)
        }
        refreshPacks()
    }

    suspend fun importPack(uri: Uri): InstalledAsset = importer.import(uri).also { refreshPacks() }

    suspend fun loadModel() {
        if (inference.state.value is InferenceState.Failed) inference.unload()
        check(inference.state.value == InferenceState.Idle) { "Model is already loaded or loading" }
        val modelPack = packs.value.firstOrNull { it.type == PackType.MODEL }
            ?: error("Install a verified model pack first")
        val modelFile = java.io.File(modelPack.rootPath, "model.gguf")
        check(modelFile.isFile) { "Verified model file is missing" }
        inference.load(modelFile.absolutePath, SYSTEM_PROMPT)
    }

    suspend fun unloadModel() {
        inference.unload()
    }

    fun hasResearchAssets(assets: List<InstalledAsset> = packs.value): Boolean =
        assets.any { it.type == PackType.MODEL } && assets.any { it.type == PackType.KNOWLEDGE }

    fun proof(metrics: ResearchMetrics?, completion: ResearchCompletion?): ProofModel =
        ProofMapper.map(diagnosticsSnapshot(metrics, completion, null, false))

    fun diagnosticsSnapshot(
        metrics: ResearchMetrics?,
        completion: ResearchCompletion?,
        question: String?,
        includeQuestion: Boolean,
    ): DiagnosticsSnapshot = AndroidDiagnosticsCollector.collect(
        context = appContext,
        assets = packs.value,
        latestResearchMetrics = metrics,
        latestResearchCompletion = completion,
        latestQuestion = question,
        includeQuestion = includeQuestion,
    )

    private val retriever = Retriever { query, limit ->
        val knowledge = packs.value.firstOrNull { it.type == PackType.KNOWLEDGE }
            ?: return@Retriever emptyList()
        val databaseFile = java.io.File(knowledge.rootPath, "content.sqlite")
        KnowledgeDatabase.open(databaseFile).use { database ->
            FtsRetriever(database).search(query, limit)
        }
    }
    private val researchOrchestrator = ResearchOrchestrator(retriever, inference)

    val researchViewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ResearchViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ResearchViewModel(
                savedStateHandle = extras.createSavedStateHandle(),
                orchestrator = researchOrchestrator,
            ) as T
        }
    }

    val benchmarkViewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BenchmarkViewModel::class.java))
            val snapshot = diagnosticsSnapshot(null, null, null, false)
            @Suppress("UNCHECKED_CAST")
            return BenchmarkViewModel(
                questions = benchmarkQuestions,
                orchestrator = researchOrchestrator,
                artifacts = packs.value.map {
                    InstalledAssetSummary(it.id, it.version, it.type, it.installedBytes, it.manifestSha256)
                },
                diagnosticsSha256 = sha256(DiagnosticsProvider.toJson(snapshot)),
            ) as T
        }
    }

    val setupViewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SetupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SetupViewModel(this@AppContainer) as T
        }
    }

    private companion object {
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        const val SYSTEM_PROMPT =
            "Use only the evidence supplied in each user prompt. Explain, compare, synthesize, " +
                "preserve conflicts and uncertainty, and cite factual claims with [S#]. Never use external knowledge."
        const val BUNDLED_KNOWLEDGE_ASSET = "fieldatlas-reference-1.0.0.fapack"
        const val BUNDLED_KNOWLEDGE_ID = "fieldatlas-reference"
        const val BUNDLED_KNOWLEDGE_VERSION = "1.0.0"
        const val LEGACY_STARTER_ID = "fieldatlas-starter"
    }
}
