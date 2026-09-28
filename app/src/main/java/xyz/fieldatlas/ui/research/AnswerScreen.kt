package xyz.fieldatlas.ui.research

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.StatusTone

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
    var showOtherSources by rememberSaveable { mutableStateOf(false) }
    var showPerformance by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar(title = "Answer", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = state.question.ifBlank { "Research result" },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = when (citedSourceGroups.size) {
                                    0 -> if (state.sources.isEmpty()) "Model-generated · no supporting sources found"
                                        else "Model-generated · sources not cited"
                                    1 -> "1 cited local source"
                                    else -> "${citedSourceGroups.size} cited local sources"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            FieldAtlasStatusPill("Offline", StatusTone.Positive)
                        }
                    }
                }
                if (state.sources.isEmpty()) {
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
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        AnswerMarkdownRenderer(
                            blocks = presentation.blocks,
                            sourceCount = state.sources.size,
                            onCitation = onCitation,
                        )
                    }
                }
                if (state.sources.isNotEmpty()) {
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
                            AnswerSourceCard(citedSourceGroups[index], onCitation)
                        }
                    }
                    if (otherSources.isNotEmpty()) {
                        item {
                            AnswerDisclosure(
                                label = otherSourceLabel(otherSources.size, showOtherSources),
                                expanded = showOtherSources,
                                onClick = { showOtherSources = !showOtherSources },
                            )
                        }
                        if (showOtherSources) {
                            items(otherSourceGroups.size, key = { "other:${otherSourceGroups[it].key}" }) { index ->
                                AnswerSourceCard(otherSourceGroups[index], onCitation)
                            }
                        }
                    }
                }
                state.metrics?.let { metrics ->
                    val model = formatResearchMetrics(metrics, state.sources.size)
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column {
                                AnswerDisclosureContent(
                                    label = "Answer details",
                                    onClick = { showPerformance = !showPerformance },
                                    expanded = showPerformance,
                                )
                                if (showPerformance) {
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        state.keywords.takeIf(List<String>::isNotEmpty)?.let { keywords ->
                                            Text(
                                                "Searched for: ${keywords.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text("Retrieval ${model.retrieval}", style = MaterialTheme.typography.bodySmall)
                                        model.firstToken?.let {
                                            Text("First word $it", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            "Total ${model.total} · ${model.tokenCount}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        model.tokenRate?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        Text(model.citationCoverage, style = MaterialTheme.typography.bodySmall)
                                        if (model.hasUnmappedCitation) {
                                            Text(
                                                "One citation could not be matched to an installed source.",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
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

@Composable
private fun AnswerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AnswerSourceCard(group: AnswerSourceGroup, onCitation: (Int) -> Unit) {
    val evidence = group.entries.first().second
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
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = iconBackground,
                shape = MaterialTheme.shapes.small,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = group.entries.first().first.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Source ${group.entries.first().first}"
                        },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    evidence.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FieldAtlasEditorial,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sourceDisplayName(evidence.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.entries.size > 1) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    group.entries.drop(1).forEach { (number, _) ->
                        Surface(
                            modifier = Modifier.heightIn(min = 48.dp)
                                .clickable(onClickLabel = "Open source $number") { onCitation(number - 1) },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("[$number]",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        AnswerDisclosureContent(label, expanded, onClick)
    }
}

@Composable
private fun AnswerDisclosureContent(label: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (expanded) {
            Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
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
