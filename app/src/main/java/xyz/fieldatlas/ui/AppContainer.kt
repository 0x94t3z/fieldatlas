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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.fieldatlas.assets.AssetImporter
import xyz.fieldatlas.assets.AssetRegistry
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.assets.ModelDownload
import xyz.fieldatlas.assets.ModelOffer
import xyz.fieldatlas.assets.ModelOffers
import xyz.fieldatlas.assets.KnowledgeCatalog
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.KnowledgePackDownload
import xyz.fieldatlas.assets.BackgroundAssetDownloads
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
    private val importer = AssetImporter(appContext, registry)
    val attachmentStore = xyz.fieldatlas.attachments.AttachmentStore(File(appContext.cacheDir, "research-attachments"))
    init {
        // Camera captures are deleted once staged; anything left is from a run that ended early.
        File(appContext.cacheDir, "research-camera").listFiles()?.forEach { it.delete() }
        File(appContext.cacheDir, "pdf-scratch").listFiles()?.forEach { it.delete() }
    }
    private val attachmentReader = xyz.fieldatlas.attachments.AndroidAttachmentReader(appContext)
    suspend fun stageAttachment(uri: Uri, name: String): xyz.fieldatlas.attachments.AttachmentInput {
        var staged: xyz.fieldatlas.attachments.AttachmentInput? = null
        try {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    attachmentStore.stage(name, stream) { if (job?.isActive == false) throw kotlinx.coroutines.CancellationException() }
                        .also { staged = it }
                } ?: throw xyz.fieldatlas.attachments.AttachmentException("This file is not available offline. Save it on your device and try again.")
            }
        } catch (error: Throwable) { staged?.let { attachmentStore.remove(it.localFile) }; throw error }
    }
    private val storageFootprint = xyz.fieldatlas.assets.StorageFootprint.forApp(appContext)
    private val provisioningMutex = Mutex()
    private val modelDownload = ModelDownload(appContext.filesDir, registry, storageFootprint)
    private val sourcePreferences = appContext.getSharedPreferences("download-sources", Context.MODE_PRIVATE)
    val downloadSources = xyz.fieldatlas.assets.DownloadSources(
        sourcePreferences.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap(),
        { sources -> sourcePreferences.edit().clear().apply {
            sources.forEach { (key, value) -> putString(key, value) }
        }.commit() },
    )
    /** Total memory as Android reports it: a little under the size on the box. */
    val deviceMemoryBytes: Long by lazy {
        android.app.ActivityManager.MemoryInfo().also {
            appContext.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)
        }.totalMem
    }
    /** Models this phone has the memory for, whether installed or not. */
    val modelOffers: List<ModelOffer> by lazy { ModelOffers.all.filter { it.fits(deviceMemoryBytes) } }
    /** False for a pinned model this phone has too little memory to run. */
    fun modelFits(asset: InstalledAsset): Boolean = ModelOffers.byId(asset.id)?.fits(deviceMemoryBytes) ?: true
    private val modelPreferences = appContext.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val modelScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var settleJob: kotlinx.coroutines.Job? = null
    private val mutableModelNotice = MutableStateFlow<String?>(null)
    /** Why the model in use is not the one that was selected, until another model is chosen. */
    val modelNotice: StateFlow<String?> = mutableModelNotice.asStateFlow()
    private val catalogRepository = xyz.fieldatlas.assets.KnowledgeCatalogRepository(
        appContext.assets.open("knowledge/catalog.json").bufferedReader().use { KnowledgeCatalog.parse(it.readText()) },
        File(appContext.filesDir, "knowledge-catalog.json"),
        xyz.fieldatlas.assets.KnowledgeCatalogFetch()::fetch,
    )
    val catalogState = catalogRepository.state
    suspend fun refreshKnowledgeCatalog() = catalogRepository.refresh()
    private val knowledgePackDownload = KnowledgePackDownload(appContext.filesDir, registry, importer, storageFootprint)
    val backgroundDownloads: BackgroundAssetDownloads by lazy { BackgroundAssetDownloads(appContext, this) }
    private val mutablePacks = MutableStateFlow<List<InstalledAsset>>(emptyList())
    private val refreshMutex = Mutex()
    private val encoderMutex = Mutex()
    private var loadedEncoderPath: String? = null
    private var encoderSynchronized = false
    /** Every user-visible failure in the app is collected here and shown in one field. */
    val errorBus = AppErrorBus()

    /** Every completed (and cancelled-with-text) answer, shown by the History tab. */
    val answerHistory = xyz.fieldatlas.research.AnswerHistoryStore(
        File(appContext.filesDir, "answers.json"),
    )
    private val benchmarkQuestions = appContext.assets.open("benchmark/questions.json")
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

    suspend fun refreshPacks() = refreshMutex.withLock {
        val installed = registry.list()
        val nextEncoder = encoderPath(installed)
        // The encoder is lazy: the first relevant vector lookup loads it after the chat model
        // has prepared. Release an old encoder when its pack is switched off or replaced.
        if (encoderSynchronized && loadedEncoderPath != null && loadedEncoderPath != nextEncoder) {
            encoderMutex.withLock {
                runCatching { inference.setEncoder(null) }
                    .onSuccess {
                        loadedEncoderPath = null
                        encoderSynchronized = true
                    }
            }
        }
        mutablePacks.value = installed
    }

    private fun encoderPath(installed: List<InstalledAsset>): String? {
        val carrier = installed.firstOrNull { asset ->
            asset.type == PackType.KNOWLEDGE && asset.enabled && asset.embedding != null &&
                xyz.fieldatlas.research.supportsVectorLayout(asset.embedding) &&
                File(File(asset.rootPath), asset.embedding.encoderPath).isFile
        }
        return carrier?.embedding?.let { File(File(carrier.rootPath), it.encoderPath).absolutePath }
    }

    /** Load the enabled pack's encoder only when a routed vector search actually requests it. */
    private suspend fun embedForSearch(expected: xyz.fieldatlas.assets.PackEmbedding, text: String): FloatArray? = encoderMutex.withLock {
        val installed = packs.value
        val encoder = encoderPath(installed) ?: return@withLock null
        val active = installed.firstOrNull { asset ->
            asset.embedding?.let { File(asset.rootPath, it.encoderPath).absolutePath == encoder } == true
        }?.embedding
        xyz.fieldatlas.research.encodeWithCompatibleEncoder(expected, active) {
            if (!encoderSynchronized || encoder != loadedEncoderPath) {
                val loaded = runCatching { inference.setEncoder(encoder) }.isSuccess
                if (!loaded) return@encodeWithCompatibleEncoder null
                loadedEncoderPath = encoder
                encoderSynchronized = true
            }
            inference.embed(text)
        }
    }

    suspend fun retireBundledReferenceIfPresent() {
        // Remove only the exact app-bundled artifact. Never delete a user's imported pack
        // merely because it happens to reuse the same ID or version.
        registry.list().firstOrNull { asset ->
            asset.id == BUNDLED_REFERENCE_ID &&
                asset.version == BUNDLED_REFERENCE_VERSION &&
                asset.manifestSha256 == BUNDLED_REFERENCE_MANIFEST_SHA256
        }?.let { registry.remove(it.id, it.version) }
        refreshPacks()
    }

    suspend fun importFileName(uri: Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    suspend fun importPack(uri: Uri): InstalledAsset = provisioningMutex.withLock {
        // Extracting a large pack takes minutes; in the background the process could be killed half-way.
        xyz.fieldatlas.ui.research.KeepAliveService.hold(appContext, "import", "Installing a collection…")
        try {
            importer.import(uri).also { refreshPacks() }
        } finally {
            xyz.fieldatlas.ui.research.KeepAliveService.release(appContext, "import")
        }
    }
    suspend fun inspectImportPack(uri: Uri) = importer.inspect(uri)

    suspend fun downloadModel(offer: ModelOffer, downloadUrl: String = offer.url, onProgress: (Long) -> Unit): InstalledAsset =
        provisioningMutex.withLock { modelDownload.install(offer, downloadUrl, onProgress).also { refreshPacks() } }
            .also { installed ->
                val active = packs.value.firstOrNull { it.type == PackType.MODEL && it.active }?.id
                if (xyz.fieldatlas.assets.takesOver(offer, active, deviceMemoryBytes)) switchWhenIdle(installed)
            }

    // Never pulled from under an answer being written or a model being loaded: the swap waits for them to finish.
    private fun switchWhenIdle(installed: InstalledAsset) {
        modelScope.launch {
            inference.state.first { it != InferenceState.Generating && it != InferenceState.Loading }
            setActiveModel(installed)
        }
    }

    // Screens read saved sizes while drawing; reading this state makes them redraw after a discard.
    private val discardedParts = androidx.compose.runtime.mutableIntStateOf(0)
    fun resumableModelBytes(offer: ModelOffer = ModelOffers.recommended): Long =
        discardedParts.intValue.let { modelDownload.resumableBytes(offer) }
    fun resumableKnowledgeBytes(pack: KnowledgeCatalogEntry): Long =
        discardedParts.intValue.let { knowledgePackDownload.resumableBytes(pack) }

    /** Discards a paused download (a model or a collection): its saved part is deleted and it is not resumed later. */
    suspend fun discardPausedDownload(item: Any) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (backgroundDownloads.state.value.active || !provisioningMutex.tryLock()) return@withContext false
        try {
            when (item) {
                is ModelOffer -> { backgroundDownloads.forget(item.id, null); modelDownload.discardResumable(item) }
                is KnowledgeCatalogEntry -> { backgroundDownloads.forget(null, item.id); knowledgePackDownload.discardResumable(item) }
                else -> false
            }.also { if (it) discardedParts.intValue++ }
        } finally {
            provisioningMutex.unlock()
        }
    }

    /** Paused downloads of collection versions the catalog has replaced; called before any download starts. */
    fun discardStaleKnowledgeDownloads(): Int = knowledgePackDownload.discardStale(catalogRepository.state.value.packs)

    suspend fun downloadKnowledge(pack: KnowledgeCatalogEntry, downloadUrl: String = pack.url, onProgress: (Long) -> Unit): InstalledAsset = provisioningMutex.withLock {
        val selected = runCatching { catalogRepository.selectForDownload(pack) }.getOrElse { error ->
            // A paused download of a version the catalog replaced (vegan places 2026.10.02 after
            // 2026.10.05 arrived) can never finish; its partial file would hold the space forever.
            // If the entry only changed its wording or category, the same bytes resume on the next tap.
            knowledgePackDownload.discardPartial(pack, catalogRepository.state.value.packs)
            throw error
        }
        val installed = knowledgePackDownload.install(selected, downloadUrl, onProgress)
        // Keep older versions available for rollback, but don't search duplicate editions.
        registry.list().filter { it.type == PackType.KNOWLEDGE && it.id == pack.id &&
            it.version != pack.version && it.enabled
        }.forEach { registry.setEnabled(it.id, it.version, false) }
        refreshPacks()
        installed
    }

    /** One model change at a time, so a second request never reads a half-written load record. */
    private val modelMutex = Mutex()

    /**
     * Loads the selected model. It runs to the end even when the screen that asked is recreated
     * (a rotation cancels its scope): the native load cannot be stopped, and bookkeeping cut short
     * would leave the load record, the loaded model and its prompt speed wrong.
     */
    suspend fun loadModel() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        modelMutex.withLock { loadModelLocked() }
    }

    private suspend fun loadModelLocked() {
        if (inference.state.value is InferenceState.Failed) inference.unload()
        // A request that waited for another load finds the model ready.
        if (inference.state.value == InferenceState.Ready && loadedModel != null) return
        check(inference.state.value == InferenceState.Idle) { "Model is already loaded or loading" }
        val models = packs.value.filter { it.type == PackType.MODEL }
        val choice = xyz.fieldatlas.assets.chooseModel(models, deviceMemoryBytes,
            modelPreferences.getString(UNFINISHED_LOAD, null))
        val modelPack = choice.pack
        val modelFile = java.io.File(modelPack.rootPath, "model.gguf")
        check(modelFile.isFile) { "Verified model file is missing" }
        choice.notice?.let { notice ->
            mutableModelNotice.value = notice
            errorBus.report("Model", notice)
        }
        if (!modelPack.active) {
            registry.setActiveModel(modelPack.id, modelPack.version)
            refreshPacks()
        }
        // A model other than the recommended one can run the phone out of memory. Android killed
        // the app 0.2 s after the 4B finished loading on a 6 GB phone, so the record stays from
        // the start of the load until the phone has settled; if the app is killed in between,
        // the next load falls back instead of trying again.
        settleJob?.cancel()
        val guarded = modelPack.id != ModelOffers.recommended.id
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            modelPreferences.edit().apply {
                if (guarded) putString(UNFINISHED_LOAD, xyz.fieldatlas.assets.modelLoadKey(modelPack))
                else remove(UNFINISHED_LOAD)
            }.commit()
        }
        var loaded = false
        (inference as? xyz.fieldatlas.inference.LlamaInferenceGateway)?.repackWeights =
            ModelOffers.byId(modelPack.id)?.repacksOn(deviceMemoryBytes) ?: true
        try {
            inference.load(modelFile.absolutePath, SYSTEM_PROMPT)
            (inference as? xyz.fieldatlas.inference.LlamaInferenceGateway)?.let { gateway ->
                val offer = ModelOffers.byId(modelPack.id)
                gateway.thinkingBudget = offer?.thinkingBudget ?: 0
                gateway.knowledgeFirst = offer?.knowledgeFirst ?: false
            }
            loaded = true
        } finally {
            if (guarded && loaded) {
                settleJob = modelScope.launch {
                    kotlinx.coroutines.delay(MODEL_SETTLE_MILLIS)
                    modelPreferences.edit().remove(UNFINISHED_LOAD).commit()
                }
            } else if (guarded) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    modelPreferences.edit().remove(UNFINISHED_LOAD).commit()
                }
            }
        }
        loadedModel = modelPack.id to modelPack.version
    }

    /** Directory of the active dictation model (audio pack), or null to use the built-in one. */
    private fun activeSpeechModelDir(): String? {
        val speech = packs.value.filter { it.type == PackType.AUDIO }
        val directory = (speech.firstOrNull { it.active } ?: speech.firstOrNull())
            ?.let { java.io.File(it.rootPath, "audio-model") }
        return directory?.takeIf { it.isDirectory }?.absolutePath
    }

    suspend fun unloadModel() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        modelMutex.withLock { unloadModelLocked() }
    }

    private suspend fun unloadModelLocked() {
        inference.unload()
        loadedModel = null
        // A released model can no longer run the phone out of memory.
        if (settleJob?.isActive == true) {
            settleJob?.cancel()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                modelPreferences.edit().remove(UNFINISHED_LOAD).commit()
            }
        }
    }

    /** Which model pack the running native engine was loaded from, if any. */
    @Volatile private var loadedModel: Pair<String, String>? = null

    /**
     * Selects the model pack used by future loads. If a model is currently loaded, the swap is
     * applied immediately: the running model is released and the selected pack is loaded, so the
     * research screen never runs a different model than the one the library marks as selected.
     */
    suspend fun setActiveModel(asset: InstalledAsset) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        modelMutex.withLock { setActiveModelLocked(asset) }
    }

    private suspend fun setActiveModelLocked(asset: InstalledAsset) {
        require(asset.type != PackType.KNOWLEDGE) { "Only answer- or speech-model packs can be selected" }
        require(packs.value.any { it.id == asset.id && it.version == asset.version }) {
            "Model pack is not installed"
        }
        if (asset.type == PackType.MODEL) mutableModelNotice.value = null
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
        if (reload) unloadModelLocked()
        registry.setActiveModel(asset.id, asset.version)
        refreshPacks()
        if (reload) loadModelLocked()
    }

    fun proof(metrics: ResearchMetrics?, completion: ResearchCompletion?): ProofModel =
        ProofMapper.map(diagnosticsSnapshot(metrics, completion, null, false)).also { lastProof = it }

    /** The last proof read off the main thread: a recreated screen (a rotation with the model mapped) starts from it. */
    @Volatile var lastProof: ProofModel? = null
        private set

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

    private val retriever: MultiKnowledgeRetriever = MultiKnowledgeRetriever(
        databaseFiles = { knowledgeSources().map { source -> source.first } },
        packEmbeddings = { knowledgeSources().map { source -> source.second } },
        packDiscoveries = { knowledgeSources().map { source -> source.third } },
        embedForPack = { expected, text -> embedForSearch(expected, text) },
    )

    /** Enabled knowledge packs and their local retrieval metadata in registry order. */
    private fun knowledgeSources(): List<Triple<java.io.File, xyz.fieldatlas.assets.PackEmbedding?, xyz.fieldatlas.assets.PackDiscovery?>> =
        packs.value.let { installed ->
        val activeEncoder = encoderPath(installed)
        val activeEmbedding = installed.firstOrNull { asset ->
            asset.embedding?.let { File(asset.rootPath, it.encoderPath).absolutePath == activeEncoder } == true
        }?.embedding
        installed
            .filter { it.type == PackType.KNOWLEDGE && it.enabled }
            .map { knowledge ->
                Triple(
                    java.io.File(knowledge.rootPath, "content.sqlite"),
                    knowledge.embedding?.takeIf { candidate ->
                        activeEmbedding != null && xyz.fieldatlas.research.canShareQueryEncoder(candidate, activeEmbedding)
                    },
                    knowledge.discovery,
                )
            }
        }

    suspend fun setPackEnabled(asset: InstalledAsset, enabled: Boolean) {
        registry.setEnabled(asset.id, asset.version, enabled)
        refreshPacks()
    }

    /** Permanently removes an installed pack (and its files) from the phone. */
    suspend fun deletePack(asset: InstalledAsset) {
        if (asset.type == PackType.MODEL && loadedModel == asset.id to asset.version) {
            runCatching { unloadModel() }
        }
        // The note explains why a model is not in use; it is stale once a model is removed.
        if (asset.type == PackType.MODEL) mutableModelNotice.value = null
        // An open database keeps its deleted file's space until it is closed; a 24 GB pack would stay
        // counted against the phone. The delete itself can take seconds, so it runs off the main thread.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            retriever.release(File(asset.rootPath))
            registry.remove(asset.id, asset.version)
        }
        refreshPacks()
    }

    /**
     * A process killed while installing leaves its staging folder, or an extracted pack the registry
     * never recorded. Nothing else removes them, and they count against the storage budget, which can
     * block the Encyclopedia for good. Runs only when no install holds the lock. A model left behind is
     * kept: its download checks it and uses it instead of fetching gigabytes again.
     */
    suspend fun recoverInterruptedInstalls() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!provisioningMutex.tryLock()) return@withContext
        try {
            File(appContext.filesDir, "pack-staging").listFiles()?.forEach { it.deleteRecursively() }
            val registered = registry.list().map { File(it.rootPath).canonicalPath }.toSet()
            xyz.fieldatlas.assets.interruptedInstalls(File(appContext.filesDir, "packs"), registered).forEach { version ->
                version.deleteRecursively()
                version.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
            }
        } finally {
            provisioningMutex.unlock()
        }
    }
    val deviceLocation = xyz.fieldatlas.location.DeviceLocation(context.applicationContext)
    /** First-aid and safety guides bundled in the APK; read on first use. */
    private val emergencyGuides: List<xyz.fieldatlas.emergency.EmergencyGuide> by lazy {
        appContext.assets.open("emergency/guides.json").bufferedReader().use {
            xyz.fieldatlas.emergency.EmergencyData.guides(it.readText()).guides
        }
    }
    /** Reads guides and answers aloud: the guides' recordings, or the phone's voice. */
    val readAloud: xyz.fieldatlas.speech.ReadAloud by lazy { xyz.fieldatlas.speech.ReadAloud(appContext) { emergencyGuides } }

    /** An activity of the app is on screen; a finished download then needs no notification. */
    @Volatile var appVisible: Boolean = false

    /** Every download Library offers (models, collections, the voice), one at a time and the rest in line. */
    val downloads: xyz.fieldatlas.assets.AppDownloads by lazy {
        xyz.fieldatlas.assets.AppDownloads(appContext, backgroundDownloads, readAloud.natural,
            isInstalled = { item ->
                when (item) {
                    is xyz.fieldatlas.assets.DownloadItem.Model -> packs.value.any { it.id == item.offer.id && it.version == item.offer.version }
                    is xyz.fieldatlas.assets.DownloadItem.Knowledge -> packs.value.any { it.id == item.pack.id && it.version == item.pack.version }
                    xyz.fieldatlas.assets.DownloadItem.Voice -> readAloud.natural.ready
                }
            },
            onFinished = { item -> if (!appVisible) xyz.fieldatlas.assets.AssetDownloadService.notifyReady(appContext, item) },
            voiceSource = { downloadSources.urlFor(xyz.fieldatlas.assets.DownloadArtifact.voice()) })
    }
    /** The licence of a bundled emergency source ("emergency:<guide>" or "emergency:numbers-<country>"). */
    fun emergencyLicense(documentId: String): String? {
        val id = documentId.removePrefix("emergency:")
        return if (id.startsWith("numbers-")) emergencyNumbers?.source?.license
            else emergencyGuides.firstOrNull { it.id == id }?.source?.license
    }
    /** Emergency telephone numbers by country, bundled in the APK; read on first use. */
    /** Reads the bundled guides and numbers off the main thread, before the first question needs them. */
    suspend fun preloadBundledData() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        emergencyGuides.size
        emergencyNumbers
        Unit
    }

    private val emergencyNumbers: xyz.fieldatlas.emergency.NumberBook? by lazy {
        runCatching {
            appContext.assets.open("emergency/numbers.json").bufferedReader().use { xyz.fieldatlas.emergency.EmergencyNumbers.book(it.readText()) }
        }.getOrNull()
    }
    /**
     * Remembered across launches: the first file answer after an update should not start blind.
     * Kept per model, since a larger model reads a prompt several times slower.
     */
    private val prefillSpeed = object : xyz.fieldatlas.research.PrefillSpeed {
        private val prefs = appContext.getSharedPreferences("research", Context.MODE_PRIVATE)
        private fun key() = xyz.fieldatlas.research.PrefillSpeed.storageKey(loadedModel?.first)
        override var tokensPerSecond: Double?
            get() = prefs.getFloat(key(), -1f).takeIf { it > 0f }?.toDouble()
            set(value) {
                val key = key()
                prefs.edit().apply { if (value == null) remove(key) else putFloat(key, value.toFloat()) }.apply()
            }
    }
    // Article depth read more of a subject's article: graded blind it raised the fork's lookup
    // questions from 45% to 55% of the reference and our own from 57% to 59%, for about 20 s more
    // reading on a slow phone when the English collection is installed.
    // Whether answers come from the model's own knowledge is decided per model when it loads (ModelOffer.knowledgeFirst).
    private val researchOrchestrator = ResearchOrchestrator(retriever, inference, location = deviceLocation::current,
        emergencyGuides = { emergencyGuides }, emergencyNumbers = { emergencyNumbers }, prefillSpeed = prefillSpeed,
        articleDepth = true)

    /** Questions the app answers itself, so they can run before (or without) the model; decided where they are answered. */
    fun answersWithoutModel(question: String, hasAttachments: Boolean): Boolean =
        researchOrchestrator.answersWithoutModel(question, hasAttachments)

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
                attachmentReader = attachmentReader,
                cleanupAttachment = attachmentStore::remove,
                stagedAttachment = attachmentStore::file,
                clearAbandonedAttachments = attachmentStore::clearAbandoned,
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
                    InstalledAssetSummary(
                        it.id,
                        it.version,
                        it.type,
                        it.installedBytes,
                        it.manifestSha256,
                        enabled = it.enabled,
                        active = it.active,
                    )
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
            "You are an offline assistant on this phone. Follow the current research prompt: " +
                "use supplied sources when present, otherwise answer from model knowledge. " +
                "Never invent citations, claim internet access, or present changing facts as verified current facts."
        const val UNFINISHED_LOAD = "unfinished_load"
        const val MODEL_SETTLE_MILLIS = 15_000L
        const val BUNDLED_REFERENCE_ID = "fieldatlas-reference"
        const val BUNDLED_REFERENCE_VERSION = "1.0.0"
        const val BUNDLED_REFERENCE_MANIFEST_SHA256 =
            "ce49309d7d1ad2d89c05fb313b132df6315ce6a3c3d6f34cd5df21e42dd230fa"
    }
}
