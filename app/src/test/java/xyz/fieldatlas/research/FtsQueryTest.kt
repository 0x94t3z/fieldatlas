package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQueryTest {
    @Test
    fun `modal filler cannot retrieve unrelated evidence`() {
        assertEquals(
            listOf("water", "contain", "pathogens"),
            FtsQuery.from("water that may contain pathogens")!!.terms,
        )
    }

    @Test fun operatorsBecomeLiteralTerms() {
        assertEquals(
            "\"climate\"* AND \"title\"*",
            FtsQuery.from("climate OR title:\"x\"")!!.matchExpression,
        )
        assertEquals(
            "\"climate\"* OR \"title\"*",
            FtsQuery.from("climate OR title:\"x\"")!!.fallbackExpression,
        )
    }

    @Test fun punctuationOnlyIsRejected() {
        assertNull(FtsQuery.from("'\"()***"))
    }

    @Test fun unicodeRemainsSearchable() {
        assertEquals(
            "\"energi\"* AND \"terbarukan\"*",
            FtsQuery.from("Energi terbarukan")!!.matchExpression,
        )
    }

    @Test fun compatibilityCharactersAreNormalized() {
        assertEquals("\"field\"* AND \"atla\"*", FtsQuery.from("Ｆｉｅｌｄ Atlas")!!.matchExpression)
    }

    @Test fun oneCharacterTermsAreRemoved() {
        assertEquals("\"bb\"*", FtsQuery.from("a BB 1")!!.matchExpression)
    }

    @Test fun iesWordsKeepAnFtsCompatiblePrefix() {
        assertEquals("\"alli\"*", FtsQuery.from("allies")!!.matchExpression)
    }

    @Test fun naturalLanguageStopWordsAreRemovedAndTermsDeduplicated() {
        val query = FtsQuery.from("Why are seasons not caused by Earth Sun distance seasons")!!
        assertEquals(
            "\"season\"* AND \"caus\"* AND \"earth\"* AND \"sun\"* AND \"distance\"*",
            query.matchExpression,
        )
    }

    @Test fun inputIsCappedAt512CodePointsWithoutSplittingSurrogates() {
        val input = "word " + "😀".repeat(507) + " excluded"
        val query = FtsQuery.from(input)!!
        assertEquals("\"word\"*", query.matchExpression)
        assertTrue(query.normalizedInput.codePointCount(0, query.normalizedInput.length) <= 512)
    }
}
