package xyz.fieldatlas.ui.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.assets.CoverageLevel
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackDiscovery
import xyz.fieldatlas.assets.PackType

class AssetPresentationTest {
    @Test fun expansionNamesKeepEachPartDistinctWithoutInventingSubjects() {
        assertEquals("Encyclopedia expansion · 1.1", encyclopediaExpansionLabel("wikipedia-en-broad-s00000-0001"))
        assertEquals("Encyclopedia expansion · 14.3", encyclopediaExpansionLabel("wikipedia-en-broad-s00013-0003"))
        assertNull(encyclopediaExpansionLabel("wikipedia-en-broad-s00013-0000"))
        assertNull(encyclopediaExpansionLabel("custom-wikipedia-en-broad-s00013-0003"))
        assertNull(encyclopediaExpansionLabel("wikipedia-en-vital"))
    }

    @Test fun expansionDescriptionDoesNotReplacePackProvidedCoverage() {
        val expansion = asset("wikipedia-en-broad-s00013-0003", PackType.KNOWLEDGE, null)
        org.junit.Assert.assertTrue(expansion.collectionDescription()!!.contains("mixed subjects"))
        assertEquals("Specific original coverage", expansion.copy(discovery = PackDiscovery(
            "Specific original coverage", emptyList(), CoverageLevel.DEMO)).collectionDescription())
        assertNull(expansion.copy(type = PackType.MODEL).collectionDescription())
        assertNull(asset("project-notes", PackType.KNOWLEDGE, null).collectionDescription())
    }

    @Test fun selectionSummaryOnlyShowsDisabledCollectionsWithoutRepeatingStorage() {
        val on = asset("one", PackType.KNOWLEDGE, null).copy(installedBytes = 1_000_000_000, enabled = true)
        val off = on.copy(id = "two", installedBytes = 2_000_000_000, enabled = false)
        assertEquals("1 of 2 collections enabled",
            knowledgeSelectionSummary(listOf(on, off, model())))
        assertNull(knowledgeSelectionSummary(listOf(model())))
        assertNull(knowledgeSelectionSummary(emptyList()))
        assertNull(knowledgeSelectionSummary(listOf(on, off.copy(enabled = true))))
        assertEquals("0 of 2 collections enabled", knowledgeSelectionSummary(listOf(on.copy(enabled = false), off)))
    }

    @Test fun consolidatedBundleIsOneCollectionWithItsOwnSizeAndSelection() {
        val bundle = asset("wikipedia-encyclopedia", PackType.KNOWLEDGE, null)
            .copy(installedBytes = 24_345_968_640L, enabled = true)
        assertEquals("Encyclopedia", bundle.toAssetCardModel().title)
        assertEquals(KnowledgeCategory.Encyclopedia, knowledgeCategory(bundle.id))
        assertEquals("24.35 GB", bundle.toAssetCardModel().size)
        assertNull(knowledgeSelectionSummary(listOf(bundle, model())))
        assertEquals("0 of 1 collections enabled",
            knowledgeSelectionSummary(listOf(bundle.copy(enabled = false))))
    }
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
