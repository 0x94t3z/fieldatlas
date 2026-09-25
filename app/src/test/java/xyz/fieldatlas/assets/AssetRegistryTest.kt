package xyz.fieldatlas.assets

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssetRegistryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun asset(
        id: String = "world-knowledge-alpha",
        version: String = "1.0.0",
        enabled: Boolean = true,
        type: PackType = PackType.KNOWLEDGE,
    ): InstalledAsset = InstalledAsset(
        id = id,
        version = version,
        type = type,
        title = "Alpha pack",
        license = "CC-BY-SA-4.0",
        installedBytes = 1_024,
        manifestSha256 = "x",
        rootPath = File(tempFolder.root, "$id-$version").absolutePath,
        enabled = enabled,
    )

    private fun registryIn(dir: File) = AssetRegistry(File(dir, "assets"))

    @Test
    fun `added assets default to enabled`() {
        val dir = tempFolder.newFolder("default-enabled")
        val registry = registryIn(dir)
        runBlocking { registry.add(asset()) }
        val stored = File(dir, "assets/asset-registry.json").readText()
        assertTrue("registry json should mark enabled", stored.contains("\"enabled\":true"))
        assertEquals(true, runBlocking { registry.list() }.single().enabled)
    }

    @Test
    fun `enabled flag persists across registry instances`() {
        val dir = tempFolder.newFolder("persist")
        val first = registryIn(dir)
        runBlocking {
            first.add(asset())
            first.add(asset(id = "world-knowledge-beta"))
        }

        // Disable only the alpha pack; the beta pack keeps its own flag.
        runBlocking { first.setEnabled("world-knowledge-alpha", "1.0.0", false) }

        val reopened = registryIn(dir)
        val assets = runBlocking { reopened.list() }
        val alpha = assets.first { it.id == "world-knowledge-alpha" }
        val beta = assets.first { it.id == "world-knowledge-beta" }
        assertFalse(alpha.enabled)
        assertTrue(beta.enabled)

        runBlocking { reopened.setEnabled("world-knowledge-alpha", "1.0.0", true) }
        assertTrue(runBlocking { registryIn(dir).list() }.first { it.id == "world-knowledge-alpha" }.enabled)
    }

    @Test
    fun `setEnabled is version scoped`() {
        val dir = tempFolder.newFolder("version-scope")
        val registry = registryIn(dir)
        runBlocking {
            registry.add(asset(version = "1.0.0", enabled = true))
            registry.add(asset(version = "2.0.0", enabled = true))
            registry.setEnabled("world-knowledge-alpha", "1.0.0", false)
        }
        val assets = runBlocking { registryIn(dir).list() }
        assertFalse(assets.first { it.version == "1.0.0" }.enabled)
        assertTrue(assets.first { it.version == "2.0.0" }.enabled)
    }

    @Test
    fun `setEnabled ignores unknown assets`() {
        val dir = tempFolder.newFolder("unknown")
        val registry = registryIn(dir)
        runBlocking {
            registry.add(asset())
            val failure = runCatching { registry.setEnabled("does-not-exist", "9.9.9", false) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
        assertTrue(runBlocking { registryIn(dir).list() }.single().enabled)
    }

    @Test
    fun activeFlagIsExclusivePerPackType() = runBlocking {
        val registry = registryIn(tempFolder.root)
        registry.add(asset("model-a", type = PackType.MODEL))
        registry.add(asset("model-b", type = PackType.MODEL))
        registry.add(asset("speech-a", type = PackType.AUDIO))
        registry.add(asset("speech-b", type = PackType.AUDIO))
        registry.add(asset("data", type = PackType.KNOWLEDGE))
        registry.setActiveModel("model-b", "1.0.0")
        registry.setActiveModel("speech-b", "1.0.0")
        val all = registry.list().associateBy { it.id }
        assertTrue(all.getValue("model-b").active)
        assertFalse(all.getValue("model-a").active)
        assertTrue(all.getValue("speech-b").active)
        assertFalse(all.getValue("speech-a").active)
        assertFalse(all.getValue("data").active)
        // Knowledge packs can never hold the active flag.
        runCatching { registry.setActiveModel("data", "1.0.0") }.let {
            assertTrue("knowledge must not be selectable", it.isFailure)
        }
        assertTrue(registry.list().associateBy { it.id }.getValue("model-b").active)
    }
}
