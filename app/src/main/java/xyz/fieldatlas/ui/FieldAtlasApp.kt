package xyz.fieldatlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.indication
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.R
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.KnowledgeCatalogEntry
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.ui.history.HistoryScreen
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.more.MoreScreen
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.VoiceUiState
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.sources.SourcesScreen
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasTheme
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.FieldAtlasColors

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun FieldAtlasApp(
    packs: List<InstalledAsset>,
    /** False until the saved packs are read: a set-up phone must not flash the setup screen. */
    packsLoaded: Boolean = true,
    /** The licence of a bundled (non-pack) source, by document id. */
    sourceLicense: (String) -> String? = { null },
    importing: Boolean,
    importingName: String? = null,
    importingPack: xyz.fieldatlas.assets.PackImportInfo? = null,
    downloading: Boolean = false,
    downloadingModelId: String? = null,
    downloadedBytes: Long = 0,
    modelError: String? = null,
    modelErrorId: String? = null,
    offerKnowledge: Boolean = false,
    availableKnowledge: List<KnowledgeCatalogEntry> = emptyList(),
    catalogRefreshing: Boolean = false,
    catalogError: String? = null,
    onRefreshCatalog: () -> Unit = {},
    knowledgeDownloadKey: String? = null,
    knowledgeDownloadedBytes: Long = 0,
    knowledgeDownloadError: String? = null,
    setupError: String?,
    researchState: ResearchUiState,
    historyRecords: List<AnswerRecord> = emptyList(),
    historyStorage: xyz.fieldatlas.research.HistoryStorage = xyz.fieldatlas.research.HistoryStorage(),
    onDeleteHistory: (Long) -> Unit = {},
    proof: ProofModel,
    navigation: FieldAtlasNavigationState = rememberFieldAtlasNavigationState(),
    onImportPack: () -> Unit,
    onDownloadModel: () -> Unit = {},
    resumableModelBytes: Long = 0,
    /** Models this phone has the memory for, offered in Library when not installed. */
    modelOffers: List<xyz.fieldatlas.assets.ModelOffer> = emptyList(),
    downloadSources: Map<String, String> = emptyMap(),
    onSaveDownloadSource: (xyz.fieldatlas.assets.DownloadArtifact, String) -> String? = { _, _ -> null },
    onResetDownloadSource: (xyz.fieldatlas.assets.DownloadArtifact) -> String? = { null },
    onDownloadModelOffer: (xyz.fieldatlas.assets.ModelOffer) -> Unit = {},
    resumableOfferBytes: (xyz.fieldatlas.assets.ModelOffer) -> Long = { 0 },
    modelFits: (InstalledAsset) -> Boolean = { true },
    modelNotice: String? = null,
    resumableKnowledgeBytes: (KnowledgeCatalogEntry) -> Long = { 0 },
    onCancelDownload: () -> Unit = {},
    onDismissKnowledgeOffer: () -> Unit = {},
    onDownloadKnowledge: (KnowledgeCatalogEntry) -> Unit = {},
    onCancelKnowledgeDownload: () -> Unit = {},
    onDiscardPausedDownload: ((Any) -> Unit)? = null,
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    voiceState: VoiceUiState = VoiceUiState(),
    onMicClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onPhotosClick: () -> Unit = {},
    onFilesClick: () -> Unit = {},
    attachmentNotice: String? = null,
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    diagnosticsText: String = "",
    onClearDiagnostics: () -> Unit = {},
    onAskAnotherQuestion: () -> Unit = {},
    onFollowUp: (String) -> Unit = {},
    onEditReadAs: () -> Unit = {},
    followUpDraft: String = "",
    onFollowUpDraftChange: (String) -> Unit = {},
    /** Which box the next dictation or file goes to: true for the follow-up box under an answer. */
    onSelectVoiceTarget: (Boolean) -> Unit = {},
    onSelectAttachmentTarget: (Boolean) -> Unit = {},
    onLeaveFollowUp: () -> Unit = {},
    /** Picks a History topic up again; false while an answer is being written. */
    onContinueTopic: (AnswerRecord) -> Boolean = { false },
    /** Answers kept off the screen: still being researched after New topic, or finished while another was shown. */
    researchBackground: List<xyz.fieldatlas.ui.research.BackgroundAnswer> = emptyList(),
    onOpenBackground: (Long) -> Boolean = { false },
    onDismissBackground: (Long) -> Unit = {},
    onParkAnswer: () -> Unit = {},
    onStop: () -> Unit,
    inferenceState: InferenceState = InferenceState.Ready,
    onLoadModel: () -> Unit = {},
    onUnloadModel: () -> Unit = {},
    onExportDiagnostics: (Boolean) -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
    onToggleResearch: (InstalledAsset, Boolean) -> Unit = { _, _ -> },
    onActivateModel: (InstalledAsset) -> Unit = {},
    onDeletePack: (InstalledAsset) -> Unit = {},
    answersWithoutModel: (String, Boolean) -> Boolean = { question, hasAttachments ->
        xyz.fieldatlas.research.QuestionRequirements.response(question, hasAttachments) != null
    },
) {
    if (!packsLoaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val ready = packs.any { it.type == PackType.MODEL }
    val enabledKnowledge = packs.filter { it.type == PackType.KNOWLEDGE && it.enabled }
    val darkTheme = isSystemInDarkTheme()
    // Asking from History or a collection's examples starts a new question. Files attached to the current
    // one would be removed with it, so that is confirmed first; a running answer is kept in History.
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    fun startQuestion(question: String) {
        onAskAnotherQuestion()
        onQuestionChange(question)
        navigation.select(PrimaryDestination.Research)
    }
    fun askAnew(question: String) {
        if (researchState.attachments.isNotEmpty()) pendingQuestion = question else startQuestion(question)
    }
    pendingQuestion?.let { question ->
        xyz.fieldatlas.ui.theme.FieldAtlasDeleteDialog(
            title = "Start a new question?",
            message = "The files attached to your current question will be removed. Its answer, if any, stays in History.",
            onKeep = { pendingQuestion = null },
            onDelete = { pendingQuestion = null; startQuestion(question) },
            deleteLabel = "Start new question",
        )
    }
    // Loading state belongs to the live inference gateway. Do not restore a stale "started"
    // flag after process death, when the new gateway is Idle again.
    var autoPreparationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(ready, inferenceState) {
        when {
            !ready -> autoPreparationStarted = false
            shouldAutoPrepareModel(ready, inferenceState, autoPreparationStarted) -> {
                autoPreparationStarted = true
                onLoadModel()
            }
            inferenceState != InferenceState.Idle -> autoPreparationStarted = true
        }
    }
    // A finished answer takes the stage immediately; the research screen stays behind it so
    // "Ask another question" (or system back) returns to a fresh asking view.
    LaunchedEffect(researchState.phase, researchState.completion) {
        if (researchState.phase == ResearchPhase.Complete && researchState.answer.isNotBlank()) {
            navigation.openAnswer()
        }
    }
    // Start research opens the answer page at once, where the live status sits until the answer replaces it. Once per
    // run: Back returns to the Research home, which keeps showing the progress, and the page reopens when it is done.
    var answerOpenedForRun by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(researchState.startedAtNanos, researchState.isRunning) {
        if (opensAnswerOnStart(researchState.startedAtNanos, researchState.isRunning, answerOpenedForRun)) {
            answerOpenedForRun = researchState.startedAtNanos
            navigation.openAnswer()
        }
    }
    FieldAtlasTheme {
        var showDownloadSources by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        fun sourceFor(artifact: xyz.fieldatlas.assets.DownloadArtifact): String =
            downloadSources[artifact.key] ?: artifact.defaultUrl
        if (showDownloadSources) {
            xyz.fieldatlas.ui.setup.DownloadSourcesSheet(
                artifacts = (modelOffers.ifEmpty { listOf(xyz.fieldatlas.assets.ModelOffers.recommended) }
                    .map(xyz.fieldatlas.assets.DownloadArtifact::model) +
                    availableKnowledge.map(xyz.fieldatlas.assets.DownloadArtifact::knowledge) +
                    xyz.fieldatlas.assets.DownloadArtifact.voice()).distinctBy { it.key },
                sources = downloadSources, onSave = onSaveDownloadSource, onReset = onResetDownloadSource,
                onDismiss = { showDownloadSources = false },
            )
        }
        if (!ready || offerKnowledge) {
            // The models setup offers this phone, best first: the 4B where it fits, then the 2B.
            val setupModels = modelOffers.sortedByDescending { it.bytes }.ifEmpty { listOf(xyz.fieldatlas.assets.ModelOffers.recommended) }
            val setupIds = setupModels.map { it.id }
            val recommendedDownloading = (downloadingModelId ?: xyz.fieldatlas.assets.ModelOffers.recommended.id) in setupIds
            SetupScreen(
                packs = packs,
                importing = importing,
                importingName = importingName,
                importingPack = importingPack,
                downloading = downloading && recommendedDownloading,
                otherModelDownloading = downloading && !recommendedDownloading,
                downloadedBytes = downloadedBytes,
                error = setupError ?: modelError?.takeIf {
                    (modelErrorId ?: xyz.fieldatlas.assets.ModelOffers.recommended.id) in setupIds
                },
                onImportPack = onImportPack,
                onDownloadModel = onDownloadModel,
                resumableModelBytes = resumableModelBytes,
                resumableKnowledgeBytes = resumableKnowledgeBytes,
                onCancelDownload = onCancelDownload,
                availableKnowledge = availableKnowledge,
                catalogRefreshing = catalogRefreshing,
                catalogError = catalogError,
                onRefreshCatalog = onRefreshCatalog,
                knowledgeDownloadKey = knowledgeDownloadKey,
                knowledgeDownloadedBytes = knowledgeDownloadedBytes,
                knowledgeError = knowledgeDownloadError,
                onDownloadKnowledge = onDownloadKnowledge,
                onCancelKnowledgeDownload = onCancelKnowledgeDownload,
                onContinue = onDismissKnowledgeOffer,
                onOpenDownloadSources = { showDownloadSources = true },
                modelDownloadUrl = sourceFor(xyz.fieldatlas.assets.DownloadArtifact.model(xyz.fieldatlas.assets.ModelOffers.recommended)),
                knowledgeDownloadUrl = { sourceFor(xyz.fieldatlas.assets.DownloadArtifact.knowledge(it)) },
                modelDownloadError = modelError?.takeIf {
                    modelErrorId == null || modelErrorId == xyz.fieldatlas.assets.ModelOffers.recommended.id
                },
                modelChoices = setupModels,
                downloadingModelId = downloadingModelId,
                resumableOfferBytes = resumableOfferBytes,
                onDownloadModelOffer = onDownloadModelOffer,
                modelDownloadUrlFor = { sourceFor(xyz.fieldatlas.assets.DownloadArtifact.model(it)) },
                modelErrorFor = { offer -> modelError?.takeIf { (modelErrorId ?: xyz.fieldatlas.assets.ModelOffers.recommended.id) == offer.id } },
            )
            return@FieldAtlasTheme
        }

        val closeDetail = { navigation.back(); Unit }
        // History keeps its scroll, filter and search while a topic it opened is on screen, so Back lands where it was.
        val historyState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        // The Research home as it was when Start research was tapped. The home changes to its running view at once;
        // while it fades out, this frame keeps its question box on screen to travel down into the answer page's.
        var launchFrame by remember { mutableStateOf<ResearchUiState?>(null) }
        // When an answer finishes off the screen, a short note says so wherever the reader is, and opens its page.
        var readyNote by remember { mutableStateOf<xyz.fieldatlas.ui.research.BackgroundAnswer?>(null) }
        val runningAside = remember { mutableSetOf<Long>() }
        LaunchedEffect(researchBackground) {
            for (answer in researchBackground) {
                if (answer.running) runningAside += answer.id
                else if (runningAside.remove(answer.id) && answer.done) readyNote = answer
            }
            if (readyNote?.let { note -> researchBackground.none { it.id == note.id } } == true) readyNote = null
        }
        LaunchedEffect(readyNote) { if (readyNote != null) { kotlinx.coroutines.delay(READY_NOTE_MILLIS); readyNote = null } }
        Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(LocalSharedTransition provides this) {
        androidx.compose.animation.AnimatedContent(
            targetState = navigation.detail,
            contentKey = ::pageKey,
            transitionSpec = { pageTransition() },
            label = "Page",
        ) { detail ->
        androidx.compose.runtime.CompositionLocalProvider(LocalPageVisibility provides this) {
        val leaving = transition.targetState == androidx.compose.animation.EnterExitState.PostExit
        if (detail != null) {
            when (detail) {
                // Start research opens this page in the same tap, but the run's first state reaches the screen a frame
                // later: until then the page shows the question as starting, and goes back if it never starts.
                DetailDestination.Answer -> {
                // Leaving the page, it keeps showing what it showed: the screen behind it may already be starting afresh.
                val answerFrame = remember { arrayOfNulls<ResearchUiState>(1) }
                if (!leaving) answerFrame[0] = researchState
                val shownState = if (leaving) answerFrame[0] ?: researchState else researchState
                val pending = launchFrame
                val starting = pending != null && !researchState.isRunning && researchState.phase != ResearchPhase.Complete &&
                    researchState.startedAtNanos == pending.startedAtNanos
                if (starting) LaunchedEffect(pending) {
                    kotlinx.coroutines.delay(START_GRACE_MILLIS)
                    navigation.back()
                }
                // Nothing running and nothing finished (stopped, an error, Edit on a follow-up's reading, or a restart
                // that kept only the question): the Research home says what happened and holds the question.
                if (!starting && !leaving && !researchState.isRunning &&
                    (researchState.phase != ResearchPhase.Complete || researchState.answer.isBlank())) {
                    LaunchedEffect(Unit) { navigation.back() }
                } else AnswerScreen(
                    state = if (starting) ResearchUiState(question = pending!!.question, attachments = pending.attachments,
                        phase = ResearchPhase.Searching) else shownState,
                    // A finished answer stays one tap away on the Research home ("Answer ready"), which starts afresh.
                    onBack = {
                        if (researchState.phase == ResearchPhase.Complete && researchState.answer.isNotBlank()) onParkAnswer()
                        navigation.back()
                    },
                    onAskAnother = { onAskAnotherQuestion(); navigation.keepInResearch(); navigation.back() },
                    // The follow-up is researched on this page, under the answer it follows; from then on the page is
                    // Research's, so a stopped or edited follow-up is held on the Research home.
                    onFollowUp = { navigation.keepInResearch(); onFollowUp(it) },
                    typicalAnswerMillis = remember(historyRecords) { xyz.fieldatlas.ui.research.typicalAnswerMillis(historyRecords) },
                    onStop = onStop,
                    onEditReadAs = onEditReadAs,
                    onCitation = navigation::openSource,
                    travelCollectionAvailable = availableKnowledge.any { it.id == "wikivoyage-places" } &&
                        packs.none { it.id == "wikivoyage-places" },
                    onOpenLibrary = {
                        navigation.back()
                        navigation.select(PrimaryDestination.Library)
                    },
                    followUpDraft = followUpDraft,
                    onFollowUpDraftChange = onFollowUpDraftChange,
                    voiceState = voiceState,
                    onFollowUpMic = { onSelectVoiceTarget(true); onMicClick() },
                    onFollowUpCamera = { onSelectAttachmentTarget(true); onCameraClick() },
                    onFollowUpPhotos = { onSelectAttachmentTarget(true); onPhotosClick() },
                    onFollowUpFiles = { onSelectAttachmentTarget(true); onFilesClick() },
                    onRemoveAttachment = onRemoveAttachment,
                    onRetryAttachment = onRetryAttachment,
                    attachmentNotice = attachmentNotice,
                    onLeaveFollowUp = onLeaveFollowUp,
                )
                }
                is DetailDestination.Source -> {
                    val source = researchState.sources.getOrNull(detail.index)
                    if (source == null) {
                        BackHandler { navigation.back() }
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                FieldAtlasTopBar(title = "Source", onBack = closeDetail)
                                Box(Modifier.padding(horizontal = 20.dp)) {
                                    Text("This source is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        SourcesScreen(
                            evidence = source,
                            sourceNumber = detail.index + 1,
                            sourceCount = researchState.sources.size,
                            isCited = "S${detail.index + 1}" in xyz.fieldatlas.research.AnswerText.citationAudit(
                                researchState.answer, researchState.sources.size).citedSourceIds,
                            // The evidence does not carry its pack ID, and the packs switched on now need not be
                            // the ones that answered. The licence is read from where the text came from; anything
                            // else is omitted, which is safer than crediting the wrong licence. Bundled emergency
                            // guides and numbers carry their own.
                            packLicense = when {
                                source.documentId.startsWith("attachment:") -> null
                                source.documentId.startsWith("emergency:") -> sourceLicense(source.documentId)
                                else -> xyz.fieldatlas.ui.presentation.licenceOfSource(source.source)
                            },
                            onBack = closeDetail,
                            onPrevious = if (detail.index > 0) ({ navigation.openSource(detail.index - 1); Unit }) else null,
                            onNext = if (detail.index < researchState.sources.lastIndex) ({ navigation.openSource(detail.index + 1); Unit }) else null,
                            original = researchState.attachments.firstOrNull {
                                source.documentId == "attachment:${it.extracted?.id}"
                            }?.let { attachment -> attachment.originalFile?.let { file ->
                                attachment.kind?.let { kind -> xyz.fieldatlas.ui.sources.SourceOriginal(file, kind) }
                            } },
                        )
                    }
                }
            }
        } else {
        // The launch frame is spent once the tabs are back and still; never in the frame of the tap itself, when the
        // page change may not have reached this transition yet.
        val settled = transition.currentState == androidx.compose.animation.EnterExitState.Visible &&
            transition.targetState == androidx.compose.animation.EnterExitState.Visible
        LaunchedEffect(settled) { if (settled && navigation.detail == null) launchFrame = null }
        val downloads = xyz.fieldatlas.ui.library.LocalDownloads.current
        val downloadStatus = downloads?.state?.collectAsState()?.value
        var showDownloads by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        if (showDownloads && downloads != null) {
            // Older editions kept after an update, switched off: their space is what a failed download most often lacks.
            val knowledge = packs.filter { it.type == xyz.fieldatlas.assets.PackType.KNOWLEDGE }
            val oldEditions = knowledge.filter { old -> !old.enabled && knowledge.any { it.id == old.id && it.enabled } }
            xyz.fieldatlas.ui.library.DownloadsSheet(downloads, oldEditions,
                onRemoveOldEditions = { oldEditions.forEach(onDeletePack) }, onDismiss = { showDownloads = false })
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(
                    Modifier.navigationBarTransition().fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    // Any download follows the person through the app, just above the tabs.
                    downloads?.let { queue ->
                        downloadStatus?.let { status ->
                            xyz.fieldatlas.ui.library.DownloadsBar(status, onPause = queue.queue::pause, onOpen = { showDownloads = true })
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.CardShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val textMeasurer = rememberTextMeasurer()
                            val labelStyle = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                            )
                            val labels = PrimaryDestination.entries.map { primaryLabel(it) }
                            val availableLabelWidth = with(LocalDensity.current) {
                                ((maxWidth - 16.dp) / 4 - 4.dp).toPx()
                            }
                            // Keep the four-across mockup at normal sizes. Reflow rather than
                            // break words when accessibility text or a narrow window needs space.
                            val columns = if (labels.any {
                                textMeasurer.measure(it, labelStyle, softWrap = false).size.width > availableLabelWidth
                            }) 2 else 4
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(8.dp).selectableGroup(),
                            ) {
                                PrimaryDestination.entries.chunked(columns).forEach { items ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        items.forEach { item ->
                                            PrimaryNavigationItem(
                                                item = item, selected = navigation.primary == item,
                                                onClick = { navigation.select(item) }, modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (navigation.primary) {
                    PrimaryDestination.Research -> ResearchScreen(
                        state = if (leaving) launchFrame ?: researchState else researchState,
                        inferenceState = inferenceState,
                        collectionCount = enabledKnowledge.size,
                        suggestions = enabledKnowledge.flatMap { it.discovery?.exampleQuestions.orEmpty() },
                        // A recording into the follow-up box is not this box's.
                        voiceState = if (voiceState.forFollowUp) VoiceUiState() else voiceState,
                        onMicClick = { onSelectVoiceTarget(false); onMicClick() },
                        onCameraClick = { onSelectAttachmentTarget(false); onCameraClick() },
                        onPhotosClick = { onSelectAttachmentTarget(false); onPhotosClick() },
                        onFilesClick = { onSelectAttachmentTarget(false); onFilesClick() },
                        attachmentNotice = attachmentNotice,
                        onRemoveAttachment = onRemoveAttachment,
                        onRetryAttachment = onRetryAttachment,
                        onQuestionChange = onQuestionChange,
                        onSubmit = {
                            launchFrame = researchState
                            navigation.openAnswer()
                            onSubmit()
                        },
                        onStop = onStop,
                        onPrepareModel = onLoadModel,
                        onOpenAnswer = navigation::openAnswer,
                        onAskAnotherQuestion = onAskAnotherQuestion,
                        onEditReadAs = onEditReadAs,
                        answersWithoutModel = answersWithoutModel,
                        background = researchBackground,
                        onOpenBackground = { id -> if (onOpenBackground(id)) navigation.openAnswer() else onDismissBackground(id) },
                        onDismissBackground = onDismissBackground,
                    )
                    PrimaryDestination.Library -> LibraryScreen(
                        packs, onImportPack, onToggleResearch, onActivateModel, onDeletePack,
                        onOpenDownloadSources = { showDownloadSources = true },
                        importing = importing,
                        modelDownloading = downloading,
                        importError = setupError,
                        availableKnowledge = availableKnowledge,
                        catalogRefreshing = catalogRefreshing,
                        catalogError = catalogError,
                        onRefreshCatalog = onRefreshCatalog,
                        knowledgeDownloadKey = knowledgeDownloadKey,
                        knowledgeDownloadedBytes = knowledgeDownloadedBytes,
                        knowledgeDownloadError = knowledgeDownloadError,
                        resumableKnowledgeBytes = resumableKnowledgeBytes,
                        onDownloadKnowledge = onDownloadKnowledge,
                        onCancelKnowledgeDownload = onCancelKnowledgeDownload,
                        onDiscardPausedDownload = onDiscardPausedDownload,
                        modelOffers = modelOffers,
                        downloadingModelId = downloadingModelId.takeIf { downloading },
                        modelDownloadedBytes = downloadedBytes,
                        modelDownloadError = modelError,
                        modelDownloadErrorId = modelErrorId,
                        onDownloadModel = onDownloadModelOffer,
                        onCancelModelDownload = onCancelDownload,
                        resumableModelBytes = resumableOfferBytes,
                        modelBusy = inferenceState == InferenceState.Loading || inferenceState == InferenceState.Generating,
                        modelFits = modelFits,
                        modelNotice = modelNotice,
                        modelDownloadUrl = { sourceFor(xyz.fieldatlas.assets.DownloadArtifact.model(it)) },
                        knowledgeDownloadUrl = { sourceFor(xyz.fieldatlas.assets.DownloadArtifact.knowledge(it)) },
                        onAskExample = { question -> askAnew(question) },
                    )
                    PrimaryDestination.History -> historyState.SaveableStateProvider("history") {
                        HistoryScreen(historyRecords,
                            onDelete = onDeleteHistory, storage = historyStorage,
                            onContinue = { record ->
                                // While an answer is being written it is never interrupted: show it instead.
                                navigation.select(PrimaryDestination.Research)
                                if (onContinueTopic(record)) navigation.openAnswer(from = PrimaryDestination.History)
                            })
                    }
                    PrimaryDestination.More -> MoreScreen(
                        packs = packs,
                        onOpenLibrary = { navigation.select(PrimaryDestination.Library) },
                        onOpenDownloadSources = { showDownloadSources = true },
                        diagnosticsText = diagnosticsText,
                        onClearDiagnostics = onClearDiagnostics,
                        proof = proof,
                        inferenceState = inferenceState,
                        onPrepareModel = onLoadModel,
                        onReleaseModel = onUnloadModel,
                        onExportDiagnostics = onExportDiagnostics,
                        onOpenBenchmark = onOpenBenchmark,
                    )
                }
            }
        }
        }
        }
        }
        }
        }
        readyNote?.let { note ->
            xyz.fieldatlas.ui.research.ReadyNote(
                note,
                onOpen = {
                    readyNote = null
                    if (onOpenBackground(note.id)) {
                        navigation.select(PrimaryDestination.Research)
                        navigation.openAnswer()
                    }
                },
                onDismiss = { readyNote = null },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        }
    }
}

@Composable
private fun PrimaryNavigationItem(
    item: PrimaryDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemLabel = primaryLabel(item)
    val indicatorColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "Navigation selection",
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.heightIn(min = 56.dp)
            // The whole tab is the touch target, but a press (or keyboard focus) shows only in the rounded pill
            // behind the icon, as in Material's navigation bar, never as a grey block over the tab. The tab is
            // named once, here, and the label below is hidden from screen readers so it is not read twice.
            .selectable(selected = selected, role = Role.Tab, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = itemLabel }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 32.dp)
                .clip(xyz.fieldatlas.ui.theme.FieldAtlasLayout.ControlShape)
                .background(indicatorColor)
                .indication(interaction, androidx.compose.material3.ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(primaryIcon(item), contentDescription = null, modifier = Modifier.size(21.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            itemLabel, modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 16.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun shouldAutoPrepareModel(
    assetsReady: Boolean,
    inferenceState: InferenceState,
    started: Boolean,
): Boolean = assetsReady && inferenceState == InferenceState.Idle && !started

@Composable
private fun primaryLabel(destination: PrimaryDestination): String = stringResource(
    when (destination) {
        PrimaryDestination.Research -> R.string.nav_research
        PrimaryDestination.Library -> R.string.nav_library
        PrimaryDestination.History -> R.string.nav_history
        PrimaryDestination.More -> R.string.nav_more
    },
)

private fun primaryIcon(destination: PrimaryDestination): ImageVector = when (destination) {
    PrimaryDestination.Research -> FieldAtlasIcons.Research
    PrimaryDestination.Library -> FieldAtlasIcons.Library
    PrimaryDestination.History -> FieldAtlasIcons.History
    PrimaryDestination.More -> FieldAtlasIcons.More
}

/** Whether a run that has just started should open the answer page: once per run, identified by its start time. */
internal fun opensAnswerOnStart(startedAtNanos: Long?, running: Boolean, openedForRun: Long?): Boolean =
    running && startedAtNanos != null && startedAtNanos != openedForRun

/** How long a just-opened answer page waits for its run to start before returning to the Research home. */
private const val START_GRACE_MILLIS = 2_000L

/** How long the note that an answer finished off the screen stays up. */
private const val READY_NOTE_MILLIS = 8_000L
