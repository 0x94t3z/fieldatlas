package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLeadTest {
    private fun row(id: String, title: String, text: String) = Evidence(id, "$id:0", title, "fixture://$id", text, 0.0)

    private val question = "is avelton old town safe to walk at night"
    private val city = row("city", "Avelton — Overview", "Avelton is the second-largest city in Velland, on the Varna river, with a walled old town.")
    private val town = row("town", "Town — Overview", "A town is a human settlement larger than a village but smaller than a city.")
    private val night = row("night", "Night — Overview", "Night is the period of darkness between sunset and sunrise.")
    private val safe = row("safe", "Avelton — Stay safe",
        "The old town of Avelton is safe to walk at night; watch for pickpockets on the night trams and around the station.")

    @Test fun theSearchsBestPassageComesBeforeALookupsFurtherSubjects() {
        // A phone reads two or three passages: the other subjects the question's words named must not fill them first.
        val packed = PromptBuilder.buildKnowledge(question, listOf(city, town, night, safe), listOf(city, town, night), emptyList(),
            8192, 1.0, searchLead = safe)
        assertEquals(listOf(city, safe), packed.sources.map { it.evidence }.take(2))
    }

    @Test fun aSingleSubjectKeepsItsOwnDetailsAhead() {
        val opera = row("opera", "Velland Opera House — Overview", "The Velland Opera House is a performing arts centre on the harbour.")
        val details = row("details", "Velland Opera House — Details", "It was designed by the architect Mira Holt, who won the 1957 competition.")
        val city = row("velland", "Velland — Overview", "Velland is the largest city on the east coast.")
        val packed = PromptBuilder.buildKnowledge("Who designed the Velland Opera House?", listOf(opera, city), listOf(opera), listOf(details),
            8192, 1.0, searchLead = city)
        assertEquals(listOf(opera, details), packed.sources.map { it.evidence }.take(2))
    }

    @Test fun aComparisonKeepsBothSides() {
        val neral = row("neral", "Neral — Overview", "Neral is a solid test material that is denser than water.")
        val velin = row("velin", "Velin — Overview", "Velin is a liquid test material that is lighter than water.")
        val other = row("other", "Laboratory — Overview", "A laboratory is a room for experiments.")
        val packed = PromptBuilder.buildKnowledge("Neral vs Velin: which one is denser?", listOf(neral, velin, other), listOf(neral, velin),
            emptyList(), 8192, 1.0, searchLead = other)
        assertEquals(listOf(neral, velin), packed.sources.map { it.evidence }.take(2))
    }
}
