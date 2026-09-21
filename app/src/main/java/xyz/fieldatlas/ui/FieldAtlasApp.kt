package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import xyz.fieldatlas.R
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.library.LibraryScreen
import xyz.fieldatlas.ui.proof.ProofScreen
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.sources.SourcesScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Research(R.string.nav_research, R.drawable.ic_research),
    Library(R.string.nav_library, R.drawable.ic_library),
    Proof(R.string.nav_proof, R.drawable.ic_proof),
}

@Composable
fun FieldAtlasApp(
    packs: List<InstalledAsset>,
    importing: Boolean,
    setupError: String?,
    researchState: ResearchUiState,
    proof: ProofModel,
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
        var destination by rememberSaveable { mutableStateOf(Destination.Research) }
        var selectedSource by remember { mutableStateOf<Evidence?>(null) }
        if (selectedSource != null) {
            SourcesScreen(
                evidence = selectedSource!!,
                packLicense = knowledgePack?.license,
                onBack = { selectedSource = null },
            )
            return@FieldAtlasTheme
        }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        val itemLabel = stringResource(item.label)
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            modifier = Modifier.semantics { contentDescription = itemLabel },
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
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
                when (destination) {
                    Destination.Research -> ResearchScreen(
                        state = researchState,
                        assetsReady = ready,
                        inferenceState = inferenceState,
                        suggestions = knowledgePack?.discovery?.exampleQuestions.orEmpty(),
                        onQuestionChange = onQuestionChange,
                        onSubmit = onSubmit,
                        onStop = onStop,
                        onLoadModel = onLoadModel,
                        onUnloadModel = onUnloadModel,
                        onSource = { selectedSource = it },
                    )
                    Destination.Library -> LibraryScreen(packs, onImportPack)
                    Destination.Proof -> ProofScreen(proof, onExportDiagnostics, onOpenBenchmark)
                }
            }
        }
    }
}
