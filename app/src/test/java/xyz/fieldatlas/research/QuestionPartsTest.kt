package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class QuestionPartsTest {
    @Test fun explicitClausesKeepTheirOwnTermsAcrossSourceWrappersAndSeparators() {
        for (separator in listOf(", and ", " and ", "? ", "; ")) {
            val q = "How does the projector form a beam${separator}what limits wet storage?"
            val parts = QuestionParts.terms(q)
            assertEquals(q, 2, parts.size)
            assertTrue(parts[0].containsAll(listOf("projector", "beam")))
            assertTrue(parts[1].containsAll(listOf("wet", "storage")))
            assertEquals(parts, QuestionParts.terms("According to the saved sources, $q"))
        }
    }

    @Test fun quotedQuestionsAndEllipticalOrUnboundedRequestsKeepOrdinaryPacking() {
        for (q in listOf("Compare battery capacity and power.", "Explain how this works and why?",
            "Explain the title \"What bends and why does glass break?\"",
            "Explain the title `What bends and why does glass break?`",
            "Explain the title “What bends and why does glass break?”",
            "Explain light (and why mirrors reflect).", "Explain \"unclosed and why mirrors reflect?",
            "Explain light (and why mirrors reflect?", "Explain light ) and why mirrors reflect?",
            "What powers lamps and why do mirrors reflect and how do magnets attract and when does glass melt?",
            "Explain " + "long ".repeat(500) + "and how do mirrors reflect?")) {
            assertTrue(q, QuestionParts.terms(q).isEmpty())
        }
    }

    @Test fun threeExplicitPartsAreBoundedWithoutSplittingAnOrdinaryComparison() {
        val parts = QuestionParts.terms("Compare solar power and stored energy, and what limits discharge, and how does insulation affect losses?")
        assertEquals(3, parts.size)
        assertTrue(parts.first().containsAll(listOf("solar", "power", "stored", "energy")))
    }

    @Test fun anchorsNeedTwoTermsAndDoNotPromoteTitleOnlyOrInventMissingSupport() {
        val parts = QuestionParts.terms("How does a projector form a beam, and what limits wet storage?")
        val texts = listOf("Projector form beam.", "Unrelated beam.", "Wet storage is not established.")
        assertEquals(listOf(texts[0], texts[2]), QuestionParts.anchors(parts, texts) { it })
        assertEquals(listOf(texts[0]), QuestionParts.anchors(parts, texts.take(2)) { it })
        assertTrue(QuestionParts.anchors(parts, listOf("Only storage.")) { it }.isEmpty())
    }
}
