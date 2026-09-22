package xyz.fieldatlas.ui.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.assets.CoverageLevel
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackDiscovery
import xyz.fieldatlas.assets.PackType

class AssetPresentationTest {
    @Test fun missingPacksNameTheNextUsefulAction() {
        assertEquals(SetupNextAction.ChooseModel, deriveSetupNextAction(emptyList(), false))
        assertEquals(SetupNextAction.ChooseKnowledge, deriveSetupNextAction(listOf(model()), false))
        assertEquals(
            SetupNextAction.OpenResearch,
            deriveSetupNextAction(listOf(model(), knowledge(CoverageLevel.DEMO)), false),
        )
        assertEquals(SetupNextAction.Importing, deriveSetupNextAction(emptyList(), true))
    }

    @Test fun knowledgeCardOmitsDemoCoverageCopy() {
        val card = knowledge(CoverageLevel.DEMO).toAssetCardModel()
        assertEquals("Knowledge", card.kind)
        assertNull(card.coverageLabel)
        assertNull(card.coverageSummary)
    }

    @Test fun modelCardKeepsItsIdentity() {
        val card = model().toAssetCardModel()
        assertEquals("Model", card.kind)
    }

    private fun model() = asset("model", PackType.MODEL, null)

    private fun knowledge(level: CoverageLevel) = asset(
        "knowledge",
        PackType.KNOWLEDGE,
        PackDiscovery("Four project-authored notes", listOf("Why do seasons change?"), level),
    )

    private fun asset(id: String, type: PackType, discovery: PackDiscovery?) = InstalledAsset(
        id = id,
        version = "1.0.0",
        type = type,
        title = id.replaceFirstChar(Char::uppercase),
        license = "CC0-1.0",
        installedBytes = 1_500,
        manifestSha256 = "a".repeat(64),
        rootPath = "/private/$id",
        discovery = discovery,
    )
}
