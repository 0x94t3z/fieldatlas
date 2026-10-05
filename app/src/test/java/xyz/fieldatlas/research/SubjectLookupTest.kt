package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectLookupTest {
    @Test fun theSubjectComesBeforeOtherWordsAndHeadNounsLead() {
        val pump = SubjectLookup.phrases("How does a heat pump heat a house?")
        assertTrue(pump.toString(), pump.indexOf("heat pump") in 0 until pump.indexOfFirst { ' ' !in it })
        // "ocean tides": the head noun "tides" (and its singular) is tried before "ocean".
        val tides = SubjectLookup.phrases("What causes ocean tides?")
        assertTrue(tides.toString(), tides.indexOf("tide") in 0 until tides.indexOf("ocean"))
        assertTrue(SubjectLookup.phrases("Why are sunsets red and orange?").contains("sunset"))
        assertTrue(SubjectLookup.leads("How is a hurricane different from a tornado?"))
        assertFalse("a personal how-to is not explained by a definition", SubjectLookup.leads("How do I cross the street in Hanoi?"))
        assertTrue("a history question opens with what the article says of its subject", SubjectLookup.leads("How did the Silk Road shape trade?"))
        assertEquals("electric cars", SubjectLookup.candidates("What are the advantages and drawbacks of electric cars for long road trips?").first().text)
    }

    @Test fun theArticleNamedByTheQuestionLeadsTheAnswer() = runBlocking {
        fun overview(title: String, text: String) = Evidence("wp-$title", "wp-$title:0", "$title — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1", text, 0.0)
        val hurricane = overview("Hurricane", "A hurricane is a large rotating storm that forms over warm ocean water.")
        val tornado = overview("Tornado", "A tornado is a violently rotating column of air that touches the ground.")
        val asked = ArrayList<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> {
                asked += phrase
                return listOf(hurricane, tornado).filter { it.title.startsWith(phrase, ignoreCase = true) }
            }
        }
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Tornadoes are smaller [S2].")))
            .research("How is a hurricane different from a tornado?").toList()
        val lead = events.filterIsInstance<ResearchEvent.Lead>().first().text
        assertTrue(lead, "> A hurricane is a large rotating storm that forms over warm ocean water. [S1]" in lead)
        assertTrue(lead, "> A tornado is a violently rotating column of air that touches the ground. [S2]" in lead)
    }

    @Test fun aWordOfTheSubjectIsNotTheSubject() {
        fun overview(title: String, text: String) = Evidence("wp-$title", "wp-$title:0", "$title — Overview", "local", text, 0.0)
        val stealth = SubjectLookup.candidates("What is a stealth address in Ethereum?")
        assertFalse("only the first noun phrase is the subject", stealth.any { it.text == "Ethereum" })
        val address = stealth.first { it.text == "address" }
        assertFalse(SubjectLookup.accepts(address, overview("Address", "An address is where someone lives or a place can be found.")))
        val tides = SubjectLookup.candidates("What causes ocean tides?").first { it.text == "tide" }
        assertTrue(SubjectLookup.accepts(tides, overview("Tide", "A tide is the rising and falling of the ocean surface caused by the Moon.")))
        val wallet = SubjectLookup.candidates("How does a hardware wallet keep my private key safe?").first { it.text == "wallet" }
        assertFalse("a leather wallet also keeps things", SubjectLookup.accepts(wallet,
            overview("Wallet", "A wallet is a small flat case that a person uses to keep cash and cards in a pocket.")))
        val hurricane = SubjectLookup.candidates("How is a hurricane different from a tornado?", 2).map { it.text }
        assertTrue(hurricane.toString(), "hurricane" in hurricane && "tornado" in hurricane)
    }

    @Test fun anAcronymFindsTheArticleThatListsIt() {
        val gps = Evidence("wp-gps", "wp-gps:2", "Global Positioning System — Overview", "local",
            "The system is run by the United States Space Force.\n\nAlso known as: GPS; Gps", 0.0)
        assertEquals("Global Positioning System", SubjectLookup.aliasOf("GPS", listOf(gps)))
        assertEquals(null, SubjectLookup.aliasOf("GP", listOf(gps)))
    }

    @Test fun aStandardNamedByWhatItIsCalled() {
        val abstract = Evidence("erc-05564-000", "erc-05564-000:0", "ERC-5564: Stealth Addresses — Abstract", "local",
            "ERC-5564: Stealth Addresses\nStatus: Final\n\nThis specification defines a standardized way of creating stealth addresses.", 0.0)
        assertEquals(abstract, SubjectLookup.standardOf("stealth address", listOf(abstract)))
        assertEquals(null, SubjectLookup.standardOf("address", listOf(abstract)))
    }

    @Test fun aStandardIsTriedWhenNoArticleOrAliasNamesTheSubject() = runBlocking {
        val abstract = Evidence("erc-05564-000", "erc-05564-000:0", "ERC-5564: Stealth Addresses — Abstract", "local",
            "ERC-5564: Stealth Addresses\nStatus: Final\n\nThis specification defines a standardized way of creating stealth addresses.", 0.0)
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun aliased(name: String, limit: Int) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> =
                if ("abstract" in sections && phrase == "stealth address") listOf(abstract) else emptyList()
        }
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("They hide the recipient [S1].")))
            .research("What is a stealth address in Ethereum?").toList()
        val lead = events.filterIsInstance<ResearchEvent.Lead>().first().text
        assertTrue(lead, "> This specification defines a standardized way of creating stealth addresses. [S1]" in lead)
    }

    @Test fun aSectionHeadedWithTheSubjectIsTheLastResort() = runBlocking {
        val section = Evidence("wp-cw", "wp-cw:3", "Cryptocurrency wallet — Types > Offline storage > Hardware wallets",
            "https://en.wikipedia.org/w/index.php?oldid=1",
            "A hardware wallet is a small device that keeps the private key inside and signs transactions there. The key never reaches the computer.", 0.0)
        val town = Evidence("wvs-t", "wvs-t:0", "Avel — Get around > Hardware wallets", "https://en.wikivoyage.org/w/index.php?oldid=1", "Shops sell them.", 0.0)
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun aliased(name: String, limit: Int) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> =
                if (sections == listOf(phrase) && phrase == "hardware wallets") listOf(town, section) else emptyList()
        }
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("It signs inside the device [S1].")))
            .research("How does a hardware wallet keep my private key safe?").toList()
        val lead = events.filterIsInstance<ResearchEvent.Lead>().first().text
        assertTrue(lead, "> A hardware wallet is a small device that keeps the private key inside" in lead)
        assertEquals(null, SubjectLookup.sectionOf("wallet", listOf(section)))
    }
}
