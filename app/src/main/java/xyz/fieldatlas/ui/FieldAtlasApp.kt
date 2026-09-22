package xyz.fieldatlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.R
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.more.MoreScreen
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.sources.SourcesScreen
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasTheme
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar

@Composable
fun FieldAtlasApp(
    packs: List<InstalledAsset>,
    importing: Boolean,
    setupError: String?,
    researchState: ResearchUiState,
    proof: ProofModel,
    navigation: FieldAtlasNavigationState = rememberFieldAtlasNavigationState(),
    onImportPack: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    inferenceState: InferenceState = InferenceState.Ready,
    onLoadModel: () -> Unit = {},
    onUnloadModel: () -> Unit = {},
    onExportDiagnostics: (Boolean) -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
) {
    val ready = packs.any { it.type == PackType.MODEL } && packs.any { it.type == PackType.KNOWLEDGE }
    val knowledgePack = packs.firstOrNull { it.type == PackType.KNOWLEDGE }
    FieldAtlasTheme {
        if (!ready) {
            SetupScreen(packs, importing, setupError, onImportPack)
            return@FieldAtlasTheme
        }

        val closeDetail = { navigation.back(); Unit }
        val detail = navigation.detail
        if (detail != null) {
            when (detail) {
                DetailDestination.Answer -> AnswerScreen(
                    state = researchState,
                    onBack = closeDetail,
                    onCitation = navigation::openSource,
                )
                is DetailDestination.Source -> {
                    val source = researchState.sources.getOrNull(detail.index)
                    if (source == null) {
                        BackHandler { navigation.back() }
                        Column(Modifier.fillMaxSize()) {
                            FieldAtlasTopBar(title = "Source", onBack = closeDetail)
                            Box(Modifier.padding(horizontal = 20.dp)) {
                                Text("This source is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        SourcesScreen(
                            evidence = source,
                            sourceNumber = detail.index + 1,
                            sourceCount = researchState.sources.size,
                            packLicense = knowledgePack?.license,
                            onBack = closeDetail,
                        )
                    }
                }
            }
            return@FieldAtlasTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    PrimaryDestination.entries.forEach { item ->
                        val itemLabel = primaryLabel(item)
                        NavigationBarItem(
                            selected = navigation.primary == item,
                            onClick = { navigation.select(item) },
                            modifier = Modifier.semantics { contentDescription = itemLabel },
                            icon = {
                                Icon(
                                    imageVector = primaryIcon(item),
                                    contentDescription = itemLabel,
                                )
                            },
                            label = { Text(itemLabel) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (navigation.primary) {
                    PrimaryDestination.Research -> ResearchScreen(
                        state = researchState,
                        inferenceState = inferenceState,
                        collectionCount = packs.count { it.type == PackType.KNOWLEDGE },
                        suggestions = knowledgePack?.discovery?.exampleQuestions.orEmpty(),
                        onQuestionChange = onQuestionChange,
                        onSubmit = onSubmit,
                        onStop = onStop,
                        onPrepareModel = onLoadModel,
                        onOpenAnswer = navigation::openAnswer,
                    )
                    PrimaryDestination.Library -> LibraryScreen(packs, onImportPack)
                    PrimaryDestination.More -> MoreScreen(
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

@Composable
private fun primaryLabel(destination: PrimaryDestination): String = stringResource(
    when (destination) {
        PrimaryDestination.Research -> R.string.nav_research
        PrimaryDestination.Library -> R.string.nav_library
        PrimaryDestination.More -> R.string.nav_more
    },
)

private fun primaryIcon(destination: PrimaryDestination): ImageVector = when (destination) {
    PrimaryDestination.Research -> FieldAtlasIcons.Research
    PrimaryDestination.Library -> FieldAtlasIcons.Library
    PrimaryDestination.More -> FieldAtlasIcons.More
}
