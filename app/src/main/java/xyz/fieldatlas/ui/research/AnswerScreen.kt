package xyz.fieldatlas.ui.research

import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.AttachmentProvenance
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.sourcePresentation
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasDisclosureRow
import xyz.fieldatlas.ui.theme.FieldAtlasFactRow
import xyz.fieldatlas.ui.theme.FieldAtlasReadingStyle
import xyz.fieldatlas.ui.theme.FieldAtlasSans

@Composable
fun AnswerScreen(
    state: ResearchUiState,
    onBack: () -> Unit,
    onAskAnother: () -> Unit,
    onCitation: (zeroBasedSourceIndex: Int) -> Unit,
    onFollowUp: (String) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    travelCollectionAvailable: Boolean = false,
    followUpDraft: String = "",
    onFollowUpDraftChange: (String) -> Unit = {},
    voiceState: VoiceUiState = VoiceUiState(),
    onFollowUpMic: () -> Unit = {},
    onFollowUpCamera: () -> Unit = {},
    onFollowUpPhotos: () -> Unit = {},
    onFollowUpFiles: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    attachmentNotice: String? = null,
    onLeaveFollowUp: () -> Unit = {},
    onStop: () -> Unit = {},
    onEditReadAs: () -> Unit = {},
    /** How long this phone's recent answers took, for the status line while one is researched. */
    typicalAnswerMillis: Long? = null,
) {
    BackHandler(onBack = onBack)
    // The page opens as soon as research starts: the live status sits where the answer will appear.
    val running = state.isRunning
    // What follows reads the finished answer. While research runs it is skipped: the research rules it calls are being
    // loaded on a worker the first time, and waiting for them here would freeze the screen.
    val presentation = if (running) AnswerPresentation(emptyList(), emptySet(), emptySet()) else buildAnswerPresentation(state.answer, state.sources.size)
    val isInputNotice = !running && state.answer.isNotBlank() && state.answer ==
        xyz.fieldatlas.research.QuestionRequirements.response(state.question, state.attachments.isNotEmpty())
    // Lookups such as "near me" reply with app-written text and never run the model, so
    // "model-generated" and "not verified against sources" labels would be wrong there.
    val noModelReply = !isInputNotice && state.metrics?.generatedTokenCount == 0
    // The app's own "not enough evidence" reply is not a model answer, whatever ran before it.
    val withheld = !running && (state.answer.trim() == xyz.fieldatlas.research.CitedExplanation.GAP ||
        state.answer.trim() == xyz.fieldatlas.research.CitedExplanation.UNAVAILABLE)
    val calculated = noModelReply && state.answer.contains("_Calculated on this phone._")
    val workedFromQuestion = state.metrics?.workedFromQuestion == true
    val suggestTravel = travelCollectionAvailable && Regex(
        "(?i)\\b(restaurants?|caf[eé]s?|hotels?|museums?|sights?|attractions?|shops?)\\b",
    ).containsMatchIn(state.question)
    val citedNumbers = presentation.availableCitations.sorted()
    val citedNumberSet = citedNumbers.toSet()
    val citedSources = citedNumbers.mapNotNull { number ->
        state.sources.getOrNull(number - 1)?.let { number to it }
    }
    val otherSources = state.sources.mapIndexedNotNull { index, evidence ->
        val number = index + 1
        if (number in citedNumberSet) null else number to evidence
    }
    val citedSourceGroups = groupAnswerSources(citedSources)
    val otherSourceGroups = groupAnswerSources(otherSources)
    val hasUnverifiedUpload = citedSources.any { AttachmentProvenance.needsWarning(it.second) }
    var showOtherSources by rememberSaveable { mutableStateOf(false) }
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    val showPassagesFirst = state.sources.isNotEmpty() &&
        state.answer.startsWith("## Model explanation—not verified against saved sources")
    var showAllPassages by rememberSaveable(state.question) { mutableStateOf(false) }
    // Citation and source taps open a quick preview first; the full source is one tap further.
    var previewIndex by rememberSaveable(state.question) { mutableStateOf<Int?>(null) }
    val openPreview: (Int) -> Unit = { index -> if (index in state.sources.indices) previewIndex = index }
    val placeCards = if (noModelReply) remember(state.answer, state.sources) {
        placeCardModels(presentation.blocks, state.sources)
    } else null
    val missingSubjects = if (running) emptyList() else xyz.fieldatlas.research.EvidenceRelevance.missingComparisonSubjects(state.question, state.sources)
    // Reading aloud: an emergency guide plays its recording from a large button; any other finished answer can be listened to.
    val readAloud = LocalReadAloud.current
    val readKey = "answer:${state.topic}:${state.shownQuestion}:${state.answer.hashCode()}"
    // The page follows only which sentence is being read here; the player and the Stop bar follow the clock themselves,
    // so the answer is not laid out again twice a second while it is read.
    val readingHere by remember(readAloud, readKey) {
        (readAloud?.state ?: kotlinx.coroutines.flow.MutableStateFlow(null))
            .map { reading -> reading?.takeIf { it.key == readKey }?.let { ReadingHere(it.shown) } }
            .distinctUntilChanged()
    }.collectAsState(null)
    val guideId = if (running) null else state.sources.singleOrNull()?.documentId
        ?.takeIf { it.startsWith("emergency:") && state.answer.startsWith("**If this is an emergency") }?.removePrefix("emergency:")
    val listenable = !running && guideId == null && state.answer.isNotBlank() && !withheld && !isInputNotice
    DisposableEffect(readKey) { onDispose { if (readAloud?.state?.value?.key == readKey) readAloud.stop() } }
    // The natural voice takes a few seconds to load on an older phone: do it while the answer is being read.
    androidx.compose.runtime.LaunchedEffect(readKey, listenable) { if (listenable) readAloud?.prepareNatural() }
    // The first Listen asks which voice to use; after that it reads at once.
    var choosingVoice by rememberSaveable { mutableStateOf(false) }
    val listen = { if (readAloud?.settings?.value?.voice == null) choosingVoice = true else readAloud.readAnswer(readKey, state.answer) }
    // Read without a tap when the person asked for it: a guide that opens (if chosen in More), or the answer to a
    // question asked with the microphone. Keyed on the finished answer, so each is read once, not on every return.
    androidx.compose.runtime.LaunchedEffect(readKey, guideId, listenable) {
        val settings = readAloud?.settings?.value ?: return@LaunchedEffect
        if (readAloud.state.value != null) return@LaunchedEffect
        if (guideId != null && settings.readGuidesAtOnce) readAloud.readGuide(readKey, guideId)
        else if (listenable && state.askedByVoice && settings.readVoiceAnswers) readAloud.readAnswer(readKey, state.answer)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            FieldAtlasTopBar(title = "Answer", onBack = onBack, action = if (state.answer.isBlank() || running) null else ({
                AnswerShareActions(answerWithSources(state.question, state.answer, state.sources), state.question)
            }))
            // While a guide is read, Stop stays in reach here, and the guide scrolls to the sentence being read.
            if (guideId != null && readingHere != null && readAloud != null) LiveReading(readAloud, readKey) { ReadingControl(it, onStop = readAloud::stop) }
            androidx.compose.runtime.CompositionLocalProvider(xyz.fieldatlas.ui.markdown.LocalSpokenSentence provides readingHere?.shown) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Earlier questions of this topic, folded to one line each.
                state.earlier.forEachIndexed { index, turn ->
                    item(key = "earlier:$index") { EarlierTurnCard(index + 1, turn) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.earlier.isNotEmpty()) FollowUpLabel(state.earlier.size + 1)
                        Text(
                            text = state.shownQuestion.ifBlank { "Research result" },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        // While it runs, how the question is read, when that differs from the title: a follow-up read
                        // with the question before it (Edit corrects it), or the extra words a weak first search tried.
                        if (running) researchReading(state)?.let { (label, reading) ->
                            ReadingLine(label, reading, onEdit = if (state.asked.isNotBlank()) onEditReadAs else null)
                        }
                        // While research runs the status card says what is happening; the pills describe a finished answer.
                        if (!running) FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            val cited = citedSourceGroups.size
                            if (cited > 0) {
                                FieldAtlasStatusPill(
                                    // Written by the app from saved data (a guide, a number, a listing), or by the model citing it.
                                    (if (noModelReply) "From saved data · " else "") +
                                        if (cited == 1) "1 cited local source" else "$cited cited local sources",
                                    StatusTone.Positive,
                                    icon = xyz.fieldatlas.ui.theme.FieldAtlasIcons.Citation,
                                )
                            } else {
                                FieldAtlasStatusPill(
                                    if (withheld) "No answer from your sources"
                                    else if (isInputNotice) "Research notice · no model generation"
                                    else if (calculated) "Calculated on this phone"
                                    // Nothing cited and no model: the app's own "I couldn't…" or "your sources don't mention X".
                                    else if (noModelReply) "App reply · no model generation"
                                    // A word problem: worked from the question's own numbers, each step re-checked here.
                                    else if (workedFromQuestion) "Worked out by the model · steps checked on this phone"
                                    else if (state.sources.isEmpty()) "Model-generated · no supporting sources found"
                                    else "Model-generated · sources not cited",
                                    if (withheld || isInputNotice || noModelReply || workedFromQuestion) StatusTone.Neutral else StatusTone.Attention,
                                )
                            }
                            (state.metrics?.totalMillis ?: state.savedMillis)?.let { FieldAtlasStatusPill(if (it < 1_000) "Under 1s" else formatResearchElapsed(it)) }
                            if (listenable && readAloud != null && readingHere == null) ListenPill(listen)
                        }
                        if (guideId != null && readAloud != null && readingHere == null) {
                            ReadAloudButton(readAloud.recordingMillis(guideId)) { readAloud.readGuide(readKey, guideId) }
                        }
                    }
                }
                if (running) {
                    // A follow-up shows how it is searched, with Edit, then the live status where its answer will appear.
                    item(key = "progress") { ResearchTrailStatus(state, typicalAnswerMillis) }
                    item(key = "forthcoming") { ForthcomingAnswer(state) }
                    item(key = "sourcesSoFar") { SourcesSoFar(state) }
                } else {
                // No collection can support a sum worked from the question's own numbers, so none is suggested.
                if (state.sources.isEmpty() && !isInputNotice && !noModelReply && !withheld && !workedFromQuestion) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (suggestTravel) {
                                "No saved travel sources support this answer. Place names and recommendations may be inaccurate."
                            } else {
                                "This answer isn't verified against saved sources. Add a relevant collection for source-backed research."
                            },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.material3.TextButton(shape = FieldAtlasButtonShape, onClick = onOpenLibrary) {
                                Text(if (suggestTravel) "Add travel collection" else "Browse knowledge collections")
                            }
                        }
                    }
                }
                if (hasUnverifiedUpload) item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
                        Text("The cited upload contains an answer labeled as unverified. This explanation does not independently verify its claims.",
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (showPassagesFirst) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AnswerSectionLabel("Saved passages")
                            Text("Original retrieved text. These passages do not verify the explanation below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (missingSubjects.isNotEmpty()) Text(
                                "These retrieved passages do not mention: ${missingSubjects.joinToString(", ")}. The saved context may not cover both sides of your comparison.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val previewCount = if (showAllPassages) state.sources.size else minOf(2, state.sources.size)
                    items(previewCount, key = { "passage:$it" }) { index ->
                        SavedPassagePreview(state.sources[index], index, cited = index + 1 in citedNumberSet, openPreview)
                    }
                    if (state.sources.size > 2) item {
                        AnswerDisclosure(
                            label = if (showAllPassages) "Show fewer passages" else "Show all ${state.sources.size} passages",
                            expanded = showAllPassages,
                            onClick = { showAllPassages = !showAllPassages },
                        )
                    }
                }
                if (placeCards != null) {
                    // A place lookup reads as a list of places to choose from, each one tappable.
                    val listAt = presentation.blocks.indexOfFirst { it is xyz.fieldatlas.ui.markdown.MarkdownBlock.ListBlock }
                    val intro = presentation.blocks.take(listAt)
                    val outro = presentation.blocks.drop(listAt + 1)
                    if (intro.isNotEmpty()) item {
                        val (lead, notes) = remember(state.answer) { placeIntroParts(placeIntroText(state.answer)) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnswerMarkdownRenderer(blocks = xyz.fieldatlas.ui.markdown.parseAnswerMarkdown(lead),
                                sourceCount = state.sources.size, onCitation = openPreview,
                                bodyStyle = FieldAtlasReadingStyle.copy(fontFamily = FieldAtlasSans, fontWeight = FontWeight.Medium))
                            notes?.let {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Info, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp).size(15.dp))
                                    AnswerMarkdownRenderer(blocks = xyz.fieldatlas.ui.markdown.parseAnswerMarkdown(it),
                                        sourceCount = state.sources.size, onCitation = openPreview,
                                        modifier = Modifier.weight(1f),
                                        bodyStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            }
                        }
                    }
                    items(placeCards.size, key = { "place:${placeCards[it].sourceIndex}" }) { index ->
                        PlaceCard(placeCards[index], openPreview)
                    }
                    if (outro.isNotEmpty()) item {
                        AnswerMarkdownRenderer(blocks = outro, sourceCount = state.sources.size, onCitation = openPreview,
                            bodyStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                } else item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                        AnswerMarkdownRenderer(
                            blocks = presentation.blocks,
                            sourceCount = state.sources.size,
                            onCitation = openPreview,
                            bodyStyle = FieldAtlasReadingStyle,
                            sourceTitles = state.sources.map { it.title },
                        )
                    }
                }
                if (state.sources.isNotEmpty() && !showPassagesFirst) {
                    item { AnswerSectionLabel("Sources") }
                    if (citedSources.isNotEmpty()) {
                        if (otherSources.isNotEmpty()) item {
                            Text(
                                "Cited in this answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(citedSourceGroups.size, key = { "cited:${citedSourceGroups[it].key}" }) { index ->
                            AnswerSourceCard(citedSourceGroups[index], openPreview, cited = true)
                        }
                    }
                }
                val showOtherRow = state.sources.isNotEmpty() && !showPassagesFirst && otherSources.isNotEmpty()
                val metrics = state.metrics
                if (showOtherRow || metrics != null) item {
                    // One grouped card, as in the design: secondary disclosures sit together.
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        Column {
                            if (showOtherRow) {
                                AnswerDisclosureContent(
                                    label = otherSourceLabel(otherSources.size, showOtherSources),
                                    expanded = showOtherSources,
                                    onClick = { showOtherSources = !showOtherSources },
                                )
                                if (showOtherSources) {
                                    Text("These passages were provided to the model but were not cited in this answer.",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    otherSourceGroups.forEach { group -> AnswerSourceCard(group, openPreview, inGroup = true) }
                                }
                            }
                            if (showOtherRow && metrics != null) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                            if (metrics != null) {
                                val model = formatResearchMetrics(metrics, state.sources.size)
                                AnswerDisclosureContent(
                                    label = "Answer details",
                                    onClick = { showPerformance = !showPerformance },
                                    expanded = showPerformance,
                                )
                                if (showPerformance) {
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        state.keywords.takeIf(List<String>::isNotEmpty)?.let { keywords ->
                                            FieldAtlasFactRow("Searched for", keywords.joinToString(", "))
                                        }
                                        FieldAtlasFactRow("Retrieval", model.retrieval)
                                        model.firstToken?.let { FieldAtlasFactRow("First word", it) }
                                        FieldAtlasFactRow("Total", "${model.total} · ${model.tokenCount}")
                                        model.promptReading?.let { FieldAtlasFactRow("Prompt reading", it) }
                                        model.tokenRate?.let { FieldAtlasFactRow("Writing speed", it) }
                                        FieldAtlasFactRow("Citations", model.citationCoverage)
                                        if (model.hasUnmappedCitation) {
                                            // Diagnostic, not a user error: the app already removed the
                                            // reference, so it reads as information rather than failure.
                                            Text(
                                                "The model referred to a source it wasn't given; that reference was removed.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            previewIndex?.let { index ->
                SourcePreviewSheet(
                    sources = state.sources,
                    index = index,
                    cited = index + 1 in citedNumberSet,
                    nextIndex = (index + 1).takeIf { it < state.sources.size },
                    onDismiss = { previewIndex = null },
                    onOpenFull = { previewIndex = null; onCitation(it) },
                    onNext = { previewIndex = it },
                    appWritten = noModelReply,
                )
            }
            }
            if (guideId == null && readingHere != null && readAloud != null) {
                LiveReading(readAloud, readKey) { ListenPlayer(it, onPause = readAloud::pause, onResume = readAloud::resume, onStop = readAloud::stop) }
            }
            // Why Listen could not start (a phone without a speech voice), said where the player would be.
            readAloud?.problem?.collectAsState()?.value?.let { problem ->
                androidx.compose.runtime.LaunchedEffect(problem) { kotlinx.coroutines.delay(8_000); readAloud.clearProblem() }
                Surface(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(problem, Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall)
                        xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton(readAloud::clearProblem, "Dismiss", xyz.fieldatlas.ui.theme.FieldAtlasIcons.Close)
                    }
                }
            }
            if (choosingVoice && readAloud != null) ChooseVoiceSheet(readAloud,
                onChosen = { choosingVoice = false; readAloud.readAnswer(readKey, state.answer) }, onDismiss = { choosingVoice = false })
            // Pinned, so a follow-up or a new topic is always one tap away. A follow-up is only sent once the
            // answer is finished: it is read together with this answer. While research runs the box's button is Stop.
            if (running || state.phase == ResearchPhase.Complete && state.answer.isNotBlank()) {
                FollowUpComposer(
                    follows = (state.earlier.firstOrNull()?.asked ?: state.shownQuestion).ifBlank { "this answer" },
                    draft = followUpDraft,
                    onDraftChange = onFollowUpDraftChange,
                    onSend = onFollowUp,
                    // New topic never stops an answer: it goes on in the background and is saved to History.
                    onNewTopic = onAskAnother,
                    voice = voiceState,
                    onMic = onFollowUpMic,
                    attachments = state.attachments,
                    canAddAttachment = state.canAddAttachment,
                    attachmentsReady = state.attachmentsReady,
                    onCamera = onFollowUpCamera,
                    onPhotos = onFollowUpPhotos,
                    onFiles = onFollowUpFiles,
                    onRemoveAttachment = onRemoveAttachment,
                    onRetryAttachment = onRetryAttachment,
                    attachmentNotice = attachmentNotice,
                    onLeave = onLeaveFollowUp,
                    running = running,
                    onStop = onStop,
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    OutlinedButton(shape = FieldAtlasButtonShape,
                        onClick = onAskAnother,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text("Ask another question")
                    }
                }
            }
        }
    }
}

/** Opens the system share sheet with an answer and its sources as plain text. */
internal fun shareAnswer(context: android.content.Context, question: String, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, question.ifBlank { "Field Atlas answer" })
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share answer")) }
}

/**
 * Copy and share carry the sources with the answer. Sharing works offline too (Bluetooth,
 * nearby share, SMS), which matters most when there is no connection.
 */
@Composable
private fun AnswerShareActions(shareText: String, question: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton(onClick = {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(shareText))
        android.widget.Toast.makeText(context, "Answer and sources copied", android.widget.Toast.LENGTH_SHORT).show()
    }, description = "Copy answer with sources", icon = xyz.fieldatlas.ui.theme.FieldAtlasIcons.Copy)
    xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton(onClick = { shareAnswer(context, question, shareText) },
        description = "Share answer with sources", icon = xyz.fieldatlas.ui.theme.FieldAtlasIcons.Share)
}

@Composable
private fun SavedPassagePreview(evidence: Evidence, index: Int, cited: Boolean, onCitation: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open complete saved passage ${index + 1}") { onCitation(index) },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1} · ${evidence.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (cited) FieldAtlasStatusPill("Cited", StatusTone.Positive)
                else FieldAtlasStatusPill("Not cited")
            }
            // Plain text, not model output or Markdown: source content cannot inject
            // clickable citations. Ellipsis is visual only; opening retains full context.
            Text(evidence.text, style = MaterialTheme.typography.bodyMedium,
                maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text("Open full passage", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnswerSectionLabel(text: String) {
    xyz.fieldatlas.ui.theme.FieldAtlasSectionLabel(text, Modifier.padding(top = 4.dp))
}

@Composable
private fun AnswerSourceCard(
    group: AnswerSourceGroup,
    onCitation: (Int) -> Unit,
    cited: Boolean = false,
    inGroup: Boolean = false,
) {
    val evidence = group.entries.first().second
    val source = sourcePresentation(evidence)
    val iconBackground = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        FieldAtlasColors.SageWash
    }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = "Open source ${group.entries.first().first}") {
                onCitation(group.entries.first().first - 1)
            },
        color = if (inGroup) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = if (inGroup) null else androidx.compose.foundation.BorderStroke(1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.entries.forEach { (number, _) ->
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clickable(onClickLabel = "Open source $number") { onCitation(number - 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = iconBackground,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = number.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Source $number"
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    evidence.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = if (source.isAttachment) 1 else 2,
                    overflow = if (source.isAttachment) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
                )
                Text(
                    (if (source.isAttachment) source.metadata else sourceDisplayName(evidence.source)) +
                        if (cited) " · cited" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                xyz.fieldatlas.ui.theme.FieldAtlasIcons.ChevronRight,
                contentDescription = "Open source",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AnswerDisclosure(label: String, expanded: Boolean, onClick: () -> Unit) {
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        AnswerDisclosureContent(label, expanded, onClick)
    }
}

@Composable
private fun AnswerDisclosureContent(label: String, expanded: Boolean, onClick: () -> Unit) {
    FieldAtlasDisclosureRow(label, expanded, onClick)
}

private data class AnswerSourceGroup(
    val key: String,
    val entries: List<Pair<Int, Evidence>>,
)

private fun groupAnswerSources(sources: List<Pair<Int, Evidence>>): List<AnswerSourceGroup> =
    sources.groupBy { (_, evidence) -> Triple(evidence.documentId, evidence.title, evidence.source) }
        .map { (key, entries) -> AnswerSourceGroup(key.toString(), entries) }

private fun otherSourceLabel(count: Int, expanded: Boolean): String {
    val noun = if (count == 1) "passage" else "passages"
    return if (expanded) "Hide $count other retrieved $noun" else "$count other retrieved $noun"
}

/** Which sentence of this page is being read aloud, if any: all the page itself needs to know. */
private data class ReadingHere(val shown: String?)
