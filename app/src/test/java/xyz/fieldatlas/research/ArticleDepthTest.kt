package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDepthTest {
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
        // The cryptography collection's own copy of a Wikipedia article is not given depth.
        val crypto = passage("wp-99-0000:0000", "Blockchain — Overview", "A blockchain is a list of records.")
        assertTrue(ArticleDepth.pick("How does a blockchain work?", listOf(crypto),
            mapOf("Blockchain" to listOf(passage("enw-77-0003:0000", "Blockchain — Structure", "Blocks hold batches of transactions."))), listOf(crypto), 10_000).isEmpty())
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
