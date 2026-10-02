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
        assertEquals(SetupNextAction.OpenResearch, deriveSetupNextAction(listOf(model()), false))
        assertEquals(
            SetupNextAction.OpenResearch,
            deriveSetupNextAction(listOf(model(), knowledge(CoverageLevel.DEMO)), false),
        )
        assertEquals(SetupNextAction.Importing, deriveSetupNextAction(emptyList(), true))
    }

    @Test fun knowledgeCardOmitsDemoCoverageCopy() {
        val card = knowledge(CoverageLevel.DEMO).toAssetCardModel()
        assertEquals("Knowledge data", card.kind)
        assertNull(card.coverageLabel)
        assertNull(card.coverageSummary)
    }

    @Test fun modelCardKeepsItsIdentity() {
        val card = model().toAssetCardModel()
        assertEquals("Answer model", card.kind)
    }

    @Test fun collectionsShowTheirShortCatalogNames() {
        assertEquals("Encyclopedia", asset("simplewiki", PackType.KNOWLEDGE, null).toAssetCardModel().title)
        assertEquals("Vegan places", asset("osm-vegan-places", PackType.KNOWLEDGE, null).toAssetCardModel().title)
        assertEquals("Travel guides", asset("wikivoyage-guides", PackType.KNOWLEDGE, null).toAssetCardModel().title)
        assertEquals("Project-notes", asset("project-notes", PackType.KNOWLEDGE, null).toAssetCardModel().title)
    }

    @Test fun knowledgeCategoryPrefersTheCatalogLabelThenThePackId() {
        assertEquals(KnowledgeCategory.Places, knowledgeCategory("osm-essentials"))
        assertEquals(KnowledgeCategory.Places, knowledgeCategory("wikivoyage-places"))
        assertEquals(KnowledgeCategory.Travel, knowledgeCategory("wikivoyage-guides"))
        assertEquals(KnowledgeCategory.Encyclopedia, knowledgeCategory("simplewiki"))
        assertEquals(KnowledgeCategory.Science, knowledgeCategory("world-knowledge-biology"))
        assertEquals(KnowledgeCategory.Other, knowledgeCategory("project-notes"))
        assertEquals(KnowledgeCategory.HowTo, knowledgeCategory("project-notes", " how-to "))
        assertEquals(KnowledgeCategory.Science, knowledgeCategory("world-knowledge-biology", "Unknown shelf"))
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
