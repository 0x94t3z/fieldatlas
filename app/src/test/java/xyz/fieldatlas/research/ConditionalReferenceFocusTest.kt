package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionalReferenceFocusTest {
    private val question = "Why can an enclosure leak even when its sealing membrane has a high pressure-resistance rating?"
    private fun passage(text: String, title: String = "Sealing membrane") =
        Evidence("wp-900-0000", "wp-900-0000:0002", "$title — Overview", "saved-reference", text, 0.0)
    private val overview = passage("A sealing membrane is a material used to close an enclosure.")
    private val detail = "An enclosure leaked through a joint. The pressure-resistance rating tests only the material, not the complete enclosure. " +
        "However, the comparison does not establish the performance of every enclosure design."

    @Test fun explicitContrastUsesBothOutcomeAndConditionBeyondTheTitle() {
        val focus = ConditionalReferenceFocus.from(question, listOf(overview))!!
        assertEquals(listOf("enclosure", "leak"), focus.outcomeTerms)
        assertEquals(listOf("pressure", "resistance", "rating"), focus.conditionTerms)
        assertTrue(focus.matches(passage(detail), detail))
        assertFalse(focus.matches(overview, overview.text))
        val outcomeOnly = "An enclosure can leak, but this passage does not describe pressure tests."
        assertFalse(focus.matches(passage(outcomeOnly), outcomeOnly))
        val conditionOnly = "The pressure-resistance rating tests only material samples."
        assertFalse(focus.matches(passage(conditionOnly), conditionOnly))
    }

    @Test fun ordinaryQuestionsPersonalTasksAndMultipleQuestionsKeepTheirExistingRoute() {
        for (asked in listOf("How does a sealing membrane work?", "What is a sealing membrane?",
            "Can an enclosure leak with a sealing membrane?", "Why can an enclosure leak?",
            "How can I install a sealing membrane despite a pressure-resistance rating?",
            "$question What should I buy?",
            "Why can an enclosure leak even when its sealing membrane works despite testing?")) {
            assertNull(asked, ConditionalReferenceFocus.from(asked, listOf(overview)))
        }
    }

    @Test fun namedArticleAndNonTitleTermsAreRequiredButMissingDetailStillReturnsAFocus() {
        assertNotNull(ConditionalReferenceFocus.from(question, listOf(overview)))
        assertNull(ConditionalReferenceFocus.from(question, emptyList()))
        assertNull(ConditionalReferenceFocus.from(question, listOf(overview.copy(documentId = "wvs-900-0000"))))
        assertNull(ConditionalReferenceFocus.from(question, listOf(passage("Pressure is force over area.", "Pressure"))))
        assertNull(ConditionalReferenceFocus.from("Why can an enclosure leak despite its sealing membrane?", listOf(overview)))
        assertNull(ConditionalReferenceFocus.from("Why can sealing membrane fail despite its rating?", listOf(passage("Other", "Sealing membranes"))))
    }

    @Test fun sourceOnlyWrappersAndEquivalentContrastMarkersPreserveScope() {
        for (asked in listOf("According to the saved sources, $question",
            question.replace("even when", "although"), question.replace("even when", "despite"),
            question.replace("even when", "in spite of"))) {
            val focus = ConditionalReferenceFocus.from(asked, listOf(overview))!!
            assertTrue(asked, focus.matches(passage(detail), detail))
        }
    }

    @Test fun onlyConnectedVerbatimUnitsFromTheNamedArticleCanPass() {
        val focus = ConditionalReferenceFocus.from(question, listOf(overview))!!
        assertFalse(focus.matches(passage(detail, "Weather forecasting"), detail))
        assertFalse(focus.matches(passage(detail), detail.replace("not the complete enclosure", "the complete enclosure")))
        val first = "An enclosure can leak, but this does not explain why."
        val second = "The pressure-resistance rating only concerns another component."
        val full = "$first Unrelated connecting passage that must not be silently removed. $second"
        assertFalse(focus.matches(passage(full), "$first $second"))
        val source = "Historical material.\n\n$detail\n\nUnrelated closing material."
        assertTrue(focus.matches(passage(source), "… $detail …"))
    }

    @Test fun ordinaryInflectionsDoNotBecomeArbitraryPrefixMatches() {
        val focus = ConditionalReferenceFocus.from(question, listOf(overview))!!
        val plural = detail.replace("rating", "ratings")
        assertTrue(focus.matches(passage(plural), plural))
        val misleading = detail.replace("leaked", "leakageproof").replace("rating", "rations")
        assertFalse(focus.matches(passage(misleading), misleading))
        val noLimitation = "An enclosure leaked. The pressure-resistance rating describes a property."
        assertFalse(focus.matches(passage(noLimitation), noLimitation))
    }

    @Test fun theLexicalBoundaryIsNotTiedToMaterialsOrPerformanceRatings() {
        val asked = "Why can a request stall despite the traffic signal having a green indication?"
        val subject = passage("A traffic signal controls requests in this synthetic example.", "Traffic signal")
        val text = "A request can stall because a green indication applies only to another queue."
        val focus = ConditionalReferenceFocus.from(asked, listOf(subject))!!
        assertTrue(focus.matches(passage(text, "Traffic signal"), text))
    }
}
