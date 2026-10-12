package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class CompactGuidanceTest {
    private val question = "How can exercise help older adults maintain balance and strength?"
    private val guidance = Evidence("guide", "guide:0", "Exercise for Older Adults", "fixture://guidance",
        "Exercise and physical activity benefit older adults.\n" +
            "* Strength exercises build muscle strength.\n* Balance exercises help prevent falls.\n" +
            "Start slowly and check which activities are right for your health.", 0.0)
    private fun keep(item: Evidence, q: String = question) =
        EvidenceRelevance.keep(listOf(item), FtsQuery.from(q)!!.terms, question = q)

    @Test fun compactListWithCompleteTitleAndBodyCoverageSurvives() {
        assertEquals(listOf(guidance), keep(guidance))
    }
    @Test fun titleAloneDoesNotEstablishAnswerSupport() {
        assertTrue(keep(guidance.copy(text = "Older adults exercise.\n* Subscribe to our newsletter for balance and strength tips.")).isEmpty())
        assertTrue(keep(guidance.copy(text = "* This page is about exercise.\n* The full explanation is elsewhere. ".repeat(3))).isEmpty())
    }
    @Test fun differentNamedPopulationCannotMatch() {
        assertTrue(keep(guidance.copy(title = "Exercise for Children")).isEmpty())
    }
    @Test fun generalRuleAlsoSupportsNonMedicalGuidance() {
        val q = "How can home insulation help reduce heat loss and energy use?"
        val item = guidance.copy(title = "Home insulation", text =
            "Home insulation reduces heat loss and energy use.\n* Seal suitable gaps.\n* Choose insulation appropriate to the building and retain required ventilation.")
        assertEquals(listOf(item), keep(item, q))
    }
}
