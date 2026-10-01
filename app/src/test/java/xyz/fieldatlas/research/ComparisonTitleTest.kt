package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonTitleTest {
    @Test fun missingComparisonSubjectUsesPassageNotTitleAndDoesNotInventCoverage() {
        val question = "Compare batteries and capacitors."
        val passage = Evidence("doc", "chunk", "Batteries and capacitors", "local",
            "Batteries store energy chemically.", 0.0)
        assertEquals(listOf("capacitors"), EvidenceRelevance.missingComparisonSubjects(question, listOf(passage)))
        assertEquals(emptyList<String>(), EvidenceRelevance.missingComparisonSubjects("Explain batteries", listOf(passage)))
        assertEquals(listOf("batteries", "capacitors"), EvidenceRelevance.missingComparisonSubjects(question, emptyList()))
    }

    @Test fun introductionsLeadOnlyForExplicitComparisonSubjects() {
        val subjects = EvidenceRelevance.comparisonTerms("How does a database differ from a spreadsheet, and when would each be useful?")
        assertEquals(0, FtsRetriever.comparisonTitlePriority("Database — Overview", "database", subjects))
        assertEquals(0, FtsRetriever.comparisonTitlePriority("Spreadsheet — Overview", "spreadsheet", subjects))
        assertEquals(1, FtsRetriever.comparisonTitlePriority("Database — Types", "database", subjects))
        assertEquals(1, FtsRetriever.comparisonTitlePriority("Database theory — Overview", "database", subjects))
        assertEquals(1, FtsRetriever.comparisonTitlePriority("Energy — Overview", "energy", subjects))
        assertEquals(1, FtsRetriever.comparisonTitlePriority("Database — Overview", "database", emptyList()))
    }
}
