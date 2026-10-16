package xyz.fieldatlas.assets

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What Discover offers, on the catalog this APK ships, for the phones that will meet it. */
class DiscoverOffersTest {
    @get:Rule val temp = TemporaryFolder()
    private val shipped = KnowledgeCatalog.parse(File("src/main/assets/knowledge/catalog.json").readText())

    private fun installed(entry: KnowledgeCatalogEntry, version: String = entry.version, bytes: Long = entry.installedBytes ?: entry.bytes,
                          enabled: Boolean = true) =
        InstalledAsset(entry.id, version, PackType.KNOWLEDGE, entry.title, entry.license, bytes, "0", "/packs/${entry.id}", enabled = enabled)

    /** The editions before the Leviathan rebuilds, as a catalog saved on a phone before 14 Oct listed them. */
    private val beforeLeviathan = shipped.packs.filter { it.version.endsWith("-leviathan") }
        .map { it.copy(version = it.version.removeSuffix("-leviathan"), bytes = it.bytes + 1, sha256 = "d".repeat(64)) }

    @Test fun aNewPhoneIsOfferedEveryCollectionAndNoUpdates() {
        val offers = discoverOffers(shipped.packs, emptyList())
        assertEquals(shipped.packs.map { it.id }.sorted(), offers.fresh.map { it.id }.sorted())
        assertTrue(offers.updates.isEmpty())
    }

    @Test fun aPhoneWithTheNewestPacksIsOfferedNothingEvenWithAnOldCatalogSaved() {
        // The owner's Redmi on 16 Oct: every shipped edition installed, and a catalog saved before the rebuilds.
        File(temp.root, "catalog.json").writeText(Json.encodeToString(KnowledgeCatalog(1, beforeLeviathan)))
        val repository = KnowledgeCatalogRepository(shipped, File(temp.root, "catalog.json"), { error("offline") })
        val offers = discoverOffers(repository.state.value.packs, shipped.packs.map { installed(it) })
        assertTrue("offered ${offers.fresh.map { it.id }}", offers.fresh.isEmpty())
        assertTrue("offered ${offers.updates.map { it.id + " " + it.version }}", offers.updates.isEmpty())
        // Even if both editions reached the list, the older one is not offered to someone who has the newer.
        val both = discoverOffers(shipped.packs + beforeLeviathan, shipped.packs.map { installed(it) })
        assertTrue(both.fresh.isEmpty() && both.updates.isEmpty())
    }

    @Test fun aPhoneStillOnTheOldPacksIsOfferedTheRebuildsAsUpdates() {
        val old = beforeLeviathan.map { installed(it) }
        val others = shipped.packs.filterNot { pack -> beforeLeviathan.any { it.id == pack.id } }.map { installed(it) }
        val offers = discoverOffers(shipped.packs, old + others)
        assertEquals(beforeLeviathan.map { it.id }.sorted(), offers.updates.map { it.id }.sorted())
        assertTrue(offers.updates.all { it.version.endsWith("-leviathan") })
        assertTrue(offers.fresh.isEmpty())
    }

    @Test fun anOldEditionKeptSwitchedOffAfterAnUpdateBringsNothingBack() {
        val places = shipped.packs.single { it.id == "wikivoyage-places" }
        val kept = installed(beforeLeviathan.single { it.id == "wikivoyage-places" }, enabled = false)
        val offers = discoverOffers(listOf(places), listOf(installed(places), kept))
        assertTrue(offers.fresh.isEmpty() && offers.updates.isEmpty())
    }
}
