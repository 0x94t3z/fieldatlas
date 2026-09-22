package xyz.fieldatlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import xyz.fieldatlas.ui.theme.FieldAtlasColors

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
    val menuBackground = if (isSystemInDarkTheme()) null else FieldAtlasColors.SageWash
    var autoPreparationStarted by rememberSaveable { mutableStateOf(false) }
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
                NavigationBar(containerColor = menuBackground ?: MaterialTheme.colorScheme.surface) {
                    PrimaryDestination.entries.forEach { item ->
                        val itemLabel = primaryLabel(item)
                        NavigationBarItem(
                            selected = navigation.primary == item,
                            onClick = { navigation.select(item) },
                            modifier = Modifier.semantics { contentDescription = itemLabel },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FieldAtlasColors.OnMenuSelection,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = FieldAtlasColors.MenuSelection,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
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
        PrimaryDestination.More -> R.string.nav_more
    },
)

private fun primaryIcon(destination: PrimaryDestination): ImageVector = when (destination) {
    PrimaryDestination.Research -> FieldAtlasIcons.Research
    PrimaryDestination.Library -> FieldAtlasIcons.Library
    PrimaryDestination.More -> FieldAtlasIcons.More
}
