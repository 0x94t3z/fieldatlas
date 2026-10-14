package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.text.MarkdownLiteral

class SubjectLookupTest {
    @Test fun aComparisonReadsTheArticleOfEachSubject() {
        // "Tell them apart" and "vs" ask for both sides, read from their own articles.
        assertTrue(SubjectLookup.leads("Cheetah vs leopard: how do you tell them apart on a safari?"))
        assertTrue(SubjectLookup.leads("How do you tell a crocodile and an alligator apart?"))
        assertTrue(SubjectLookup.subjectsWanted("How do you tell a crocodile and an alligator apart?") >= 2)
        // A comparison of facts stays a lookup of each side ("Australia" and "Europe", not "Size").
        assertTrue(SubjectLookup.looksUp("how big is australia compared to europe?"))
    }

    @Test fun aCompoundWordMentionsTheWordItHolds() {
        // "phone touchscreen": the article "Touchscreen" says "smartphones", which mentions the phone.
        val article = Evidence("enw-touch", "enw-touch:0000", "Touchscreen — Overview", "https://en.wikipedia.org/wiki/Touchscreen",
            "A touchscreen is a type of display used in smartphones, tablets and laptops.", 0.0)
        assertTrue(SubjectLookup.accepts(SubjectLookup.Candidate("touchscreen", listOf("phone"), "phone"), article))
        // A word the article never holds, whole or inside another, still rules it out.
        assertFalse(SubjectLookup.accepts(SubjectLookup.Candidate("touchscreen", listOf("glove"), "glove"), article))
    }

    @Test fun modalWhyQuestionsReachExplanatoryArticleDepth() {
        for (auxiliary in listOf("can", "could", "would", "should", "will", "may", "might", "must")) {
            val question = "Why $auxiliary an enclosure leak despite its sealing membrane?"
            assertTrue(question, SubjectLookup.explains(question))
            assertTrue(question, SubjectLookup.leads(question))
            assertFalse(question, SubjectLookup.looksUp(question))
            val candidate = SubjectLookup.candidates(question).first()
            assertEquals(question, "enclosure", candidate.text)
            assertTrue(question, candidate.rest.isEmpty())
        }
        assertTrue(SubjectLookup.explains("According to the saved sources, why can an enclosure leak?"))
        assertFalse(SubjectLookup.explains("How can I install a sealing membrane?"))
        assertFalse(SubjectLookup.explains("Where can a sealing membrane be purchased?"))
    }

    @Test fun usingOnlySavedSourcesKeepsANumberedEventLookupReachable() {
        val plain = "where was the 16th Summit of the Non-Aligned Movement held?"
        val scoped = "Using only my saved sources, " + plain
        assertEquals(SubjectLookup.looksUp(plain), SubjectLookup.looksUp(scoped))
        val expected = SubjectLookup.candidates(plain, lookup = true)
        assertTrue(expected.toString(), expected.any { it.text == "16th Summit of the Non-Aligned Movement" })
        assertEquals(expected, SubjectLookup.candidates(scoped, lookup = true))
        assertEquals(FtsQuery.from(plain)?.terms, FtsQuery.from(scoped)?.terms)
    }

    @Test fun auxiliaryPredicatesDoNotSpendTheSubjectPhraseBudgetOnObjects() {
        for ((question, expected) in listOf(
            "How do lichens obtain nutrients?" to "lichen",
            "How does a siphon move water?" to "siphon",
            "Why does induction heating warm a metal object?" to "induction heating",
            "How do roots absorb minerals?" to "root",
            "How does a qanat supply water?" to "qanat",
            "How do warm fronts move across a continent?" to "warm front")) {
            val found = SubjectLookup.candidates(question)
            assertTrue("$question: $found", found.any { it.text == expected && it.rest.isEmpty() })
            assertEquals("$question: $found", expected, found.first().text.removeSuffix("s"))
        }
        assertTrue(SubjectLookup.candidates("What causes ocean tides?").first().text.startsWith("ocean tide"))
    }

    @Test fun hyphenatedAliasMatchesOnlyTheSameWords() {
        val voting = Evidence("swp-1-0000", "swp-1-0000:0001", "Plurality voting — Overview", "Local",
            "Also known as: First past the post; FPTP", 0.0)
        assertEquals("Plurality voting", SubjectLookup.aliasOf("first-past-the-post", listOf(voting)))
        assertEquals("Plurality voting", SubjectLookup.aliasOf("first past the post", listOf(voting)))
        assertNull(SubjectLookup.aliasOf("post", listOf(voting)))
        assertNull(SubjectLookup.aliasOf("first-past-the-postal", listOf(voting)))
    }
    @Test fun sourceScopePrefixDoesNotSpendArticleLookupSlots() {
        for (prefix in listOf("According to the saved sources, ", "Based on my notes: ",
            "According to the saved travel guides, ", "According to local sources ")) {
            val plain = "what causes ocean tides?"
            assertEquals(prefix, SubjectLookup.phrases(plain), SubjectLookup.phrases(prefix + plain))
        }
        val question = "which hash function does Ethereum use?"
        assertEquals(SubjectLookup.phrases(question),
            SubjectLookup.phrases("According to the saved sources, " + question))
    }

    @Test fun sourceScopePreservesDiscoveryRoutingAndComparisonSubjects() {
        for (plain in listOf("what causes ocean tides?", "which hash function does Ethereum use?",
            "how does evaporation differ from boiling?", "compare hurricanes and tornadoes")) {
            val scoped = "According to the saved sources, " + plain
            assertEquals(plain, SubjectLookup.leads(plain), SubjectLookup.leads(scoped))
            assertEquals(plain, SubjectLookup.looksUp(plain), SubjectLookup.looksUp(scoped))
            assertEquals(plain, SubjectLookup.subjectsWanted(plain), SubjectLookup.subjectsWanted(scoped))
            val lookup = SubjectLookup.looksUp(plain)
            assertEquals(plain, SubjectLookup.candidates(plain, SubjectLookup.subjectsWanted(plain, lookup), lookup),
                SubjectLookup.candidates(scoped, SubjectLookup.subjectsWanted(scoped, lookup), lookup))
        }
    }

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
        val model = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Hurricanes form over warm oceans [S1]; tornadoes are rotating columns touching the ground [S2]."))
        var observed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever, model, onEvidencePacked = { observed = it })
            .research("How is a hurricane different from a tornado?").toList()
        assertEquals(listOf(hurricane, tornado), observed!!.sources.map { it.evidence })
        val answer = MarkdownLiteral.decode(events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertTrue(answer, "Hurricanes form over warm oceans [S1]" in answer)
        assertTrue(answer, "tornadoes are rotating columns touching the ground [S2]" in answer)
        assertFalse(answer.contains("Tornadoes are smaller"))
        assertEquals(1, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
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
        var observed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("ERC-5564 standardizes how stealth addresses are created [S1].")),
            onEvidencePacked = { observed = it })
            .research("What is a stealth address in Ethereum?").toList()
        assertEquals(listOf(abstract), observed!!.sources.map { it.evidence })
        val answer = MarkdownLiteral.decode(events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertEquals("ERC-5564 standardizes how stealth addresses are created [S1].", answer)
        assertTrue(answer.contains("[S1]"))
        assertFalse(answer.contains("They hide the recipient"))
        assertEquals(1, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
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
        var observed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("It signs inside the device [S1].")),
            onEvidencePacked = { observed = it })
            .research("How does a hardware wallet keep my private key safe?").toList()
        assertEquals(listOf(section), observed!!.sources.map { it.evidence })
        val answer = MarkdownLiteral.decode(events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertEquals("It signs inside the device [S1].", answer)
        assertEquals(1, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
        assertEquals(null, SubjectLookup.sectionOf("wallet", listOf(section)))
    }

    @Test fun aFactualLookupNamesWhatItIsAbout() {
        assertTrue(SubjectLookup.looksUp("Who won the First World War?"))
        assertTrue(SubjectLookup.looksUp("What is the population of Canada?"))
        assertTrue(SubjectLookup.looksUp("tell me about taiwan"))
        assertTrue(SubjectLookup.looksUp("are lions or tigers heavier?"))
        assertFalse("an explanation leads with its one subject instead", SubjectLookup.looksUp("How does a heat pump heat a house?"))
        assertFalse(SubjectLookup.looksUp("What should I eat to live a long and healthy life?"))
        fun lookup(question: String) = SubjectLookup.candidates(question, 3, lookup = true).map { it.text }
        assertEquals("First World War", lookup("Who won the First World War?").first())
        assertEquals(listOf("Canada"), lookup("What is the population of Canada?"))
        assertEquals(listOf("world population"), lookup("What is the world population in 2026?"))
        assertEquals("2018 FIFA World Cup", lookup("Which country won the 2018 FIFA World Cup?").first())
        val japan = lookup("is Japan's population larger than Nigeria's?")
        assertTrue(japan.toString(), "Japan" in japan && "Nigeria" in japan)
        val cats = lookup("are lions or tigers heavier?")
        assertTrue(cats.toString(), "lion" in cats && "tiger" in cats)
        assertTrue(lookup("What is the tallest animal in the world?").isEmpty())
        assertFalse(lookup("Is it safe to drink the tap water?").contains("safe"))
        val war = Evidence("swp-9-0000:0000", "swp-9-0000:0000", "Thirty Years' War — Overview", "local", "A war in central Europe.", 0.0)
        assertEquals(war, SubjectLookup.overviewOf("Thirty Years War", listOf(war)))
        // A name with a possessive is tried as written first.
        assertEquals("Ohm's law", SubjectLookup.candidates("How does Ohm's law work?").first().text)
        assertEquals("tide", SubjectLookup.candidates("What's a tide and why does it happen?").first().text)
        val ohm = Evidence("swp-10-0000:0000", "swp-10-0000:0000", "Ohm's law — Overview", "local", "Ohm's law says…", 0.0)
        assertEquals(ohm, SubjectLookup.overviewOf("Ohm's law", listOf(ohm)))
    }

    @Test fun aLookupReadsMoreOfEachArticleItNames() = runBlocking {
        fun row(id: String, title: String, text: String) =
            Evidence(id.substringBefore(':'), id, title, "https://en.wikipedia.org/w/index.php?oldid=1", text, 0.0)
        val lion = row("swp-1-0000:0000", "Lion — Overview", "The lion is a large cat of the genus Panthera native to Africa and India.")
        val tiger = row("swp-2-0000:0000", "Tiger — Overview", "The tiger is the largest living cat species and a member of the genus Panthera.")
        val articles = mapOf(
            "Lion" to listOf(lion, row("enw-1-0003:0000", "Lion — Description", "Adult male lions weigh 150 to 250 kg.")),
            "Tiger" to listOf(tiger, row("enw-2-0002:0000", "Tiger — Characteristics", "Male Bengal tigers weigh 200 to 260 kg.")),
        )
        // Whole articles read for depth; a candidate with no overview ("lions") is also probed for one.
        val read = ArrayList<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                listOf(lion, tiger).filter { it.title.startsWith("$phrase — ", ignoreCase = true) }
            override suspend fun article(article: String, limit: Int): List<Evidence> {
                if (limit == ArticleDepth.ARTICLE_ROWS) read += article
                return articles[article].orEmpty().take(limit)
            }
        }
        val gateway = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Tigers are heavier [S2]."))
        ResearchOrchestrator(retriever, gateway, articleDepth = true).research("are lions or tigers heavier?").toList()
        assertEquals(listOf("Lion", "Tiger"), read)
        val prompt = gateway.prompts.last().first
        assertTrue(prompt, "Adult male lions weigh 150 to 250 kg." in prompt && "Male Bengal tigers weigh 200 to 260 kg." in prompt)
        // Without article depth a lookup is searched as before.
        read.clear()
        val plain = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Tigers."))
        ResearchOrchestrator(retriever, plain).research("are lions or tigers heavier?").toList()
        assertTrue(read.isEmpty())
        assertTrue(plain.prompts.none { "Bengal" in it.first })
    }

    @Test fun aChoiceBetweenOptionsLeadsWithBoth() {
        assertTrue(SubjectLookup.leads("DEET vs picaridin, which mosquito repellent is better for a trip to the tropics?"))
        assertTrue(SubjectLookup.leads("ibuprofen or paracetamol for a fever and headache, what's the difference and which should I take?"))
        assertTrue(SubjectLookup.leads("whats the actual difference between a hurricane, a typhoon and a cyclone"))
        assertTrue(SubjectLookup.leads("whats the difference between a seed phrase and a private key"))
        assertTrue(SubjectLookup.leads("Should I use a debit card or a credit card when travelling abroad?"))
        assertFalse("a choice is not an explanation by its wording", SubjectLookup.explains("Should I use a debit card or a credit card when travelling abroad?"))
        assertFalse("\"or in\" joins no second option", SubjectLookup.leads("The card machine asks if I want to pay in euros or in my home currency. Which one is cheaper?"))
        assertFalse(SubjectLookup.leads("Do I need a visa or can I get one on arrival?"))
        // A factual comparison stays a lookup, which reads both articles anyway.
        assertFalse(SubjectLookup.leads("are lions or tigers heavier?"))
        assertTrue(SubjectLookup.looksUp("are lions or tigers heavier?"))
        assertFalse(SubjectLookup.looksUp("Should I use a debit card or a credit card when travelling abroad?"))
    }

    @Test fun aComparisonWantsEachSubjectItChains() {
        assertEquals(3, SubjectLookup.subjectsWanted("whats the actual difference between a hurricane, a typhoon and a cyclone"))
        assertEquals(2, SubjectLookup.subjectsWanted("ibuprofen or paracetamol for a fever and headache, what's the difference and which should I take?"))
        assertEquals(2, SubjectLookup.subjectsWanted("What is the difference between ibuprofen and paracetamol, and when should I use each?"))
        assertEquals(2, SubjectLookup.subjectsWanted("How is a hurricane different from a tornado?"))
        assertEquals(2, SubjectLookup.subjectsWanted("How do hostels compare with guesthouses for a solo traveller on a budget?"))
        assertEquals(2, SubjectLookup.subjectsWanted("Compare tea and coffee for caffeine content and health effects"))
        assertEquals(1, SubjectLookup.subjectsWanted("How does a heat pump heat a house?"))
        assertEquals("\"and\" outside a comparison joins no subjects", 1, SubjectLookup.subjectsWanted("Why are sunsets red and orange?"))
        assertEquals(3, SubjectLookup.subjectsWanted("are lions or tigers heavier?", lookup = true))
    }

    @Test fun aChoiceIsReadFromItsOptions() {
        val deet = SubjectLookup.candidates("DEET vs picaridin, which mosquito repellent is better for a trip to the tropics?", 2)
        assertEquals("DEET", deet.first().text)
        assertEquals(1, deet.first { it.text == "picaridin" }.run)
        assertFalse("the two options lead; later phrases are only fallback candidates", deet.any { it.text == "mosquito repellent" && it.run < 2 })
        val train = SubjectLookup.candidates("Madrid to Barcelona: is the high-speed train or a flight the better option?", 2)
        assertEquals("high-speed train", train.first().text)
        assertTrue(train.any { it.text == "flight" })
        assertFalse("the options follow the first one; the places before it are not the choice", train.any { it.text == "Madrid" })
        val cards = SubjectLookup.candidates("Should I use a debit card or a credit card when travelling abroad?", 2).map { it.text }
        assertEquals(listOf("debit card", "credit card"), cards.filter { ' ' in it })
    }

    @Test fun casualSpellingIsReadAsItsContraction() {
        assertEquals("what's the difference between a seed phrase and a private key", QuestionText.normalised("whats the difference between a seed phrase and a private key"))
        assertEquals("What's that? I'm lost. I've no map. Don't go.", QuestionText.normalised("Whats that? Im lost. Ive no map. Dont go."))
        assertEquals("WHAT'S THIS", QuestionText.normalised("WHATS THIS"))
        assertEquals("a quick whatsapp message", QuestionText.normalised("a quick whatsapp message"))
        val hurricane = SubjectLookup.candidates("whats the actual difference between a hurricane, a typhoon and a cyclone", 3).map { it.text }
        assertEquals(listOf("hurricane", "typhoon", "cyclone"), hurricane)
    }

    @Test fun anotherNameMatchesInTheSingularOrPlural() {
        val cyclone = Evidence("swp-19521-0000", "swp-19521-0000:0001", "Tropical cyclone — Overview", "local",
            "They die when they spend a long time over land.\n\nAlso known as: Tropical storm; Hurricanes; Typhoons; Hurrycane", 0.0)
        assertEquals("Tropical cyclone", SubjectLookup.aliasOf("hurricane", listOf(cyclone)))
        assertEquals("Tropical cyclone", SubjectLookup.aliasOf("Typhoon", listOf(cyclone)))
        assertEquals("Tropical cyclone", SubjectLookup.aliasOf("tropical storms", listOf(cyclone)))
        assertEquals(null, SubjectLookup.aliasOf("hurry", listOf(cyclone)))
    }

    @Test fun aStubOrTravelGuideGivesWayToTheFullArticle() {
        fun overview(id: String, title: String, text: String, source: String = "https://simple.wikipedia.org/w/index.php?oldid=1") =
            Evidence(id, "$id:0000", "$title — Overview", source, text, 0.0)
        val hurricane = overview("swp-19516-0000", "Hurricane", "A Hurricane is a cyclone type that is formed in the Atlantic Ocean and in the Eastern Pacific Ocean.")
        val cyclone = overview("swp-19521-0000", "Tropical cyclone", "A tropical cyclone is a circular air movement that starts over the warm ocean waters in the warm part of Earth near the equator. " +
            "Most tropical cyclones create fast winds and heavy rain. While some tropical cyclones stay out in the sea, others pass over land.")
        val typhoonStub = overview("swp-28999-0000", "Typhoon", "A typhoon is a type of cyclone that is formed in the Western Pacific Ocean. The strongest recorded typhoons were Typhoon Haiyan and Typhoon Tip.")
        val typhoon = overview("enw-3092190-0000", "Typhoon", "A typhoon is a tropical cyclone that develops between 180° and 100°E in the Northern Hemisphere and which produces sustained hurricane-force winds of at least 119 km/h (74 mph). " +
            "This region is referred to as the Northwestern Pacific Basin, accounting for almost one third of the world's tropical cyclones.", "https://en.wikipedia.org/w/index.php?oldid=1")
        assertTrue(SubjectLookup.isStub(hurricane))
        assertTrue(SubjectLookup.isStub(typhoonStub))
        assertFalse(SubjectLookup.isStub(cyclone))
        assertFalse("a short overview that is not a definition is not a stub",
            SubjectLookup.isStub(overview("swp-1-0000", "Fajr-5", "The Fajr-5 was first shown in 2006. It can reach 75 km.")))
        val candidate = SubjectLookup.candidates("whats the actual difference between a hurricane, a typhoon and a cyclone", 3).first()
        // Under the subject's own name first, then under another of its names; a stub keeps its place when neither is fuller.
        assertEquals(cyclone, SubjectLookup.fuller(candidate, hurricane, listOf(null, cyclone)))
        assertEquals(typhoon, SubjectLookup.fuller(candidate, typhoonStub, listOf(typhoon, cyclone)))
        assertEquals(hurricane, SubjectLookup.fuller(candidate, hurricane, listOf(null, null)))
        assertEquals(cyclone, SubjectLookup.fuller(candidate, cyclone, listOf(typhoon)))
        // An explanation is read from the encyclopedia, not a travel guide of the same name.
        val guide = overview("wvs-16301-0000", "Jet lag", "Jet lag is a form of disorientation and fatigue caused by abruptly switching to a different sleeping schedule. " +
            "It is caused by rapid travel across time zones. Most travellers feel it for a few days after a long flight.", "https://en.wikivoyage.org/w/index.php?oldid=1")
        val article = overview("enw-56552-0000", "Jet lag", "Jet lag is a temporary physiological condition that occurs when a person's circadian rhythm is out of sync with the time zone they are in.", "https://en.wikipedia.org/w/index.php?oldid=1")
        val jetLag = SubjectLookup.candidates("Why does jet lag happen, and why is flying east supposed to be worse than flying west?").first()
        assertEquals(article, SubjectLookup.fuller(jetLag, guide, listOf(article)))
        assertEquals(guide, SubjectLookup.fuller(jetLag, guide, listOf(null)))
        // The same article in another collection is taken as it is; a different article must still fit the candidate's other words.
        val wallet = SubjectLookup.candidates("How does a hardware wallet keep my private key safe?").first { it.text == "wallet" }
        val stub = overview("swp-2-0000", "Wallet", "A wallet is a small case for money.")
        val leather = overview("enw-3-0000", "Wallet", "A wallet is a flat case or pouch, often used to carry small personal items such as physical currency, debit cards, and credit cards; identification documents.", "https://en.wikipedia.org/w/index.php?oldid=1")
        assertEquals(leather, SubjectLookup.fuller(wallet, stub, listOf(leather)))
        val purse = overview("enw-4-0000", "Purse", "A purse is a small bag for carrying money and personal items, often with a strap or handle.", "https://en.wikipedia.org/w/index.php?oldid=1")
        assertEquals(stub, SubjectLookup.fuller(wallet, stub, listOf(purse)))
        assertFalse("the full article still fails the candidate it was taken for", SubjectLookup.accepts(wallet, leather))
    }

    @Test fun aConnectorStaysInsideANameButNotBetweenCommonWords() {
        fun lookup(question: String) = SubjectLookup.candidates(question, 3, lookup = true).map { it.text }
        val wall = lookup("When was the Great Wall of China built?")
        assertTrue(wall.toString(), "Great Wall of China" in wall)
        assertFalse("a piece never opens or closes with the connector", wall.any { it.startsWith("of ") || it.endsWith(" of") })
        assertEquals("Statue of Liberty", SubjectLookup.candidates("How tall is the Statue of Liberty?", 3, lookup = true).first().text)
        assertEquals("Dissolution of the Soviet Union", SubjectLookup.candidates("Why did the Dissolution of the Soviet Union happen so fast?").first().text)
        val wine = lookup("Is a glass of red wine a day actually good for your heart?")
        assertEquals(wine.toString(), "red wine", wine.first())
        assertFalse("a measure is not the subject", wine.any { it == "glass" || it.startsWith("glass of") })
        // "And" chains a comparison's subjects; it joins a name only when one subject is wanted.
        val chained = SubjectLookup.candidates("What is the difference between Celsius and Fahrenheit?", 2)
        assertEquals(setOf(0, 1), chained.map { it.run }.toSet())
        assertEquals("Trinidad and Tobago", SubjectLookup.candidates("How does Trinidad and Tobago elect its parliament?").first().text)
    }

    @Test fun aLooseModifierDoesNotNarrowTheSubject() {
        fun overview(title: String, text: String) = Evidence("wp-$title", "wp-$title:0", "$title — Overview", "local", text, 0.0)
        val bitcoin = overview("Bitcoin", "Bitcoin is a digital and global money system cryptocurrency. It allows people to send or receive units across the internet.")
        val halvingCandidates = SubjectLookup.candidates("when is the next bitcoin halving and what actually happens when it does", 3, lookup = true)
        assertTrue(halvingCandidates.toString(), halvingCandidates.any { it.text == "bitcoin" })
        val halving = halvingCandidates.first { it.text == "bitcoin" }
        assertEquals("next", halving.modifier)
        assertTrue("\"next\" says when, not which Bitcoin", SubjectLookup.accepts(halving, bitcoin))
        val hot = SubjectLookup.candidates("What is the difference between a hot wallet and a cold wallet?", 2).first { it.text == "wallet" }
        assertFalse(SubjectLookup.accepts(hot, overview("Wallet", "A wallet is a small flat case that a person uses to keep cash and cards in a pocket.")))
        val maya = SubjectLookup.candidates("Why did the Classic Maya civilization collapse? Was it drought, war, or something else?")
        val civilization = maya.first { it.text == "Maya civilization" }
        assertEquals("Classic", civilization.modifier)
        assertTrue("a capitalised word before a name is part of a longer name", SubjectLookup.accepts(civilization,
            overview("Maya civilization", "The Maya civilization was a Mesoamerican civilization that existed from antiquity to the early modern period.")))
        val common = maya.first { it.text == "civilization" }
        assertFalse("a name before a common noun is the subject itself", SubjectLookup.accepts(common,
            overview("Civilization", "A civilization is any complex society characterized by the development of the state and social stratification.")))
    }

    @Test fun omittedProperNameWordsCannotSelectADifferentEntity() {
        val candidate = SubjectLookup.Candidate("River council", listOf("Silver"), "Silver")
        fun record(text: String) = Evidence("fixture", "fixture:0", "River council — Overview", "local", text, 0.0)
        assertFalse(SubjectLookup.accepts(candidate, record("The River council administers a district.")))
        assertFalse(SubjectLookup.accepts(candidate, record("Silverware is traded by the River council.")))
        assertFalse(SubjectLookup.accepts(candidate, record("Silver is a metal. The River council administers a district.")))
        assertTrue(SubjectLookup.accepts(candidate, record("The Silver River council administers the northern district.")))
    }

    @Test fun aChoiceOptionStartsWhereItsNounPhraseDoes() {
        val choice = SubjectLookup.choice("Is a hardware wallet like a Ledger or Trezor really safer than keeping crypto in a phone wallet app? Which is better?")!!
        assertEquals("hardware wallet like a Ledger", choice.first)
        assertEquals("Trezor", choice.second)
        val candidates = SubjectLookup.candidates("Is a hardware wallet like a Ledger or Trezor really safer than keeping crypto in a phone wallet app? What can still go wrong?", 2)
        assertEquals("hardware wallet", candidates.first().text)
        assertEquals(1, candidates.first { it.text == "Ledger" }.run)
        assertFalse("\"like\" is a simile, not a name's word", candidates.any { "like" in it.text.lowercase().split(' ') })
        assertFalse(candidates.any { it.text == "Still" || it.text == "still" })
        // The question's own phrases follow the options, for when neither names an article.
        val fallback = SubjectLookup.candidates("Should I use a zig or a zag when travelling? How does a heat pump heat a house?", 2)
        assertEquals(listOf("zig", "zag"), fallback.filter { it.run < 2 }.map { it.text })
        assertTrue(fallback.toString(), fallback.any { it.text == "heat pump" && it.run >= 2 })
        val train = SubjectLookup.choice("Madrid to Barcelona: is the high-speed train or a flight the better option?")!!
        assertEquals("high-speed train", train.first)
        assertEquals("flight", train.second)
        assertEquals("credit", SubjectLookup.choice("Should I use a debit card or a credit card when travelling abroad?")!!.second)
    }

    @Test fun whatHappenedToASubjectIsExplained() {
        assertTrue(SubjectLookup.explains("what happened to the aral sea? why did it shrink so much and has any of it come back?"))
        assertTrue(SubjectLookup.explains("Whatever happened to the Concorde?"))
        assertTrue(SubjectLookup.explains("what's the deal with Ethereum gas fees?"))
        assertFalse(SubjectLookup.looksUp("what happened to the aral sea?"))
        assertEquals("aral sea", SubjectLookup.candidates("what happened to the aral sea? why did it shrink so much and has any of it come back?").first().text)
    }

    @Test fun theScenariosPeopleAndTheSubjectsPredicateAreNotTheSubject() {
        val seasick = SubjectLookup.candidates("Why do some people get seasick on boats and others don't, and what actually helps?")
        assertEquals("seasick", seasick.first().text)
        assertTrue("the condition's noun is tried too", seasick.any { it.text == "seasickness" })
        assertFalse(seasick.any { it.text == "people" || it.text == "some people" })
        assertEquals("seasickness", SubjectLookup.conditionOf("seasick"))
        assertEquals("motion sickness", SubjectLookup.conditionOf("carsick"))
        assertTrue(SubjectLookup.headsSection("seasickness"))
        assertFalse("a lone common word heads too many sections", SubjectLookup.headsSection("wallet"))
        val section = Evidence("enw-62580-0006", "enw-62580-0006:0000", "Motion sickness — Cause > Motion felt but not seen > Seasickness",
            "https://en.wikipedia.org/w/index.php?oldid=1", "Seasickness is a form of motion sickness characterized by a feeling of nausea.", 0.0)
        assertEquals(section, SubjectLookup.sectionOf("seasickness", listOf(section)))
        // After a copula the phrase's last word says what the subject is: "the sky" is blue.
        val sky = SubjectLookup.candidates("why is the sky blue but sunsets are orange and red?")
        assertEquals("sky", sky.first().text)
        assertFalse(sky.any { it.text == "sky blue" })
        assertEquals("sunset", SubjectLookup.candidates("Why are sunsets red and orange?").first { it.text.startsWith("sunset") }.text.removeSuffix("s"))
        // "water boil" is explained by the property "Boiling point", tried before the bare subject.
        val boil = SubjectLookup.candidates("Why does water boil at a lower temperature up in the mountains?").map { it.text }
        assertTrue(boil.toString(), boil.indexOf("boiling point") in 0 until boil.indexOf("water"))
        assertTrue(boil.indexOf("boiling") in 0 until boil.indexOf("water"))
        val gas = SubjectLookup.candidates("Why are Ethereum gas fees sometimes so high?").map { it.text }
        assertEquals("Ethereum gas fees", gas.first())
        assertFalse("\"sometimes\" frames the question", gas.any { "sometimes" in it })
        val lake = SubjectLookup.candidates("How deep is Lake Baikal, and how much of the world's fresh water does it hold?", 3, lookup = true).map { it.text }
        assertEquals("Lake Baikal", lake.first())
        assertFalse(lake.any { it == "deep" || it == "hold" || it == "Depth" })
    }

    @Test fun theArticlesHeadingsMayMentionTheNarrowingWord() {
        fun overview(id: String, title: String, text: String) = Evidence(id, "$id:0000", "$title — Overview", "https://en.wikipedia.org/w/index.php?oldid=1", text, 0.0)
        val headphones = overview("enw-191884-0000", "Headphones", "Headphones are a pair of small loudspeaker drivers worn on or around the head over a user's ears.")
        val candidate = SubjectLookup.candidates("How do noise-cancelling headphones actually work?").first { it.text == "headphones" }
        assertEquals("noise-cancelling", candidate.modifier)
        assertFalse(SubjectLookup.accepts(candidate, headphones))
        assertTrue(SubjectLookup.accepts(candidate, headphones, listOf("Headphones — Overview", "Headphones — Ambient noise reduction", "Headphones — Specifications")))
        assertFalse(SubjectLookup.accepts(candidate, headphones, listOf("Headphones — Specifications", "Headphones — Applications")))
    }

    @Test fun theArticleOnHowTheSubjectEndedIsReadToo() {
        assertEquals(SubjectLookup.RELATED_WORDS, SubjectLookup.relatedWords("Why did the Soviet Union collapse so quickly in 1991?"))
        assertNotNull(SubjectLookup.relatedWords("what happened to the aral sea?"))
        assertNull(SubjectLookup.relatedWords("How does a heat pump heat a house?"))
        assertEquals(listOf("Maya civilization", "Maya"), SubjectLookup.relatedNames("Maya civilization"))
        assertEquals(listOf("Soviet Union"), SubjectLookup.relatedNames("Soviet Union"))
        assertEquals(listOf("steam engine"), SubjectLookup.relatedNames("steam engine"))
        assertEquals(emptyList<String>(), SubjectLookup.relatedNames("inflation"))
        fun overview(id: String, title: String, text: String, source: String = "https://en.wikipedia.org/w/index.php?oldid=1") =
            Evidence(id, "$id:0000", "$title — Overview", source, text, 0.0)
        val dissolution = overview("enw-40494892-0000", "Dissolution of the Soviet Union",
            "On 26 December 1991, the Soviet Union was formally dissolved. The collapse of the Soviet Union in 1991 resulted from several factors: chronic economic stagnation and intense ethnic nationalism.")
        val party = overview("enw-35897659-0000", "History of the Communist Party of the Soviet Union (Bolsheviks)", "The party was founded in 1912.")
        val late = overview("swp-32470-0000", "History of the Soviet Union (1985–1991)", "The history of the Soviet Union from 1985 to 1991 covers the dissolution of the Soviet Union.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val union = overview("swp-3600-0000", "Soviet Union", "The Soviet Union was a country.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val town = Evidence("enw-861325-0005", "enw-861325-0005:0000", "Khujand — History > Soviet Union", "https://en.wikipedia.org/w/index.php?oldid=1", "The town was in the Soviet Union.", 0.0)
        assertEquals(dissolution, SubjectLookup.relatedTitle("Soviet Union", listOf(town, union, late, party, dissolution)))
        assertEquals("a bracketed qualifier only when no plain title fits", late, SubjectLookup.relatedTitle("Soviet Union", listOf(union, late, party)))
        assertNull(SubjectLookup.relatedTitle("Soviet Union", listOf(union, town)))
        val maya = overview("enw-10443117-0000", "Classic Maya collapse", "In archaeology, the classic Maya collapse is a model to describe the destabilization of Classic Maya civilization.")
        assertEquals(maya, SubjectLookup.relatedTitle("Maya", listOf(maya)))
        assertNull("the name must be whole in the title", SubjectLookup.relatedTitle("Maya civilization", listOf(maya)))
        val rome = overview("enw-1-0000", "Fall of the Western Roman Empire", "The fall of the Western Roman Empire was the loss of central political control.")
        assertEquals(rome, SubjectLookup.relatedTitle("Western Roman Empire", listOf(rome)))
    }

    @Test fun aWhyQuestionOpensWithTheArticleThatNamesTheCause() {
        fun overview(id: String, title: String, text: String) = Evidence(id, "$id:0000", "$title — Overview", "https://en.wikipedia.org/w/index.php?oldid=1", text, 0.0)
        val union = overview("swp-3600-0000", "Soviet Union", "The Soviet Union was a transcontinental country that spanned much of Eurasia from 1922 to 1991.")
        val dissolution = overview("enw-40494892-0000", "Dissolution of the Soviet Union",
            "The collapse of the Soviet Union in 1991 resulted from several factors: chronic economic stagnation and intense ethnic nationalism.")
        val sources = listOf(PromptSource("S1", union), PromptSource("S2", dissolution))
        val lead = SubjectLookup.lead("Why did the Soviet Union collapse so quickly in 1991?", listOf(union, dissolution), sources)!!
        assertEquals("S2", lead.citationId)
        assertEquals(listOf("S1"), lead.more.map { it.first })
    }

    @Test fun theWordsAfterANameAreItsPredicate() {
        assertEquals(emptyList<String>(), SubjectLookup.candidates("Why did the Soviet Union collapse?").first { it.text == "Soviet Union" }.rest)
        assertEquals(emptyList<String>(), SubjectLookup.candidates("How did the Black Death change wages and society in Europe?").first { it.text == "Black Death" }.rest)
        assertEquals(emptyList<String>(), SubjectLookup.candidates("How did the steam engine change society?").first { it.text == "steam engine" }.rest)
        val western = SubjectLookup.candidates("Why did the Western Roman Empire collapse while the Eastern half survived for another thousand years?").map { it.text to it.rest }
        assertEquals(emptyList<String>(), western.first { it.first == "Western Roman Empire" }.second)
        assertTrue(western.toString(), western.indexOfFirst { it.first == "Western Roman Empire" } < western.indexOfFirst { it.first == "Roman Empire" })
        assertEquals(emptyList<String>(), SubjectLookup.candidates("why do my ears pop on planes, and why does it hurt more when we're landing?").first { it.text == "ear" }.rest)
        assertEquals(emptyList<String>(), SubjectLookup.candidates("Why are sunsets red and orange?").first { it.text == "sunset" }.rest)
        assertEquals(emptyList<String>(), SubjectLookup.candidates("Why is the ocean salty?").first { it.text == "ocean" }.rest)
        // A noun after the name still narrows it: "ocean" is not the subject of "ocean tides".
        assertEquals(listOf("tides"), SubjectLookup.candidates("What causes ocean tides?").first { it.text == "ocean" }.rest)
        fun overview(title: String, text: String) = Evidence("wp-$title", "wp-$title:0", "$title — Overview", "local", text, 0.0)
        val government = overview("Country", "A country is a region governed by a government that holds power over the people within it.")
        val go = SubjectLookup.Candidate("country", listOf("go"), null)
        assertFalse("\"go\" is not in \"government\"", SubjectLookup.accepts(go, government))
        assertTrue(SubjectLookup.accepts(SubjectLookup.Candidate("country", listOf("power"), null), government))
        val wallet = SubjectLookup.candidates("How does a hardware wallet keep my private key safe?").first { it.text == "wallet" }
        assertFalse(SubjectLookup.accepts(wallet, overview("Wallet", "A wallet is a small flat case that a person uses to keep cash and cards in a pocket.")))
    }

    @Test fun aLaterClauseIsTriedWhenTheFirstNamesNoArticle() {
        val message = SubjectLookup.candidates("How can someone send me an encrypted message without us ever meeting? How does public key cryptography work?")
        assertEquals("encrypted message", message.first().text)
        val cryptography = message.first { it.text == "public key cryptography" }
        assertTrue(cryptography.run > 0)
        assertFalse("\"meeting\" is a verb here", message.any { it.text == "meeting" })
        assertFalse(message.any { it.text == "someone" || it.text == "us" })
        assertEquals("the US", "US", SubjectLookup.candidates("What is the population of the US?", 3, lookup = true).first().text)
        // A phrase after a preposition places the subject and is never the subject.
        assertFalse(SubjectLookup.candidates("What is a stealth address in Ethereum?").any { it.text == "Ethereum" })
        assertFalse(SubjectLookup.candidates("why do my ears pop on planes, and why does it hurt more when we're landing?").any { it.text == "plane" })
        // A comparison's subjects sit side by side; nothing after them is a subject.
        assertFalse(SubjectLookup.candidates("How do hostels compare with guesthouses for a solo traveller on a budget?", 2).any { it.text == "budget" })
        // A number inside a name stays in it; a year may open one.
        val rollup = SubjectLookup.candidates("what is a layer 2 rollup on Ethereum")
        assertEquals("layer 2 rollup", rollup.first().text)
        assertFalse(rollup.any { it.text == "Ethereum" || it.text == "2" })
        assertEquals("2008 financial crisis", SubjectLookup.candidates("Why did the 2008 financial crisis happen?").first().text)
        assertEquals(listOf("world population"), SubjectLookup.candidates("What is the world population in 2026?", 3, lookup = true).map { it.text })
    }

    @Test fun aQualifiedTitleIsTheSubjectWhenTheQuestionNamesItsKind() {
        fun overview(id: String, title: String) = Evidence(id, "$id:0000", "$title — Overview", "local", "$title is a place.", 0.0)
        val country = overview("swp-14986-0000", "Georgia (country)")
        val state = overview("swp-4951-0000", "Georgia (U.S. state)")
        val dome = overview("swp-5-0000", "Georgia Dome")
        val found = listOf(dome, country, state)
        assertEquals(country, SubjectLookup.overviewOf("Georgia", found, "tell me about Georgia, the country not the US state"))
        assertEquals(state, SubjectLookup.overviewOf("Georgia", found, "tell me about the state of Georgia"))
        assertEquals("two qualified titles and no word to choose by", null, SubjectLookup.overviewOf("Georgia", found, "tell me about Georgia"))
        assertEquals(country, SubjectLookup.overviewOf("Georgia", listOf(dome, country), "tell me about Georgia"))
        assertEquals("without the question only the exact title counts", null, SubjectLookup.overviewOf("Georgia", found))
        assertEquals(listOf("country", "state"), SubjectLookup.qualifiers("tell me about Georgia, the country not the US state"))
    }

    @Test fun aWhyOrHowQuestionOpensWithTheSectionThatAnswersIt() {
        fun row(id: String, title: String, text: String) = Evidence(id.substringBefore(':'), id, title, "https://en.wikipedia.org/w/index.php?oldid=1", text, 0.0)
        val gps = row("swp-82503-0000:0000", "Global Positioning System — Overview", "A Global Positioning System, also known as GPS, is a system of satellites designed to help navigate on the Earth, in the air, and on water.")
        val principles = row("enw-11866-0007:0000", "Global Positioning System — Principles",
            "The GPS satellites carry very stable atomic clocks that are synchronized with one another and with the reference atomic clocks at the ground control stations. Since the speed of radio waves is constant, the time delay is proportional to the distance.")
        val geometry = row("enw-11866-0009:0000", "Global Positioning System — Principles > User-satellite geometry", "Although usually not formed explicitly, the time differences of arrival define the measurement geometry.")
        val sources = listOf(gps, principles, geometry).mapIndexed { index, item -> PromptSource("S${index + 1}", item) }
        val how = SubjectLookup.lead("How does GPS on my phone work when I have no internet or signal?", listOf(gps), sources, depth = listOf(principles, geometry))!!
        assertEquals("S2", how.citationId)
        assertTrue(how.text, how.text.startsWith("The GPS satellites carry very stable atomic clocks"))
        // A definition question opens with the definition even when a section answers its second half.
        val what = SubjectLookup.lead("What is GPS and how does it work?", listOf(gps), sources, depth = listOf(principles))!!
        assertEquals("S1", what.citationId)
        // Without depth, or when the section was not packed, the definition opens as before.
        assertEquals("S1", SubjectLookup.lead("How does GPS on my phone work when I have no internet or signal?", listOf(gps), sources)!!.citationId)
        assertEquals("S1", SubjectLookup.lead("How does GPS on my phone work when I have no internet or signal?", listOf(gps), sources.take(1), depth = listOf(principles))!!.citationId)
        // A section opening with a pronoun would quote out of context.
        val pronoun = row("enw-11866-0008:0000", "Global Positioning System — Principles > More detailed description", "It broadcasts a signal that includes a pseudorandom code. The receiver measures its arrival time.")
        assertEquals("S1", SubjectLookup.lead("How does GPS work?", listOf(gps), sources + PromptSource("S4", pronoun), depth = listOf(pronoun))!!.citationId)
        // A comparison opens with each subject's definition.
        val tornado = row("swp-1-0000:0000", "Tornado — Overview", "A tornado is an area of fast spinning winds made by a strong rotating thunderstorm that touches the ground.")
        val both = SubjectLookup.lead("How is a hurricane different from a tornado?", listOf(gps, tornado), sources + PromptSource("S4", tornado), depth = listOf(principles))!!
        assertEquals("S1", both.citationId)
        assertEquals(listOf("S4"), both.more.map { it.first })
    }

    @Test fun aDefinitionOpensAWhyQuestionOnlyWhenItSpeaksToIt() {
        fun overview(title: String, text: String) = Evidence("swp-$title", "swp-$title:0000", "$title — Overview", "https://simple.wikipedia.org", text, 0.0)
        val sunset = overview("Sunset", "Sunset (or sundown) is the disappearance of the Sun at the end of the Sun path, below the horizon of the Earth due to its rotation. It happens about once every 24 hours.")
        val tide = overview("Tide", "A tide is the periodic rising and falling of Earth's ocean surface caused mainly by the gravitational pull of the Moon acting on the oceans. Tides cause changes in the depth of marine waters.")
        fun sources(item: Evidence) = listOf(PromptSource("S1", item, item.text))
        // "Red and orange" is nowhere in the definition of a sunset: the model answers without a quote.
        assertNull(SubjectLookup.lead("Why are sunsets red and orange?", listOf(sunset), sources(sunset)))
        // A definition that names the cause opens the answer.
        assertNotNull(SubjectLookup.lead("What causes ocean tides?", listOf(tide), sources(tide)))
        // A what-is question keeps its definition.
        assertNotNull(SubjectLookup.lead("What is a sunset?", listOf(sunset), sources(sunset)))
    }
}
