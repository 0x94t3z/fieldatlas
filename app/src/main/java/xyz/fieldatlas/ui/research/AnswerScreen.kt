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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.AttachmentProvenance
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.sourcePresentation
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasDisclosureRow
import xyz.fieldatlas.ui.theme.FieldAtlasFactRow

@Composable
fun AnswerScreen(
    state: ResearchUiState,
    onBack: () -> Unit,
    onAskAnother: () -> Unit,
    onCitation: (zeroBasedSourceIndex: Int) -> Unit,
    onOpenLibrary: () -> Unit = {},
    travelCollectionAvailable: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val presentation = buildAnswerPresentation(state.answer, state.sources.size)
    val isInputNotice = state.answer.isNotBlank() && state.answer ==
        xyz.fieldatlas.research.QuestionRequirements.response(state.question, state.attachments.isNotEmpty())
    // Lookups such as "near me" reply with app-written text and never run the model, so
    // "model-generated" and "not verified against sources" labels would be wrong there.
    val noModelReply = !isInputNotice && state.metrics?.generatedTokenCount == 0
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
    val missingSubjects = xyz.fieldatlas.research.EvidenceRelevance.missingComparisonSubjects(state.question, state.sources)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            FieldAtlasTopBar(title = "Answer", onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = state.question.ifBlank { "Research result" },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val cited = citedSourceGroups.size
                            if (cited > 0) {
                                FieldAtlasStatusPill(
                                    if (cited == 1) "1 cited local source" else "$cited cited local sources",
                                    StatusTone.Positive,
                                    icon = xyz.fieldatlas.ui.theme.FieldAtlasIcons.Citation,
                                )
                            } else {
                                FieldAtlasStatusPill(
                                    if (isInputNotice) "Research notice · no model generation"
                                    else if (noModelReply) "Lookup · no model generation"
                                    else if (state.sources.isEmpty()) "Model-generated · no supporting sources found"
                                    else "Model-generated · sources not cited",
                                    if (isInputNotice || noModelReply) StatusTone.Neutral else StatusTone.Attention,
                                )
                            }
                            state.metrics?.totalMillis?.let { FieldAtlasStatusPill(formatResearchElapsed(it)) }
                        }
                    }
                }
                if (state.sources.isEmpty() && !isInputNotice && !noModelReply) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (suggestTravel) {
                                "No saved travel sources support this answer. Place names and recommendations may be inaccurate."
                            } else {
                                "This answer isn't verified against saved sources. Add a relevant collection for source-backed research."
                            },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.material3.TextButton(onClick = onOpenLibrary) {
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
                        SavedPassagePreview(state.sources[index], index, onCitation)
                    }
                    if (state.sources.size > 2) item {
                        AnswerDisclosure(
                            label = if (showAllPassages) "Show fewer passages" else "Show all ${state.sources.size} passages",
                            expanded = showAllPassages,
                            onClick = { showAllPassages = !showAllPassages },
                        )
                    }
                }
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        AnswerMarkdownRenderer(
                            blocks = presentation.blocks,
                            sourceCount = state.sources.size,
                            onCitation = onCitation,
                            bodyStyle = MaterialTheme.typography.bodyLarge,
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
                            AnswerSourceCard(citedSourceGroups[index], onCitation, cited = true)
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
                                    otherSourceGroups.forEach { group -> AnswerSourceCard(group, onCitation, inGroup = true) }
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
                                        model.tokenRate?.let { FieldAtlasFactRow("Speed", it) }
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
            // Pinned like the design, so the next question is always one tap away.
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

@Composable
private fun SavedPassagePreview(evidence: Evidence, index: Int, onCitation: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open complete saved passage ${index + 1}") { onCitation(index) },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${index + 1} · ${evidence.title}", style = MaterialTheme.typography.titleSmall)
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
                                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (source.isAttachment) MaterialTheme.typography.bodyLarge.fontFamily else FieldAtlasEditorial,
                    fontWeight = FontWeight.SemiBold,
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
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
