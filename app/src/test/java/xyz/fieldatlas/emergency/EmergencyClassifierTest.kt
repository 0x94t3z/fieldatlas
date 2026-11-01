package xyz.fieldatlas.emergency

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyClassifierTest {
    private val model = EmergencyClassifier.read(File("src/main/assets/emergency/classifier.bin").readBytes())

    @Test fun theShippedModelReadsMessagesExactlyAsTheTrainingScriptDid() {
        // 120 messages the training script classified with these same weights (tools/emergency-classifier/export.py).
        val vectors = Json.parseToJsonElement(File("src/test/resources/emergency-classifier-parity.json").readText()).jsonArray
        for (element in vectors) {
            val v = element.jsonObject
            val q = v["q"]!!.jsonPrimitive.content
            val (label, probability) = model.classify(q)
            assertEquals(q, v["label"]!!.jsonPrimitive.content, label)
            assertEquals(q, v["p"]!!.jsonPrimitive.double, probability, 1e-3)
        }
    }

    @Test fun theHashIsFnv1aOverUtf8() {
        assertEquals(0x811c9dc5.toInt(), EmergencyClassifier.fnv1a(""))
        assertEquals(0xe40c292c.toInt(), EmergencyClassifier.fnv1a("a"))
        assertEquals(0xbf9cf968.toInt(), EmergencyClassifier.fnv1a("foobar"))
    }

    @Test fun everyLabelIsAGuideOrNone() {
        val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides.map { it.id }.toSet()
        assertTrue(model.labels.toString(), model.labels.all { it == "none" || it in guides })
        assertEquals(guides.size + 1, model.labels.size)
    }
}
