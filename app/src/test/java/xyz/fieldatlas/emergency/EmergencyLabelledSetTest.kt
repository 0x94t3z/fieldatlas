package xyz.fieldatlas.emergency

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 320 emergency and look-alike questions written without the matcher's code, each labelled with
 * the guide to show first, other safe choices, and guides that would mislead (the Heimlich
 * manoeuvre for a heart attack, cooling water for a jellyfish sting). No question may open a
 * misleading guide, and the number answered well must not fall.
 */
class EmergencyLabelledSetTest {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())

    @Test fun noQuestionOpensAMisleadingGuideAndNoneGetWorse() {
        val rows = javaClass.classLoader!!.getResource("emergency-labelled.jsonl")!!.readText().lines().filter(String::isNotBlank)
            .map { Json.parseToJsonElement(it).jsonObject }
        fun list(row: kotlinx.serialization.json.JsonObject, key: String) = row.getValue(key).jsonArray.map { it.jsonPrimitive.content }
        val unsafe = mutableListOf<String>()
        var right = 0
        for (row in rows) {
            val question = row.getValue("q").jsonPrimitive.content
            val got = EmergencyGuideMatch.match(question, book.guides)?.id ?: "none"
            if (got == row.getValue("expected").jsonPrimitive.content || got in list(row, "acceptable")) right++
            if (got in list(row, "unsafe")) unsafe += "$got :: $question"
        }
        assertTrue(unsafe.joinToString("\n"), unsafe.isEmpty())
        assertTrue("$right of ${rows.size} answered well", right >= 129)
    }
}
