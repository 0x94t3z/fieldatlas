package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelSectionsTest {
    @Test fun aPlaceAndAPracticalTopicNameTheGuideSection() {
        assertEquals(TravelSections.Lookup(listOf("Hanoi"), listOf("Stay safe")),
            TravelSections.lookup("How do I cross the street safely in Hanoi traffic?"))
        assertEquals(listOf("Stay healthy"), TravelSections.lookup("Which vaccinations are recommended before travelling to Kenya?")!!.sections)
        assertTrue(TravelSections.lookup("Do I need cash in Japan or can I pay by card everywhere?")!!.let { "Japan" in it.places && "Buy" in it.sections })
        assertNull("no practical topic", TravelSections.lookup("Tell me about the history of Rome"))
        assertNull("no place", TravelSections.lookup("Is it safe to swim after eating?"))
    }

    @Test fun thePlacesOwnSectionLeadsTheEvidence() = runBlocking {
        fun section(title: String, text: String) = Evidence("wvs-$title", "wvs-$title:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val traffic = section("Hanoi — Stay safe > Traffic", "When you leave the curb, look left and right. Take each step deliberately; never walk backwards.")
        val scams = section("Hanoi — Stay safe > Hustles and scams", "Taxi drivers may overcharge.")
        val other = section("Ho Chi Minh City — Stay safe > Traffic", "Traffic is heavy.")
        var asked: Pair<String, List<String>>? = null
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> {
                if (phrase == "Hanoi") asked = phrase to sections
                return if (phrase == "Hanoi") listOf(other, scams, traffic) else emptyList()
            }
        }
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Walk steadily [S1].")))
            .research("How do I cross the street safely in Hanoi traffic?").toList()
        assertEquals("Hanoi" to listOf("Stay safe"), asked)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().last().evidence.map { it.title }
        assertEquals(listOf("Hanoi — Stay safe > Traffic", "Hanoi — Stay safe > Hustles and scams"), sources)
    }
}
