package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class TravelResumeRoutingTest {
    @Test fun generalShopHoursAreGuideQuestionsButSpecificShopsRemainLookups() {
        assertFalse(VenueLookup.isPlaceLookup("Will supermarkets be open on Sunday in Frankfurt? I need to buy food."))
        assertFalse(VenueLookup.isPlaceLookup("Are shops closed on public holidays in Berlin?"))
        assertTrue(VenueLookup.isPlaceLookup("Which supermarkets are open on Sunday in Berlin?"))
        assertTrue(VenueLookup.isPlaceLookup("Find a supermarket open now near me"))
        // Asked on the phone on 14 October 2026; it went to the encyclopedia's Lisbon article.
        assertTrue(VenueLookup.isPlaceLookup("Where can I get vegan pastries in Lisbon?"))
    }

    @Test fun aRegionalRailOverviewDoesNotCoverValidation() {
        fun row(title: String, text: String) = Evidence("wvs-$title", "$title:0000", title, "https://en.wikivoyage.org/", text, 0.0)
        val question = "A train ticket from Milan to Varenna: do I have to stamp it before boarding?"
        assertFalse(TravelSections.coversPart(question, "Lombardy", listOf(row("Lombardy — Get around", "Regional trains accept the regional pass."))))
        assertFalse(TravelSections.coversPart(question, "Italy", listOf(row("Italy — Get around > By car > Rules", "Validate your parking ticket before leaving."))))
        assertTrue(TravelSections.coversPart(question, "Italy", listOf(row("Italy — Get around > By train > Rules", "You must validate the ticket before boarding."))))
    }

    @Test fun practicalTopicQuestionsPreferInstructionsToBackground() {
        fun row(title: String, text: String) = Evidence("wvs-$title", "$title:0000", title, "https://en.wikivoyage.org/", text, 0.0)
        val rows = listOf(row("Jet lag — Understand", "Jet lag follows long flights; reducing jet lag matters after flying."),
            row("Jet lag — Cope", "Follow the local daylight and sleep schedule."),
            row("Jet lag — Prepare", "Adjust your sleep schedule before the flight."))
        val selected = TopicGuides.pick("How can I reduce jet lag after a long flight?", "Jet lag", rows, 2)
        assertTrue(selected.toString(), selected.all { !it.title.endsWith("Understand") })
        assertEquals(2, selected.size)
    }

    @Test fun culturalTopicSupplementsMustExplicitlyNameTheDestination() {
        fun row(title: String, text: String) = Evidence("wvs-$title", "$title:0000", title, "https://en.wikivoyage.org/", text, 0.0)
        val rows = listOf(row("Sauna — How to bathe", "In Finland you normally enter nude after a shower."),
            row("Sauna — In Finland", "Sauna etiquette in Finland is relaxed."),
            row("Sauna — In Sweden", "Public sauna etiquette in Sweden normally permits swimwear."))
        val selected = TopicGuides.pickForDestination("What's sauna etiquette in Finland? Do people go naked?", "Sauna", rows, listOf("Finland", "Sauna"), 2)
        assertEquals("Sauna — How to bathe", selected.first().title)
        assertTrue(selected.none { it.title.endsWith("Sweden") })
    }

    @Test fun aRuleAndItsRelevantExceptionAreRetrievedTogether() {
        val first = Evidence("wvs-hours", "wvs-hours:0000", "Germany — Buy > Opening hours", "https://en.wikivoyage.org/", "Shops must close on Sundays.", 0.0)
        val exception = first.copy(chunkId = "wvs-hours:0001", text = "Some supermarkets at railway stations may open on Sundays.")
        val selected = TravelSections.representatives(listOf(exception, first), { if (it == first) 10 else 20 }, 2, 10)
        assertEquals(listOf(first, exception), selected)
        val lead = TravelSections.lead("Will supermarkets be open on Sunday in Germany?", "Germany",
            listOf(PromptSource("S1", first), PromptSource("S2", exception)))!!
        assertTrue(lead.toString(), lead.text.contains("close on Sundays"))
        assertTrue(lead.toString(), lead.more.any { it.second.contains("supermarkets") })
    }

    @Test fun shopOpeningRulesReachTheContainingCountryGuide() = runBlocking {
        fun row(title: String, text: String) = Evidence("wvs-$title", "$title:0000", title, "https://en.wikivoyage.org/", text, 0.0)
        val records = listOf(row("Frankfurt — Overview", "Frankfurt is a city in Germany."),
            row("Germany — Overview", "Germany is a country."),
            row("Frankfurt — Buy", "Frankfurt has many shopping streets."),
            row("Germany — Buy > Opening hours", "Supermarkets and shops are closed on Sundays, except at some railway stations and airports."))
        val saved = object : Retriever {
            override fun hasEligiblePacks(query: String) = false
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) = records.filter {
                it.title.substringBefore(" — ").equals(phrase, true) && sections.any { section -> it.title.substringAfter(" — ").contains(section, true) }
            }.take(limit)
        }
        val events = ResearchOrchestrator(saved, FakeInferenceGateway(listOf("Model explanation")))
            .research("Will supermarkets be open on Sunday in Frankfurt? I need to buy food.").toList()
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.any { it.title == "Germany — Buy > Opening hours" })
    }

    @Test fun culturalDestinationQuestionsActuallyReadTheMatchingTopicGuide() = runBlocking {
        fun row(title: String, text: String) = Evidence("wvs-$title", "$title:0000", title, "https://en.wikivoyage.org/", text, 0.0)
        val records = listOf(row("Finland — Overview", "Finland is a country in northern Europe."),
            row("Finland — Do > Sauna", "Sauna etiquette is relaxed. People gather for quiet conversation."),
            row("Sauna — Overview", "A sauna is a bathing facility."),
            row("Sauna — How to bathe", "In Finland you normally enter nude after a shower."),
            row("Sauna — In Finland", "Public sauna etiquette in Finland is relaxed."))
        val saved = object : Retriever {
            override fun hasEligiblePacks(query: String) = false
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) = records.filter {
                it.title.substringBefore(" — ").equals(phrase, true) && sections.any { section -> it.title.substringAfter(" — ").contains(section, true) }
            }.take(limit)
            override suspend fun sections(article: String, limit: Int) = records.filter { it.title.substringBefore(" — ").equals(article, true) }.take(limit)
        }
        val events = ResearchOrchestrator(saved, FakeInferenceGateway(listOf("Explanation")))
            .research("first time going to a public sauna in finland. what's the etiquette? do people go naked?").toList()
        assertTrue(events.toString(), events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }.any { it.title == "Sauna — How to bathe" })
    }
}
