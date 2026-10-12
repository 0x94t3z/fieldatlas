package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDepthTest {
    @Test fun flattenedBibliographyDoesNotBecomeAnswerDepth() {
        val lead = passage("swp-42-0000:0000", "Chloroplast — Overview", "Chloroplasts conduct photosynthesis.")
        val body = passage("swp-42-0001:0000", "Chloroplast — Details", "Thylakoids contain chlorophyll pigments that capture light. doi:10.123/example")
        val references = passage("swp-42-0001:0002", "Chloroplast — Details",
            "Role of chloroplast retention. doi:10.123/one. PMID 12345. {{cite journal}}: CS1 maint: multiple names. doi:10.123/two.")
        val question = "What role do chloroplasts play in photosynthesis?"
        val articles = mapOf("Chloroplast" to listOf(lead, references, body))
        val ranked = ArticleDepth.ranked(question, "Chloroplast", articles.getValue("Chloroplast"))
        assertTrue(body in ranked)
        assertFalse(references in ranked)
        val prompt = ArticleDepth.pickForPrompt(question, listOf(lead), articles, 3600)
        assertTrue(body in prompt)
        assertFalse(references in prompt)
    }
    @Test fun anArticleNamedInAConditionCanSupplyDetailWithoutPromotingGenericKeywordHits() {
        val membrane = passage("wp-420-0000:0000", "Sealing membrane — Overview", "A sealing membrane is a component.")
        val pressure = passage("wp-421-0000:0000", "Pressure — Overview", "Pressure is force over area.")
        val other = passage("wp-422-0000:0000", "Industrial enclosure — Overview", "Enclosures may use sealing membranes.")
        val guide = passage("wvs-420-0000:0000", membrane.title, "A similarly named travel guide.")
        val question = "Why can an enclosure leak even when its sealing membrane has a high pressure-resistance rating?"
        assertEquals(listOf(membrane), ArticleDepth.supportingSubjects(question,
            listOf(pressure, other, guide, membrane), emptyList()))
        assertEquals(listOf(membrane), ArticleDepth.supportingSubjects("According to the saved sources, $question",
            listOf(pressure, membrane), emptyList()))
        assertTrue(ArticleDepth.supportingSubjects(question, listOf(membrane), listOf(membrane)).isEmpty())
        assertTrue(ArticleDepth.supportingSubjects("Where can I buy sealing membrane?", listOf(membrane), emptyList()).isEmpty())
    }

    @Test fun supplementaryArticlesMatchWholeOrderedWordsAndRespectTheSubjectBudget() {
        val titles = listOf("Alpha system", "Beta system", "Gamma system", "Systematic alpha")
        val rows = titles.mapIndexed { index, title -> passage("wp-${500 + index}-0000:0000", "$title — Overview", title) }
        val question = "Why can alpha systems fail with beta system and gamma system?"
        assertEquals(setOf("Beta system — Overview", "Gamma system — Overview"),
            ArticleDepth.supportingSubjects(question, rows, emptyList()).map { it.title }.toSet())
        val existing = rows.take(2)
        assertEquals(listOf(rows[2]), ArticleDepth.supportingSubjects(question, rows.reversed(), existing))
        assertTrue(ArticleDepth.supportingSubjects(question, rows, rows.take(3)).isEmpty())
        val duplicate = rows[1].copy(chunkId = "wp-501-0000:0002", text = "Later same article.")
        assertEquals(listOf(rows[1]), ArticleDepth.supportingSubjects("Why can beta-system fail?",
            listOf(duplicate, rows[1]), emptyList()))
    }

    @Test fun aLaterOverviewDetailBeatsTheOpeningDefinitionAndKeepsTheQualification() {
        val question = "Why can an enclosure leak even when its sealing membrane has a high pressure-resistance rating?"
        val overview = passage("wp-600-0000:0000", "Sealing membrane — Overview",
            "A sealing membrane is used in industrial components. It resists penetration under laboratory conditions.")
        val unrelated = passage("wp-600-0000:0001", overview.title,
            "The first materials were developed by a manufacturing company during the previous century.")
        val detail = passage("wp-600-0000:0002", overview.title,
            "An enclosure can leak through a joint. A membrane pressure-resistance rating tests only the material, not the complete enclosure. " +
                "However, this comparison does not establish the performance of every enclosure design.")
        val selected = ArticleDepth.pickForPrompt(question, listOf(overview),
            mapOf("Sealing membrane" to listOf(overview, unrelated, detail)), detail.text.length)
        assertEquals(listOf(detail), selected)
        val packed = PromptBuilder.buildKnowledge(question, listOf(unrelated, overview), listOf(overview), selected, 2048, 5.0)
        val source = packed.sources.single { it.evidence.chunkId == detail.chunkId }
        assertEquals(detail, source.evidence)
        assertEquals(detail.text, source.excerpt)
        assertEquals(listOf(detail), packed.sources.map { it.evidence })
    }

    @Test fun detailSelectionDoesNotInventAPassageWhenTheNamedArticleIsMissing() {
        val question = "Why can an enclosure leak even when its sealing membrane has a high pressure-resistance rating?"
        val unrelated = passage("wp-700-0000:0000", "Pressure — Overview", "Pressure is force over area.")
        assertTrue(ArticleDepth.supportingSubjects(question, listOf(unrelated), emptyList()).isEmpty())
        val overview = passage("wp-701-0000:0000", "Sealing membrane — Overview", "A sealing membrane is a component.")
        assertTrue(ArticleDepth.pickForPrompt(question, listOf(overview),
            mapOf("Sealing membrane" to listOf(overview)), 1000).isEmpty())
        assertTrue(ArticleDepth.pickForPrompt(question, listOf(overview),
            mapOf("Sealing membrane" to listOf(unrelated)), 1000).isEmpty())
    }

    @Test fun aConditionalQuoteKeepsItsFullQualificationBeyondTheSoftReadingBudget() {
        val question = "Why can an enclosure leak even when its sealing membrane has a high pressure-resistance rating?"
        val overview = passage("wp-710-0000:0000", "Sealing membrane — Overview", "A sealing membrane is a material.")
        val body = "An enclosure can leak through a joint. The pressure-resistance rating tests only the material, not the complete enclosure." +
            " The material remains subject to the stated conditions.".repeat(18)
        assertTrue(body.length in 1001..1200)
        val detail = overview.copy(chunkId = "wp-710-0000:0002", text = body)
        val irrelevant = overview.copy(chunkId = "wp-710-0000:0001", text = "The history of the component is documented elsewhere.")
        val selected = ArticleDepth.pickForPrompt(question, listOf(overview),
            mapOf("Sealing membrane" to listOf(overview, irrelevant, detail)), ArticleDepth.budget(10.0))
        assertEquals(listOf(detail), selected)
        val packed = PromptBuilder.buildKnowledge(question, listOf(overview), listOf(overview), selected, 2048, 1.0)
        assertEquals(body, packed.sources.single().excerpt)
        assertEquals(detail, packed.sources.single().evidence)
        val tooSmall = PromptBuilder.buildKnowledge(question, listOf(overview), listOf(overview), selected, 128, 1.0)
        assertTrue("A hard context limit must omit the complete unit, never clip its qualification", tooSmall.sources.isEmpty())
        assertTrue(PreciseReferenceAnswer.conditionalAnswer(question, tooSmall, listOf(overview))!!.contains("do not establish"))
        assertTrue("Ordinary explanations retain their existing soft budget", ArticleDepth.pickForPrompt(
            "How does a sealing membrane limit pressure?", listOf(overview),
            mapOf("Sealing membrane" to listOf(overview, detail)), 1000).isEmpty())
    }

    @Test fun aGeneralSupplyPhraseDoesNotDisplaceTheOperatingMechanism() {
        val overview = passage("swp-201-0000:0000", "Well — Overview", "A well is a water supply system.")
        val benefits = passage("enw-201-0000:0001", "Well — Overview", "The well supplies water. Its water supply is fairly resistant to disasters.")
        val mechanism = passage("enw-201-0001:0000", "Well — Features", "Water drains by gravity through the underground channel.")
        val picked = ArticleDepth.pickForPrompt("How does a well supply water?", listOf(overview),
            mapOf("Well" to listOf(benefits, mechanism)), 100)
        assertEquals(mechanism, picked.first())
    }
    @Test fun requestedProcessInTheSelectedTextOutranksAGenericStructureHeading() {
        val overview = passage("swp-200-0000:0000", "Plant — Overview", "A plant is a living organism.")
        val structure = passage("enw-200-0001:0000", "Plant — Internal structure", "A plant consists of cells.")
        val nutrients = passage("enw-200-0000:0001", "Plant — Overview", "A plant absorbs nutrients through its roots.")
        val picked = ArticleDepth.pickForPrompt("How does a plant obtain nutrients?", listOf(overview),
            mapOf("Plant" to listOf(structure, nutrients)), 100)
        assertEquals(nutrients, picked.first())
    }
    @Test fun promptDepthKeepsTheCompleteMechanismUnitAndOriginalViewerText() {
        val intro = "A heat pump moves heat between locations."
        val history = "Historical inventors used different designs. ".repeat(14).trim()
        val mechanism = "A heat pump transfers heat through a refrigerant cycle."
        val caveat = "However, performance depends on operating conditions."
        val overview = passage("swp-100-0000:0000", "Heat pump — Overview", intro)
        val full = passage("enw-100-0004:0000", "Heat pump — Operating principle", "$history\n$mechanism\n$caveat")
        val picked = ArticleDepth.pickForPrompt("How does a heat pump transfer heat?", listOf(overview),
            mapOf("Heat pump" to listOf(overview, full)), 350)
        assertEquals(listOf(full), picked)
        val packed = PromptBuilder.buildKnowledge("How does a heat pump transfer heat?",
            listOf(overview, full), listOf(overview), picked, 2048, 15.0)
        assertEquals(full, packed.sources[1].evidence)
        assertTrue(packed.sources[1].excerpt.contains(mechanism))
        assertTrue(packed.sources[1].excerpt.contains(caveat))
        assertFalse(packed.sources[1].excerpt.contains(history))
    }
    private fun passage(id: String, title: String, text: String, source: String = "https://en.wikipedia.org/w/index.php?oldid=1") =
        Evidence(id.substringBefore(':'), id, title, source, text, 0.0)

    // A lead split by the chunker: the second chunk repeats the last 150 characters of the first.
    private val leadStart = "In economics, inflation is an increase in the average price of goods and services. " + "x".repeat(200)
    private val leadEnd = leadStart.takeLast(150) + " It is typically caused by excessive money supply growth.\n\nAlso known as: Price inflation"
    private val simple = passage("swp-14655-0000:0000", "Inflation — Overview",
        "Inflation means that the general level of prices is going up.", "https://simple.wikipedia.org/w/index.php?oldid=2")
    private val inflation = listOf(
        simple,
        passage("enw-15384-0000:0000", "Inflation — Overview", leadStart),
        passage("enw-15384-0000:0001", "Inflation — Overview", leadEnd),
        passage("enw-15384-0004:0000", "Inflation — History > Ancient Europe", "Alexander the Great's conquest led to inflation in Greece."),
        passage("enw-15384-0021:0000", "Inflation — Causes > Demand-pull", "Demand-pull inflation happens when demand outpaces supply."),
        passage("enw-15384-0030:0000", "Inflation — Measures", "Consumer price indices measure the price of a basket of goods."),
    )

    @Test fun theEnglishLeadComesWholeThenTheSectionThatAnswersWhy() {
        val picked = ArticleDepth.pick("Why does inflation happen?", listOf(simple), mapOf("Inflation" to inflation), listOf(simple), 10_000)
        assertEquals(listOf("enw-15384-0000:0000", "enw-15384-0021:0000"), picked.take(2).map { it.chunkId })
        val lead = picked.first().text
        assertTrue(lead.startsWith("In economics"))
        assertTrue(lead.endsWith("excessive money supply growth."))
        assertEquals(leadStart.length + leadEnd.length - 150 - "\n\nAlso known as: Price inflation".length, lead.length)
        assertFalse(lead.contains("Also known as"))
        assertTrue("history comes after causes", picked.indexOfFirst { "History" in it.title } > 1)
    }

    @Test fun onlyEncyclopediaRowsOfTheSubjectsArticleAreRead() {
        val guide = passage("wvs-55-0000:0000", "Inflation — Overview", "A travel guide that happens to share the name.", "https://en.wikivoyage.org/w/index.php?oldid=5")
        val picked = ArticleDepth.pick("Why does inflation happen?", listOf(simple), mapOf("Inflation" to listOf(simple, guide)), listOf(simple), 10_000)
        assertTrue(picked.isEmpty())
        // The cryptography collection's own Wikipedia articles are given depth like the others.
        val crypto = passage("wp-99-0000:0000", "Blockchain — Overview", "A blockchain is a list of records.")
        val structure = passage("wp-99-0003:0000", "Blockchain — Structure", "Blocks hold batches of transactions.")
        assertTrue(ArticleDepth.isEncyclopedia(crypto))
        assertEquals(listOf(structure.chunkId), ArticleDepth.pick("How does a blockchain work?", listOf(crypto),
            mapOf("Blockchain" to listOf(crypto, structure)), listOf(crypto), 10_000).map { it.chunkId })
        assertFalse(ArticleDepth.isEncyclopedia(passage("eip-01559-000:0000", "EIP-1559 — Abstract", "A fee market change.")))
    }

    @Test fun aLookupsLoneCommonNounReadsNoDepthAndTheNamedSubjectReadsMost() {
        val baikal = passage("swp-20092-0000:0000", "Lake Baikal — Overview", "Lake Baikal is a lake in Siberia.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val water = passage("swp-939-0000:0000", "Water — Overview", "Water is a chemical compound.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val depthSection = passage("enw-18213-0002:0000", "Lake Baikal — Geography and hydrography > Water characteristics", "Baikal is the deepest lake in the world at 1,642 m and holds 23,615 km3 of fresh water.")
        val density = passage("enw-33306-0007:0000", "Water — Properties > States > Density", "Water is densest at 4 °C.")
        val question = "How deep is Lake Baikal, and how much of the world's fresh water does it hold?"
        val picked = ArticleDepth.pick(question, listOf(baikal, water), mapOf("Lake Baikal" to listOf(baikal, depthSection), "Water" to listOf(water, density)), listOf(baikal, water), 10_000)
        assertEquals(listOf(depthSection.chunkId), picked.map { it.chunkId })
        // Two common nouns compared read alike; a lookup of two common nouns reads both.
        val lion = passage("swp-1-0000:0000", "Lion — Overview", "The lion is a large cat.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val tiger = passage("swp-2-0000:0000", "Tiger — Overview", "The tiger is the largest cat.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val lionMore = passage("enw-1-0003:0000", "Lion — Description", "Adult male lions weigh 150 to 250 kg.")
        val tigerMore = passage("enw-2-0002:0000", "Tiger — Characteristics", "Male Bengal tigers weigh 200 to 260 kg.")
        val cats = ArticleDepth.pick("are lions or tigers heavier?", listOf(lion, tiger), mapOf("Lion" to listOf(lion, lionMore), "Tiger" to listOf(tiger, tigerMore)), listOf(lion, tiger), 10_000)
        assertEquals(setOf(lionMore.chunkId, tigerMore.chunkId), cats.map { it.chunkId }.toSet())
        // The named subject reads 60% of a tight budget; the other reads what is left.
        val union = passage("swp-3600-0000:0000", "Soviet Union — Overview", "The Soviet Union was a country.", "https://simple.wikipedia.org/w/index.php?oldid=1")
        val dissolution = passage("enw-40494892-0000:0000", "Dissolution of the Soviet Union — Overview", "The Soviet Union was dissolved in 1991.")
        val unionSections = (1..4).map { passage("enw-26779-000$it:0000", "Soviet Union — History > Part $it", "The collapse came in stage $it. " + "x".repeat(260)) }
        val dissolutionSections = (1..4).map { passage("enw-40494892-000$it:0000", "Dissolution of the Soviet Union — Causes > Cause $it", "The collapse had cause $it. " + "x".repeat(260)) }
        val shared = ArticleDepth.pick("Why did the Soviet Union collapse?", listOf(union, dissolution),
            mapOf("Soviet Union" to listOf(union) + unionSections, "Dissolution of the Soviet Union" to listOf(dissolution) + dissolutionSections), listOf(union, dissolution), 1_000)
        assertEquals(2, shared.count { it.documentId.startsWith("enw-26779") })
        assertEquals(1, shared.count { it.documentId.startsWith("enw-40494892") })
    }

    @Test fun theLeadChunkIsCutAtASentenceRatherThanLost() {
        val sentence = "The Global Positioning System is a satellite-based navigation system owned by the United States Space Force. "
        val lead = passage("enw-11866-0000:0000", "Global Positioning System — Overview", sentence.repeat(6).trim() + "\n\nAlso known as: GPS")
        val principles = passage("enw-11866-0007:0000", "Global Positioning System — Principles", "The GPS satellites carry very stable atomic clocks. " + "word ".repeat(200))
        val picked = ArticleDepth.pick(gpsQuestion, listOf(gpsSimple), mapOf("Global Positioning System" to listOf(gpsSimple, lead, principles)), listOf(gpsSimple), 1_500)
        assertEquals(listOf(principles.chunkId, lead.chunkId), picked.map { it.chunkId })
        val cut = picked.last().text
        assertTrue(cut, cut.endsWith("Space Force."))
        assertTrue(cut.length in ArticleDepth.MIN_CUT..(1_500 - principles.text.length))
        assertFalse(cut.contains("Also known as"))
        // Less than the minimum would say only what the subject is: nothing is cut.
        assertEquals(null, ArticleDepth.truncated(lead, 200))
        assertEquals(null, ArticleDepth.truncated(lead, sentence.length + 10))
    }

    @Test fun whatHappenedAndWhatItCostsAreAnsweredUnderTheirHeadings() {
        val impact = passage("enw-68915-0009:0000", "Aral Sea — Impact on environment, economy, and public health", "The shrinking of the sea left salt flats and a ruined fishing industry.")
        val solutions = passage("enw-68915-0017:0000", "Aral Sea — Solutions", "The Kok-Aral Dam raised the North Aral Sea's level and fish returned.")
        val ecology = passage("enw-68915-0003:0000", "Aral Sea — Ecology > Native fish", "The sea had 20 native fish species.")
        val ranked = ArticleDepth.ranked("what happened to the aral sea? why did it shrink so much and has any of it come back?", "Aral Sea", listOf(ecology, solutions, impact))
        assertEquals(setOf(impact.chunkId, solutions.chunkId), ranked.take(2).map { it.chunkId }.toSet())
        assertEquals(ecology.chunkId, ranked.last().chunkId)
        val gas = passage("wp-9966-0005:0000", "Ethereum — Design > Gas", "Gas is the unit that measures the computational work of a transaction; its fee is paid in ether.")
        val history = passage("wp-9966-0002:0000", "Ethereum — History > Founding", "Ethereum was proposed in 2013 by Vitalik Buterin.")
        assertEquals(gas, ArticleDepth.answering("Why are Ethereum gas fees sometimes so high?", "Ethereum", listOf(history, gas)))
        val health = passage("enw-904269-0004:0000", "Red wine — Health effects", "Moderate consumption has been studied for cardiovascular effects; the evidence is mixed.")
        assertEquals(health, ArticleDepth.answering("Is a glass of red wine a day actually good for your heart? What does the current evidence say?", "Red wine", listOf(health)))
    }

    @Test fun theBudgetIsKeptWithoutCuttingAPassage() {
        val picked = ArticleDepth.pick("Why does inflation happen?", listOf(simple), mapOf("Inflation" to inflation), listOf(simple), 160)
        assertTrue(picked.sumOf { it.text.length } <= 160)
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.all { piece -> inflation.any { it.chunkId == piece.chunkId && it.text.startsWith(piece.text.take(40)) } })
    }

    @Test fun twoSubjectsTakeTurns() {
        val cold = passage("swp-5-0000:0000", "Common cold — Overview", "The common cold is a viral infection.", "https://simple.wikipedia.org/w/index.php?oldid=3")
        val flu = passage("swp-6-0000:0000", "Influenza — Overview", "Influenza is an infectious disease.", "https://simple.wikipedia.org/w/index.php?oldid=4")
        val coldMore = passage("enw-7-0001:0000", "Common cold — Signs and symptoms", "The typical symptoms include cough and runny nose.")
        val fluMore = passage("enw-8-0001:0000", "Influenza — Signs and symptoms", "Symptoms begin suddenly with fever and muscle pain.")
        val picked = ArticleDepth.pick("What is the difference between a common cold and the flu?", listOf(cold, flu),
            mapOf("Common cold" to listOf(cold, coldMore), "Influenza" to listOf(flu, fluMore)), listOf(cold, flu), 10_000)
        assertEquals(listOf(coldMore.chunkId, fluMore.chunkId), picked.map { it.chunkId })
    }

    @Test fun nothingIsReadWhenThereIsNoRoom() {
        assertTrue(ArticleDepth.pick("Why does inflation happen?", listOf(simple), mapOf("Inflation" to inflation), listOf(simple), 0).isEmpty())
    }

    @Test fun theDepthFitsAboutTwentySecondsOfReadingOnThisPhone() {
        assertEquals(2_000, ArticleDepth.budget(null))
        assertEquals(1_360, ArticleDepth.budget(17.0))   // a Redmi 13C reads about 17 tokens a second
        assertEquals(1_000, ArticleDepth.budget(5.0))
        assertEquals(3_600, ArticleDepth.budget(120.0))
    }

    // Real-shaped rows of the English article on GPS: a 930-character lead chunk and the sections.
    private fun gps(id: String, title: String, length: Int, opening: String) =
        passage("enw-11866-$id", "Global Positioning System — $title", (opening + " " + "word ".repeat(300)).take(length).trimEnd() + ".")
    private val gpsLead = gps("0000:0000", "Overview", 930, "The Global Positioning System (GPS) is a satellite-based hyperbolic navigation system owned by the United States Space Force.")
    private val gpsPrinciples = gps("0007:0000", "Principles", 1176, "The GPS satellites carry very stable atomic clocks that are synchronized with one another.")
    private val gpsGeometry = gps("0009:0000", "Principles > User-satellite geometry", 1108, "Although usually not formed explicitly in the receiver processing, the conceptual time differences of arrival define the measurement geometry.")
    private val gpsApplications = gps("0011:0000", "Principles > Non-navigation applications", 643, "GPS requires four or more satellites to be visible for accurate navigation. The signal carries the time.")
    private val gpsStructure = gps("0012:0000", "Structure", 392, "The current GPS consists of three major segments.")
    private val gpsSimple = passage("swp-82503-0000:0000", "Global Positioning System — Overview",
        "A Global Positioning System, also known as GPS, is a system of satellites designed to help navigate.", "https://simple.wikipedia.org/w/index.php?oldid=2")
    private val gpsArticle = listOf(gpsSimple, gpsLead, gpsPrinciples, gpsGeometry, gpsApplications, gpsStructure)
    private val gpsQuestion = "How does GPS on my phone work when I have no internet or signal?"

    @Test fun theSectionThatAnswersFitsBeforeTheLeadEatsTheBudget() {
        // On a phone of unknown speed the budget is 2000 characters: the lead chunk and "Principles" cannot both fit.
        val picked = ArticleDepth.pick(gpsQuestion, listOf(gpsSimple), mapOf("Global Positioning System" to gpsArticle), listOf(gpsSimple), 2_000)
        assertEquals(listOf(gpsPrinciples.chunkId, gpsApplications.chunkId), picked.map { it.chunkId })
        assertTrue(picked.sumOf { it.text.length } <= 2_000)
        // With room for both, the lead chunk comes first and the section's own heading outranks its subsections.
        val roomy = ArticleDepth.pick(gpsQuestion, listOf(gpsSimple), mapOf("Global Positioning System" to gpsArticle), listOf(gpsSimple), 10_000)
        assertEquals(listOf(gpsLead.chunkId, gpsPrinciples.chunkId), roomy.take(2).map { it.chunkId })
        assertTrue(roomy.indexOfFirst { it.chunkId == gpsStructure.chunkId } > roomy.indexOfFirst { it.chunkId == gpsApplications.chunkId })
    }

    @Test fun aSectionsChunksStayTogetherBehindItsBestChunk() {
        val colorsFirst = passage("enw-190933-0003:0000", "Sunset — Colors", "As a ray of white sunlight travels through the atmosphere to an observer, " + "x".repeat(300))
        val colorsSecond = passage("enw-190933-0003:0001", "Sunset — Colors", "x".repeat(150) + " some of the colors are scattered out of the beam, changing the final red and orange color the viewer sees.")
        val occurrence = passage("enw-190933-0001:0000", "Sunset — Occurrence", "The time of sunset varies throughout the year.")
        val ranked = ArticleDepth.ranked("Why are sunsets red and orange?", "Sunset", listOf(occurrence, colorsSecond, colorsFirst))
        assertEquals(listOf(colorsFirst.chunkId, colorsSecond.chunkId, occurrence.chunkId), ranked.map { it.chunkId })
    }

    @Test fun theAnsweringSectionIsNamedByItsOwnHeading() {
        assertEquals(gpsPrinciples, ArticleDepth.answering(gpsQuestion, "Global Positioning System", gpsArticle))
        val onion = passage("enw-51258-0011:0000", "Onion — Adverse effects and toxicity > Eye irritation", "Freshly cut onions can produce a stinging sensation in the eyes of people nearby and often uncontrollable tears.")
        val culinary = passage("enw-51258-0005:0000", "Onion — Uses > Culinary", "Onions are commonly chopped and used as an ingredient in various hearty warm dishes; people cut them and cry.")
        assertEquals(onion, ArticleDepth.answering("why do onions make you cry when you cut them?", "Onion", listOf(culinary, onion)))
        val cyclone = passage("enw-8282374-0001:0000", "Tropical cyclone — Definition and terminology", "A tropical cyclone is the generic term; a hurricane is one in the North Atlantic and a typhoon one in the Northwest Pacific.")
        assertEquals(cyclone, ArticleDepth.answering("whats the actual difference between a hurricane, a typhoon and a cyclone", "Tropical cyclone", listOf(cyclone)))
        // Sharing the question's words is not answering it; a later chunk does not open the section.
        val environment = passage("enw-26779-0004:0000", "Soviet Union — Geography > Environment", "The collapse of the Soviet Union left polluted sites.")
        assertEquals(null, ArticleDepth.answering("Why did the Soviet Union collapse?", "Soviet Union", listOf(environment)))
        val dissolution = passage("enw-26779-0040:0000", "Soviet Union — History > Dissolution", "The Soviet Union was dissolved on 26 December 1991.")
        assertEquals(dissolution, ArticleDepth.answering("Why did the Soviet Union collapse?", "Soviet Union", listOf(environment, dissolution)))
        assertEquals(null, ArticleDepth.answering(gpsQuestion, "Global Positioning System", listOf(gpsPrinciples.copy(chunkId = "enw-11866-0007:0001"))))
        assertEquals(null, ArticleDepth.answering(gpsQuestion, "Onion", gpsArticle))
    }
}
