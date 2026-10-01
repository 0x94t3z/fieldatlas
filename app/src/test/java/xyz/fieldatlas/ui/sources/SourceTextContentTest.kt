package xyz.fieldatlas.ui.sources

import org.junit.Assert.*
import org.junit.Test

class SourceTextContentTest {
    @Test fun preservesNumericSpellingDuplicateKeysAndStringEscapes() {
        val raw = """{"amount":1.2300e+09,"amount":9007199254740993,"text":"a\\b\"c"}"""
        val formatted = formattedSourceJson(raw)!!
        assertTrue(formatted.contains("\"amount\": 1.2300e+09"))
        assertTrue(formatted.contains("\"amount\": 9007199254740993"))
        assertTrue(formatted.contains(""""text": "a\\b\"c""""))
        assertTrue(formatted.contains('\n'))
    }
    @Test fun incompleteExcerptsAreNotRepairedOrInvented() {
        assertNull(formattedSourceJson("{\"name\":\"partial"))
        assertNull(formattedSourceJson("answer: 42"))
        assertNull(formattedSourceJson("[".repeat(1000) + "]".repeat(1000)))
    }
}
