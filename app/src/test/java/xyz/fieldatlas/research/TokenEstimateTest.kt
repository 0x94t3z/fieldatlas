package xyz.fieldatlas.research

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counts recorded from the real Qwen3.5 tokenizer (llama.cpp /tokenize) on varied text. */
class TokenEstimateTest {
    private val samples = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("token-estimate-samples.json")!!.readText(),
    ).jsonObject.getValue("samples").jsonArray.map { it.jsonObject }

    @Test fun neverUndercountsTheRealTokenizer() {
        for (sample in samples) {
            val text = sample.getValue("text").jsonPrimitive.content
            val actual = sample.getValue("tokens").jsonPrimitive.int
            val estimate = TokenEstimate.of(text)
            assertTrue("${sample["kind"]}/${sample["variant"]}: estimate $estimate < actual $actual", estimate >= actual)
        }
    }

    @Test fun staysCloseEnoughToBeUsefulForEnglish() {
        val prose = samples.filter { it["kind"]!!.jsonPrimitive.content in setOf("prose", "code") }
        val ratio = prose.sumOf { TokenEstimate.of(it.getValue("text").jsonPrimitive.content).toDouble() } /
            prose.sumOf { it.getValue("tokens").jsonPrimitive.int.toDouble() }
        assertTrue("mean overcount $ratio", ratio < 1.6)
        // Byte counting, the previous budget, overcounts English far more.
        val bytes = prose.sumOf { it.getValue("text").jsonPrimitive.content.toByteArray().size.toDouble() } /
            prose.sumOf { it.getValue("tokens").jsonPrimitive.int.toDouble() }
        assertTrue("bytes $bytes vs estimate $ratio", bytes > ratio * 2)
    }

    @Test fun digitsAreCountedOneEach() {
        assertTrue(TokenEstimate.of("1234567890".repeat(10)) >= 100)
    }
}
