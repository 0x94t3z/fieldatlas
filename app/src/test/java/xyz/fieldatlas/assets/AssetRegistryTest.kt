package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssetRegistryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun discoveryRoundTripsThroughRegistry() = runBlocking {
        val root = temporaryFolder.newFolder("round-trip")
        val registry = AssetRegistry(root)
        val discovery = PackDiscovery(
            coverageSummary = "Four demo notes",
            exampleQuestions = listOf("Why do seasons change?"),
            coverageLevel = CoverageLevel.DEMO,
        )
        registry.add(asset(discovery))
        assertEquals(discovery, AssetRegistry(root).list().single().discovery)
    }

    @Test fun registryWithoutDiscoveryStillLoads() = runBlocking {
        val root = temporaryFolder.newFolder("legacy")
        File(root, "asset-registry.json").writeText(
            """[{"id":"old","version":"1","type":"KNOWLEDGE","title":"Old","license":"CC0-1.0","installedBytes":1,"manifestSha256":"${"a".repeat(64)}","rootPath":"/old"}]""",
        )
        assertNull(AssetRegistry(root).list().single().discovery)
    }

    private fun asset(discovery: PackDiscovery) = InstalledAsset(
        id = "demo",
        version = "1",
        type = PackType.KNOWLEDGE,
        title = "Demo",
        license = "CC0-1.0",
        installedBytes = 1,
        manifestSha256 = "a".repeat(64),
        rootPath = "/demo",
        discovery = discovery,
    )
}
