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
        // Every offered collection gets its own subject icon, never one shared pin or book.
        val offered = mapOf(
            "wikipedia-encyclopedia" to FieldAtlasIcons.Encyclopedia,
            "wikivoyage-guides" to FieldAtlasIcons.Compass,
            "wikivoyage-places" to FieldAtlasIcons.Place,
            "osm-vegan-places" to FieldAtlasIcons.Leaf,
            "osm-essentials" to FieldAtlasIcons.Storefront,
            "crypto-ethereum" to FieldAtlasIcons.Key,
            "world-knowledge-biology" to FieldAtlasIcons.Microscope,
        )
        offered.forEach { (id, icon) -> assertEquals(id, icon, packVisual(asset(id)).icon) }
        assertEquals(offered.size, offered.values.toSet().size)
        assertEquals(PackVisual(FieldAtlasIcons.Document, TileTone.Sage), packVisual(asset("everyday-reference")))
        // Every knowledge tile shares one background colour.
        assertEquals(setOf(TileTone.Sage), offered.keys.map { packVisual(asset(it)).tone }.toSet())
        assertEquals(TileTone.Sage, packVisual(asset("qwen3.5-2b-q4-k-m", PackType.MODEL)).tone)
    }
}
