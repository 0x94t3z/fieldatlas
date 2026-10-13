package xyz.fieldatlas.emergency

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.research.ResearchEvent
import xyz.fieldatlas.research.ResearchOrchestrator
import xyz.fieldatlas.research.Retriever

class EmergencyNumbersTest {
    private val book = EmergencyNumbers.book(File("src/main/assets/emergency/numbers.json").readText())
    private fun text(q: String) = EmergencyNumbers.answer(q, book)?.text

    @Test fun aCountrysNumbersComeFromTheSavedTable() {
        val thailand = text("Which numbers do I call in an emergency in Thailand?")!!
        assertTrue(thailand, thailand.startsWith("In **Thailand**: police **191**, ambulance **1669**, fire **199** [S1]."))
        assertTrue(thailand, "Tourist police – 1155" in thailand && "revision 1377218654" in thailand)
        assertTrue(text("What number do I call for an ambulance in Japan?")!!.contains("ambulance **119**"))
        assertTrue(text("What's the emergency number in the USA?")!!.startsWith("In **United States of America**, call **911** for police, ambulance and fire [S1]."))
        assertTrue(text("Which emergency number works in South Africa?")!!.contains("**South Africa**"))
        assertTrue(text("What is the number for the fire brigade in Austria?")!!.contains("**Austria**"))
        assertTrue(text("What are the emergency numbers in Georgia?")!!.contains("In the US state of Georgia, call **911**."))
        assertTrue(text("What is the emergency number in Niger?")!!.contains("**Niger**:"))
    }

    @Test fun theEuropeanUnionHasOneNumber() {
        assertTrue(text("What is the emergency phone number in the European Union?")!!.startsWith("**112** is the standard emergency number across the European Union [S1]."))
    }

    @Test fun otherQuestionsAreNotNumberLookups() {
        assertNull(text("What is the population of Thailand?"))
        assertNull(text("Tell me about emergency medicine in Thailand"))
        assertNull(text("What emergency numbers should I know when hiking?"))
        assertNull(text("What number should I call to book a taxi in Thailand?"))
    }

    @Test fun theAnswerIsCitedAndNeedsNoModel() = runBlocking {
        val inference = FakeInferenceGateway(listOf("must not be generated"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference, emergencyNumbers = { book })
            .research("Which numbers do I call in an emergency in Thailand?").toList()
        assertEquals(0, inference.generateCalls)
        assertEquals("List of emergency telephone numbers — Thailand", events.filterIsInstance<ResearchEvent.Sources>().single().evidence.single().title)
        assertTrue((events.last() as ResearchEvent.Complete).metrics.citedSourceIds == setOf("S1"))
    }

    @Test fun aStateOrRegionIsNotTheCountryInsideItsName() {
        assertTrue(text("What is the emergency number in Sydney, New South Wales?")!!.contains("**Australia**, call **000**"))
        assertTrue(text("What is the emergency number in New Jersey?")!!.contains("**United States of America**, call **911**"))
        assertTrue(text("What is the emergency number in Atlanta, Georgia, USA?")!!.contains("**United States of America**"))
        // North Korea lists only a fire number; "local numbers only" is not shown as a number to dial.
        assertTrue(text("What is the emergency number in North Korea?")!!.let { it.startsWith("In **Democratic People's Republic of Korea**: fire **110 or 8119**") && "police **local" !in it })
        assertTrue(text("What is the emergency number in China?")!!.contains("**People's Republic of China**: police **110**"))
        assertTrue(text("What is the emergency number in Burma?")!!.contains("**Myanmar**"))
        assertTrue(text("What is the emergency number in the DRC?")!!.contains("police **112**, fire **118**"))
    }

    @Test fun theCountryIsWhereThePersonIsNotWhereTheyAreFrom() {
        assertTrue(text("I'm from Australia, travelling in Japan. What's the emergency number?")!!.contains("**Japan**"))
        assertTrue(text("I'm from New Zealand and I'm in Spain. What's the emergency number?")!!.contains("**Spain**"))
        assertTrue(text("I'm from Texas, travelling in Thailand. What's the emergency number?")!!.contains("**Thailand**"))
        assertTrue(text("I live in New York but I'm in Italy now. What is the emergency number?")!!.contains("**Italy**"))
        assertTrue(text("We're from Queensland, on holiday in Bali, Indonesia. What's the emergency number?")!!.contains("**Indonesia**"))
        assertTrue(text("I'm American, travelling to Georgia. What's the emergency number?")!!.contains("**Georgia**"))
        // Only a home country named: where the person is is not known, so no number is given.
        assertNull(text("I'm from Australia. What's the emergency number?"))
    }

    @Test fun twoCountriesAskedAboutGetBoth() {
        val both = text("What's the emergency number in Sweden or Norway?")!!
        assertTrue(both, "**Sweden**" in both && "**Norway**" in both)
        val pair = text("What are the police numbers in France and Spain?")!!
        assertTrue(pair, "**France**" in pair && "**Spain**" in pair)
    }

    @Test fun aPersonsNameIsNotACountry() {
        assertNull(text("My friend Chad is hurt badly. What is the ambulance number?"))
        assertNull(text("My brother Jordan was in a crash, what's the emergency number?"))
        assertTrue(text("What is the emergency number in Chad?")!!.contains("**Chad**"))
    }

    @Test fun theFooterCreditsTheArticleAndItsLicence() {
        val t = text("Which numbers do I call in an emergency in Thailand?")!!
        assertTrue(t, "From the Wikipedia article \"List of emergency telephone numbers\" by Wikipedia contributors (revision 1377218654" in t && "CC BY-SA 4.0" in t)
    }

    @Test fun aFirstAidQuestionKeepsItsGuideWithTheNumberAbove() = runBlocking {
        val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, FakeInferenceGateway(listOf("Start chest compressions [S1].")),
            emergencyGuides = { guides }, emergencyNumbers = { book })
            .research("Someone collapsed and isn't breathing. Which number do I call in Thailand?").toList()
        val lead = events.filterIsInstance<ResearchEvent.Lead>().first().text
        assertTrue(lead, lead.startsWith("Emergency numbers saved for **Thailand**: police **191**, ambulance **1669**, fire **199**."))
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().first().evidence.first().documentId == "emergency:cpr")
    }
}
