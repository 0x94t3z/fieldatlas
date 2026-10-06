package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQueryTest {
    @Test fun `auxiliary verbs do not become prefix queries`() {
        assertEquals(listOf("hemispheres", "opposite", "seasons"),
            FtsQuery.from("Why do hemispheres have opposite seasons?")!!.terms)
        assertNull(FtsQuery.from("have has had"))
    }
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
        assertEquals("(\"bb\" OR \"bbs\")", FtsQuery.from("a BB 1")!!.matchExpression)
    }

    @Test fun iesWordsKeepAnFtsCompatiblePrefix() {
        assertEquals("\"alli\"*", FtsQuery.from("allies")!!.matchExpression)
    }

    @Test fun naturalLanguageStopWordsAreRemovedAndTermsDeduplicated() {
        val query = FtsQuery.from("Why are seasons not caused by Earth Sun distance seasons")!!
        assertEquals(
            "\"season\"* AND \"caus\"* AND \"earth\"* AND (\"sun\" OR \"suns\" OR \"suning\" OR \"suned\" OR \"sunning\" OR \"sunned\") AND \"distance\"*",
            query.matchExpression,
        )
    }

    @Test fun inputIsCappedAt512CodePointsWithoutSplittingSurrogates() {
        val input = "word " + "😀".repeat(507) + " excluded"
        val query = FtsQuery.from(input)!!
        assertEquals("\"word\"*", query.matchExpression)
        assertTrue(query.normalizedInput.codePointCount(0, query.normalizedInput.length) <= 512)
    }

    @Test fun shortTermsMatchWholeWordsNotPrefixes() {
        // "mev"* matched "mevalonate" and "kem"* the town of Kemer for ML-KEM.
        assertEquals("(\"mev\" OR \"mevs\" OR \"meving\" OR \"meved\" OR \"mevving\" OR \"mevved\") AND \"proposer\"* AND \"builder\"*",
            FtsQuery.from("What is MEV and proposer builder?")!!.matchExpression)
        assertEquals("(\"ml\" OR \"mls\") AND (\"kem\" OR \"kems\" OR \"keming\" OR \"kemed\" OR \"kemming\" OR \"kemmed\")", FtsQuery.from("ML-KEM")!!.matchExpression)
        assertEquals("(\"bus\" OR \"buses\" OR \"busing\" OR \"bused\" OR \"bussing\" OR \"bussed\")", FtsQuery.from("bus")!!.matchExpression)
        // "7702"* matched the postcode 77020.
        assertEquals("(\"eip\" OR \"eips\" OR \"eiping\" OR \"eiped\") AND \"7702\"", FtsQuery.from("EIP-7702")!!.matchExpression)
    }

    @Test fun aShortStemMatchesWholeWordsAndContractionsLeaveNoFragments() {
        // "ear"* found earthquakes and Earth Day for a question about ears; "we're" left the term "re".
        val ears = FtsQuery.from("why do my ears pop on planes, and why does it hurt more when we're landing?")!!
        assertEquals(listOf("ears", "pop", "planes", "hurt", "more", "landing"), ears.terms)
        assertTrue(ears.matchExpression, ears.matchExpression.startsWith("(\"ears\" OR \"ear\" OR \"earing\" OR \"eared\") AND "))
        assertFalse(ears.matchExpression.contains("\"ear\"*"))
        assertEquals("(\"ties\" OR \"tie\" OR \"tied\" OR \"tying\")", FtsQuery.from("ties")!!.matchExpression)
        assertEquals("a longer stem still matches as a prefix", "\"season\"*", FtsQuery.from("seasons")!!.matchExpression)
        assertEquals(null, FtsQuery.from("can't won't I'll they've it's we're I'd I'm"))
        assertEquals(listOf("japan", "population"), FtsQuery.from("Japan's population")!!.terms)
        assertEquals(listOf("rain"), FtsQuery.from("Won't it rain?")!!.terms)
    }
}
