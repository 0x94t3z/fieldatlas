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
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.withStyle
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
    typicalAnswerRange: LongRange? = null,
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
    // Written from the model's own knowledge: the line under the answer says so, and the pills would only repeat it.
    val fromKnowledge = !running && state.answer.lines().any { xyz.fieldatlas.research.SavedFactCheck.isKnowledgeNote(it) }
    // Read for a rare subject: the model cited the passages itself, so they are cited sources, not figures that happen to agree.
    val readSources = fromKnowledge && state.answer.lines().any { it.trim().startsWith(xyz.fieldatlas.research.SavedFactCheck.KNOWLEDGE_PREFIX + " and your saved sources") }
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
    // A guide shown as published opens with either call line (22 Oct: the "call now" opening left the recording unused and
    // the slow live voice read the guide instead).
    val guideId = if (running) null else state.sources.singleOrNull()?.documentId
        ?.takeIf { it.startsWith("emergency:") && (state.answer.startsWith("**If this is an emergency") ||
            state.answer.startsWith("**Call your local emergency number now")) }?.removePrefix("emergency:")
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

    // An earlier answer's source opens in the same preview, from that answer's own passages.
    var earlierPreview by remember { mutableStateOf<Pair<List<Evidence>, Int>?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val headerIndex = if (state.earlier.isEmpty()) 0 else 1
    val density = androidx.compose.ui.platform.LocalDensity.current
    // An answer's player sits where Read aloud was; once that has scrolled away it pins under the bar.
    val playerScrolledAway by remember(headerIndex, density) {
        androidx.compose.runtime.derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == headerIndex }
            if (header == null) listState.firstVisibleItemIndex > headerIndex
            else header.offset + header.size < with(density) { 26.dp.roundToPx() }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            FieldAtlasTopBar(title = "Answer", onBack = onBack, action = if (state.answer.isBlank() || running) null else ({
                AnswerShareActions(answerWithSources(state.question, state.answer, state.sources), state.question)
            }))
            // Guides and answers read the same way: the player sits where Read aloud was and pins here once scrolled away,
            // so Stop stays in reach while a guide scrolls to the sentence being read.
            if (readingHere != null && readAloud != null && playerScrolledAway) LiveReading(readAloud, readKey) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    AnswerPlayerBar(it, onBack = readAloud::back, onPause = readAloud::pause, onResume = readAloud::resume, onStop = readAloud::stop,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 8.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(xyz.fieldatlas.ui.markdown.LocalSpokenSentence provides readingHere?.shown) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Earlier questions of this topic, one short list above the newest answer.
                if (state.earlier.isNotEmpty()) item(key = "earlier") {
                    EarlierTopicList(state.earlier, onOpenSource = { sources, index -> earlierPreview = sources to index })
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
                        // A from-memory answer carries its own line at the foot and shows no source pill; its time still shows
                        // (owner, 22 Oct: the time had gone missing on those answers). One that read notes and cited them
                        // says so at the top like any cited answer (owner, 23 Oct: the 4B's fact answers cite [1] under a
                        // lone time pill).
                        if (!running) FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            val cited = citedSourceGroups.size
                            if (!showsSourcePill(fromKnowledge, readSources, cited)) {
                                // The foot line already says where the answer came from.
                            } else if (cited > 0) {
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
                            // The clock says what the number is: the time the answer took, not the listening time under it.
                            (state.metrics?.totalMillis ?: state.savedMillis)?.let {
                                FieldAtlasStatusPill(if (it < 1_000) "Under 1s" else formatResearchElapsed(it), icon = xyz.fieldatlas.ui.theme.FieldAtlasIcons.Clock)
                            }
                        }
                        if (guideId != null && readAloud != null) {
                            if (readingHere == null) ReadAloudButton(readAloud.recordingMillis(guideId)) { readAloud.readGuide(readKey, guideId) }
                            else LiveReading(readAloud, readKey) {
                                AnswerPlayerBar(it, onBack = readAloud::back, onPause = readAloud::pause, onResume = readAloud::resume, onStop = readAloud::stop)
                            }
                        }
                        if (listenable && readAloud != null) {
                            if (readingHere == null) {
                                val millis = remember(state.answer) { readAloud.answerMillis(state.answer) }
                                ReadAloudButton(millis, label = "Read this answer aloud", compact = true, onClick = listen)
                            } else LiveReading(readAloud, readKey) {
                                AnswerPlayerBar(it, onBack = readAloud::back, onPause = readAloud::pause, onResume = readAloud::resume, onStop = readAloud::stop)
                            }
                        }
                    }
                }
                if (running) {
                    // A follow-up shows how it is searched, with Edit, then the live status where its answer will appear.
                    item(key = "progress") { ResearchTrailStatus(state, typicalAnswerRange) }
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
                if (placeCards != null) {
                    // A place lookup reads as a list of places to choose from, each one tappable.
                    val listAt = presentation.blocks.indexOfFirst { it is xyz.fieldatlas.ui.markdown.MarkdownBlock.ListBlock }
                    val intro = presentation.blocks.take(listAt)
                    val outro = presentation.blocks.drop(listAt + 1)
                    val asked = state.shownQuestion.ifBlank { state.question }
                    if (intro.isNotEmpty()) item {
                        val (lead, notes) = remember(state.answer) { placeIntroParts(placeIntroText(state.answer)) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Regular weight, so the bold count ("**6 hospitals**") stands out from the rest of the sentence.
                            AnswerMarkdownRenderer(blocks = xyz.fieldatlas.ui.markdown.parseAnswerMarkdown(placeLead(lead, asked, placeCards.size)),
                                sourceCount = state.sources.size, onCitation = openPreview,
                                bodyStyle = FieldAtlasReadingStyle.copy(fontFamily = FieldAtlasSans, fontWeight = FontWeight.Normal))
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
                    val outroText = placeOutroText(state.answer)
                    // Health places: what to do in an emergency comes before the list, not in its small print.
                    if (PLACE_EMERGENCY_LINE in outroText) item {
                        val emergencyLine = hospitalEmergencyLine(placeCards)
                        Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp,
                                    bottom = if (emergencyLine == null) 12.dp else 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Call, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                    // 112 reaches emergency services from mobile phones in many countries; the local number may differ.
                                    Text("Emergency? Call 112 or your local number.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                emergencyLine?.let { (text, sourceIndex) ->
                                    val opens = sourceIndex != null
                                    Row(Modifier.fillMaxWidth()
                                        .then(if (opens) Modifier.clickable(onClickLabel = "Open this hospital") { openPreview(sourceIndex!!) } else Modifier)
                                        .heightIn(min = 44.dp).padding(start = 46.dp, end = 14.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (opens) FontWeight.SemiBold else FontWeight.Normal)
                                        if (opens) Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.ChevronRight, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    val showDiet = dietAsked.containsMatchIn(asked)
                    items(placeCards.size, key = { "place:${placeCards[it].sourceIndex}" }) { index ->
                        PlaceCard(placeCards[index], openPreview, showDiet)
                    }
                    if (outro.isNotEmpty()) item {
                        val footnote = remember(state.answer) { placeFootnote(outroText, placeMapDate(placeIntroText(state.answer))) }
                        val full = outroText.replace(PLACE_EMERGENCY_LINE, "").trim()
                        var expanded by rememberSaveable(state.answer) { mutableStateOf(false) }
                        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Hide details" else "About these places") { expanded = !expanded }
                                .heightIn(min = 48.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(xyz.fieldatlas.ui.theme.FieldAtlasIcons.Info, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                                Text(androidx.compose.ui.text.buildAnnotatedString {
                                    append(footnote ?: "Distances, hours and where these places come from.")
                                    append(" ")
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                        append(if (expanded) "Hide\u00A0details" else "About\u00A0these\u00A0places")
                                    }
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (expanded && full.isNotBlank()) {
                                AnswerMarkdownRenderer(blocks = xyz.fieldatlas.ui.markdown.parseAnswerMarkdown(full), sourceCount = state.sources.size,
                                    onCitation = openPreview,
                                    bodyStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }
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
                if (state.sources.isNotEmpty()) {
                    item { AnswerSectionLabel("Sources") }
                    if (missingSubjects.isNotEmpty()) item {
                        Text("These passages do not mention: ${missingSubjects.joinToString(", ")}. The saved context may not cover both sides of your comparison.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (citedSources.isNotEmpty()) {
                        if (otherSources.isNotEmpty() || fromKnowledge) item {
                            Text(
                                if (fromKnowledge && !readSources) "State the same figures as the answer" else "Cited in this answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(citedSourceGroups.size, key = { "cited:${citedSourceGroups[it].key}" }) { index ->
                            AnswerSourceCard(citedSourceGroups[index], openPreview, cited = true)
                        }
                    }
                }
                val showOtherRow = state.sources.isNotEmpty() && otherSources.isNotEmpty()
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
                                    Text(if (fromKnowledge) "Found for this question; the answer was written without them."
                                        else "These passages were provided to the model but were not cited in this answer.",
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
            earlierPreview?.let { (sources, index) ->
                SourcePreviewSheet(sources = sources, index = index, cited = true, nextIndex = null,
                    onDismiss = { earlierPreview = null }, onOpenFull = null, onNext = {})
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
            // Why Read aloud could not start (a phone without a speech voice), said where the player would be.
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
                passageOpening(evidence.text)?.let { opening ->
                    Text(opening, style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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

// One row per passage, even when one article gave two: stacked numbers beside one title read as a half-empty row,
// and each number in the answer should lead to its own passage.
private fun groupAnswerSources(sources: List<Pair<Int, Evidence>>): List<AnswerSourceGroup> =
    sources.map { entry -> AnswerSourceGroup("${entry.first}:${entry.second.chunkId}", listOf(entry)) }

/** The first words of a passage, so two passages from the same article can be told apart before one is opened. */
internal fun passageOpening(text: String): String? {
    var words = xyz.fieldatlas.research.EvidenceRelevance.passageText(text).replace(Regex("\\s+"), " ").trim()
    if (words.isEmpty()) return null
    // A passage cut from the middle of a word ("e parts on site") opens at its first whole word instead.
    if (words.first().isLowerCase() && ' ' in words) words = "\u2026" + words.substringAfter(' ')
    return "\u201C" + (if (words.length > 90) words.take(89).trimEnd() + "\u2026" else words) + "\u201D"
}

private fun otherSourceLabel(count: Int, expanded: Boolean): String {
    val noun = if (count == 1) "passage" else "passages"
    return if (expanded) "Hide $count other retrieved $noun" else "$count other retrieved $noun"
}

/** Which sentence of this page is being read aloud, if any: all the page itself needs to know. */
private data class ReadingHere(val shown: String?)

/**
 * Whether a finished answer's header names its sources. A from-memory answer leaves that to its foot line, unless the
 * model read notes and cited them: then the passages it cites are cited sources, shown as for any cited answer.
 */
internal fun showsSourcePill(fromKnowledge: Boolean, readSources: Boolean, cited: Int): Boolean =
    !fromKnowledge || readSources && cited > 0
