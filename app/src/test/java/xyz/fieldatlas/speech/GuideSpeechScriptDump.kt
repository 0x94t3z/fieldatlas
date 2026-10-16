package xyz.fieldatlas.speech

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Test
import xyz.fieldatlas.emergency.EmergencyData

/**
 * Writes every bundled guide's read-aloud script for tools/packs/build_guide_audio.py, so the recordings say exactly
 * what [SpeechText.guide] says. Runs only when FIELDATLAS_GUIDE_SCRIPT names the output file.
 */
class GuideSpeechScriptDump {
    @Test fun dump() {
        val out = System.getenv("FIELDATLAS_GUIDE_SCRIPT") ?: return
        val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
        val json = buildJsonArray {
            guides.forEach { guide ->
                add(buildJsonObject {
                    put("id", guide.id)
                    putJsonArray("segments") {
                        SpeechText.guide(guide).forEach { segment -> add(buildJsonObject { put("shown", segment.shown); put("spoken", segment.spoken) }) }
                    }
                })
            }
        }
        File(out).writeText(Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), json))
    }
}
