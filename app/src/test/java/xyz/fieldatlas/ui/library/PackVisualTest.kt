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
        assertEquals(PackVisual(FieldAtlasIcons.Place, TileTone.Gold), packVisual(asset("wikivoyage-places")))
        assertEquals(PackVisual(FieldAtlasIcons.Place, TileTone.Gold), packVisual(asset("osm-vegan-places")))
        assertEquals(PackVisual(FieldAtlasIcons.Knowledge, TileTone.Sage), packVisual(asset("world-knowledge-biology")))
        assertEquals(PackVisual(FieldAtlasIcons.Document, TileTone.Paper), packVisual(asset("everyday-reference")))
        assertEquals(TileTone.Sage, packVisual(asset("qwen3.5-2b-q4-k-m", PackType.MODEL)).tone)
    }
}
