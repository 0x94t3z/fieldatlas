package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldAccentsTest {
    @Test fun latinAccentsFoldToThePlainLettersPeopleType() {
        assertEquals("Ha Long Bay", QuestionText.foldAccents("Hạ Long Bay"))
        assertEquals("Da Nang", QuestionText.foldAccents("Đà Nẵng"))
        assertEquals("reykjavik", QuestionText.foldAccents("reykjavík"))
        assertEquals("Tromso Lodz Strasse", QuestionText.foldAccents("Tromsø Łódź Straße"))
    }

    @Test fun otherScriptsAndPlainTextAreLeftAlone() {
        assertEquals("ありがとうございます", QuestionText.foldAccents("ありがとうございます"))
        assertEquals("Москва", QuestionText.foldAccents("Москва"))
        assertEquals("plain text", QuestionText.foldAccents("plain text"))
    }

    @Test fun aSubjectTypedWithoutAccentsFindsItsOverview() {
        val overview = Evidence("swp-1", "swp-1:0", "Hạ Long Bay — Overview", "Local", "Hạ Long Bay is a bay in Vietnam.", -1.0)
        assertEquals(overview, SubjectLookup.overviewOf("ha long bay", listOf(overview)))
        assertEquals(1, EvidenceRelevance.termHits("Reykjavík — Pharmacy", listOf("reykjavik")))
    }
}
