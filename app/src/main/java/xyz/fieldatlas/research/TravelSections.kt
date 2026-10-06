package xyz.fieldatlas.research

import java.text.Normalizer
import java.util.Locale

/**
 * A travel question that names a place and a practical topic ("How do I cross the street in
 * Hanoi?", "Which vaccinations for Kenya?") is answered best by that place's own guide section:
 * "Hanoi — Stay safe > Traffic", "Kenya — Stay healthy". Keyword search over hundreds of
 * thousands of passages often prefers another town's section that shares more words, so the
 * section is looked up by its title, as the travel guides name them.
 */
object TravelSections {
    // A Visa card is paid with, not applied for: "my Visa card", "Visa debit", "Visa or Mastercard".
    private const val VISA = "visas?\\b(?!\\s*(?:(?:/|or|and)\\s*)?(?:cards?|debit|credit|mastercard)\\b)"
    // "which areas should I avoid", "areas to avoid", "avoid the northern districts".
    private const val AREAS = "areas? to avoid|avoid(?:ed|ing)?(?: \\w+){0,3} (?:areas?|neighbou?rhoods?|districts?|parts of town)|" +
        "(?:areas?|neighbou?rhoods?|districts?)(?: \\w+){0,4} avoid|which (?:areas?|neighbou?rhoods?|districts?)"

    /** Wikivoyage's standard sections, and the words that ask about each. */
    private val topics = listOf(
        "Stay safe" to Regex("(?i)\\b(safe|safety|unsafe|dangerous|danger|scams?|crime|theft|pickpocket(?:s|ing)?|traffic|cross(?:ing)? the (?:street|road)|" +
            "swim(?:ming)?|beach|flags?|rip|police|robbery|mugg\\w*|$AREAS)\\b"),
        "Stay healthy" to Regex("(?i)\\b(vaccin\\w*|jabs?|malaria|health|healthy|sick|illness|disease|tap water|drinking water|altitude|mosquito\\w*|doctor|hospital|" +
            "pharmac\\w*|food poisoning|diarrh\\w*|repellents?|insects?|bugs?|bites?|bed ?bugs?|ticks?|ears? (?:pop\\w*|hurt\\w*|pain|ache)|ear ?ache|air ?sick\\w*|motion sick\\w*)\\b"),
        // A card to pay with, not a SIM, IC (transit) or SD card.
        "Buy" to Regex("(?i)\\b(cash|credit|debit|(?<!sim )(?<!ic )(?<!sd )(?<!memory )cards?|card payments?|by card|atms?|money|currency|exchange|haggl\\w*|bargain\\w*|tip|tips|tipping|" +
            "prices?|souks?|markets?|pay|paying|budget\\w*|afford\\w*|cost of living|how much money)\\b"),
        "Get in" to Regex("(?i)\\b(airport|arrive|arriving|arrival|get (?:to|from)|$VISA|border|ferry from|train to|flights?)\\b"),
        "Get around" to Regex("(?i)\\b(get around|getting around|bus|buses|taxi|taxis|metro|subway|tram|train|trains|ride[- ]hailing|grab|uber|bolt|lyft|rent a car|car rental|drive|driving|scooter|motorbike|bike)\\b"),
        "Respect" to Regex("(?i)\\b(wear|dress|clothing|temples?|wats?|mosques?|churches|church|cathedrals?|shrines?|pagodas?|etiquette|customs?|rude|polite|respect\\w*|taboo|shoes|" +
            "cover(?:ing)? (?:up|my|your|the)?\\s*(?:shoulders|knees|head|hair|arms)|modest(?:ly)?)\\b"),
        "Connect" to Regex("(?i)\\b(sim|sim card|phone|mobile data|internet|wi-?fi|esim)\\b"),
        "Talk" to Regex("(?i)\\b(language|speak|say|phrase|english)\\b"),
        "Understand" to Regex("(?i)\\b(climate|weather|season|rainy|monsoon|best time|when to (?:go|visit)|holidays?|festivals?)\\b"),
        "Cope" to Regex("(?i)\\b(embass\\w*|consulate|laundry|launderette|toilets?)\\b"),
    )
    /** A daily budget, and the headings it is filed under; "Price discrimination" is no cost of a day. */
    private val budget = Regex("(?i)\\b(?:budget\\w*|afford\\w*|cost of living|how much money)\\b") to Regex("(?i)\\b(?:costs?|budget\\w*)\\b")

    /**
     * The part of a topic a question asks about, and the headings guides file it under: a metro
     * ticket is under "By public transport" or "By metro", a visa under "Entry requirements", a
     * SIM card under "Mobile phones". A section headed for that part comes before one that only
     * shares words with the question ("By boat" also sells tickets), and its quote comes first.
     * A practical part ([care] false) is set aside for a safety, health or etiquette question,
     * which those sections answer whatever transport it names; a care part (what to wear, a place
     * of worship, areas to avoid, tap water) always applies. A place of worship says where, not
     * what, so its [weight] is half. A transport mode ([mode]) is context when a ticket or transit
     * card is asked about, and then adds half its weight too.
     */
    private class Part(val asks: (String) -> Boolean, val headings: Regex, val care: Boolean = false, val weight: Int = HEADED, val mode: Boolean = false,
        /** A part that is one only when asked about: "By train > Tickets" is filed under the train unless the ticket itself is asked about. */
        val whenAsked: Boolean = false) {
        constructor(asks: Regex, headings: Regex, care: Boolean = false, weight: Int = HEADED, mode: Boolean = false) :
            this({ q: String -> asks.containsMatchIn(q) }, headings, care, weight, mode)
    }

    private val transit = Regex("(?i)\\b(?:metro|subway|underground|tube|trams?|bus(?:es)?|ferr(?:y|ies)|public transport|transit|mrt|lrt)\\b")
    private val ticket = Regex("(?i)\\b(?:tickets?|fares?|pass(?:es)?|cards?)\\b")
    // "İstanbulkart", "Oyster card", "Navigo pass"; not a Visa, SIM or SD card.
    private val namedCard = Regex("\\b\\p{L}+kart\\b|\\b(?!Visa\\b|Mastercard\\b|Amex\\b|SIM\\b|SD\\b|IC\\b)\\p{Lu}[\\p{L}-]+ (?:card|pass)\\b")

    /** A ticket, fare, pass or card for city transit, or a transit card by name. */
    private fun asksTicket(question: String) =
        ticket.containsMatchIn(question) && transit.containsMatchIn(question) || namedCard.containsMatchIn(question)

    private val parts = listOf(
        Part(Regex("(?i)\\b$VISA|\\bentry requirements?\\b"), Regex("(?i)\\b(?:visas?|entry requirements?|immigration|formalities)\\b")),
        Part(Regex("(?i)\\b(?:metro|subway|underground|tube|u-bahn|s-bahn|mrt|lrt)\\b"),
            Regex("(?i)\\b(?:metro|subway|underground|public transport|public transit|rapid transit|u-bahn|mrt)\\b"), mode = true),
        Part(Regex("(?i)\\b(?:sim|esim|sim cards?|mobile data|data plan|phone plan|mobile phones?|cell ?phones?)\\b"),
            Regex("(?i)\\b(?:mobile|cell(?:ular)?|phones?|sim|telecom\\w*|telephones?)\\b")),
        Part(budget.first, budget.second),
        // A journey's ticket ("Lisbon to Porto by train") is sold by the national railway, not under a city's "Fares".
        Part({ q: String -> asksTicket(q) && journeyOf(q) == null }, Regex("(?i)\\b(?:tickets?|ticketing|fares?|pass(?:es)?|cards?|\\p{L}+kart|\\p{L}+card)\\b"), whenAsked = true),
        Part(Regex("(?i)\\bbus(?:es)?\\b"), Regex("(?i)\\bbus(?:es)?\\b"), mode = true),
        Part(Regex("(?i)\\btaxis?\\b"), Regex("(?i)\\b(?:taxis?|ride[- ]?hailing|ride[- ]?sharing|cabs?)\\b"), mode = true),
        // An app has its own heading ("By ride-hailing", "Rideshare taxi"); a metered or river taxi is no answer to it.
        Part(Regex("(?i)\\bride[- ]?(?:hailing|shar\\w*)\\b|\\be-?hailing\\b|\\b(?:grab|uber|bolt|lyft)\\b"),
            Regex("(?i)\\b(?:ride[- ]?(?:hailing|shar\\w*)|e-?hailing|car-?hailing|online taxis?|grab|uber|bolt|lyft)\\b")),
        Part(Regex("(?i)\\btrains?\\b|\\brail(?:way)?\\b"), Regex("(?i)\\b(?:trains?|rail\\w*)\\b"), mode = true),
        Part(Regex("(?i)\\bferr(?:y|ies)\\b|\\bboats?\\b"), Regex("(?i)\\b(?:ferr(?:y|ies)|boats?)\\b"), mode = true),
        Part(Regex("(?i)\\b(?:cash|atms?|currency|exchange|credit cards?|debit cards?)\\b"), Regex("(?i)\\b(?:money|cash|atms?|exchange|currency|cards?)\\b")),
        Part(Regex("(?i)\\btip(?:s|ping)?\\b"), Regex("(?i)\\btip(?:s|ping)?\\b")),
        Part(Regex("(?i)\\bhaggl\\w*|\\bbargain\\w*"), Regex("(?i)\\b(?:haggl\\w*|bargain\\w*)\\b")),
        Part(Regex("(?i)\\bscams?\\b"), Regex("(?i)\\bscams?\\b")),
        // Driving on the left is not renting a car: "By car > Rental" sells cheap hire cars.
        Part(Regex("(?i)\\bdriv(?:e|ing)\\b"), Regex("(?i)\\b(?:by car|driving)\\b")),
        Part(Regex("(?i)\\brent(?:ing)? a car\\b|\\bcar rental\\b"), Regex("(?i)\\b(?:by car|car rental|rent(?:al|ing)?)\\b")),
        Part(Regex("(?i)\\b(?:bikes?|bicycles?|cycling)\\b"), Regex("(?i)\\b(?:bikes?|bicycles?|cycling)\\b")),
        Part(Regex("(?i)\\b(?:wear|dress(?:ed|ing)?|cloth(?:es|ing)|cover(?:ing)? (?:up|my|your)|shoulders|knees|attire)\\b"),
            Regex("(?i)\\b(?:dress\\w*|cloth\\w*|attire)\\b"), care = true),
        // Monks are left out: "Buddhist Monks" tells how to treat them, not what to wear in a temple.
        Part(Regex("(?i)\\b(?:temples?|wats?|mosques?|churches|church|cathedrals?|shrines?|pagodas?|places of worship)\\b"),
            Regex("(?i)\\b(?:religio\\w*|worship|temples?|mosques?|churches|church|shrines?|sacred|pagodas?)\\b"), care = true, weight = HEADED / 2),
        Part(Regex("(?i)\\b(?:$AREAS|dangerous (?:areas?|neighbou?rhoods?))\\b"),
            Regex("(?i)\\b(?:dangerous areas?|areas?|neighbou?rhoods?|districts?)\\b"), care = true),
        Part(Regex("(?i)\\b(?:tap water|drinking water|water safe|safe to drink)\\b"), Regex("(?i)\\bwater\\b"), care = true),
    )

    /** The parts a question's headings are read by: practical ones outside a care question, and the ticket only when asked about. */
    private fun activeParts(question: String): List<Part> {
        val care = asksCare(question)
        return parts.filter { (it.care || !care) && (!it.whenAsked || it.asks(question)) }
    }

    /** The headings of the parts asked about, each with its weight; empty when the question names no part. */
    private class Wanted(val headings: List<Pair<Regex, Int>>, val active: List<Part>) {
        fun isEmpty() = headings.isEmpty()
    }

    private fun wanted(question: String): Wanted {
        val active = activeParts(question)
        val ticket = asksTicket(question) && journeyOf(question) == null
        return Wanted(active.filter { it.asks(question) }.map { it.headings to if (ticket && it.mode) it.weight / 2 else it.weight }, active)
    }

    /** The headings the parts a question asks about are filed under; empty when it names no part. */
    internal fun partHeadings(question: String): List<Regex> = wanted(question).headings.map { it.first }

    /**
     * The heading a section is filed under by part: its own heading when that names a part
     * ("By taxi > Metered taxi" is no answer to Grab, nor "By car > Rental" to driving on the left),
     * else its nearest parent's. A heading that names no part ("Fares", "Rules") is filed under its
     * parent's: the metro fare is under "By public transport > Fares" and the rule to validate a
     * train ticket under "By train > Rules".
     */
    private fun partHeading(title: String, active: List<Part>): String? = title.substringAfter(" — ").split(" > ")
        .lastOrNull { h -> active.any { it.headings.containsMatchIn(h) } }

    /** The weight of the section's heading for the parts asked about; 0 when it is headed for none. */
    private fun headedFor(item: Evidence, wanted: Wanted): Int {
        val heading = partHeading(item.title, wanted.active) ?: return 0
        return wanted.headings.filter { (headings, _) -> headings.containsMatchIn(heading) }.maxOfOrNull { it.second } ?: 0
    }

    /** Which of the [wanted] parts the section is headed for, by index. */
    private fun partsOf(item: Evidence, wanted: Wanted): Set<Int> {
        val heading = partHeading(item.title, wanted.active) ?: return emptySet()
        return wanted.headings.indices.filter { wanted.headings[it].first.containsMatchIn(heading) }.toSet()
    }

    // Wikivoyage's standard headings; a map listing ("New York — Buy Rite Pharmacy") is titled with none of them.
    private val standardHeadings = setOf("understand", "get in", "get around", "see", "do", "buy", "eat", "drink", "sleep", "stay safe", "stay healthy",
        "respect", "connect", "cope", "talk", "work", "learn", "go next", "ground transportation", "prepare", "walk", "drive", "flights",
        "fees and permits", "see and do", "eat and drink")
    private val headingWords = standardHeadings.flatMap { it.split(' ') }.toSet()

    /** Whether the title is a guide section's: "<place> — <standard heading>" or a subsection of one. */
    internal fun isSection(title: String): Boolean =
        " — " in title && title.substringAfter(" — ").substringBefore(" > ").trim().lowercase() in standardHeadings

    // A name is capitalised and then lowercase; "ATMs", "SIM" and "UK" are things. A linking word only
    // inside a name ("Rio de Janeiro"), never trailing it ("How do").
    private const val PLACE_WORD = "\\p{Lu}[\\p{Ll}'’-][\\p{L}'’-]*"
    private val place = Regex("\\b($PLACE_WORD(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?$PLACE_WORD)*)")
    private val notPlaces = setOf("How", "What", "Which", "Where", "When", "Why", "Who", "Is", "Are", "Can", "Do", "Does", "Should",
        "I", "I'm", "My", "We", "Tell", "Please", "The", "A", "An", "In", "At", "To", "From", "For", "Any", "Best", "EU")
    private val sentenceStart = Regex("(?:^|[.!?:]\\s+)(?=\\p{Lu})")
    // A nationality ("Canadian passport", "Japanese onsen") names no guide.
    private val demonym = Regex("(?:ian|ese|ish)$")
    private val modes = Regex("(?i)\\b(?:trains?|rail(?:way)?|bus(?:es)?|coach(?:es)?|ferr(?:y|ies)|boats?|flights?|fly(?:ing)?|driv(?:e|ing))\\b")
    private val journey = Regex("\\b($PLACE_WORD(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?$PLACE_WORD)*)\\s+to\\s+($PLACE_WORD(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?$PLACE_WORD)*)\\b")

    /** A journey between two named places: its [origin] and [destination]. */
    data class Journey(val origin: String, val destination: String)

    /**
     * "Lisbon to Porto by train", "from Madrid to Barcelona by bus": two names joined by "to" and
     * a way of travelling. Its ticket is sold by the country's railway or coach lines, which the
     * country guide's "Get around" describes, and arrival by the destination's "Get in".
     */
    fun journeyOf(question: String): Journey? {
        val match = journey.find(question) ?: return null
        if (!modes.containsMatchIn(question)) return null
        val (origin, destination) = match.destructured
        if (origin in notPlaces || destination in notPlaces) return null
        return Journey(origin, destination)
    }

    /** The places named in the question (longest first; a journey's destination, then its origin, first) and the sections asked for. */
    data class Lookup(val places: List<String>, val sections: List<String>, val journey: Journey? = null)

    /** The guide sections a question's topic asks for, whether or not it names a place. */
    fun topicsOf(question: String): List<String> = topics.filter { (_, words) -> words.containsMatchIn(question) }.map { it.first }

    // The travel topic guides answer travellers: "what should I eat to live a long and healthy life"
    // shares a word with Stay healthy and nothing with travel.
    private val travelContext = Regex("(?i)\\b(travel\\w*|trip|abroad|overseas|tourists?|backpack\\w*|flights?|fly(?:ing)?|planes?|airports?|airlines?|" +
        "visas?|passports?|borders?|customs|sim|roaming|hotels?|hostels?|jet ?lag|currenc\\w*|exchange|atms?|euros?|dollars?|pounds|yen|" +
        "tipping|tip|taxis?|metro|trains?|bus|buses|ferry|driv(?:e|ing)|licen[cs]e|vaccin\\w*|malaria|dengue|altitude|tap water|" +
        "mosquito\\w*|repellents?|destination|country|countries|city|cities|local|locals)\\b")

    /** Whether a question with no place is a traveller's, so a travel topic guide may answer it. */
    fun inTravelContext(question: String): Boolean = travelContext.containsMatchIn(question)

    /** The places named in the question and the guide sections its topic asks for. */
    fun lookup(question: String): Lookup? {
        var sections = topicsOf(question)
        if (sections.isEmpty()) return null
        val journey = journeyOf(question)
        val starts = sentenceStart.findAll(question).map { it.range.last + 1 }.toSet()
        val places = ArrayList<String>()
        journey?.let { places += listOf(it.destination, it.origin) }
        for (match in place.findAll(question)) {
            var words = match.groupValues[1].trim().split(Regex("\\s+"))
            // The first word of a sentence is capitalised whatever it is ("Flying into Bali", "Is Rio de
            // Janeiro safe"), unless a colon marks it as a name ("Hanoi: how do I cross the street?").
            if (match.range.first in starts && !question.substring(match.range.last + 1).startsWith(":")) words = words.drop(1)
            while (words.isNotEmpty() && words.first() in notPlaces) words = words.drop(1)
            if (words.isNotEmpty()) places += words.joinToString(" ")
        }
        val named = places.filter { it !in notPlaces && it.length >= 3 && !demonym.containsMatchIn(it) }.distinct()
            .let { if (journey == null) it.sortedByDescending(String::length) else it }
        if (named.isEmpty()) return null
        // A transit ticket or card is under "Get around" whatever other topic the question's words name.
        if (journey == null && asksTicket(question) && "Get around" !in sections) sections = sections + "Get around"
        return Lookup(named, sections, journey)
    }

    // Forms that share no prefix with the rest of the verb: "lost" is "lose" and "losing".
    private val irregular = mapOf("lost" to "los", "lose" to "los", "losing" to "los", "loses" to "los")
    private val marks = Regex("\\p{Mn}+")

    /** The text without accents, in lowercase: "Métro" matches "metro", and "İstanbulkart" stays one word. */
    internal fun fold(text: String): String = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** A question word as a prefix of its other forms: "souks" finds "souk", "lost" finds "losing", "ears" finds "eardrums". */
    internal fun stem(word: String): String {
        val folded = fold(word)
        if (folded.length == 4 && folded.endsWith("s") && !folded.endsWith("ss")) return folded.take(3)
        return irregular[folded] ?: folded.take(maxOf(4, folded.length - 1))
    }

    // Words that say how a question is put, not what it asks: "how do I get into", "do I need to take".
    internal val filler = setOf("into", "get", "go", "need", "want", "much", "many", "more", "just", "really", "one", "kind", "anything", "way", "take", "make", "use")
    // Words that name the topic rather than the thing asked about: "safe" alone answers "is the tap water safe" no better than any safety sentence.
    private val generic = setOf("safe", "safel", "safety", "health", "healthy")

    private val questionWord = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]*")
    private val apps = setOf("grab", "uber", "bolt", "lyft")

    /**
     * The words of the names a question gives after its first word ("New York", "Singapore"): they
     * say where, not what. A jet lag guide's sentence about a Paris–New York flight shares "New
     * York" with "after flying from New York to Singapore" and still says nothing about jet lag.
     * An acronym ("SIM", "ATMs") is a thing, not a name, and stays, as does a ride-hailing app ("Can I
     * use Grab in Bangkok?" asks about Grab).
     */
    internal fun nameTerms(question: String): Set<String> = questionWord.findAll(question).drop(1).map { it.value }
        .filter { w -> w.first().isUpperCase() && w.length > 1 && w[1].isLowerCase() && w.lowercase() !in apps }
        .flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toSet()

    /** The question's words that ask something, stemmed and without accents: not its names, nor how it is put. */
    private fun questionWords(question: String, placeWords: Set<String>): List<String> =
        FtsQuery.from(question)?.terms.orEmpty().filterNot { it in placeWords || it in filler }.map(::stem).distinct()

    // "a beach in Sydney", "in northern Cambodia", "on the outskirts of Kyoto".
    private val within = Regex("\\b(?:in|near|outside|of)\\s+(?:the\\s+)?(?:(?:north|south|east|west|central|northern|southern|eastern|western|" +
        "north-?eastern|north-?western|south-?eastern|south-?western)\\s+)?(\\p{Lu}[\\p{L}'’-]*(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?\\p{Lu}[\\p{L}'’-]*)*)")
    // "Porto is Portugal's second largest city", "Delhi, India's largest city".
    private val owns = Regex("\\b(\\p{Lu}[\\p{L}'’-]*(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?\\p{Lu}[\\p{L}'’-]*)*)['’]s\\s+" +
        "(?:second|third|largest|biggest|capital|main|most|busiest|oldest|principal|former|northern|southern|eastern|western)")
    private val directions = setOf("North", "South", "East", "West", "Central", "Northern", "Southern", "Eastern", "Western")
    private val aside = Regex("\"[^\"]*\"|“[^”]*”|\\([^)]*\\)")

    /** The text without its hatnotes and list items ('- "BKK" redirects here.'). */
    private fun prose(text: String): String = text.lines().filterNot { it.trimStart().startsWith("- ") }.joinToString("\n")

    /**
     * The place a beach, district or city lies in, from the first sentence of its overview:
     * "Bondi Beach is a beach in Sydney, Australia" gives "Sydney", whose guide has the beach
     * safety section that Bondi Beach, with no guide of its own, lacks; "Bali is the most visited
     * part of Indonesia" gives Indonesia, whose guide has the visa section Bali's defers to.
     */
    fun parentOf(place: String, overview: String): String? {
        val first = SourceLead.sentences(prose(overview).replace(Regex("\\s+"), " ").trim()).firstOrNull() ?: return null
        // A nickname in quotes or a note in brackets names no containing place: 'the "Island of the Gods"'.
        val plain = aside.replace(first, " ")
        return (within.findAll(plain).map { it.range.first to it.groupValues[1] } + owns.findAll(plain).map { it.range.first to it.groupValues[1] })
            .sortedBy { it.first }.map { it.second.removeSuffix("'s").removeSuffix("’s").trim() }
            .firstOrNull { it.length >= 3 && !it.equals(place, ignoreCase = true) && !place.contains(it, ignoreCase = true) && it !in notPlaces && it !in directions }
    }

    private val pointsBack = Regex("^(?:These|This|Those|They|It|Its|Such|That)\\b")

    /** A section's own heading, the last after its parents: "Make copies" in "What if I lose it? > Make copies". */
    internal fun ownHeading(title: String): String = fold(title.substringAfter(" — ").substringAfterLast(" > "))

    private const val TERMINAL = ".!?\"”’)"

    // "Is Rio de Janeiro safe for a solo traveller?", "how safe is Johannesburg", "is it safe to walk around Naples at night".
    private val verdict = Regex("(?i)\\bis \\p{Lu}[\\p{L}'’ -]*?\\bsafe\\b|\\bhow safe is\\b|\\bis it safe (?:to (?:travel|go|visit|walk|stay)|in|at|for)\\b|" +
        "\\bsafe (?:for|as) (?:a |an )?(?:solo|single|female|woman|women|girls?|lgbt\\w*|gay|tourists?|travell?ers?|backpackers?|famil(?:y|ies))\\b|" +
        "\\bsafe (?:to visit|to travel|to go|at night|city|country|place|destination)\\b")
    // An activity's safety ("is it safe to drive at night", "is the tap water safe") is answered by its own passage, not the verdict on the place.
    private val activity = Regex("(?i)\\b(?:driv\\w*|swim\\w*|drink\\w*|eat\\w*|rid(?:e|ing)|cycl\\w*|hik\\w*|tap water|food|beach|surf\\w*|taxis?|bus|metro|scooters?|motorbikes?|cars?)\\b")
    private val label = Regex("^(?:Caution|Warning|Note|Important):\\s*")

    /** Whether the question asks how safe the place itself is, which its "Stay safe" section opens with. */
    internal fun asksSafetyVerdict(question: String): Boolean = verdict.containsMatchIn(question) && !activity.containsMatchIn(question)

    private fun isFirstChunk(item: Evidence): Boolean = item.chunkId.substringAfterLast(':').trimStart('0').isEmpty()

    private fun isVerdictSection(place: String, item: Evidence): Boolean = item.title == "$place — Stay safe" && isFirstChunk(item)

    /** The first two sentences of the place's "Stay safe" section: the guide's own verdict on how safe it is. */
    private fun verdictQuote(place: String, sources: List<PromptSource>): Pair<String, String>? {
        val source = sources.firstOrNull { isVerdictSection(place, it.evidence) } ?: return null
        val sentences = leadSentences(label.replace(source.evidence.text.trim(), ""))
        val quote = StringBuilder(sentences.firstOrNull() ?: return null)
        sentences.getOrNull(1)?.let { if (quote.length + 1 + it.length <= MAX_QUOTE) quote.append(' ').append(it) }
        return if (quote.last() in TERMINAL) source.citationId to quote.toString() else null
    }

    private val prohibition = Regex("(?i)\\b(?:do not|don't|never|avoid|take off|remove|forbidden|not allowed|unacceptable|offensive|rude)\\b")
    private val asksNot = Regex("(?i)\\b(?:shouldn't|should not|not do|what not|avoid|taboo|forbidden|offend\\w*|rude)\\b")

    /**
     * The sentences of the place's own section that answer the question, quoted for the answer's
     * opening: the sentence sharing most of the question's words, with the one or two after it.
     * The question's own words count double; the section's heading and the topic's own words
     * count too, since a guide answers "how does haggling work" with "you generally need to
     * bargain", but a sentence needs at least one of the question's words. Quoted alone, it is the
     * whole answer, so it comes from the section the guide files the answer under. A compound
     * question (what to wear and what not to do; is it safe and which areas to avoid) gets a second
     * quote from the other picked section, and a question about a place's airport one from the
     * airport guide [second], in [SourceLead.Lead.more].
     */
    fun lead(question: String, place: String?, sources: List<PromptSource>, second: String? = null): SourceLead.Lead? {
        if (place == null) return null
        val placeWords = FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question)
        val words = questionWords(question, placeWords)
        if (words.isEmpty()) return null
        // A safety, health or etiquette question is answered by those sections, in their words:
        // pickpockets on the metro are under "Stay safe", while the metro's own section, full of
        // "subway" and "getting around", gives its opening hours.
        val care = asksCare(question)
        val asked = topics.filter { (name, pattern) -> (!care || name in CARE_TOPICS) && pattern.containsMatchIn(question) }.map { it.second }
        var own = sources.filter { it.evidence.title.startsWith("$place — ") }
        if (care) own = own.filter { isCare(it.evidence) }.ifEmpty { own }
        // A day's budget is under "Costs" or "Budget"; "Tax" says "per day" too ("a refund if you spend
        // ¥5,000 per day per shop") and answers something else, so without such a section the model answers.
        if (budget.first.containsMatchIn(question)) own = own.filter { budget.second.containsMatchIn(ownHeading(it.evidence.title)) }
        val wanted = wanted(question)
        val (headed, others) = own.partition { headedFor(it.evidence, wanted) > 0 }
        val best = if (headed.isEmpty()) bestSentence(words, asked, others, 1, 2) else {
            // The section headed for the part asked about answers, unless another picked section shares more
            // of the question's words: "do not rely on credit cards" under "Shopping" beats "ATMs accept credit
            // cards" under "Money", but "night cashiers" under "Opening hours" says nothing about cash.
            val part = bestSentence(words, asked, headed, 1, 2)
            bestSentence(words, asked, others, maxOf(2, (part?.shared ?: 0) + 1), part?.hits ?: 2) ?: part
        }
        val verdict = if (asksSafetyVerdict(question)) verdictQuote(place, own) else null
        if (best == null && verdict == null) return null
        val more = ArrayList<Pair<String, String>>()
        if (best != null) {
            // The other picked section headed for a different part asked about answers the question's other half.
            val chosen = own.first { it.citationId == best.lead.citationId }
            val partsOfBest = partsOf(chosen.evidence, wanted)
            val others2 = own.filter { it.citationId != best.lead.citationId && partsOf(it.evidence, wanted).let { p -> p.isNotEmpty() && (p intersect partsOfBest).isEmpty() } }
            if (others2.isNotEmpty()) {
                // A practical second section ("By metro" beside the transit card) must share two words; an etiquette one, one.
                var second2 = bestSentence(words, asked, others2, if (care) 1 else 2, 1, skip = best.lead.text)
                // "Is there anything I shouldn't do inside?" is answered by the section's first prohibition.
                if (second2 == null && asksNot.containsMatchIn(question)) second2 = others2.firstNotNullOfOrNull { source ->
                    val sentences = leadSentences(source.evidence.text)
                    sentences.indexOfFirst { prohibition.containsMatchIn(it) }.takeIf { it >= 0 }
                        ?.let { i -> quoteAt(sentences, i)?.let { Quote(0, 0, SourceLead.Lead(source.citationId, it)) } }
                }
                second2?.let { more += it.lead.citationId to it.lead.text }
            }
        }
        if (second != null) {
            val theirs = sources.filter { it.evidence.title.startsWith("$second — ") }
            val theirWords = questionWords(question, FtsQuery.from(second)?.terms.orEmpty().toSet() + placeWords)
            bestSentence(theirWords, asked, theirs, 1, 1)?.let { more += it.lead.citationId to it.lead.text }
        }
        if (verdict != null) {
            val (citation, text) = verdict
            val rest = listOfNotNull(best?.lead?.takeUnless { it.citationId == citation && (it.text in text || text in it.text) }?.let { it.citationId to it.text }) + more
            return SourceLead.Lead(citation, text, rest)
        }
        return SourceLead.Lead(best!!.lead.citationId, best.lead.text, more)
    }

    private class Quote(val hits: Int, val shared: Int, val lead: SourceLead.Lead)

    /** The passage's sentences, a list item each as the sentence it is, without a piece that opens mid-sentence. */
    private fun leadSentences(text: String): List<String> =
        // A passage may open inside a sentence (passages overlap a little); that piece is no quote.
        text.split(Regex("\\n+")).filterNot { it.startsWith("- ") && it.length < 40 }
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ") }
            .flatMap { SourceLead.sentences(it.replace(Regex("\\s+"), " ").trim()) }
            .dropWhile { s -> s.firstOrNull()?.let { !it.isUpperCase() && !it.isDigit() && it !in "\"“'‘(¥€$£" } ?: true }

    private const val MAX_QUOTE = 640

    /**
     * The quote around sentence [i]: it, with the one or two after it, or, when it ends its passage,
     * the [relevant] ones before it.
     */
    private fun quoteAt(sentences: List<String>, i: Int, relevant: (Int) -> Boolean = { false }): String? {
        val sentence = sentences[i]
        // "These flags are placed by the lifeguards..." points back to the sentence
        // before it ("swim between the yellow and red flags"), so the quote starts there.
        val back = if (i > 0 && pointsBack.containsMatchIn(sentence)) 1 else 0
        var quote = (if (back == 1) sentences[i - 1] + " " else "") + sentence
        for (next in sentences.drop(i + 1).take(2)) {
            // A list lead-in ("The following cards are available:") ends the quote.
            if (quote.length + next.length > 520 || next.last() !in TERMINAL) break
            quote += " $next"
        }
        // The last sentence of a passage usually qualifies the rule before it ("Long pants are
        // preferable to shorts, and you should always cover your shoulders. If you are a woman, ...
        // Outside of the major temples, men wearing shorts is generally acceptable."), so the one
        // or two sentences before it are quoted with it when one of them speaks to the question.
        if (i == sentences.lastIndex && back == 0) {
            val before = (i - 1 downTo maxOf(0, i - 2)).toList()
            if (before.any(relevant)) for (j in before) {
                if (sentences[j].length + 1 + quote.length > MAX_QUOTE) break
                quote = "${sentences[j]} $quote"
            }
        }
        return quote.takeIf { it.last() in TERMINAL && it.length <= MAX_QUOTE }
    }

    /** The best sentence of [sources] scoring above [over] and sharing at least [atLeast] of the question's [words]; not one already in [skip]. */
    private fun bestSentence(words: List<String>, asked: List<Regex>, sources: List<PromptSource>, atLeast: Int, over: Int, skip: String = ""): Quote? {
        var best: Quote? = null
        val hasSpecific = words.any { it !in generic }
        for (source in sources) {
            val sentences = leadSentences(source.evidence.text)
            // A section headed with the question's own word ("Stay safe > Traffic") counts double;
            // a subsection does not inherit its parent's heading ("... > Make copies").
            val heading = ownHeading(source.evidence.title)
            val scored = sentences.map { sentence ->
                val tokens = Regex("[\\p{L}\\p{N}]+").findAll(fold(sentence)).map { it.value }.toSet()
                val shared = words.count { w -> tokens.any { it.startsWith(w) } }
                // "Safe" alone is no answer to "is the tap water safe": a specific word must be shared when the question has one.
                val specific = if (hasSpecific) words.count { w -> w !in generic && tokens.any { it.startsWith(w) } } else shared
                val topical = asked.flatMap { p -> p.findAll(sentence).map { it.value.lowercase() }.toList() }.distinct().size
                Triple(2 * shared + words.count { w -> heading.contains(w) } + topical, shared, specific)
            }
            scored.forEachIndexed { i, (hits, shared, specific) ->
                // "See the Indonesia article for information about visa regulations" sends the reader elsewhere; it is no answer.
                if (specific >= atLeast && hits > (best?.hits ?: over) && sentences[i] !in skip && !defers.containsMatchIn(sentences[i])) {
                    quoteAt(sentences, i) { j -> scored[j].first > 0 }?.let { best = Quote(hits, shared, SourceLead.Lead(source.citationId, it)) }
                }
            }
        }
        return best
    }

    /**
     * Of the titled passages found, those of the named place's guide sections, closest to the
     * question first, one passage per section before a second of any: "Getting tickets" must not
     * crowd out "Rules", where Italy's guide says to validate a ticket.
     */
    fun pick(question: String, place: String, found: List<Evidence>, limit: Int): List<Evidence> {
        val words = FtsQuery.from(question)?.terms.orEmpty().filterNot { it in filler }.map(::fold).toSet()
        val wanted = wanted(question)
        val verdict = asksSafetyVerdict(question)
        val scores = HashMap<Evidence, Int>()
        fun score(item: Evidence) = scores.getOrPut(item) {
            val tokens = Regex("[\\p{L}\\p{N}]+").findAll(fold(item.title + " " + item.text)).map { it.value }.toSet()
            val title = fold(item.title)
            // The section heading naming the topic ("Traffic", "Beachgoing") counts most, and a
            // heading for the part asked about ("Entry requirements" for a visa) more still;
            // "safe" in "Stay safe" names the topic, not what the section is about.
            words.count { w -> tokens.any { it.startsWith(w) } } + 3 * words.count { w -> w !in headingWords && title.contains(w) } +
                headedFor(item, wanted) + if (verdict && isVerdictSection(place, item)) HEADED else 0
        }
        val ranked = found.filter { it.title.startsWith("$place — ") && isSection(it.title) }.sortedByDescending(::score)
        return representatives(ranked, ::score, limit, HEADED)
    }

    /**
     * One passage per section first, then the rest. A section headed for what is asked (scoring
     * at least [headed]) is stood for by its opening passage when that scores within one of its
     * best, since the opening states the rule and later passages its exceptions; a section found
     * by shared words alone is stood for by the passage that shares them.
     */
    internal fun representatives(ranked: List<Evidence>, score: (Evidence) -> Int, limit: Int, headed: Int): List<Evidence> {
        val first = ranked.distinctBy { it.title }.map { item ->
            val opening = ranked.firstOrNull { it.title == item.title && isFirstChunk(it) }
            if (opening != null && score(item) >= headed && score(opening) >= score(item) - 1) opening else item
        }
        return (first + ranked).distinct().take(limit)
    }

    private val defers = Regex("(?i)\\bsee the \\p{L}+(?: \\p{L}+)? article\\b")

    /**
     * Whether the place's own [picked] sections answer the part asked about: one is headed for it,
     * or one says two of the question's words in a sentence that does not send the reader to
     * another guide ("See the Indonesia article for information about visa regulations"). When
     * they do not, the containing country's same sections may: Wikivoyage files visas, money, SIM
     * cards and national rail under the country, and its cities and islands defer to it.
     */
    fun coversPart(question: String, place: String, picked: List<Evidence>): Boolean {
        val wanted = wanted(question)
        if (wanted.isEmpty() || asksCare(question)) return true
        val own = picked.filter { it.title.startsWith("$place — ") }
        if (own.any { headedFor(it, wanted) > 0 }) return true
        val words = questionWords(question, FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question))
        // A sentence covers the part only when it speaks of it ("visa"), not when it merely shares
        // two everyday words ("weekend", "apply") with the question, as a parking paragraph did.
        val asking = activeParts(question).filter { it.asks(question) }
        return own.any { item ->
            leadSentences(item.text).any { sentence ->
                val tokens = Regex("[\\p{L}\\p{N}]+").findAll(fold(sentence)).map { it.value }.toSet()
                !defers.containsMatchIn(sentence) && asking.any { it.asks(sentence) } &&
                    words.count { w -> w !in generic && tokens.any { it.startsWith(w) } } >= 2
            }
        }
    }

    private val airport = Regex("\\b($PLACE_WORD(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?$PLACE_WORD)*)\\s+(?i:(?:international\\s+)?airport)\\b")
    private val airportTopics = mapOf("Connect" to "Connect", "Buy" to "Buy", "Cope" to "Cope", "Get in" to "Ground transportation", "Get around" to "Ground transportation")

    /** The place whose airport the question names ("Delhi airport"), for a question an airport guide's sections answer. */
    fun airportOf(question: String): String? {
        val place = airport.find(question)?.groupValues?.get(1) ?: return null
        if (place in notPlaces || topicsOf(question).none { it in airportTopics }) return null
        return place
    }

    /** The airport guide's sections for the question: "airport" itself asks how to get in; a SIM or cash question at the airport reads Connect or Buy, not the trains. */
    fun airportSections(question: String): List<String> {
        val asked = topicsOf(question).filter { it in airportTopics }
        return asked.filter { it in setOf("Connect", "Buy", "Cope") }.map { airportTopics.getValue(it) }.ifEmpty { asked.map { airportTopics.getValue(it) }.distinct() }
    }

    /** The airport guide, among [overviews] of airports, whose first sentence names the place: "Indira Gandhi International Airport (DEL), the main airport of Delhi". */
    fun airportGuide(place: String, overviews: List<Evidence>): String? = overviews.firstOrNull { item ->
        item.title.endsWith(" — Overview") && SourceLead.sentences(prose(item.text).replace(Regex("\\s+"), " ").trim()).firstOrNull()?.let { first ->
            Regex("\\b${Regex.escape(place)}\\b").containsMatchIn(first) && Regex("(?i)\\bairport\\b").containsMatchIn(first)
        } == true
    }?.title?.removeSuffix(" — Overview")

    private val choice = Regex("(?i)\\b(?:which (?:one|is|of these|of them)|better|cheaper|should i|best)\\b")

    /** "Euros or my home currency, which one is cheaper?": a choice between the question's own alternatives, which a quoted passage alone does not make. */
    fun asksChoice(question: String): Boolean =
        Regex("(?i)\\bor\\b").containsMatchIn(question) && choice.containsMatchIn(question) || Regex("(?i)\\bwhich (?:one|is (?:better|cheaper|safer))\\b").containsMatchIn(question)

    private const val HEADED = 10
    private val CARE_TOPICS = setOf("Stay safe", "Stay healthy", "Respect")

    /** A safety, health or etiquette question, which those sections answer whatever transport it names. */
    private fun asksCare(question: String): Boolean =
        topics.any { (name, pattern) -> name in CARE_TOPICS && pattern.containsMatchIn(question) }

    private fun isCare(item: Evidence): Boolean = CARE_TOPICS.any { item.title.substringAfter(" — ").startsWith(it) }
}
