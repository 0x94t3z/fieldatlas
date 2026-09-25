package xyz.fieldatlas.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import java.io.File
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
import xyz.fieldatlas.speech.VoskSpeechTranscriber
import xyz.fieldatlas.diagnostics.AndroidDiagnosticsCollector
import xyz.fieldatlas.diagnostics.DiagnosticsSnapshot
import xyz.fieldatlas.diagnostics.DiagnosticsProvider
import xyz.fieldatlas.diagnostics.InstalledAssetSummary
import xyz.fieldatlas.proof.ProofMapper
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.research.MultiKnowledgeRetriever
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
    /** Every user-visible failure in the app is collected here and shown in one field. */
    val errorBus = AppErrorBus()

    /** Every completed (and cancelled-with-text) answer, shown by the History tab. */
    val answerHistory = xyz.fieldatlas.research.AnswerHistoryStore(
        File(appContext.filesDir, "answers.json"),
    )
    private val benchmarkQuestions = appContext.assets.open("benchmark/questions-v2.json")
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
        syncEncoder()
    }

    /**
     * Loads the embedding encoder shipped inside the first enabled vector-capable knowledge
     * pack (all current vector packs share one bge-small encoder, so first-enabled is a stable
     * pick). No-op safe: without an encoder the retriever simply stays keyword-only.
     */
    private suspend fun syncEncoder() {
        val carrier = packs.value.firstOrNull { asset ->
            asset.type == PackType.KNOWLEDGE && asset.enabled && asset.embedding != null &&
                File(File(asset.rootPath), asset.embedding.encoderPath).isFile
        }
        val encoder = carrier?.embedding?.let { File(File(carrier.rootPath), it.encoderPath).absolutePath }
        runCatching { inference.setEncoder(encoder) }
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
        val models = packs.value.filter { it.type == PackType.MODEL }
        val modelPack = models.firstOrNull { it.active } ?: models.firstOrNull()
            ?: error("Install a verified model pack first")
        val modelFile = java.io.File(modelPack.rootPath, "model.gguf")
        check(modelFile.isFile) { "Verified model file is missing" }
        inference.load(modelFile.absolutePath, SYSTEM_PROMPT)
        loadedModel = modelPack.id to modelPack.version
        if (models.none { it.active }) {
            registry.setActiveModel(modelPack.id, modelPack.version)
            refreshPacks()
        }
    }

    /** Directory of the active dictation model (audio pack), or null to use the built-in one. */
    private fun activeSpeechModelDir(): String? {
        val speech = packs.value.filter { it.type == PackType.AUDIO }
        val directory = (speech.firstOrNull { it.active } ?: speech.firstOrNull())
            ?.let { java.io.File(it.rootPath, "audio-model") }
        return directory?.takeIf { it.isDirectory }?.absolutePath
    }

    suspend fun unloadModel() {
        inference.unload()
        loadedModel = null
    }

    /** Which model pack the running native engine was loaded from, if any. */
    private var loadedModel: Pair<String, String>? = null

    /**
     * Selects the model pack used by future loads. If a model is currently loaded, the swap is
     * applied immediately: the running model is released and the selected pack is loaded, so the
     * research screen never runs a different model than the one the library marks as selected.
     */
    suspend fun setActiveModel(asset: InstalledAsset) {
        require(asset.type != PackType.KNOWLEDGE) { "Only answer- or speech-model packs can be selected" }
        require(packs.value.any { it.id == asset.id && it.version == asset.version }) {
            "Model pack is not installed"
        }
        if (asset.type != PackType.MODEL) {
            // Speech model swap: no inference reload involved.
            registry.setActiveModel(asset.id, asset.version)
            refreshPacks()
            return
        }
        if (loadedModel == asset.id to asset.version && inference.state.value == InferenceState.Ready) {
            registry.setActiveModel(asset.id, asset.version)
            refreshPacks()
            return
        }
        val reload = loadedModel != null
        if (reload) unloadModel()
        registry.setActiveModel(asset.id, asset.version)
        refreshPacks()
        if (reload) loadModel()
    }

    fun hasResearchAssets(assets: List<InstalledAsset> = packs.value): Boolean =
        assets.any { it.type == PackType.MODEL } &&
            assets.any { it.type == PackType.KNOWLEDGE && it.enabled }

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

    private val retriever: Retriever = MultiKnowledgeRetriever(
        databaseFiles = { knowledgeSources().map { source -> source.first } },
        packEmbeddings = { knowledgeSources().map { source -> source.second } },
        embed = { text -> inference.embed(text) },
    )

    /** Enabled knowledge packs: (database file, embedding metadata) in registry order. */
    private fun knowledgeSources(): List<Pair<java.io.File, xyz.fieldatlas.assets.PackEmbedding?>> =
        packs.value
            .filter { it.type == PackType.KNOWLEDGE && it.enabled }
            .map { knowledge -> java.io.File(knowledge.rootPath, "content.sqlite") to knowledge.embedding }

    suspend fun setPackEnabled(asset: InstalledAsset, enabled: Boolean) {
        registry.setEnabled(asset.id, asset.version, enabled)
        refreshPacks()
    }

    /** Permanently removes an installed pack (and its files) from the phone. */
    suspend fun deletePack(asset: InstalledAsset) {
        if (asset.type == PackType.MODEL && loadedModel == asset.id to asset.version) {
            runCatching { unloadModel() }
        }
        registry.remove(asset.id, asset.version)
        refreshPacks()
    }
    private val researchOrchestrator = ResearchOrchestrator(retriever, inference)

    val researchViewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ResearchViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ResearchViewModel(
                savedStateHandle = extras.createSavedStateHandle(),
                orchestrator = researchOrchestrator,
                createTranscriber = {
                    VoskSpeechTranscriber(appContext, activeSpeechModelDir())
                },
                onError = { message -> errorBus.report("Research", message) },
                historyStore = answerHistory,
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
                onError = { message -> errorBus.report("Benchmark", message) },
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
