package xyz.fieldatlas.research

/**
 * Language help from the travel guides' phrasebooks. "How do I say thank you in Thai?" is answered
 * by the line of Wikivoyage's "Thai phrasebook" that says it, quoted with the script and the
 * pronunciation the phrasebook gives, not by a small model's guess at a language it barely knows.
 * A country stands for its language ("basic phrases for Vietnam"). When no line says what was
 * asked, the phrasebook's phrase lists become the model's evidence instead.
 */
object Phrasebook {
    /** Rows read per phrasebook; the longest have about 150. */
    const val ROWS = 200

    /**
     * A passage of [book]'s phrases: under "Phrase list", as Wikivoyage heads them, or a variant
     * ("Phraselist", "Hindi Phrases", "Basics").
     */
    fun isPhraseList(item: Evidence, book: String): Boolean {
        if (!item.title.startsWith("$book — ")) return false
        val heading = item.title.substringAfter(" — ").substringBefore(" > ")
        return heading.contains("phrase", ignoreCase = true) || heading.equals("Basics", ignoreCase = true)
    }

    /** A request for [phrase] in [language] (a language or a country, as asked); a null phrase asks for the basics. */
    data class Ask(val language: String, val phrase: String?)

    /** A phrase line ("Thank you. : ありがとう。 Arigatō."), its English side, and the passage it is in. */
    data class Line(val text: String, val english: String, val source: Evidence)

    /**
     * The lines that say what was asked; [near] when one of its words was matched only by a related
     * one ("vegetarian" for "vegan"); [exact] when a line says the asked words and nothing else,
     * filler words included ("Help!" is not "can you help me"), with no note limiting its use
     * ("Hello (only on the phone)").
     */
    data class Match(val lines: List<Line>, val near: Boolean, val exact: Boolean = false)

    private const val LANGUAGE = "([\\p{L}]+(?:[ -][\\p{L}]+)?)"
    private const val END = "\\s*[?.!]*\\s*$"
    // "Say" and "write" ask for words in a language or a country's language; "ask for", "order" and
    // "tell" do so only with a language: "how do I order a taxi in Thailand?" asks how taxis work.
    private val verb = "(say|write|translate|ask(?: for)?|order|tell (?:someone|them|people|a waiter|the waiter|the driver|a driver))"
    private val wordVerbs = setOf("say", "write", "translate")
    // The phrase is everything between the verb and the last "in <language>".
    private val phraseForms = listOf(
        Regex("(?i)\\bhow (?:do|would|can|could|should) (?:i|you|we|one) $verb\\s+(.+)\\s+in\\s+$LANGUAGE$END"),
        Regex("(?i)\\bhow to $verb\\s+(.+)\\s+in\\s+$LANGUAGE$END"),
        Regex("(?i)\\b(translate)\\s+(.+)\\s+(?:in|into|to)\\s+$LANGUAGE$END"),
    )
    // "What is 'thank you' in Japanese?" and "what's the word for bread in French?" ask for words;
    // "what is the speed limit in German towns?" does not, so the phrase must be quoted or named a word.
    private val wordFor = Regex("(?i)^\\s*what(?:'s|’s| is| are)\\s+(?:the (?:word|words|phrase|term|expression) for\\s+(.+?)|" +
        "[\"“'‘](.+?)[\"”'’])\\s+in\\s+$LANGUAGE$END")
    // "Do you have a Thai phrasebook?": the language may follow a determiner, or another word the
    // two-word language group takes in ("need Thai phrasebook"), so its last word is tried too.
    private val phrasebookForm = Regex("(?i)\\b(?:(?:a|an|the|my|your)\\s+)?$LANGUAGE\\s+phrasebook\\b")
    private val basicsForms = listOf(
        // The request ends there: "some Thai words that are considered rude" asks something else.
        Regex("(?i)\\b(?:(?:basic|useful|common|essential|key|important|simple|survival|polite|a few|some)\\s+)+$LANGUAGE\\s+(?:phrases|words|expressions)" +
            "(?:\\s+(?:for (?:travel\\w*|tourists?|a trip|beginners)|to (?:know|learn)|please))?$END"),
        // Not "words in Japanese", which may ask how many there are.
        Regex("(?i)\\b(?:phrases|expressions)\\s+(?:in|for|to use in)\\s+$LANGUAGE\\b"),
        phrasebookForm,
    )

    /** Languages with a Wikivoyage phrasebook, by the names people use; values are the phrasebook names. */
    private val languages: Map<String, List<String>> = buildMap {
        listOf(
            "Afrikaans", "Albanian", "Amharic", "Arabic", "Armenian", "Azerbaijani", "Basque", "Belarusian", "Bengali", "Bosnian",
            "Brazilian Portuguese", "Bulgarian", "Burmese", "Cantonese", "Catalan", "Cebuano", "Chinese", "Croatian", "Czech", "Danish",
            "Dari", "Dutch", "Egyptian Arabic", "Estonian", "Fijian", "Finnish", "French", "Galician", "Georgian", "German", "Greek",
            "Gujarati", "Haitian Creole", "Hausa", "Hawaiian", "Hebrew", "Hindi", "Hungarian", "Icelandic", "Igbo", "Indonesian",
            "Irish", "Italian", "Japanese", "Javanese", "Kannada", "Kazakh", "Khmer", "Kinyarwanda", "Korean", "Kurdish", "Kyrgyz",
            "Lao", "Latvian", "Lithuanian", "Luxembourgish", "Macedonian", "Malagasy", "Malay", "Malayalam", "Maltese", "Marathi",
            "Mongolian", "Moroccan Arabic", "Nepali", "Norwegian", "Pashto", "Persian", "Polish", "Portuguese", "Punjabi", "Quechua",
            "Romanian", "Russian", "Samoan", "Serbian", "Sinhala", "Slovak", "Slovenian", "Somali", "Spanish", "Swahili", "Swedish",
            "Tagalog", "Tajik", "Tamil", "Telugu", "Thai", "Tibetan", "Tigrinya", "Tongan", "Tunisian Arabic", "Turkish", "Turkmen",
            "Ukrainian", "Urdu", "Uyghur", "Uzbek", "Vietnamese", "Welsh", "Wolof", "Xhosa", "Yoruba", "Zulu",
        ).forEach { put(it.lowercase(), listOf(it)) }
        put("mandarin", listOf("Chinese")); put("mandarin chinese", listOf("Chinese")); put("farsi", listOf("Persian"))
        put("filipino", listOf("Tagalog")); put("bahasa", listOf("Indonesian", "Malay")); put("bahasa indonesia", listOf("Indonesian"))
        put("brazilian", listOf("Brazilian Portuguese", "Portuguese")); put("castilian", listOf("Spanish")); put("maori", listOf("Māori"))
        put("māori", listOf("Māori")); put("swiss german", listOf("Swiss-German", "German")); put("burmese", listOf("Burmese"))
    }

    /** The language a traveller needs in each country, regional phrasebook first where there is one. */
    private val countries: Map<String, List<String>> = buildMap {
        fun add(language: List<String>, vararg names: String) = names.forEach { put(it, language) }
        add(listOf("Japanese"), "japan")
        add(listOf("Chinese"), "china", "taiwan", "beijing", "shanghai")
        add(listOf("Cantonese", "Chinese"), "hong kong", "macau", "macao")
        add(listOf("Korean"), "korea", "south korea", "north korea")
        add(listOf("Thai"), "thailand")
        add(listOf("Vietnamese"), "vietnam", "viet nam")
        add(listOf("Khmer"), "cambodia")
        add(listOf("Lao"), "laos")
        add(listOf("Burmese"), "myanmar", "burma")
        add(listOf("Malay"), "malaysia", "brunei")
        add(listOf("Indonesian"), "indonesia", "bali", "sumatra", "lombok")
        add(listOf("Tagalog"), "philippines", "the philippines")
        add(listOf("Hindi"), "india")
        add(listOf("Nepali"), "nepal")
        add(listOf("Sinhala"), "sri lanka")
        add(listOf("Bengali"), "bangladesh")
        add(listOf("Urdu"), "pakistan")
        add(listOf("Persian"), "iran")
        add(listOf("Dari", "Pashto"), "afghanistan")
        add(listOf("Turkish"), "turkey", "türkiye", "turkiye")
        add(listOf("Greek"), "greece", "cyprus")
        add(listOf("Italian"), "italy")
        add(listOf("Spanish"), "spain", "mexico", "argentina", "colombia", "peru", "chile", "bolivia", "ecuador", "uruguay", "paraguay",
            "venezuela", "cuba", "costa rica", "guatemala", "honduras", "nicaragua", "panama", "el salvador", "dominican republic")
        add(listOf("Brazilian Portuguese", "Portuguese"), "brazil")
        add(listOf("Portuguese"), "portugal", "mozambique", "angola")
        add(listOf("French"), "france", "belgium", "monaco", "senegal", "ivory coast", "côte d'ivoire", "quebec", "québec")
        add(listOf("German"), "germany", "austria", "switzerland")
        add(listOf("Dutch"), "netherlands", "the netherlands", "holland")
        add(listOf("Polish"), "poland")
        add(listOf("Czech"), "czechia", "czech republic")
        add(listOf("Slovak"), "slovakia")
        add(listOf("Hungarian"), "hungary")
        add(listOf("Romanian"), "romania", "moldova")
        add(listOf("Bulgarian"), "bulgaria")
        add(listOf("Russian"), "russia")
        add(listOf("Ukrainian"), "ukraine")
        add(listOf("Serbian"), "serbia")
        add(listOf("Croatian"), "croatia")
        add(listOf("Bosnian"), "bosnia", "bosnia and herzegovina")
        add(listOf("Slovenian"), "slovenia")
        add(listOf("Albanian"), "albania", "kosovo")
        add(listOf("Macedonian"), "north macedonia", "macedonia")
        add(listOf("Lithuanian"), "lithuania")
        add(listOf("Latvian"), "latvia")
        add(listOf("Estonian"), "estonia")
        add(listOf("Georgian"), "georgia")
        add(listOf("Armenian"), "armenia")
        add(listOf("Azerbaijani"), "azerbaijan")
        add(listOf("Kazakh", "Russian"), "kazakhstan")
        add(listOf("Uzbek"), "uzbekistan")
        add(listOf("Kyrgyz"), "kyrgyzstan")
        add(listOf("Tajik"), "tajikistan")
        add(listOf("Mongolian"), "mongolia")
        add(listOf("Egyptian Arabic", "Arabic"), "egypt")
        add(listOf("Moroccan Arabic", "Arabic"), "morocco")
        add(listOf("Tunisian Arabic", "Arabic"), "tunisia")
        add(listOf("Algerian Arabic", "Arabic"), "algeria")
        add(listOf("Jordanian Arabic", "Arabic"), "jordan")
        add(listOf("Lebanese Arabic", "Arabic"), "lebanon")
        add(listOf("Arabic"), "saudi arabia", "united arab emirates", "uae", "dubai", "abu dhabi", "oman", "qatar", "kuwait",
            "bahrain", "iraq", "syria", "yemen", "libya", "sudan")
        add(listOf("Hebrew"), "israel")
        add(listOf("Amharic"), "ethiopia")
        add(listOf("Swahili"), "kenya", "tanzania", "zanzibar")
        add(listOf("Somali"), "somalia")
        add(listOf("Malagasy"), "madagascar")
        add(listOf("Kinyarwanda"), "rwanda")
        add(listOf("Swedish"), "sweden")
        add(listOf("Norwegian"), "norway")
        add(listOf("Danish"), "denmark")
        add(listOf("Finnish"), "finland")
        add(listOf("Icelandic"), "iceland")
        add(listOf("Haitian Creole"), "haiti")
        add(listOf("Tibetan"), "tibet")
        add(listOf("Catalan"), "catalonia")
        add(listOf("Welsh"), "wales")
    }

    /** The phrasebooks to read for [language] as asked, in order: "Egypt" is Egyptian Arabic, then Arabic. */
    fun books(language: String): List<String> {
        val key = language.trim().lowercase()
        val names = languages[key] ?: countries[key] ?: return emptyList()
        return names.map { "$it phrasebook" }
    }

    private fun cleanPhrase(raw: String): String = raw.trim()
        .removePrefix("the phrase ").removePrefix("the word ").removePrefix("the words ")
        .trim().trim('"', '“', '”', '\'', '‘', '’', '«', '»').trim()

    // Words that may trail a language without being part of it: "in Thai please".
    private val trailing = setOf("please", "language", "now", "too", "then")

    /** The language or country named, as written ("Brazilian Portuguese"), or before a trailing word ("Thai please"). */
    private fun named(words: String, known: (String) -> Boolean): String? {
        val whole = words.trim()
        if (known(whole.lowercase())) return whole
        val first = whole.substringBefore(' ')
        return first.takeIf { ' ' in whole && whole.substringAfter(' ').lowercase() in trailing && known(it.lowercase()) }
    }

    /** What the question asks the phrasebook for, or null when it is not a request for phrases. */
    fun ask(question: String): Ask? = request(question)?.first

    /** The question without its request for a phrase: what else it says ("my friend is choking, …"). */
    fun withoutRequest(question: String): String = request(question)?.second?.let { question.removeRange(it).trim() } ?: question

    private fun request(question: String): Pair<Ask, IntRange>? {
        for (form in phraseForms) {
            val m = form.find(question) ?: continue
            val words = m.groupValues[1].lowercase() in wordVerbs
            val language = named(m.groupValues[3]) { languages.containsKey(it) || words && countries.containsKey(it) } ?: continue
            return Ask(language, cleanPhrase(m.groupValues[2]).takeIf(String::isNotBlank)) to m.range
        }
        wordFor.find(question)?.let { m ->
            named(m.groupValues[3]) { languages.containsKey(it) }?.let { language ->
                return Ask(language, cleanPhrase(m.groupValues[1].ifBlank { m.groupValues[2] }).takeIf(String::isNotBlank)) to m.range
            }
        }
        for (form in basicsForms) {
            val m = form.find(question) ?: continue
            val known = { name: String -> languages.containsKey(name) || countries.containsKey(name) }
            val language = named(m.groupValues[1], known)
                ?: m.groupValues[1].substringAfterLast(' ').takeIf { form === phrasebookForm && known(it.lowercase()) }
            language?.let { return Ask(it, null) to m.range }
        }
        return null
    }

    private val bullet = Regex("^\\s*[-*•;]\\s*")
    private val separator = Regex("\\s+:\\s*|(?<=[\\p{L}\\p{N}.!?)\\]_])\\s*:\\s+")
    private val latin = Regex("[A-Za-z]")
    private val letter = Regex("\\p{L}")

    private fun latinShare(text: String): Double {
        val letters = letter.findAll(text).count()
        return if (letters == 0) 0.0 else latin.findAll(text).count().toDouble() / letters
    }

    /** The phrase lines of [rows], each with its English side ("Toilet" in "ห้องน้ำ (hông náam): Toilet"). */
    internal fun lines(rows: List<Evidence>): List<Line> = rows.flatMap { row ->
        row.text.lines().mapNotNull { raw ->
            if (!bullet.containsMatchIn(raw)) return@mapNotNull null
            val line = raw.replace(bullet, "").trim()
            val split = separator.find(line) ?: return@mapNotNull null
            val left = line.substring(0, split.range.first).trim()
            val right = line.substring(split.range.last + 1).trim()
            if (left.isEmpty() || right.isEmpty() || left.length > 120) return@mapNotNull null
            // Most lines give the English first; a sign or a word in its own script may come first.
            val english = if (latinShare(left) >= latinShare(right)) left else right
            Line(line, english, row)
        }
    }.distinctBy { it.text }

    private val word = Regex("[\\p{L}\\p{N}]+(?:'[\\p{L}]+)?")
    // Words every phrase request shares; what is left says which phrase.
    private val filler = setOf("i", "i'm", "im", "am", "a", "an", "the", "is", "are", "to", "you", "your", "do", "does", "my", "me",
        "it", "it's", "this", "that", "please", "say", "in", "of", "for", "and", "or", "can", "could", "would", "will", "some",
        "someone", "them", "we", "be", "there", "here", "just", "very")
    // A request's word and the phrasebook's usual word for it. "Near" ones say something close, not the same.
    private val same = mapOf(
        "bathroom" to "toilet", "restroom" to "toilet", "loo" to "toilet", "wc" to "toilet", "washroom" to "toilet", "toilets" to "toilet",
        "check" to "bill", "thanks" to "thank", "hi" to "hello", "hey" to "hello", "bye" to "goodbye", "cheers" to "cheers",
        "price" to "cost", "costs" to "cost",
    )
    private val near = mapOf("vegan" to listOf("vegetarian"), "sorry" to listOf("excuse"), "excuse" to listOf("sorry"))

    // "where's" is "where": the contraction says nothing about which phrase.
    private fun words(text: String) = word.findAll(text.lowercase().replace('’', '\'')).map { it.value.removeSuffix("'s") }.toList()

    // A note in brackets says how a phrase is used ("(less formal)"), not what it says.
    private val aside = Regex("\\([^)]*\\)|\\[[^]]*]")
    // "Check in" and "check out" are a hotel stay, kept as one word; only the check itself is the bill.
    private val stay = Regex("(?i)(?<!\\bthe )\\bcheck[ -]?(in|out)\\b")
    // "Where's" says "where is", as the phrasebook's "Where is the toilet?" does.
    private val contracted = mapOf("'m" to "am", "'s" to "is", "'re" to "are", "'ll" to "will", "'d" to "would", "'ve" to "have")

    /** Every word [text] says, in order and without its notes: "Where's the loo? (polite)" is [where, is, the, toilet]. */
    private fun said(text: String): List<String> =
        word.findAll(text.replace(aside, " ").replace(stay, "check$1").lowercase().replace('’', '\'')).flatMap { m ->
            val w = m.value
            val cut = w.indexOf('\'')
            val full = if (cut > 0) contracted[w.substring(cut)] else null
            when {
                full != null -> sequenceOf(w.substring(0, cut), full)
                w == "im" -> sequenceOf("i", "am")
                else -> sequenceOf(w)
            }
        }.map { same[it] ?: it }.toList()

    // Words that change nothing a line says: "The check, please." is the bill, and so is "Bill / check".
    private val neutral = setOf("a", "an", "the", "please")
    private fun spoken(said: List<String>) = said.filterNot { it in neutral }.ifEmpty { said }.distinct()

    /** Whether a line saying [line] says [asked] and nothing else: each word of either is in the other, as many. */
    private fun saysOnly(asked: List<String>, line: List<String>) =
        asked.size == line.size && asked.all { matches(it, line) } && line.all { matches(it, asked) }

    // A word matches itself and its plural, never another word that starts the same ("cheers", "cheese").
    private fun matches(asked: String, said: Collection<String>) = said.any { it == asked || it == asked + "s" || it + "s" == asked ||
        it == asked + "es" || it + "es" == asked }
    private val limited = Regex("(?i)\\((?:only|not|formal|informal|polite|rude|slang|vulgar)\\b")

    /**
     * The lines of [rows] that say [phrase]: every word of it, and as little else as possible, with
     * the variants of that line ("Thank you. (less formal)", "Thank you. (normal)").
     */
    fun match(phrase: String, rows: List<Evidence>): Match? {
        val whole = said(phrase)
        // The words that say which phrase: "can you help me" is {help}. "Please" is all filler, and
        // then its own words do.
        val bare = whole.all { it in filler }
        fun content(words: List<String>) = words.filter { bare || it !in filler }.toSet()
        val asked = content(whole)
        if (asked.isEmpty()) return null
        val wanted = spoken(whole)
        data class Scored(val line: Line, val all: List<String>, val said: Set<String>, val score: Double, val near: Boolean, val shared: Int)
        val scored = lines(rows).mapNotNull { line ->
            val all = said(line.english)
            val key = content(all)
            if (key.isEmpty()) return@mapNotNull null
            var score = 0.0
            var approximate = false
            for (w in asked) when {
                matches(w, key) -> score += 1.0
                near[w].orEmpty().any { matches(it, key) } -> { score += NEAR; approximate = true }
                else -> return@mapNotNull null
            }
            Scored(line, all, key, score, approximate, wanted.distinct().count { matches(it, all) })
        }
        // Among lines with as few other words, the one that also says the asked filler words:
        // "How are you?" for "how are you", not "How?".
        val ranked = scored.sortedWith(compareBy({ -it.score }, { it.said.size }, { -it.shared }, { it.all.size }, { it.line.english.length }))
        val best = ranked.firstOrNull() ?: return null
        val variants = ranked.filter { spoken(it.all) == spoken(best.all) && it.score == best.score }.take(MAX_LINES)
        // The whole answer only when the line says each asked word and nothing else, filler words
        // included: "Help!" is the shout, not "can you help me".
        val exact = !best.near && saysOnly(wanted, spoken(best.all)) && variants.none { limited.containsMatchIn(it.line.english) }
        return Match(variants.map { it.line }, best.near, exact)
    }

    private const val NEAR = 0.75

    /** Everyday phrases a traveller needs first, in this order, for a request for the basics. */
    private val essentials = listOf(
        listOf("hello"), listOf("thank"), listOf("please"), listOf("yes"), listOf("no"), listOf("excuse"), listOf("sorry"),
        listOf("speak", "english"), listOf("understand"), listOf("where", "toilet"), listOf("how", "much"), listOf("help"),
    )
    private const val MAX_LINES = 3
    private const val MAX_BASICS = 10

    // A line for one situation ("Hello (only on the phone)") is not the everyday phrase; one for a
    // narrower use ("Thanks. (informal)", "Please. (offer)") comes only when nothing plainer says it.
    private val situational = Regex("(?i)\\((?:only\\b|[^)]*\\b(?:tele)?phone\\b)")
    private val narrower = Regex("(?i)\\((?:informal|offer|rude|slang|vulgar)\\b")

    /** The basic phrases of [rows], one line each, in [essentials] order. */
    fun basics(rows: List<Evidence>): List<Line> {
        val all = lines(rows).filterNot { situational.containsMatchIn(it.english) }
        fun plain(line: Line) = words(line.english.replace(aside, " "))
        // The line that is only the phrase: "Thank you." rather than "Fine, thank you."
        return essentials.mapNotNull { needed ->
            all.filter { line -> plain(line).let { said -> needed.all { matches(it, said) } } }
                .minWithOrNull(compareBy({ narrower.containsMatchIn(it.english) }, { plain(it).size }, { it.english.length }))
        }.distinctBy { it.text }.take(MAX_BASICS)
    }

    /** The passages to read for [ask]: the ones holding its lines first, then the basics. */
    fun pick(ask: Ask, rows: List<Evidence>, limit: Int): List<Evidence> {
        val holding = (ask.phrase?.let { match(it, rows)?.lines } ?: basics(rows)).map { it.source }
        val basics = rows.filter { it.title.substringAfter(" — ").endsWith("Basics") }
        return (holding + basics + rows).distinctBy { it.documentId to it.chunkId }.take(limit)
    }

    /**
     * The answer's quoted opening: the lines that say what was asked, each cited, or the basic
     * phrases for a request for them. Null when the phrasebook has no such line.
     */
    fun lead(ask: Ask, sources: List<PromptSource>): SourceLead.Lead? {
        val rows = sources.map { it.evidence }
        val lines = ask.phrase?.let { phrase -> match(phrase, rows)?.lines } ?: if (ask.phrase == null) basics(rows) else null
        if (lines.isNullOrEmpty()) return null
        val cited = lines.mapNotNull { line ->
            sources.firstOrNull { it.evidence.documentId == line.source.documentId && it.evidence.chunkId == line.source.chunkId }
                ?.let { it.citationId to line.text }
        }
        if (cited.isEmpty()) return null
        return SourceLead.Lead(cited.first().first, cited.first().second, cited.drop(1))
    }

    /** Whether the phrasebook's line is the whole answer: it says exactly what was asked, with no limiting note. */
    fun answersAlone(ask: Ask, sources: List<PromptSource>): Boolean =
        ask.phrase == null || match(ask.phrase, sources.map { it.evidence })?.exact == true

    /** A note for a line matched only by a related word: "vegetarian" is not "vegan". */
    fun nearNote(ask: Ask, sources: List<PromptSource>): String? {
        val phrase = ask.phrase ?: return null
        val match = match(phrase, sources.map { it.evidence }) ?: return null
        if (!match.near) return null
        val asked = words(phrase).filterNot { it in filler }.filter { w -> near.containsKey(w) && match.lines.none { matches(w, words(it.english)) } }
        if (asked.isEmpty()) return null
        return "The phrasebook has no line with “${asked.joinToString("”, “")}”; this is its closest."
    }
}
