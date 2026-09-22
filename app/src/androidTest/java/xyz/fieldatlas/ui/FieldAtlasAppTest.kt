package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.assets.CoverageLevel
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackDiscovery
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.benchmark.BenchmarkQuestion
import xyz.fieldatlas.benchmark.BenchmarkRun
import xyz.fieldatlas.inference.InferenceState
import xyz.fieldatlas.proof.ProofFact
import xyz.fieldatlas.proof.ProofModel
import xyz.fieldatlas.proof.ProofOrigin
import xyz.fieldatlas.proof.ProofState
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.research.ResearchPhase
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.proof.BenchmarkScreen
import xyz.fieldatlas.ui.proof.BenchmarkUiState
import xyz.fieldatlas.ui.setup.SetupScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

class FieldAtlasAppTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstLaunchShowsSetupAndAccessibleImport() {
        render(packs = emptyList())
        compose.onNodeWithText("FIELD ATLAS").assertExists()
        compose.onNodeWithText("Pack your atlas").assertExists()
        compose.onNodeWithText("No cloud").assertExists()
        compose.onNodeWithText("1. Add model pack").assertExists()
        compose.onNodeWithText("2. Add knowledge pack").assertExists()
        compose.onNodeWithText("Turn off Wi-Fi and mobile data", substring = true).assertExists()
        compose.onNodeWithText("Choose model pack").assertExists().assertHasClickAction()
    }

    @Test fun setupPrimaryActionRemainsVisibleAtLargeFontOnNarrowPhone() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    SetupScreen(
                        emptyList(),
                        false,
                        null,
                        onImportPack = {},
                        listState = rememberLazyListState(initialFirstVisibleItemIndex = 4),
                    )
                }
            }
        }
        compose.onNodeWithText("Choose model pack")
            .assertIsDisplayed().assertHasClickAction()
    }

    @Test fun researchPrimaryActionRemainsReachableAtLargeFontOnNarrowPhone() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ResearchScreen(
                        state = ResearchUiState(question = "Explain evidence"),
                        inferenceState = InferenceState.Ready,
                        collectionCount = 1,
                        suggestions = listOf("Why do seasons change?"),
                        onQuestionChange = {},
                        onSubmit = {},
                        onStop = {},
                        onPrepareModel = {},
                        onOpenAnswer = {},
                        listState = rememberLazyListState(initialFirstVisibleItemIndex = 4),
                    )
                }
            }
        }
        compose.onNodeWithText("Start research").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun verifiedPacksEnableResearch() {
        render(researchState = ResearchUiState(question = "Explain evidence"))
        compose.onNodeWithText("Start research").assertIsEnabled()
        compose.onNodeWithContentDescription("Research question").assertExists()
    }

    @Test fun runningResearchKeepsStopVisible() {
        render(researchState = ResearchUiState(question = "Explain", phase = ResearchPhase.Generating))
        compose.onNodeWithText("Stop").assertExists().assertIsEnabled()
    }

    @Test fun bottomNavigationUsesLabeledSemanticIcons() {
        render()
        compose.onNodeWithContentDescription("Research").assertExists()
        compose.onNodeWithContentDescription("Library").assertExists()
        compose.onNodeWithContentDescription("More").assertExists()
        compose.onNodeWithText("R").assertDoesNotExist()
    }

    @Test fun sourceDetailHidesPrimaryNavigationAndBackTraversesAnswer() {
        val passage = Evidence("doc", "doc:0000", "Exact title", "Exact source", "Exact passage text.", 1.0)
        render(researchState = completedResearch("Answer [S1]", passage))

        compose.onNodeWithText("Read answer").performClick()
        compose.onNodeWithContentDescription("Open source 1").performClick()
        compose.onNodeWithContentDescription("Back").assertExists().performClick()
        compose.onNodeWithText("Answer from 1 source").assertExists()
        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Library").assertExists()
    }

    @Test fun citationOpensExactPassage() {
        val passage = Evidence("doc", "doc:0000", "Exact title", "Exact source", "Exact passage text.", 1.0)
        render(researchState = completedResearch("Answer [S1]", passage))
        compose.onNodeWithText("Read answer").performClick()
        compose.onNodeWithContentDescription("Open source 1").performClick()
        compose.onNodeWithText("Exact passage text.").assertExists()
        compose.onNodeWithText("Source details").performClick()
        compose.onNodeWithText("Document doc").assertExists()
        compose.onNodeWithText("Chunk doc:0000").assertExists()
        compose.onNodeWithText("CC0-1.0").assertExists()
    }

    @Test fun completedResearchRendersMarkdownWithoutControlSymbols() {
        var capturedQuestion = ""
        val passage = Evidence("doc", "doc:0000", "Exact title", "Exact source", "Exact passage.", 1.0)
        val longItem = "Long field observation ".repeat(24)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.5f, 1f)) {
                FieldAtlasApp(
                    packs = verifiedPacks(),
                    importing = false,
                    setupError = null,
                    researchState = ResearchUiState(
                        question = "Compare flood responses",
                        answer = "# Different kinds of protection\n\nWetlands **slow water** [S1] and retain habitat. [S8]\n\n- $longItem",
                        sources = listOf(passage),
                        phase = ResearchPhase.Complete,
                    ),
                    proof = proofModel(),
                    onImportPack = {},
                    onQuestionChange = { capturedQuestion = it },
                    onSubmit = {},
                    onStop = {},
                )
            }
        }
        compose.onNodeWithText("Read answer").performClick()
        compose.onNodeWithText("Different kinds of protection").assertExists()
        compose.onNodeWithText("# Different kinds of protection").assertDoesNotExist()
        compose.onNodeWithText("slow water", substring = true).assertExists()
        compose.onNodeWithText("**slow water**").assertDoesNotExist()
        compose.onNodeWithText("Long field observation", substring = true).assertExists()
        compose.onNodeWithContentDescription("Open source 1").assertExists()
        compose.onNodeWithContentDescription("Open source 8").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Why do seasons change?").performClick()
        compose.runOnIdle { assertEquals("Why do seasons change?", capturedQuestion) }
    }

    @Test fun compactAnswerKeepsAllEightCitationActionsAvailable() {
        val sources = (1..8).map { index ->
            Evidence("doc-$index", "doc-$index:0000", "Source $index", "Collection", "Passage $index", 1.0)
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.5f, 1f)) {
                FieldAtlasTheme {
                    AnswerScreen(
                        state = ResearchUiState(
                            question = "Compare all evidence",
                            answer = (1..8).joinToString(" ") { "[S$it]" },
                            sources = sources,
                            phase = ResearchPhase.Complete,
                        ),
                        onBack = {},
                        onCitation = {},
                    )
                }
            }
        }
        (1..8).forEach { compose.onNodeWithContentDescription("Open source $it").assertExists() }
    }

    @Test fun libraryShowsBytesHashAndLicense() {
        render()
        compose.onNodeWithText("Library").performClick()
        compose.onAllNodesWithText("Verification details")[0].performClick()
        compose.onNodeWithText("Verification details").performClick()
        compose.onNodeWithText("License: Apache-2.0").assertExists()
        compose.onNodeWithText("License: CC0-1.0").assertExists()
        compose.onNodeWithText("SHA-256: ${"a".repeat(64)}").assertExists()
        compose.onNodeWithText("SHA-256: ${"b".repeat(64)}").assertExists()
        compose.onAllNodesWithText("KB", substring = true).assertCountEquals(2)
    }

    @Test fun moreShowsNoNetworkState() {
        var opened = false
        render(onOpenBenchmark = { opened = true })
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Private by design").assertExists()
        compose.onNodeWithText("Technical details").performClick()
        compose.onNodeWithText("Internet permission").assertExists()
        compose.onNodeWithText("Absent").assertExists()
        compose.onNodeWithText("Pass · Manifest audit").assertExists()
        compose.onNodeWithText("Open device benchmark").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun benchmarkShowsFrozenNextQuestionAndExplicitAction() {
        var backed = false
        val question = BenchmarkQuestion(
            answerable = true,
            category = "factual",
            id = "fact-seasons",
            prohibitedClaims = emptyList(),
            prompt = "What causes Earth's seasons?",
            requiredEvidence = listOf("axial tilt"),
            scoringNotes = "Fixture",
        )
        val run = BenchmarkRun(
            runId = "run-1",
            startedAt = "2026-09-20T00:00:00Z",
            artifacts = emptyList(),
            diagnosticsSha256 = "a".repeat(64),
            results = emptyList(),
        )
        compose.setContent {
            FieldAtlasTheme {
                BenchmarkScreen(
                    state = BenchmarkUiState(run, 18, question),
                    inferenceState = InferenceState.Ready,
                    onRunNext = {},
                    onStop = {},
                    onExport = {},
                    onBack = { backed = true },
                )
            }
        }
        compose.onNodeWithText("0 / 18 completed").assertExists()
        compose.onNodeWithText("What causes Earth's seasons?").assertExists()
        compose.onNodeWithText("Run next").assertHasClickAction().assertIsEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(backed) }
    }

    private fun render(
        packs: List<InstalledAsset> = verifiedPacks(),
        researchState: ResearchUiState = ResearchUiState(),
        proof: ProofModel = proofModel(),
        onOpenBenchmark: () -> Unit = {},
    ) {
        compose.setContent {
            FieldAtlasApp(
                packs = packs,
                importing = false,
                setupError = null,
                researchState = researchState,
                proof = proof,
                onImportPack = {},
                onQuestionChange = {},
                onSubmit = {},
                onStop = {},
                onOpenBenchmark = onOpenBenchmark,
            )
        }
    }

    private fun completedResearch(answer: String, source: Evidence) = ResearchUiState(
        question = "Exact question",
        answer = answer,
        sources = listOf(source),
        phase = ResearchPhase.Complete,
    )

    private fun verifiedPacks() = listOf(
        asset("model", PackType.MODEL, "Local model", "Apache-2.0", 1_500, "a"),
        asset(
            "knowledge",
            PackType.KNOWLEDGE,
            "Knowledge pack",
            "CC0-1.0",
            2_500,
            "b",
            PackDiscovery("A focused science sample.", listOf("Why do seasons change?"), CoverageLevel.DEMO),
        ),
    )

    private fun proofModel() = ProofModel(
        offline = listOf(
            ProofFact("Internet permission", "Absent", ProofOrigin.ManifestAudit, ProofState.Pass),
        ),
        device = listOf(
            ProofFact("RSS", "Not measured", ProofOrigin.MeasuredQuery, ProofState.Missing),
        ),
        latestRun = listOf(
            ProofFact("Status", "No run recorded", ProofOrigin.MeasuredQuery, ProofState.Missing),
        ),
    )

    private fun asset(
        id: String,
        type: PackType,
        title: String,
        license: String,
        bytes: Long,
        hashCharacter: String,
        discovery: PackDiscovery? = null,
    ) = InstalledAsset(
        id = id,
        version = "1",
        type = type,
        title = title,
        license = license,
        installedBytes = bytes,
        manifestSha256 = hashCharacter.repeat(64),
        rootPath = "/data/$id",
        discovery = discovery,
    )
}
