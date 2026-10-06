package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class PhrasebookTest {
    private fun row(id: String, heading: String, text: String, book: String = "Japanese phrasebook") =
        Evidence(id.substringBefore(':'), id, "$book — $heading", "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)

    private val overview = row("wvs-16153-0000:0000", "Overview", "Japanese is spoken in Japan.")
    private val signs = row("wvs-16153-0039:0000", "Phrase list", "Common signs\n- 営業中 (eigyōchū) : Open\n- 出口 (deguchi) : Exit")
    private val basics = row("wvs-16153-0040:0000", "Phrase list > Basics", listOf(
        "- Hello. : こんにちは。 Konnichiwa. (kon-nee-chee-wah)",
        "- Fine, thank you. : はい、元気です。 Hai, genki desu.",
        "- Thank you. (less formal) : ありがとうございます。 Arigatō gozaimasu.",
        "- Thank you. (normal) : ありがとう。 Arigatō.",
        "- Please. : お願いします。 Onegai shimasu.",
        "- Excuse me. : すみません。 Sumimasen.",
        "- Do you speak English? : 英語を話せますか? Eigo o hanasemasu ka?",
        "- Where is the toilet? : トイレはどこですか? Toire wa doko desu ka?",
    ).joinToString("\n"))
    private val eating = row("wvs-16153-0050:0000", "Phrase list > Eating", listOf(
        "- I'm a vegetarian. : 私はベジタリアンです。 Watashi wa bejitarian desu.",
        "- The check, please. : お勘定お願いします。 O-kanjō onegai shimasu.",
    ).joinToString("\n"))
    private val grammar = row("wvs-16153-0010:0000", "Grammar", "- wa : marks the topic of a sentence")

    @Test fun aPhraseRequestNamesItsLanguageOrCountry() {
        assertEquals(Phrasebook.Ask("Thai", "thank you"), Phrasebook.ask("How do I say thank you in Thai?"))
        assertEquals(Phrasebook.Ask("Japanese", "I'm vegan"), Phrasebook.ask("how do you say 'I'm vegan' in Japanese"))
        assertEquals(Phrasebook.Ask("Spanish", "where is the toilet"), Phrasebook.ask("Translate where is the toilet into Spanish"))
        assertEquals(Phrasebook.Ask("Japanese", "excuse me"), Phrasebook.ask("What's \"excuse me\" in Japanese?"))
        assertEquals(Phrasebook.Ask("Thai", "the bill"), Phrasebook.ask("How do I ask for the bill in Thai please?"))
        assertEquals(Phrasebook.Ask("Japan", "thank you"), Phrasebook.ask("How do I say thank you in Japan?"))
        assertEquals(Phrasebook.Ask("Vietnam", null), Phrasebook.ask("Any useful phrases for Vietnam?"))
        assertEquals(Phrasebook.Ask("Korean", null), Phrasebook.ask("teach me some basic Korean phrases"))
        assertEquals(listOf("Egyptian Arabic phrasebook", "Arabic phrasebook"), Phrasebook.books("Egypt"))
        assertEquals(listOf("Chinese phrasebook"), Phrasebook.books("Mandarin"))
        // Not requests for words in a language.
        assertNull(Phrasebook.ask("What is the population in Japan?"))
        assertNull(Phrasebook.ask("How many words are there in Japanese?"))
        assertNull(Phrasebook.ask("How do I say no to a tout politely?"))
        assertNull(Phrasebook.ask("How do I get a SIM card in Japan?"))
        // Ordering or asking for something in a country is how it works there, not a phrase.
        assertNull(Phrasebook.ask("How do I order a taxi in Thailand?"))
        assertNull(Phrasebook.ask("How do I ask for a visa extension in Thailand?"))
        assertEquals(Phrasebook.Ask("Thai", "the bill"), Phrasebook.ask("How do I ask for the bill in Thai?"))
        assertEquals("My friend is choking,", Phrasebook.withoutRequest("My friend is choking, how do I say help in Thai?"))
        // Part of a place name is not a language, and a question about a thing is not a word request.
        assertNull(Phrasebook.ask("What is the emergency number in French Polynesia?"))
        assertNull(Phrasebook.ask("What is the speed limit in German towns?"))
        assertNull(Phrasebook.ask("What are some Thai words that are considered rude?"))
        assertNull(Phrasebook.ask("How do I write a for loop in Java?"))
        assertEquals(Phrasebook.Ask("French", "bread"), Phrasebook.ask("What's the word for bread in French?"))
    }

    @Test fun aPhrasebookIsAskedForWithAWordBeforeItsLanguage() {
        assertEquals(Phrasebook.Ask("Thai", null), Phrasebook.ask("Do you have a Thai phrasebook?"))
        assertEquals(Phrasebook.Ask("Japanese", null), Phrasebook.ask("Show me the Japanese phrasebook"))
        assertEquals(Phrasebook.Ask("Brazilian Portuguese", null), Phrasebook.ask("Open my Brazilian Portuguese phrasebook"))
        assertEquals(Phrasebook.Ask("Thai", null), Phrasebook.ask("I need Thai phrasebook"))
        assertEquals(Phrasebook.Ask("Thai", null), Phrasebook.ask("Thai phrasebook"))
        assertNull(Phrasebook.ask("Do you have a travel phrasebook?"))
    }

    @Test fun aLineIsTheWholeAnswerOnlyWhenItIsExactlyThePhrase() {
        val bars = row("wvs-1-0070:0000", "Phrase list > Bars", "- Cheers! : Prost!\n- cheese : Käse (KAY-zuh)", "German phrasebook")
        assertEquals("Cheers! : Prost!", Phrasebook.match("cheers", listOf(bars))!!.lines.single().text)
        val phone = row("wvs-2-0040:0000", "Phrase list > Basics", "- Hello (only on the phone) : もしもし moshi moshi")
        val hello = Phrasebook.match("hello", listOf(phone))!!
        assertFalse("a line limited to the phone is not the whole answer", hello.exact)
        assertTrue(Phrasebook.match("thank you", listOf(basics))!!.exact)
        assertFalse(Phrasebook.match("food", listOf(row("wvs-3-0050:0000", "Phrase list > Eating", "- food coloring : 人工着色料")))?.exact ?: false)
    }

    @Test fun theWholeAnswerSaysEachAskedWordAndNothingElseFillerIncluded() {
        val problems = row("wvs-16153-0060:0000", "Phrase list > Problems", listOf(
            "- Help! : 助けて! Tasukete!",
            "- How? : どうやって? Dōyatte",
            "- How are you? : お元気ですか? O-genki desu ka?",
            "- Who? : 誰? Dare?",
        ).joinToString("\n"))
        // "Help!" is the shout: the nearest line, but not "can you help me".
        val canYouHelp = Phrasebook.match("can you help me", listOf(problems))!!
        assertEquals(listOf("Help! : 助けて! Tasukete!"), canYouHelp.lines.map { it.text })
        assertFalse(canYouHelp.exact)
        assertTrue(Phrasebook.match("help", listOf(problems))!!.exact)
        val howAreYou = Phrasebook.match("how are you", listOf(problems))!!
        assertEquals(listOf("How are you? : お元気ですか? O-genki desu ka?"), howAreYou.lines.map { it.text })
        assertTrue(howAreYou.exact)
        assertFalse(Phrasebook.match("who are you", listOf(problems))!!.exact)
        // "The check, please." says the bill and nothing else.
        assertTrue(Phrasebook.match("the bill", listOf(eating))!!.exact)
    }

    @Test fun checkingInIsNotAskingForTheBill() {
        val lodging = row("wvs-16153-0045:0000", "Phrase list > Lodging", "- I want to check out. : チェックアウトしたいです。 Chekkuauto shitai desu.")
        val rows = listOf(eating, lodging)
        assertNull(Phrasebook.match("check in", rows))
        assertNull(Phrasebook.match("can I check in", rows))
        assertNull(Phrasebook.match("I want to check in", rows))
        assertNull(Phrasebook.match("check-in", rows))
        val checkOut = Phrasebook.match("I want to check out", rows)!!
        assertEquals(listOf("I want to check out. : チェックアウトしたいです。 Chekkuauto shitai desu."), checkOut.lines.map { it.text })
        assertTrue(checkOut.exact)
        assertEquals("The check, please. : お勘定お願いします。 O-kanjō onegai shimasu.", Phrasebook.match("the check", rows)!!.lines.single().text)
    }

    @Test fun aPhraseOfFillerWordsIsMatchedOnItsOwnWords() {
        val please = Phrasebook.match("please", listOf(basics, eating))!!
        assertEquals(listOf("Please. : お願いします。 Onegai shimasu."), please.lines.map { it.text })
        assertTrue(please.exact)
    }

    @Test fun theLineThatSaysItIsFoundWithItsVariants() {
        val rows = listOf(signs, basics, eating)
        val thanks = Phrasebook.match("thank you", rows)!!
        assertEquals(setOf("Thank you. (normal) : ありがとう。 Arigatō.", "Thank you. (less formal) : ありがとうございます。 Arigatō gozaimasu."),
            thanks.lines.map { it.text }.toSet())
        assertFalse(thanks.near)
        assertEquals("Where is the toilet? : トイレはどこですか? Toire wa doko desu ka?", Phrasebook.match("where's the bathroom", rows)!!.lines.single().text)
        assertEquals("The check, please. : お勘定お願いします。 O-kanjō onegai shimasu.", Phrasebook.match("the bill", rows)!!.lines.single().text)
        // A sign comes before its meaning.
        assertEquals("出口 (deguchi) : Exit", Phrasebook.match("exit", rows)!!.lines.single().text)
        // "Vegetarian" is only the closest line for "vegan".
        assertTrue(Phrasebook.match("I'm vegan", rows)!!.near)
        assertNull(Phrasebook.match("I'm allergic to peanuts", rows))
    }

    @Test fun aRequestForTheBasicsGetsTheEverydayLines() {
        assertEquals(listOf("Hello.", "Thank you. (normal)", "Please.", "Excuse me.", "Do you speak English?", "Where is the toilet?"),
            Phrasebook.basics(listOf(signs, basics, eating)).map { it.english })
        assertTrue(Phrasebook.isPhraseList(basics, "Japanese phrasebook"))
        assertFalse(Phrasebook.isPhraseList(grammar, "Japanese phrasebook"))
        assertFalse(Phrasebook.isPhraseList(overview, "Japanese phrasebook"))
    }

    @Test fun theBasicsAreTheEverydayLinesNotTheNarrowOnes() {
        val japanese = row("wvs-16153-0040:0000", "Phrase list > Basics", listOf(
            "- Hello (only on the phone) : もしもし moshi moshi",
            "- Thanks. (informal) : どうも。 Dōmo.",
            "- Thank you. (normal) : ありがとう。 Arigatō.",
            "- Please. (offer) : どうぞ。 Dōzo.",
            "- Please. (request) : お願いします。 Onegai shimasu.",
        ).joinToString("\n"))
        assertEquals(listOf("Thank you. (normal)", "Please. (request)"), Phrasebook.basics(listOf(japanese)).map { it.english })
        // With nothing plainer, the narrower line still stands for its phrase.
        val informal = row("wvs-2-0040:0000", "Phrase list > Basics", "- Thanks. (informal) : どうも。 Dōmo.")
        assertEquals(listOf("Thanks. (informal)"), Phrasebook.basics(listOf(informal)).map { it.english })
    }

    @Test fun aPhraseIsQuotedFromThePhrasebookWithoutTheModel() = runBlocking {
        val read = ArrayList<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun article(article: String, limit: Int): List<Evidence> {
                read += article
                return if (article == "Japanese phrasebook") listOf(overview, grammar, signs, basics, eating) else emptyList()
            }
        }
        val gateway = FakeInferenceGateway(listOf("Arigatou."))
        val events = ResearchOrchestrator(retriever, gateway).research("How do I say thank you in Japanese?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertTrue(answer, "> Thank you. (normal) : ありがとう。 Arigatō. [S1]" in answer)
        assertTrue(answer, "Wikivoyage phrasebook" in answer)
        assertEquals(0, gateway.generateCalls)
        assertEquals(listOf("Japanese phrasebook"), read)
        // "Vegan" has only a near line: it leads, says so, and the model writes the rest.
        val near = FakeInferenceGateway(listOf("You can also say that you eat no animal products."))
        val vegan = ResearchOrchestrator(retriever, near).research("How do I say I'm vegan in Japanese?").toList()
        val lead = vegan.filterIsInstance<ResearchEvent.Lead>().first().text
        assertTrue(lead, "I'm a vegetarian. : 私はベジタリアンです。" in lead && "no line with “vegan”" in lead)
        assertEquals(1, near.generateCalls)
        assertTrue(near.prompts.single().first.contains("私はベジタリアンです"))
    }

    @Test fun anEmergencyWithAPhraseRequestIsAnEmergencyFirst() = runBlocking {
        val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
        val help = row("wvs-16153-0060:0000", "Phrase list > Problems", "- Help! : 助けて! Tasukete!")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun article(article: String, limit: Int) = if (article == "Japanese phrasebook") listOf(basics, help) else emptyList()
        }
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("Give back blows [S1].")), emergencyGuides = { guides })
            .research("My friend is choking, how do I say help in Japanese?").toList()
        val sources = events.filterIsInstance<ResearchEvent.Sources>().first().evidence
        assertTrue(sources.toString(), sources.first().title.contains("Choking", ignoreCase = true))
    }

    @Test fun aPhraseWithAHotDayInItIsStillAPhrase() = runBlocking {
        val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
        val read = ArrayList<String>()
        val spanish = row("wvs-9-0040:0000", "Phrase list > Basics", "- Hello. : Hola. (OH-lah)", "Spanish phrasebook")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun article(article: String, limit: Int): List<Evidence> {
                read += article
                return if (article == "Spanish phrasebook") listOf(spanish) else emptyList()
            }
        }
        val gateway = FakeInferenceGateway(listOf("Hoy hace 40 grados."))
        // 40°C outside today would be a heat warning on its own; here it is words to say.
        val events = ResearchOrchestrator(retriever, gateway, emergencyGuides = { guides })
            .research("How do I say 'it is 40°C outside today' in Spanish?").toList()
        val shown = events.filterIsInstance<ResearchEvent.Lead>().joinToString("") { it.text } +
            events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertFalse(shown, "104°F" in shown)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().flatMap { it.evidence }
        assertFalse(sources.toString(), sources.any { it.documentId.startsWith("emergency:") })
        assertEquals(listOf("Spanish phrasebook"), read)
        assertEquals(1, gateway.generateCalls)
    }

    @Test fun aPhraseRequestNeedsTheModelAsItsAnswerDoes() {
        val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun article(article: String, limit: Int) = emptyList<Evidence>()
        }
        val orchestrator = ResearchOrchestrator(retriever, FakeInferenceGateway(), emergencyGuides = { guides })
        // The words name an emergency, but the question asks how to say them: the phrasebook answers,
        // and a line that is not the whole answer needs the model.
        for (question in listOf("How do I say my friend is choking in Spanish?", "How do I say I'm having a heart attack in Spanish?")) {
            assertTrue(question, orchestrator.phraseRequest(question) != null)
            assertFalse(question, orchestrator.answersWithoutModel(question, hasAttachments = false))
        }
        // An emergency told alongside the phrase request is answered from its guide, model or not.
        val emergency = "My friend is choking, how do I say help in Japanese?"
        assertNull(orchestrator.phraseRequest(emergency))
        assertTrue(orchestrator.answersWithoutModel(emergency, hasAttachments = false))
        assertNull(orchestrator.phraseRequest("How do I say thank you in Japanese?", hasAttachments = true))
    }

    @Test fun aPhraseAboutAnEmergencyIsStillAPhrase() = runBlocking {
        val help = row("wvs-16153-0060:0000", "Phrase list > Problems", "- Help! : 助けて! Tasukete!\n- Call the police! : 警察を呼んで! Keisatsu o yonde!")
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun article(article: String, limit: Int) = if (article == "Japanese phrasebook") listOf(basics, help) else emptyList()
        }
        val gateway = FakeInferenceGateway()
        val answer = ResearchOrchestrator(retriever, gateway).research("How do I say call the police in Japanese?").toList()
            .filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertTrue(answer, "Call the police! : 警察を呼んで! Keisatsu o yonde!" in answer)
    }
}
