package xyz.fieldatlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import androidx.compose.foundation.layout.Column
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

@Composable
fun FieldAtlasApp(
    packs: List<InstalledAsset>,
    importing: Boolean,
    importingName: String? = null,
    importingPack: xyz.fieldatlas.assets.PackImportInfo? = null,
    downloading: Boolean = false,
    downloadedBytes: Long = 0,
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
    onDeleteHistory: (Long) -> Unit = {},
    proof: ProofModel,
    navigation: FieldAtlasNavigationState = rememberFieldAtlasNavigationState(),
    onImportPack: () -> Unit,
    onDownloadModel: () -> Unit = {},
    resumableModelBytes: Long = 0,
    resumableKnowledgeBytes: (KnowledgeCatalogEntry) -> Long = { 0 },
    onCancelDownload: () -> Unit = {},
    onDismissKnowledgeOffer: () -> Unit = {},
    onDownloadKnowledge: (KnowledgeCatalogEntry) -> Unit = {},
    onCancelKnowledgeDownload: () -> Unit = {},
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
    val ready = packs.any { it.type == PackType.MODEL }
    val enabledKnowledge = packs.filter { it.type == PackType.KNOWLEDGE && it.enabled }
    val darkTheme = isSystemInDarkTheme()
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
    FieldAtlasTheme {
        if (!ready || offerKnowledge) {
            SetupScreen(
                packs = packs,
                importing = importing,
                importingName = importingName,
                importingPack = importingPack,
                downloading = downloading,
                downloadedBytes = downloadedBytes,
                error = setupError,
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
            )
            return@FieldAtlasTheme
        }

        val closeDetail = { navigation.back(); Unit }
        val detail = navigation.detail
        if (detail != null) {
            when (detail) {
                DetailDestination.Answer -> AnswerScreen(
                    state = researchState,
                    onBack = closeDetail,
                    onAskAnother = { onAskAnotherQuestion(); navigation.back() },
                    onCitation = navigation::openSource,
                    travelCollectionAvailable = availableKnowledge.any { it.id == "wikivoyage-places" } &&
                        packs.none { it.id == "wikivoyage-places" },
                    onOpenLibrary = {
                        navigation.back()
                        navigation.select(PrimaryDestination.Library)
                    },
                )
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
                            // With several active packs the evidence does not carry its pack ID;
                            // omitting this field is safer than crediting the wrong license.
                            packLicense = if (source.documentId.startsWith("attachment:")) null else enabledKnowledge.singleOrNull()?.license,
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
            return@FieldAtlasTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                    ) {
                        // Custom bar: Material's selected indicator is a fixed pill, and the app
                        // uses rounded rectangles for every pressable surface.
                        Row(
                            Modifier.fillMaxWidth().height(76.dp).selectableGroup(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PrimaryDestination.entries.forEach { item ->
                                val itemLabel = primaryLabel(item)
                                val selected = navigation.primary == item
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        // No full-cell ripple: the indicator itself changes on selection.
                                        .selectable(selected = selected, role = Role.Tab,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { navigation.select(item) })
                                        .semantics { contentDescription = itemLabel },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        Modifier.size(width = 60.dp, height = 32.dp).background(
                                            if (selected) {
                                                if (darkTheme) MaterialTheme.colorScheme.primaryContainer else FieldAtlasColors.MenuSelection
                                            } else Color.Transparent,
                                            FieldAtlasButtonShape,
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            primaryIcon(item), contentDescription = null,
                                            tint = when {
                                                !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                                                darkTheme -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> FieldAtlasColors.OnMenuSelection
                                            },
                                        )
                                    }
                                    Text(
                                        itemLabel,
                                        modifier = Modifier.padding(top = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                        state = researchState,
                        inferenceState = inferenceState,
                        collectionCount = enabledKnowledge.size,
                        suggestions = enabledKnowledge.flatMap { it.discovery?.exampleQuestions.orEmpty() },
                        voiceState = voiceState,
                        onMicClick = onMicClick,
                        onCameraClick = onCameraClick,
                        onPhotosClick = onPhotosClick,
                        onFilesClick = onFilesClick,
                        attachmentNotice = attachmentNotice,
                        onRemoveAttachment = onRemoveAttachment,
                        onRetryAttachment = onRetryAttachment,
                        onQuestionChange = onQuestionChange,
                        onSubmit = onSubmit,
                        onStop = onStop,
                        onPrepareModel = onLoadModel,
                        onOpenAnswer = navigation::openAnswer,
                        onAskAnotherQuestion = onAskAnotherQuestion,
                        answersWithoutModel = answersWithoutModel,
                    )
                    PrimaryDestination.Library -> LibraryScreen(
                        packs, onImportPack, onToggleResearch, onActivateModel, onDeletePack,
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
                        onAskExample = { question ->
                            onAskAnotherQuestion()
                            onQuestionChange(question)
                            navigation.select(PrimaryDestination.Research)
                        },
                    )
                    PrimaryDestination.History -> HistoryScreen(historyRecords, onAskAgain = { question ->
                        onAskAnotherQuestion()
                        onQuestionChange(question)
                        navigation.select(PrimaryDestination.Research)
                    }, onDelete = onDeleteHistory)
                    PrimaryDestination.More -> MoreScreen(
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
