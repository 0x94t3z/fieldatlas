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

    @Test fun aGuideAnswersInItsOwnWordsAndAPassageOpeningMidSentenceIsSkipped() {
        fun source(title: String, text: String) = PromptSource("S1", Evidence("wvs-$title", "wvs-$title:1", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0))
        val market = source("Port Avel — Buy",
            "ices are rarely marked in the bazaar stalls.\n\nThe old market sells rugs and brass lamps. Prices are never fixed, so expect to bargain: " +
                "start at a third of the asking price. Walking away politely often brings a better offer.")
        val lead = TravelSections.lead("How does haggling work in the Port Avel markets?", "Port Avel", listOf(market))
        assertTrue(lead.toString(), lead!!.text.startsWith("The old market sells rugs"))
        assertTrue(lead.text, "expect to bargain" in lead.text)
        assertNull("a topic word alone is not enough",
            TravelSections.lead("How does haggling work in the Port Avel markets?", "Port Avel", listOf(source("Port Avel — Buy", "Tipping is not expected. Cash is king."))))
    }

    @Test fun eachSectionGetsAPlaceBeforeASecondPassageOfAny() {
        fun part(title: String, chunk: Int, text: String) = Evidence("wvs-it", "wvs-it:$chunk", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val tickets1 = part("Avelia — Get around > By train > Tickets", 1, "Buy train tickets at the station before boarding; machines take cards.")
        val tickets2 = part("Avelia — Get around > By train > Tickets", 2, "Train tickets bought on board cost more; boarding without one risks a fine.")
        val rules = part("Avelia — Get around > By train > Rules", 3, "Validate a paper ticket in the box on the platform before boarding the train.")
        val picked = TravelSections.pick("Do I need to validate my train ticket before boarding in Avelia?", "Avelia", listOf(tickets1, tickets2, rules), 2)
        assertEquals(listOf("Avelia — Get around > By train > Tickets", "Avelia — Get around > By train > Rules"), picked.map { it.title })
        val lead = TravelSections.lead("Do I need to validate my train ticket before boarding in Avelia?", "Avelia",
            picked.mapIndexed { i, e -> PromptSource("S${i + 1}", e) })
        assertTrue(lead.toString(), lead!!.text.startsWith("Validate a paper ticket"))
    }

    @Test fun aBeachWithoutAGuideIsCoveredByThePlaceItLiesIn() = runBlocking {
        assertEquals("Avelton", TravelSections.parentOf("Port Avel Beach", "Port Avel Beach is a beach in Avelton, on the north coast. It is busy in summer."))
        assertEquals("Avelia", TravelSections.parentOf("Marrow", "The town of Marrow, in northern Avelia, is the gateway to the old temples."))
        assertNull(TravelSections.parentOf("Avel Cove", "Avel Cove is a quiet bay with clear water."))

        fun section(title: String, text: String, source: String = "https://en.wikivoyage.org/w/index.php?oldid=1") =
            Evidence("x-$title", "x-$title:0", title, source, text, 0.0)
        val overview = section("Port Avel Beach — Overview", "Port Avel Beach is a beach in Avelton, on the north coast.", "https://simple.wikipedia.org/w/index.php?oldid=2")
        val beaches = section("Avelton — Stay safe > Beaches", "Swim between the red and yellow flags, which lifeguards set up each morning. A red flag alone means the beach is closed.")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> = when {
                phrase == "Port Avel Beach" && sections == listOf(SubjectLookup.SECTION) -> listOf(overview)
                phrase == "Avelton" && "Stay safe" in sections -> listOf(beaches)
                else -> emptyList()
            }
        }
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Swim between the flags [S1].")))
            .research("Is it safe to swim at Port Avel Beach, and what do the flags mean?").toList()
        assertEquals("Avelton — Stay safe > Beaches", events.filterIsInstance<ResearchEvent.Sources>().last().evidence.first().title)
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, "> Swim between the red and yellow flags, which lifeguards set up each morning. A red flag alone means the beach is closed." in answer)
    }

    @Test fun aQuoteThatPointsBackStartsAtTheSentenceItPointsTo() {
        val beach = PromptSource("S1", Evidence("wvs-av", "wvs-av:0", "Avelton — Stay safe > Beaches", "https://en.wikivoyage.org/w/index.php?oldid=1",
            "Take care on any beach. The main rule is to swim between the yellow and red flags. These flags are set by lifeguards and mark the safest place to swim.", 0.0))
        val lead = TravelSections.lead("Is it safe to swim at Avelton, and what do the flags mean?", "Avelton", listOf(beach))
        assertTrue(lead.toString(), lead!!.text.startsWith("The main rule is to swim between the yellow and red flags. These flags"))
    }

    @Test fun aQuestionWithNumbersKeepsTheModelUnderTheGuideQuote() = runBlocking {
        fun section(title: String, text: String) = Evidence("wvs-$title", "wvs-$title:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val water = section("Avelton — Stay healthy", "Carry plenty of drinking water on every hike in the heat. The trails have no taps.")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                if (phrase == "Avelton" && "Stay healthy" in sections) listOf(water) else emptyList()
        }
        val model = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("About 3 litres for 6 hours."))
        val events = ResearchOrchestrator(retriever, model)
            .research("How much drinking water do I need for a 6-hour hike near Avelton?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, "> Carry plenty of drinking water on every hike in the heat." in answer)
        assertTrue("the worked answer is the model's", "About 3 litres for 6 hours." in answer)
    }
}
