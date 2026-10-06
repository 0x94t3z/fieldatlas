package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun aComparisonKeepsTheModelUnderTheGuideQuote() = runBlocking {
        fun section(title: String, text: String) = Evidence("wvs-$title", "wvs-$title:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val transit = section("Avelton — Get around > By metro",
            "A single metro ticket costs 2 euros and is valid for 90 minutes. A day pass costs 8 euros and covers every line.")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) =
                if (phrase == "Avelton" && "Get around" in sections) listOf(transit) else emptyList()
        }
        val quoteOnly = ResearchOrchestrator(retriever, xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Unused.")))
            .research("How do I buy a metro ticket in Avelton?").toList()
            .filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(quoteOnly, "> A single metro ticket costs 2 euros" in quoteOnly && "Unused." !in quoteOnly)
        val model = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("Four rides make the day pass cheaper."))
        val answer = ResearchOrchestrator(retriever, model)
            .research("What is the difference between a single metro ticket and a day pass in Avelton?").toList()
            .filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer, "> A single metro ticket costs 2 euros" in answer)
        assertTrue("the comparison is the model's", "Four rides make the day pass cheaper." in answer)
    }


    @Test fun theSectionHeadedForWhatIsAskedLeadsAndIsQuoted() {
        fun section(title: String, chunk: Int, text: String) = Evidence("wvs-${title.hashCode()}", "wvs-$chunk:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val boat = section("Paris — Get around > By boat", 1, "You can buy a day or 3 day ticket and hop on and off the boat as needed. The boats follow the river.")
        val transit = section("Paris — Get around > By public transport", 2,
            "Paris has an excellent public transport system. A single metro ticket costs €2.50 and can be bought from machines in every station.")
        val picked = TravelSections.pick("How do I buy a metro ticket in Paris?", "Paris", listOf(boat, transit), 2)
        assertEquals(transit, picked.first())
        val sources = picked.mapIndexed { i, item -> PromptSource("S${i + 1}", item) }
        val quote = TravelSections.lead("How do I buy a metro ticket in Paris?", "Paris", sources)!!
        assertTrue(quote.text, quote.text.startsWith("A single metro ticket costs €2.50"))
        // A visa is under "Entry requirements", not the yacht permits of "By boat".
        val yacht = section("Turkey — Get in > By boat", 3, "On entry one gets a voyage permission document; as a tourist one may stay 90 days.")
        val visa = section("Turkey — Get in > Entry requirements > Visa-free", 4, "Citizens of many countries can visit visa-free for up to 90 days.")
        assertEquals(visa, TravelSections.pick("Do I need a visa to visit Turkey as a tourist?", "Turkey", listOf(yacht, visa), 2).first())
    }

    @Test fun aSafetyQuestionIsNotNarrowedToATransportSection() {
        fun section(title: String, chunk: Int, text: String) = Evidence("wvs-${title.hashCode()}", "wvs-$chunk:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
        val transit = section("Barcelona — Get around > By public transport", 1, "The whole area is covered by the Integrated Fare System, which is divided into 6 zones.")
        val scams = section("Barcelona — Stay safe > Scams", 2, "Pickpockets work the crowded metro trains. Keep your wallet in a front pocket on the metro.")
        val sources = listOf(transit, scams).mapIndexed { i, item -> PromptSource("S${i + 1}", item) }
        val quote = TravelSections.lead("Are there pickpockets on the metro in Barcelona?", "Barcelona", sources)!!
        assertTrue(quote.text, quote.text.startsWith("Pickpockets work the crowded metro trains."))
        // Picking keeps the safety section too, though two transport sections name the metro.
        val metro = section("Barcelona — Get around > By public transport > By metro", 3, "The metro has eight lines and runs until midnight.")
        val picked = TravelSections.pick("Are there pickpockets on the metro in Barcelona?", "Barcelona", listOf(transit, metro, scams), 2)
        assertTrue(picked.map { it.title }.toString(), scams in picked)
    }

    private fun guide(title: String, text: String) = Evidence("wvs-${title.hashCode()}", "wvs-${title.hashCode()}:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)
    private fun sources(vararg items: Evidence) = items.mapIndexed { i, item -> PromptSource("S${i + 1}", item) }

    @Test fun aSafetyQuestionIsQuotedFromTheSafetySectionNotTheMetrosOwn() {
        // The metro's own section shares "metro" and every "subway" and "getting around" with the
        // question, and quoted alone its opening hours were the whole answer about pickpockets.
        val metro = guide("Avelona — Get around > By public transport > By metro",
            "The Metro (subway) is an efficient way of getting around town. Operation times are 05:00–24:00.")
        val pickpockets = guide("Avelona — Stay safe > Pickpockets",
            "Pickpocketing is the most pressing issue for visitors to Avelona. Never keep your wallet in a back pocket.")
        val quote = TravelSections.lead("Are there pickpockets on the metro in Avelona?", "Avelona", sources(metro, pickpockets))!!
        assertTrue(quote.text, quote.text.startsWith("Pickpocketing is the most pressing issue"))
        // With no safety section picked, the picked ones answer: "beach" asks no safety question here.
        val bus = guide("Avelona — Get in > By plane", "The Smart Bus connects the airport with the beach every hour. Fares are 100 baht.")
        val ride = TravelSections.lead("How do I get to the beach from the airport in Avelona?", "Avelona", sources(bus))!!
        assertTrue(ride.text, ride.text.startsWith("The Smart Bus connects the airport with the beach"))
    }

    @Test fun aBudgetIsQuotedOnlyFromASectionAboutCosts() {
        val question = "How much should I budget per day for backpacking in Avelia?"
        val tax = guide("Avelia — Buy > Tax", "Foreign visitors can claim a refund on the consumption tax if they spend at least ¥5,000 per day per shop.")
        assertNull("a tax refund per day is no daily budget", TravelSections.lead("How much should I budget per day in Avelia?", "Avelia", sources(tax)))
        val discrimination = guide("Avelia — Buy > Shopping > Price discrimination",
            "Some sellers overcharge foreigners. They may argue inflated prices are still cheap for a day out.")
        val costs = guide("Avelia — Buy > Shopping > Costs", "Budget travellers can get by on US\$25 per day, including a dorm bed and street food.")
        assertEquals(costs, TravelSections.pick(question, "Avelia", listOf(discrimination, costs), 2).first())
        assertTrue(TravelSections.partHeadings(question).none { it.containsMatchIn("Price discrimination") })
        val quote = TravelSections.lead(question, "Avelia", sources(discrimination, costs))!!
        assertTrue(quote.text, quote.text.startsWith("Budget travellers can get by on US\$25 per day"))
        assertNull("without a costs section the model answers", TravelSections.lead(question, "Avelia", sources(discrimination)))
    }

    @Test fun theSectionForThePartAskedComesFirstButAnotherThatSharesMoreAnswers() {
        val money = guide("Avelia — Buy > Money", "ATMs are commonplace, at least in cities. They usually accept major international credit and debit cards.")
        val shopping = guide("Avelia — Buy > Shopping",
            "While travelling through the countryside, do not rely on credit cards; in small towns they're accepted by only a few shops and restaurants. Cash is king around the country.")
        val cards = TravelSections.lead("Are credit cards widely accepted in Avelia?", "Avelia", sources(money, shopping))!!
        assertTrue(cards.text, cards.text.startsWith("While travelling through the countryside, do not rely on credit cards"))
        val tipping = guide("Avelia — Buy > Tipping", "It is not possible to add tips manually to a credit card bill, so you'll need cash.")
        val withdraw = TravelSections.lead("Can I withdraw cash with my Visa debit card in Avelia?", "Avelia", sources(money, tipping))!!
        assertTrue(withdraw.text, withdraw.text.startsWith("ATMs are commonplace"))
        // "Cashier" shares no more than "cash" with the question, which the money section did not answer.
        val notes = guide("Avelia — Buy > Money", "Notes larger than €100 are refused at some stores.")
        val hours = guide("Avelia — Buy > Opening hours", "Petrol stations open all night. At night you might have to pay the cashier through a window.")
        assertNull(TravelSections.lead("Do I need cash in Avelia?", "Avelia", sources(notes, hours)))
    }

    @Test fun aVisaCardIsPaidWithNotAppliedFor() {
        val question = "Can I pay with my Visa card in Avelia?"
        assertEquals(listOf("Buy"), TravelSections.lookup(question)!!.sections)
        assertTrue(TravelSections.partHeadings(question).none { it.containsMatchIn("Entry requirements") })
        assertTrue(TravelSections.partHeadings("Is Visa or Mastercard accepted in Avelia?").none { it.containsMatchIn("Entry requirements") })
        assertEquals(listOf("Get in"), TravelSections.lookup("Do I need a visa for Avelia?")!!.sections)
        assertTrue(TravelSections.partHeadings("Do I need a visa for Avelia?").any { it.containsMatchIn("Entry requirements") })
        val evisa = guide("Avelia — Get in > Entry requirements > E-Visa", "Don't forget to pay for your e-visa, otherwise your visa application will be on hold.")
        val atms = guide("Avelia — Buy > Money > ATMs", "ATMs are common in every city. They accept credit and debit cards, including Visa and MasterCard.")
        val quote = TravelSections.lead(question, "Avelia", sources(evisa, atms))!!
        assertTrue(quote.text, quote.text.startsWith("ATMs are common in every city"))
    }

    @Test fun aPartIsTheSectionsOwnHeadingAndAnAppIsAWordOfTheQuestion() {
        assertEquals(setOf("avelok"), TravelSections.nameTerms("Can I use Grab in Avelok?"))
        val metered = guide("Avelok — Get around > By taxi > Metered taxi",
            "Metered taxis are a quick and comfortable way to get around town, but taxi drivers are notorious for refusing the meter.")
        val rideshare = guide("Avelok — Get around > By taxi > Rideshare taxi",
            "The main ride sharing company operating in Avelok is Grab, which can also be used to book taxis.")
        assertEquals(rideshare, TravelSections.pick("Can I use Grab in Avelok?", "Avelok", listOf(metered, rideshare), 2).first())
        val grab = TravelSections.lead("Can I use Grab in Avelok?", "Avelok", sources(rideshare, metered))!!
        assertTrue(grab.text, grab.text.startsWith("The main ride sharing company operating in Avelok is Grab"))
        // Hire cars under "By car" are no answer to driving on the left.
        val car = guide("Avelia — Get around > By car",
            "Traffic drives on the left. Motorways are only found near major cities, with most intercity driving done on narrow highways.")
        val rental = guide("Avelia — Get around > By car > Rental",
            "The smaller car rental firms can be substantially cheaper, though you may not end up driving this year's latest model.")
        val camper = guide("Avelia — Get around > By car > Campervan", "Campervans are popular.")
        val driving = "What should I know before driving on the left in Avelia?"
        assertTrue(rental !in TravelSections.pick(driving, "Avelia", listOf(rental, car, camper), 2))
        val road = TravelSections.lead(driving, "Avelia", sources(car, rental))!!
        // The sentence before the best one, which shares "left", is quoted with it: the best ends its passage.
        assertTrue(road.text, "Motorways are only found near major cities" in road.text && "rental firms" !in road.text)
        // A heading that names no part ("Fares") is filed under its parent's.
        val boat = guide("Avelia — Get around > By boat", "You can buy a day ticket and hop on and off the boat.")
        val fares = guide("Avelia — Get around > By public transport > Fares", "A single metro ticket costs €2 at any station machine.")
        assertEquals(fares, TravelSections.pick("How do I buy a metro ticket in Avelia?", "Avelia", listOf(boat, fares), 2).first())
    }

    @Test fun aSimCardIsAConnectQuestionAndABudgetAMoneyOne() {
        assertEquals(listOf("Connect"), TravelSections.lookup("How do I get a local SIM card in Vietnam?")!!.sections)
        assertEquals(listOf("Buy"), TravelSections.lookup("How much should I budget per day for backpacking in Vietnam?")!!.sections)
        assertTrue(TravelSections.lookup("Can I pay by credit card in Japan?")!!.sections.contains("Buy"))
        assertTrue(TravelSections.lookup("Can I use cards in Japan?")!!.sections.contains("Buy"))
        // A price of something else is not a money question.
        assertNull(TravelSections.lookup("Is street food cheap in Bangkok?"))
        assertEquals(listOf("Get around"), TravelSections.lookup("How much does the metro cost in Paris?")!!.sections)
    }
    @Test fun aSentenceInitialWordAnAcronymAndANationalityAreNoPlaces() {
        assertEquals(listOf("Bali"), TravelSections.lookup("Flying into Bali on a UK passport. Can I get a visa on arrival and how much is it?")!!.places)
        assertEquals(listOf("Rio de Janeiro"), TravelSections.lookup("Is Rio de Janeiro safe for a solo traveller? Which areas should I avoid at night?")!!.places)
        assertEquals("no suffix of a name", listOf("New York"), TravelSections.lookup("How much should I tip taxi drivers in New York?")!!.places)
        assertEquals(listOf("Phuket"), TravelSections.lookup("Do I need a visa for Phuket on a Canadian passport?")!!.places)
        assertEquals("a colon marks a name", listOf("Avelton"), TravelSections.lookup("Avelton: how do I cross the street?")!!.places)
        assertEquals(setOf("kyoto"), TravelSections.nameTerms("Can I withdraw cash from ATMs in Kyoto with a foreign card?"))
    }

    @Test fun theCountrysSectionAnswersWhenThePlacesOwnDoNotCoverThePart() {
        val visa = "Flying into Bali on a UK passport. Can I get a visa on arrival and how much is it?"
        val plane = guide("Bali — Get in > By plane", "The airport is the hub for island hopping. See the Indonesia article for information about visa regulations.")
        val bus = guide("Bali — Get in > By bus", "The fare is Rp 4400 from the airport, although it only stops at major roads, so you will need to walk or get a taxi.")
        assertFalse(TravelSections.coversPart(visa, "Bali", listOf(plane, bus)))
        assertNull("filler words and a deferral make no quote", TravelSections.lead(visa, "Bali", sources(bus, plane)))
        val voa = guide("Indonesia — Get in > Visa > Visa waiver and visas on arrival", "Visitors from 77 other countries may buy a visa-on-arrival (VOA) for Rp 500,000, valid for 30 days.")
        assertTrue(TravelSections.coversPart(visa, "Indonesia", listOf(voa)))
        assertEquals("Indonesia", TravelSections.parentOf("Bali", "Bali, the famed \"Island of the Gods\", is the most visited part of Indonesia. Its landscape is diverse."))
        assertEquals("Portugal", TravelSections.parentOf("Porto", "Porto is Portugal's second largest city and the capital of the Northern region."))
        assertEquals("a hatnote is no overview", "Japan", TravelSections.parentOf("Tokyo", "- Tokyo can be broadly divided into wards.\nTokyo is the capital of Japan."))
        // A section that says the question's words itself covers the part, though no heading names it.
        val atms = guide("Kyoto — Buy", "Many ATMs in Kyoto do not allow non-domestic credit cards to be used, but ATMs in post offices usually do.")
        assertTrue(TravelSections.coversPart("Can I withdraw cash from ATMs in Kyoto with a foreign card?", "Kyoto", listOf(atms)))
        assertTrue("a care question reads its own place", TravelSections.coversPart("What should I wear to visit temples in Kyoto?", "Kyoto", emptyList()))
    }

    @Test fun careQuestionsKeepTheirOwnPartsAndACompoundOneGetsASecondQuote() {
        val question = "what should I wear to visit temples in Avelia, and is there anything I shouldn't do inside?"
        val caution = guide("Avelia — Respect", "If planning to visit Temples or a Royal Memorial, wearing black or dark colors is expected during the mourning period.")
        val dress = guide("Avelia — Respect > Dress", "Long pants are preferable to shorts, and you should always cover your shoulders. Outside of the major temples, men wearing shorts is acceptable.")
        val etiquette = guide("Avelia — Respect > Religious etiquette", "Take off your shoes before entering a temple building. Do not point your feet at a Buddha image.")
        val picked = TravelSections.pick(question, "Avelia", listOf(caution, dress, etiquette), 2)
        assertEquals(listOf(dress.title, etiquette.title), picked.map { it.title })
        val lead = TravelSections.lead(question, "Avelia", sources(dress, etiquette))!!
        assertTrue(lead.text, lead.text.startsWith("Long pants are preferable to shorts"))
        assertEquals(listOf("S2"), lead.more.map { it.first })
        assertTrue(lead.more.toString(), lead.more.single().second.startsWith("Take off your shoes"))
        assertEquals(listOf("Stay safe"), TravelSections.lookup("Which areas of Johannesburg should I avoid?")!!.sections)
        assertTrue(TravelSections.partHeadings("Which areas of Johannesburg should I avoid?").any { it.containsMatchIn("Dangerous areas") })
        assertTrue(TravelSections.lookup("Do I need to cover my shoulders in churches in Rome?")!!.sections.contains("Respect"))
    }

    @Test fun aSafetyVerdictLeadsWithTheStaySafeOpening() {
        val question = "Is Avelona safe for a solo traveller? Which areas should I avoid at night?"
        assertTrue(TravelSections.asksSafetyVerdict(question))
        assertFalse("an activity's safety is its own passage", TravelSections.asksSafetyVerdict("Is it safe to drive at night in Avelona?"))
        assertFalse(TravelSections.asksSafetyVerdict("Is the tap water safe to drink in Avelona?"))
        val opening = guide("Avelona — Stay safe", "Caution: Caution is recommended when traveling to Avelona because of the high crime rates. Travel to favelas is very dangerous.")
        val areas = guide("Avelona — Stay safe > Dangerous areas", "Avoid the downtown area at night. Crowded areas with good lighting are safer.")
        val carjacking = guide("Avelona — Stay safe > Carjacking", "Keep windows closed at night in traffic, since carjackers target cars with open windows at night.")
        assertTrue(opening in TravelSections.pick(question, "Avelona", listOf(carjacking, areas, opening), 2))
        val lead = TravelSections.lead(question, "Avelona", sources(areas, opening))!!
        assertEquals("S2", lead.citationId)
        assertTrue(lead.text, lead.text.startsWith("Caution is recommended when traveling to Avelona"))
        assertTrue(lead.more.toString(), lead.more.single().let { it.first == "S1" && it.second.startsWith("Avoid the downtown area at night") })
        assertNull("\"safe\" alone answers nothing about tap water", TravelSections.lead("Is the tap water safe to drink in Avelona?", "Avelona", sources(opening, areas)))
    }

    @Test fun aTransitCardIsItsOwnPartAndATransportModeIsContext() {
        val question = "how does public transport work in Avelbul? do I need some kind of card for the metro and ferries"
        assertTrue(TravelSections.lookup(question)!!.sections.contains("Get around"))
        val metro = guide("Avelbul — Get around > By metro", "The old tokens are no longer valid: the only way to pay for the metro is the Avelkart or limited-pass cards from the machines.")
        val boat = guide("Avelbul — Get around > By boat", "Ferries cross the strait every twenty minutes and are the most scenic way to travel.")
        val card = guide("Avelbul — Get around > Avelkart", "The Avelkart is Avelbul's public transport smart card, used on buses, trams, metro and ferries. Buy it at kiosks or station machines.")
        assertEquals(card, TravelSections.pick(question, "Avelbul", listOf(metro, boat, card), 2).first())
        val lead = TravelSections.lead(question, "Avelbul", sources(card, metro))!!
        assertTrue(lead.text, lead.text.startsWith("The Avelkart is Avelbul's public transport smart card"))
        assertTrue("the metro section's own words about the card follow", lead.more.single().second.startsWith("The old tokens are no longer valid"))
        // A train ticket to validate is filed under the train: "Tickets" is a part only when the ticket itself is asked about.
        assertTrue(TravelSections.partHeadings("Do I need to validate my train ticket before boarding in Avelia?").none { it.containsMatchIn("Getting tickets") })
    }

    @Test fun aJourneyReadsTheDestinationFirstWithoutTheTicketPart() {
        val question = "Lisbon to Porto by train, do I need to book ahead or can I just buy a ticket at the station on the day?"
        assertEquals(TravelSections.Journey("Lisbon", "Porto"), TravelSections.journeyOf(question))
        val lookup = TravelSections.lookup(question)!!
        assertEquals(listOf("Porto", "Lisbon"), lookup.places)
        assertTrue(TravelSections.partHeadings(question).any { it.containsMatchIn("By train") })
        assertTrue("a city's fares do not sell the journey", TravelSections.partHeadings(question).none { it.containsMatchIn("Fares and tickets") })
        val rail = guide("Portugal — Get around > By train", "It's recommended to purchase your tickets in advance, due to high demand. If booked five days ahead there is a 40% discount.")
        assertTrue(TravelSections.coversPart(question, "Portugal", listOf(rail)))
        assertTrue(TravelSections.lead(question, "Portugal", sources(rail))!!.text.startsWith("It's recommended to purchase your tickets in advance"))
        assertNull("a flight between two cities names no practical topic", TravelSections.lookup("How can I reduce jet lag after flying from New York to Singapore?"))
        assertNull(TravelSections.journeyOf("Do I need a visa to visit Turkey as a tourist?"))
    }

    @Test fun onlyGuideSectionsArePickedAndAccentsFold() {
        val listing = Evidence("osm-place-w1", "osm-place-w1:0", "New York — Buy Rite Pharmacy", "https://www.openstreetmap.org/way/1", "Destination: New York\nCategory: Buy\nTip: none", 0.0)
        val tipping = guide("New York — Buy > Tipping", "Tip taxi drivers 15 to 20 percent of the fare.")
        assertEquals(listOf(tipping), TravelSections.pick("How much should I tip taxi drivers in New York?", "New York", listOf(listing, tipping), 2))
        assertTrue(TravelSections.isSection("Rome — Get around > By public transport (ATAC)"))
        assertFalse(TravelSections.isSection("New York — Buy Rite Pharmacy"))
        assertEquals("istanbulkart", TravelSections.fold("İstanbulkart"))
        assertEquals("metr", TravelSections.stem("Métro"))
        assertEquals("\"ears\" finds \"eardrums\"", "ear", TravelSections.stem("ears"))
        val fares = guide("Paris — Get around > By public transport > Fares",
            "The card can be loaded with a variety of fares (e.g. Métro-Train-RER Tickets). It cannot be loaded with credit (e.g. €50).\nThe following cards are available to obtain:\n- Navigo Easy")
        val quote = TravelSections.lead("How do I buy a metro ticket in Paris?", "Paris", sources(fares))!!
        assertEquals("The card can be loaded with a variety of fares (e.g. Métro-Train-RER Tickets). It cannot be loaded with credit (e.g. €50).", quote.text)
    }

    @Test fun anAirportQuestionReadsTheAirportGuideAsASecondSource() {
        val question = "what do I need to get a tourist SIM card in India? can I just buy one at Delhi airport"
        assertEquals("Delhi", TravelSections.airportOf(question))
        assertEquals(listOf("Connect"), TravelSections.airportSections(question))
        assertNull("no airport named", TravelSections.airportOf("How do I get a local SIM card in Vietnam?"))
        val delhi = guide("Indira Gandhi International Airport — Overview", "Indira Gandhi International Airport (DEL), the main airport of Delhi, is the primary hub of all national traffic in India.")
        val other = guide("Monterrey International Airport — Overview", "Monterrey International Airport serves Monterrey. Flights to Delhi are rare.")
        assertEquals("Indira Gandhi International Airport", TravelSections.airportGuide("Delhi", listOf(other, delhi)))
        val mobile = guide("India — Connect > By phone > Mobile", "Many mobile stores refuse to sell SIM cards to foreigners without local ID. To get a SIM card as a foreigner, visit the operator's own store.")
        val connect = guide("Indira Gandhi International Airport — Connect",
            "The airport provides free Wi-Fi in all terminals.\n\nSIM cards can also be bought inside the airport:\n- Airtel: Bharti Airtel provides great internet and voice quality all over India.")
        val lead = TravelSections.lead(question, "India", sources(mobile, connect), second = "Indira Gandhi International Airport")!!
        assertTrue(lead.text, lead.text.startsWith("Many mobile stores refuse to sell SIM cards"))
        assertTrue(lead.more.toString(), lead.more.single().let { it.first == "S2" && it.second.startsWith("SIM cards can also be bought inside the airport: Airtel") })
    }

    @Test fun aChoiceBetweenTheQuestionsOwnAlternativesIsNoQuoteOnlyAnswer() {
        assertTrue(TravelSections.asksChoice("The card machine asks if I want to pay in euros or in my home currency. Which one is cheaper?"))
        assertTrue(TravelSections.asksChoice("Should I bring euros to change in Morocco or just take out dirhams from an ATM when I land?"))
        assertFalse(TravelSections.asksChoice("why do my ears pop on planes and why does it hurt more when landing?"))
        assertEquals(listOf("Buy"), TravelSections.topicsOf("The card machine asks if I want to pay in euros or in my home currency. Which one is cheaper?"))
        assertNull("no place, so no place lookup", TravelSections.lookup("The card machine asks if I want to pay in euros or in my home currency. Which one is cheaper?"))
    }

    @Test fun aTopicGuideAnswersOnlyATravellersQuestion() {
        for (question in listOf("The card machine asks if I want to pay in euros or in my home currency. Which one is cheaper?",
            "why do my ears pop on planes, and why does it hurt more when we're landing?",
            "DEET vs picaridin, which mosquito repellent is better for a trip to the tropics?")) {
            assertTrue(question, TravelSections.inTravelContext(question))
        }
        // Shares "healthy" with the Stay healthy topic and nothing with travel.
        assertFalse(TravelSections.inTravelContext("what should I eat to live a long and healthy life?"))
        assertFalse(TravelSections.inTravelContext("Is intermittent fasting better than eating less to lose weight?"))
    }

    @Test fun aSectionReadForAnotherPartDoesNotCoverTheQuestion() {
        fun section(title: String, text: String) = Evidence("wvs-1", "wvs-1:0000", title, "https://en.wikivoyage.org", text, 0.0)
        val parking = section("Manchester — Get in > By car", "On Saturday from 12:30PM to Monday morning you can park on a single yellow line in Salford without paying. Streets like Chapel Street are a good bet.")
        val visa = section("Indonesia — Get in > Visa > Visa on arrival", "A visa on arrival costs Rp 500,000 and is valid for 30 days.")
        // A visa question read into a parking section: the quote cannot stand alone.
        assertFalse(TravelSections.coversPart("I'm a German citizen flying to Manchester for a weekend next month. Do I need a visa or the new ETA?", "Manchester", listOf(parking)))
        assertTrue(TravelSections.coversPart("Flying into Bali on a UK passport. Can I get a visa on arrival and how much is it?", "Indonesia", listOf(visa)))
    }
}
