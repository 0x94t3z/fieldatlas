package xyz.fieldatlas.desktop

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPilotSettingsTest {
    @get:Rule val temp = TemporaryFolder()

    private fun pin(path: String = "/owned/source.sqlite", bytes: Long = 100) = buildJsonObject {
        put("path", path); put("bytes", bytes); put("sha256", "a".repeat(64))
    }
    private fun config() = buildJsonObject {
        put("schemaVersion", 1)
        put("backends", buildJsonArray { add("app") })
        put("packs", buildJsonArray { add(buildJsonObject { put("id", "pack-a"); put("source", pin()) }) })
        put("cases", buildJsonArray { add(buildJsonObject { put("id", "case-a"); put("query", "planet discovery") }) })
    }
    private fun reject(data: JsonObject) {
        try { LibraryPilotSettings.parse(data); fail("Invalid settings accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun baselineOnlyDoesNotNeedAnExperimentalIndexOrBinary() {
        val parsed = LibraryPilotSettings.parse(config())
        assertNull(parsed.binary); assertNull(parsed.packs.single().index)
        assertEquals(listOf("original"), parsed.orders); assertEquals(1, parsed.repetitions)
        assertEquals(24, parsed.cases.single().candidateLimit); assertEquals(8, parsed.cases.single().finalLimit)
        assertEquals("planet discovery", parsed.cases.single().question)
    }

    @Test fun rejectsDuplicatePackIdsAndAliasedSourcePaths() {
        val first = config().getValue("packs").jsonArray.single().jsonObject
        reject(JsonObject(config() + ("packs" to JsonArray(listOf(first, JsonObject(first + ("source" to pin("/owned/another.sqlite"))))))))
        reject(JsonObject(config() + ("packs" to JsonArray(listOf(first, JsonObject(first + ("id" to JsonPrimitive("another"))))))))
    }

    @Test fun rejectsUnboundedOrOverflowingDataAndMalformedPins() {
        for (bytes in listOf(0L, -1L, Long.MAX_VALUE, 50_000_000_001L)) {
            try { LibraryPinnedFile.parse(pin(bytes = bytes)); fail("Invalid bytes accepted") }
            catch (_: IllegalArgumentException) { }
        }
        val tooLarge = buildJsonArray {
            for (i in 0..1) add(buildJsonObject { put("id", "p$i"); put("source", pin("/owned/$i", 30_000_000_000L)) })
        }
        reject(JsonObject(config() + ("packs" to tooLarge)))
        for (bad in listOf(JsonObject(pin() + ("sha256" to JsonPrimitive("unverified"))), pin("relative.sqlite"))) {
            try { LibraryPinnedFile.parse(bad); fail("Invalid pin accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsUnboundedRunsUnknownModesAndDuplicateCases() {
        for (patch in listOf(
            "repetitions" to JsonPrimitive(0), "repetitions" to JsonPrimitive(6),
            "orders" to buildJsonArray { add("random") }, "backends" to buildJsonArray { add("unknown") },
            "orders" to buildJsonArray { add("original"); add("original") },
            "cases" to JsonArray(config().getValue("cases").jsonArray.let { it + it }),
        )) reject(JsonObject(config() + patch))
        val invalidCase = JsonObject(config().getValue("cases").jsonArray.single().jsonObject + ("candidateLimit" to JsonPrimitive(4)))
        reject(JsonObject(config() + ("cases" to JsonArray(listOf(invalidCase)))))
        reject(JsonObject(config() + ("backends" to buildJsonArray { add("leviathan") })))
    }

    @Test fun identityChecksDetectChangedOriginalFiles() {
        val file = temp.newFile("source.sqlite").apply { writeText("unchanged source") }
        val pin = LibraryPinnedFile(file, file.length(), libraryDigest(file))
        pin.verify()
        file.writeText("changed original")
        try { pin.verify(); fail("Changed source accepted") } catch (_: IllegalStateException) { }
    }
}
