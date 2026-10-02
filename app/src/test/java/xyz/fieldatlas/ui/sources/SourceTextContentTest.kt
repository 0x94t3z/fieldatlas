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

class StructuredSourceFieldsTest {
    @Test fun listingExcerptsBecomeLabelValueRows() {
        val text = "Destination: Berlin/Mitte\nCategory: Eat\nPlace: Vedis\nAddress: Schönhauser Allee 142\n" +
            "Listing last checked: 2020-10-25"
        assertEquals(
            listOf("Destination" to "Berlin/Mitte", "Category" to "Eat", "Place" to "Vedis",
                "Address" to "Schönhauser Allee 142", "Listing last checked" to "2020-10-25"),
            structuredSourceFields(text),
        )
    }

    @Test fun proseAndShortExcerptsStayAsWritten() {
        assertNull(structuredSourceFields("A boat is a watercraft. Note: it floats.\nIt is smaller than a ship.\nRafts differ."))
        assertNull(structuredSourceFields("Place: Vedis\nCategory: Eat"))
        // One prose line breaks the pattern, so nothing is reformatted.
        assertNull(structuredSourceFields("Place: Vedis\nCategory: Eat\nthe owners also run a bakery"))
    }
}
