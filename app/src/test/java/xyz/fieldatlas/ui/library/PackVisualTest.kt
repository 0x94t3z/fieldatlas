package xyz.fieldatlas.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.TileTone

class PackVisualTest {
    private fun asset(id: String, type: PackType = PackType.KNOWLEDGE) =
        InstalledAsset(id, "1", type, id, "CC BY-SA", 1, "sha", "/tmp/$id")

    @Test fun packsAreDrawnBySubject() {
        // Each offered collection is drawn with the website's icon for its subject. Travel guides and travel
        // places are one card there, so they share its pin; every other collection has an icon of its own.
        val offered = mapOf(
            "wikipedia-encyclopedia" to FieldAtlasIcons.CollectionBook,
            "wikivoyage-guides" to FieldAtlasIcons.CollectionPin,
            "wikivoyage-places" to FieldAtlasIcons.CollectionPin,
            "osm-vegan-places" to FieldAtlasIcons.CollectionBowl,
            "osm-essentials" to FieldAtlasIcons.CollectionCross,
            "crypto-ethereum" to FieldAtlasIcons.Key,
            "world-knowledge-biology" to FieldAtlasIcons.CollectionLeaf,
        )
        offered.forEach { (id, icon) -> assertEquals(id, icon, packVisual(asset(id)).icon) }
        assertEquals(offered.size - 1, offered.values.toSet().size)
        assertEquals(PackVisual(FieldAtlasIcons.Document, TileTone.Sage), packVisual(asset("everyday-reference")))
        // Every knowledge tile shares one background colour.
        assertEquals(setOf(TileTone.Sage), offered.keys.map { packVisual(asset(it)).tone }.toSet())
        assertEquals(TileTone.Sage, packVisual(asset("qwen3.5-2b-q4-k-m", PackType.MODEL)).tone)
    }
}
