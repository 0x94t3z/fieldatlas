package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicGuidesTest {
    private fun section(title: String, text: String, source: String = "https://en.wikivoyage.org/w/index.php?oldid=1") =
        Evidence("wvs-${title.substringBefore(" — ")}", "wvs-$title:0", title, source, text, 0.0)

    private val lost = section("Passport — If you lose it while travelling",
        "Losing a passport abroad is stressful but fixable. Report the loss to the police and ask your embassy for an emergency travel document.")
    private val apply = section("Passport — Applying for one", "Apply at least two months before a trip; bring two photos and proof of citizenship.")
    private val visas = section("Passport — Visas", "Some countries stamp a visa into a passport page.")

    @Test fun theQuestionNamesTheTopicNotThePlace() {
        val names = TopicGuides.names("I lost my passport while travelling in Spain. What should I do?")
        assertTrue(names.toString(), "passport" in names)
        assertFalse("a capitalized name is a place", "Spain" in names)
        assertTrue(TopicGuides.names("How can I reduce jet lag after flying from New York to Singapore?").let { "jet lag" in it && "New York" !in it })
        assertTrue("the first word of a question is no place", "Hostels" in TopicGuides.names("Hostels: are they safe for families?"))
    }

    @Test fun theSectionSharingTheQuestionsWordsIsPicked() {
        val picked = TopicGuides.pick("I lost my passport while travelling in Spain. What should I do?", "passport", listOf(apply, visas, lost), 2)
        assertEquals("Passport — If you lose it while travelling", picked.first().title)
        val encyclopedia = section("Passport — Overview", "A passport is a travel document.", "https://simple.wikipedia.org/w/index.php?oldid=2")
        assertEquals("an encyclopedia article is not a travel guide", emptyList<Evidence>(),
            TopicGuides.pick("I lost my passport", "passport", listOf(encyclopedia), 2))
    }

    @Test fun aTopicGuideLeadsWhenNoPlaceGuideAnswers() = runBlocking {
        val asked = ArrayList<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun sections(article: String, limit: Int): List<Evidence> {
                asked += article
                return if (article.equals("passport", ignoreCase = true)) listOf(apply, visas, lost) else emptyList()
            }
        }
        val model = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("must not be generated"))
        val events = ResearchOrchestrator(retriever, model).research("I lost my passport while travelling in Spain. What should I do?").toList()
        assertTrue(asked.toString(), "passport" in asked)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().last().evidence.map { it.title }
        assertEquals("Passport — If you lose it while travelling", sources.first())
        // The guide's advice is the answer, quoted in full, without the model.
        val answer = events.filterIsInstance<ResearchEvent.Token>().single().text
        assertTrue(answer, "> Losing a passport abroad is stressful but fixable. Report the loss to the police and ask your embassy" in answer)
        assertTrue(answer, answer.endsWith("_Quoted from the saved travel guide, without a model summary; tap the citation to read the whole section._"))
        // The fake model's only text may plan a search; it never reaches the answer.
        assertFalse(answer, "must not be generated" in answer)
    }

    @Test fun aKindOfPlaceIsLeftToThePlacesListings() = runBlocking {
        val asked = ArrayList<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun sections(article: String, limit: Int): List<Evidence> { asked += article; return emptyList() }
        }
        ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("x"))).research("What museums are worth visiting in Amsterdam?").toList()
        assertFalse(asked.toString(), asked.any { it.equals("museums", ignoreCase = true) })
    }

    @Test fun aSubsectionDoesNotInheritItsParentsHeadingAndNamesSayWhereNotWhat() {
        val parent = section("Passport — If you lose it while travelling",
            "Losing a passport is stressful but fixable. Report it to the police and ask your embassy for an emergency travel document.")
        val child = section("Passport — If you lose it while travelling > Copies",
            "Copies help most when travelling in areas where the risk of loss is high.")
        val question = "I lost my passport while travelling. What should I do?"
        val picked = TopicGuides.pick(question, "passport", listOf(child, parent), 2)
        assertEquals(parent.title, picked.first().title)
        val lead = TravelSections.lead(question, "Passport", picked.mapIndexed { i, e -> PromptSource("S${i + 1}", e) })
        assertTrue(lead.toString(), lead!!.text.startsWith("Losing a passport is stressful"))

        val background = section("Jet lag — Background", "A flight from Lisbon to New York crosses five time zones, so a traveller often feels it.")
        val before = section("Jet lag — Before you fly", "To reduce the effect, shift your sleep by an hour a day for a few days before flying.")
        assertEquals(before.title, TopicGuides.pick("How can I reduce jet lag after flying from New York to Singapore?", "jet lag",
            listOf(background, before), 2).first().title)
    }
}
