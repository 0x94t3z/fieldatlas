package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryExpansionTest {
    @Test
    fun `parse discards think tags and hidden reasoning`() {
        val raw = "The tiger is the biggest cat. Now the keywords: tiger, feline, panthera tigris"
        assertEquals(listOf("feline", "panthera tigris"), QueryExpansion.parse(raw))
    }

    @Test
    fun `parse survives unclosed think blocks and tag fragments`() {
        assertEquals(listOf("lion"), QueryExpansion.parse("lion,  reasoning that never ends"))
        assertEquals(emptyList<String>(), QueryExpansion.parse(""))
    }

    @Test
    fun `prompt targets keyword planning and stays inside budget`() {
        val prompt = QueryExpansion.prompt("What causes heart attacks?")
        assertTrue(prompt.contains("What causes heart attacks?"))
        assertTrue(prompt.contains("/no_think"))
        assertTrue(prompt.contains("comma-separated"))
        assertTrue(prompt.contains("at least 8"))
    }

    @Test
    fun `parse splits commas and lowercases terms`() {
        assertEquals(
            listOf("cardiac arrest", "myocardial infarction", "cholesterol"),
            QueryExpansion.parse("cardiac arrest, Myocardial Infarction, cholesterol"),
        )
    }

    @Test
    fun `parse strips numbering bullets and stray punctuation`() {
        assertEquals(
            listOf("mitochondria", "aging", "ros"),
            QueryExpansion.parse("1. mitochondria\n- AGING\n\"ros\"."),
        )
    }

    @Test
    fun `parse caps terms and drops oversized or sentence-like fragments`() {
        val raw = (1..20).joinToString(", ") { "term$it" } +
            ", " + "x".repeat(60) + ", a phrase with three extra words here"
        val parsed = QueryExpansion.parse(raw)
        assertEquals(QueryExpansion.MAX_TERMS, parsed.size)
        assertTrue(parsed.none { it.codePointCount(0, it.length) > 48 })
    }

    @Test
    fun `parse tolerates empty and garbage output`() {
        assertEquals(emptyList<String>(), QueryExpansion.parse(""))
        assertEquals(emptyList<String>(), QueryExpansion.parse("   ,, ;;, "))
    }

    @Test
    fun `parse falls back to words when the model ignores commas`() {
        assertEquals(
            listOf("tiger", "lion", "weight", "comparison"),
            QueryExpansion.parse("tiger lion weight comparison tiger lion weight"),
        )
    }
}
