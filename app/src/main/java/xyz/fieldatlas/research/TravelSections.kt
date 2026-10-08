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
            "prices?|souks?|markets?|pay|paying|budget\\w*|afford\\w*|cost of living|how much money|" +
            // What the shops sell and when they open; a bare "open" says nothing about shops.
            "shops?|shopping|supermarkets?|grocer\\w*|opening hours|open on|closed on|sundays?|public holidays?)\\b"),
        "Get in" to Regex("(?i)\\b(airport|arrive|arriving|arrival|get (?:to|from)|$VISA|border|ferry from|train to|flights?)\\b"),
        "Get around" to Regex("(?i)\\b(get around|getting around|bus|buses|taxi|taxis|metro|subway|tram|train|trains|ride[- ]hailing|grab|uber|bolt|lyft|rent a car|car rental|drive|driving|scooter|motorbike|bike|" +
            "rickshaws?|tuk-?tuks?|autos?)\\b"),
        "Respect" to Regex("(?i)\\b(wear|dress|clothing|temples?|wats?|mosques?|churches|church|cathedrals?|shrines?|pagodas?|etiquette|customs?|rude|polite|respect\\w*|taboo|shoes|" +
            "cover(?:ing)? (?:up|my|your|the)?\\s*(?:shoulders|knees|head|hair|arms)|modest(?:ly)?)\\b"),
        "Connect" to Regex("(?i)\\b(sim|sim card|phone|mobile data|internet|wi-?fi|esim)\\b"),
        "Talk" to Regex("(?i)\\b(language|speak|say|phrase|english)\\b"),
        "Understand" to Regex("(?i)\\b(climate|weather|season|rainy|monsoon|best time|when to (?:go|visit)|holidays?|festivals?)\\b"),
        "Cope" to Regex("(?i)\\b(embass\\w*|consulate|laundry|launderette|toilets?)\\b"),
        // Sauna customs are filed under Do in Finland's guide, not just Respect.
        "Do" to Regex("(?i)\\bsaunas?\\b"),
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
    private val validation = Regex("(?i)\\b(?:validat\\w*|stamps?|stamped|stamping|punch(?:ed|ing)?)\\b")
    private val validationHeading = Regex("(?i)\\b(?:rules?|validat\\w*)\\b")
    private val ruleHeading = Regex("(?i)^(?:opening hours|entry requirements|immigration|rules|regulations)$")
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
        Part({ q: String -> asksTicket(q) && !asksJourney(q) }, Regex("(?i)\\b(?:tickets?|ticketing|fares?|pass(?:es)?|cards?|\\p{L}+kart|\\p{L}+card)\\b"), whenAsked = true),
        Part({ q: String -> ticket.containsMatchIn(q) && validation.containsMatchIn(q) }, validationHeading, whenAsked = true),
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
        // Scams are a safety matter, so the part stays active in a safety question ("what scams at the airport?").
        Part(Regex("(?i)\\bscams?\\b"), Regex("(?i)\\bscams?\\b|\\bstay safe\\b"), care = true),
        // Whether a rickshaw is safe at night is still answered by its own section.
        Part(Regex("(?i)\\brickshaws?\\b|\\btuk-?tuks?\\b"), Regex("(?i)\\b(?:rickshaws?|tuk-?tuks?)\\b"), care = true),
        // Shops open on a Sunday: the country guide files it under "Opening hours".
        Part(Regex("(?i)\\bopening hours\\b|\\bopen on\\b|\\bclosed\\b|\\bsundays?\\b|\\bholidays?\\b"), Regex("(?i)\\b(?:opening hours|hours|holidays?)\\b")),
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
        Part(Regex("(?i)\\bsaunas?\\b"), Regex("(?i)\\bsaunas?\\b"), care = true),
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
        val ticket = asksTicket(question) && !asksJourney(question)
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
    private const val NAME = "$PLACE_WORD(?:\\s+(?:(?:de|da|do|del|la|el)\\s+)?$PLACE_WORD)*"
    private val place = Regex("\\b($NAME)")
    private val notPlaces = setOf("How", "What", "Which", "Where", "When", "Why", "Who", "Is", "Are", "Can", "Do", "Does", "Should",
        "I", "I'm", "My", "We", "Tell", "Please", "The", "A", "An", "In", "At", "To", "From", "For", "Any", "Best", "EU",
        // A day or a month names when, not where: "late on a Saturday", "a week in November".
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val sentenceStart = Regex("(?:^|[.!?:]\\s+)(?=\\p{Lu})")
    // A nationality ("Canadian passport", "Japanese onsen", "German citizen", "Swiss francs") names no guide.
    private val nationality = Regex("(?:ian|ese|ish|German|French|Swiss|Dutch|Greek|Thai|Czech)$")
    private val modes = Regex("(?i)\\b(?:trains?|rail(?:way)?|bus(?:es)?|coach(?:es)?|ferr(?:y|ies)|boats?|flights?|fly(?:ing)?|driv(?:e|ing))\\b")
    private val journey = Regex("\\b($NAME)\\s+to\\s+($NAME)\\b")
    // "from milan to varenna": a journey typed in lower case, resolved against the guides' titles by [resolved].
    private val lowercaseJourney = Regex("(?i)\\bfrom\\s+(\\p{Ll}[\\p{L}'’-]{3,})\\s+to\\s+(\\p{Ll}[\\p{L}'’-]{3,})\\b")
    // The object of a preposition is the place asked about: "to China", "in the UK"; "a UK passport" is an adjective.
    private const val PREPOSITION = "(?:in|to|from|at|into|near|around|for|visit(?:ing)?|enter(?:ing)?)"
    private val prepositional = Regex("\\b$PREPOSITION\\s+(?:the\\s+)?($NAME)")
    private val aliased = Regex("(?i)\\b$PREPOSITION\\s+(?:the\\s+)?(UK|US|USA|UAE|NZ|HK|Holland)\\b(?!\\s+(?:passport|citizen|national|resident|visa|driving|licen[cs]e|dollar|embassy|consulate)s?\\b)")
    private val aliases = mapOf("uk" to "United Kingdom", "us" to "United States", "usa" to "United States", "uae" to "United Arab Emirates",
        "nz" to "New Zealand", "hk" to "Hong Kong", "holland" to "Netherlands")
    // A word typed in lower case after a preposition may name a guide ("in finland"); two words may ("in new delhi").
    private val lowercaseName = Regex("(?i)\\b$PREPOSITION\\s+(?:(?:the|a|an)\\s+)?(\\p{Ll}[\\p{L}'’-]*)(?:\\s+(\\p{Ll}[\\p{L}'’-]*))?")
    // Guides whose title is also an everyday word: "nice weather", "a bath", "split the bill" name no place unless capitalised.
    private val homonyms = setOf("nice", "bath", "split", "orange", "reading", "turkey", "normal")
    private val linking = setOf("de", "da", "do", "del", "la", "el")
    private const val MAX_CANDIDATES = 8

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

    /** A journey typed in lower case ("from milan to varenna by train"), as the question spells it. */
    private fun lowercaseJourneyOf(question: String): Pair<String, String>? {
        if (journeyOf(question) != null || !modes.containsMatchIn(question)) return null
        val match = lowercaseJourney.find(question) ?: return null
        val (origin, destination) = match.destructured
        if (!isCandidate(origin) || !isCandidate(destination)) return null
        return origin to destination
    }

    /** Whether the question describes a journey, however it is spelt. */
    private fun asksJourney(question: String): Boolean = journeyOf(question) != null || lowercaseJourneyOf(question) != null

    /**
     * The places named in the question (a journey's destination, then its origin, first; else the
     * objects of prepositions, longest first) and the sections asked for. [candidates] are names
     * the question may spell differently from the guides: words typed in lower case ("in finland",
     * "from milan to varenna") when no capitalised name was found, and the prefixes of a capitalised
     * run ("Venice Marco Polo" has no guide; "Venice" has). The orchestrator looks each up by title
     * and passes what it found to [resolved]. [journeyCandidate] is a lower-case journey's origin
     * and destination.
     */
    data class Lookup(val places: List<String>, val sections: List<String>, val journey: Journey? = null,
        val candidates: List<String> = emptyList(), val journeyCandidate: Pair<String, String>? = null)

    /** The guide sections a question's topic asks for, whether or not it names a place. */
    fun topicsOf(question: String): List<String> = topics.filter { (_, words) -> words.containsMatchIn(question) }.map { it.first }

    // The travel topic guides answer travellers: "what should I eat to live a long and healthy life"
    // shares a word with Stay healthy and nothing with travel.
    private val travelContext = Regex("(?i)\\b(travel\\w*|trip|abroad|overseas|tourists?|backpack\\w*|flights?|fly(?:ing)?|planes?|airports?|airlines?|" +
        "visas?|passports?|borders?|customs|sim|roaming|hotels?|hostels?|jet ?lag|currenc\\w*|exchange|atms?|euros?|dollars?|pounds|yen|" +
        "tipping|tip|taxis?|metro|trains?|bus|buses|ferry|driv(?:e|ing)|licen[cs]e|vaccin\\w*|malaria|dengue|altitude|tap water|" +
        "mosquito\\w*|repellents?|destination|country|countries|city|cities|local|locals)\\b")
    private val guideTopics = setOf("Respect", "Stay safe", "Buy")

    /**
     * Whether a question with no place is a traveller's, so a travel topic guide may answer it. A
     * question on manners, safety or shopping that names a guide in its own words ([namesGuide],
     * "sauna etiquette") is a traveller's too.
     */
    fun inTravelContext(question: String, namesGuide: Boolean = false): Boolean =
        travelContext.containsMatchIn(question) || namesGuide && topicsOf(question).any { it in guideTopics }

    /** Whether a lower-case word may name a guide: a word of four letters or more that is neither a stopword nor an everyday homonym. */
    private fun isCandidate(word: String): Boolean {
        val folded = fold(word)
        return folded.length >= 4 && folded !in homonyms && folded !in filler && FtsQuery.from(word) != null &&
            folded !in setOf("swim", "after", "home", "currency", "discounted", "price") &&
            notPlaces.none { it.equals(word, ignoreCase = true) }
    }

    /** The prefixes of a capitalised run, longest first: "Venice Marco Polo" offers "Venice Marco" and "Venice". */
    private fun prefixes(name: String): List<String> {
        val words = name.split(Regex("\\s+"))
        val out = ArrayList<String>()
        var rest = words.dropLast(1)
        while (rest.isNotEmpty()) {
            while (rest.isNotEmpty() && rest.last() in linking) rest = rest.dropLast(1)
            if (rest.isEmpty()) break
            out += rest.joinToString(" ")
            rest = rest.dropLast(1)
        }
        return out
    }

    /** The places named in the question and the guide sections its topic asks for. */
    fun lookup(question: String): Lookup? {
        var sections = topicsOf(question)
        if (sections.isEmpty()) return null
        val journey = journeyOf(question)
        val starts = sentenceStart.findAll(question).map { it.range.last + 1 }.toSet()
        val places = ArrayList<String>()
        journey?.let { places += listOf(it.destination, it.origin) }
        aliased.findAll(question).forEach { places += aliases.getValue(it.groupValues[1].lowercase()) }
        for (match in place.findAll(question)) {
            var words = match.groupValues[1].trim().split(Regex("\\s+"))
            // The first word of a sentence is capitalised whatever it is ("Flying into Bali", "Is Rio de
            // Janeiro safe"), unless a colon marks it as a name ("Hanoi: how do I cross the street?").
            if (match.range.first in starts && !question.substring(match.range.last + 1).startsWith(":")) words = words.drop(1)
            while (words.isNotEmpty() && words.first() in notPlaces) words = words.drop(1)
            if (words.isNotEmpty()) places += aliases[words.joinToString(" ").lowercase()] ?: words.joinToString(" ")
        }
        val objects = prepositional.findAll(question).map { it.groupValues[1] }.toSet()
        val named = places.filter { it !in notPlaces && it.length >= 3 && !nationality.containsMatchIn(it) }.distinct()
            .let { if (journey == null) it.sortedWith(compareBy<String> { it !in objects }.thenByDescending(String::length)) else it }
        // A transit ticket or card is under "Get around" whatever other topic the question's words name.
        if (!asksJourney(question) && asksTicket(question) && "Get around" !in sections) sections = sections + "Get around"
        if (named.isNotEmpty()) return Lookup(named, sections, journey, named.flatMap(::prefixes).distinct())
        val journeyCandidate = lowercaseJourneyOf(question)
        val candidates = ArrayList<String>()
        journeyCandidate?.let { candidates += listOf(it.second, it.first) }
        for (match in lowercaseName.findAll(question)) {
            val first = match.groupValues[1]
            val second = match.groupValues[2]
            if (isCandidate(first)) candidates += first
            if (second.isNotEmpty() && isCandidate(second)) {
                if (isCandidate(first)) candidates += "$first $second"
                candidates += second
            }
        }
        val distinct = candidates.distinctBy(::fold).filterNot { it in setOf("swim", "after", "home", "currency", "discounted", "price") }.take(MAX_CANDIDATES)
        if (distinct.isEmpty()) return null
        return Lookup(emptyList(), sections, null, distinct, journeyCandidate)
    }

    /**
     * The lookup with its [Lookup.candidates] resolved against the guide [titles] found for them
     * ("Finland — Respect", "Milan — Overview"): a candidate names the guide whose title, before
     * " — ", is spelt the same but for case, and that spelling is used from then on. A lower-case
     * journey whose ends both resolve becomes a [Journey]. Null when no candidate names a guide.
     */
    fun resolved(lookup: Lookup, titles: List<String>): Lookup? {
        val guides = titles.map { it.substringBefore(" — ").trim() }.distinct()
        fun guideOf(candidate: String): String? = guides.firstOrNull { fold(it) == fold(candidate) }
        val places = lookup.candidates.mapNotNull(::guideOf).distinct()
        if (places.isEmpty()) return null
        val journey = lookup.journeyCandidate?.let { (origin, destination) ->
            val from = guideOf(origin)
            val to = guideOf(destination)
            if (from != null && to != null) Journey(from, to) else null
        }
        val ordered = if (journey != null) (listOf(journey.destination, journey.origin) + places).distinct() else places
        return Lookup(ordered, lookup.sections, journey)
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

    /**
     * Whether a passage [token] is a form of the question's [stem]: it begins with it, and a short
     * stem (four letters or fewer) is allowed only a few letters more, so "time" finds "times" and
     * never "timeshare", nor "ear" "earthquake".
     */
    internal fun matches(stem: String, token: String): Boolean = token.startsWith(stem) && (stem.length > 4 || token.length <= stem.length + 4)

    private fun shares(stem: String, tokens: Set<String>): Boolean = tokens.any { matches(stem, it) }

    private fun tokensOf(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+").findAll(fold(text)).map { it.value }.toSet()

    // Words that say how a question is put, not what it asks: "how do I get into", "do I need to take".
    internal val filler = setOf("into", "get", "go", "need", "want", "much", "many", "more", "just", "really", "one", "kind", "anything", "way", "take", "make", "use",
        // When the trip is says nothing about what is asked: "next month", "for a weekend", "late on a Saturday".
        "next", "month", "months", "week", "weeks", "weekend", "year", "years", "today", "tomorrow", "tonight", "first",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
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

    /**
     * The name words with the places whose guides were read ([resolved], however the question
     * spelt them) and without the [things] the orchestrator looked up and found no guide for:
     * WhatsApp, Gmail and Google Maps in a question about China are what is asked about, and stay
     * question words. A place's alias ("UK") is a name word of the place too.
     */
    internal fun nameTerms(question: String, resolved: List<String>, things: List<String>): Set<String> {
        fun terms(names: List<String>) = names.flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toSet()
        val aliasesOf = resolved.flatMap { place -> aliases.filterValues { it == place }.keys }
        return nameTerms(question) - terms(things) + terms(resolved) + aliasesOf
    }

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

    // "German state", "Italian city": a nationality before a lowercase noun describes, it does not name a place.
    private val adjectiveLike = Regex("(?:ian|ean|ese|ish|an|ch|ic)$|^(?:German|French|Swiss|Dutch|Greek|Thai|Czech)$")
    private val functionWords = setOf("in", "of", "on", "at", "to", "for", "and", "or", "is", "was", "are", "were", "with", "by", "from", "near",
        "where", "which", "that", "but", "as", "has", "have", "had", "who", "whose", "after", "before", "since", "while", "when", "than", "the", "a", "an")

    /**
     * The places a beach, district or city lies in, from the first two sentences of its overview,
     * nearest first: "Varenna is a lakeside village in the central part of Lake Como in the Lombardy
     * region of Italy" gives Lake Como, Lombardy and Italy, and "Frankfurt is the largest city in
     * the German state of Hesse ... centre of Germany" Hesse and Germany. Each may hold the section
     * the place's own guide lacks (Bondi Beach defers to Sydney for beach safety, Bali to Indonesia
     * for visas, Frankfurt to Germany for shop opening hours).
     */
    fun parentsOf(place: String, overview: String): List<String> {
        val first = SourceLead.sentences(prose(overview).replace(Regex("\\s+"), " ").trim()).take(2)
        if (first.isEmpty()) return emptyList()
        // A nickname in quotes or a note in brackets names no containing place: 'the "Island of the Gods"'.
        val plain = aside.replace(first.joinToString(" "), " ")
        val found = (within.findAll(plain) + owns.findAll(plain)).map { it.groups[1]!!.range }.sortedBy { it.first }
        return found.filterNot { range ->
            // "the German state of Hesse": a nationality followed by a lowercase noun is an adjective.
            val name = plain.substring(range)
            val next = Regex("^\\s+(\\p{Ll}[\\p{L}'’-]*)").find(plain.substring(range.last + 1))?.groupValues?.get(1)
            ' ' !in name && adjectiveLike.containsMatchIn(name) && next != null && next !in functionWords
        }.map { plain.substring(it).removeSuffix("'s").removeSuffix("’s").trim() }
            .filter { it.length >= 3 && !it.equals(place, ignoreCase = true) && !place.contains(it, ignoreCase = true) && it !in notPlaces && it !in directions }
            .distinct()
            .toList()
    }

    /** The nearest place the guide's subject lies in; see [parentsOf]. */
    fun parentOf(place: String, overview: String): String? = parentsOf(place, overview).firstOrNull()

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

    // A question's clauses end at "?", ";" or ", and", or begin at "also" or "or should".
    private val clauseEnd = Regex("[?;!]|\\.(?=\\s|$)")
    private val clauseBreak = Regex("(?i),\\s+and\\b|\\balso\\b|\\bor should\\b")
    private val asksForm = Regex("(?i)^\\s*(?:do|does|did|is|are|am|can|could|should|will|would|what|which|where|when|how|why|who|any|must|may|might|shall|what's|whats)\\b")
    // "How much for restaurants": the clause asks an amount, which only a sentence with a figure gives.
    private val amount = Regex("(?i)\\bhow much\\b|\\bhow many\\b|\\bamounts?\\b|\\bcosts?\\b|\\bprices?\\b|\\bfees?\\b|\\bcheap(?:est|er)?\\b")
    private val figure = Regex("\\p{N}|[$€£¥₹]")

    /**
     * The clauses of the question that ask something: "Do you tip in Egypt? How much for
     * restaurants, tour guides and drivers, and should I carry lots of small notes?" asks three
     * things. A statement ("Going to Switzerland for a week in November.") asks nothing.
     */
    internal fun clauses(question: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        val segments = ArrayList<Pair<String, Boolean>>()
        for (end in clauseEnd.findAll(question)) {
            segments += question.substring(start, end.range.first) to (end.value == "?")
            start = end.range.last + 1
        }
        segments += question.substring(start) to false
        for ((segment, asked) in segments) {
            for (piece in segment.split(clauseBreak)) {
                val clause = piece.trim().trimStart(',', ' ')
                if (clause.isNotEmpty() && (asked || asksForm.containsMatchIn(clause))) out += clause
            }
        }
        return out
    }

    /** The clause's own specific words, stemmed: not its names, how it is put, nor its topic ("safe"). */
    private fun clauseWords(clause: String, placeWords: Set<String>): List<String> = questionWords(clause, placeWords).filterNot { it in generic }

    /** Whether a quoted [text] answers the [clause]: it shares a specific word, with a figure when an amount is asked. */
    private fun covers(clause: String, words: List<String>, text: String): Boolean {
        val tokens = tokensOf(text)
        if (words.none { shares(it, tokens) }) return false
        return !amount.containsMatchIn(clause) || figure.containsMatchIn(text)
    }

    /**
     * Whether every clause the question asks is answered by one of the [lead]'s quotes, so the
     * quotes can stand alone: when one is not ("what happens if I forget?"), the model answers
     * under the quotes. A clause asking what not to do is answered by a quoted prohibition.
     */
    fun coversEveryClause(question: String, lead: SourceLead.Lead, resolved: List<String> = emptyList(), things: List<String> = emptyList()): Boolean {
        val placeWords = nameTerms(question, resolved, things)
        val quotes = listOf(lead.text) + lead.more.map { it.second }
        return clauses(question).all { clause ->
            val words = clauseWords(clause, placeWords)
            // "Is Rio safe for a solo traveller?" is answered by the guide's verdict, which the lead opens with.
            words.isEmpty() || asksSafetyVerdict(clause) || quotes.any { covers(clause, words, it) } ||
                asksNot.containsMatchIn(clause) && quotes.any { prohibition.containsMatchIn(it) }
        }
    }

    /**
     * The sentences of the place's own section that answer the question, quoted for the answer's
     * opening: the sentence sharing most of the question's words, with the one or two after it.
     * The question's own words count double; the section's heading and the topic's own words
     * count too, since a guide answers "how does haggling work" with "you generally need to
     * bargain", but a sentence needs at least one of the question's words. Quoted alone, it is the
     * whole answer, so it comes from the section the guide files the answer under. A compound
     * question (what to wear and what not to do; is it safe and which areas to avoid) gets a second
     * quote from the other picked section, a question about a place's airport one from the airport
     * guide [second], and every other clause asked ("also are auto rickshaws ok at night?") the
     * best sentence of any picked passage that shares two of its words, in [SourceLead.Lead.more].
     * [resolved] names the places whose guides were read, and [things] the names looked up without
     * finding a guide; see [nameTerms].
     */
    fun lead(question: String, place: String?, sources: List<PromptSource>, second: String? = null, resolved: List<String> = listOfNotNull(place),
        things: List<String> = emptyList()): SourceLead.Lead? {
        if (place == null) return null
        val placeWords = FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question, resolved, things)
        val words = questionWords(question, placeWords)
        if (words.isEmpty()) return null
        // A safety, health or etiquette question is answered by those sections, in their words:
        // pickpockets on the metro are under "Stay safe", while the metro's own section, full of
        // "subway" and "getting around", gives its opening hours.
        val care = asksCare(question)
        val asked = topics.filter { (name, pattern) -> (!care || name in CARE_TOPICS) && pattern.containsMatchIn(question) }.map { it.second }
        val all = sources.filter { it.evidence.title.startsWith("$place — ") }
        var own = all
        if (care) own = own.filter { isCare(it.evidence) }.ifEmpty { own }
        // A day's budget is under "Costs" or "Budget"; "Tax" says "per day" too ("a refund if you spend
        // ¥5,000 per day per shop") and answers something else, so without such a section the model answers.
        if (budget.first.containsMatchIn(question)) own = own.filter { budget.second.containsMatchIn(ownHeading(it.evidence.title)) }
        val wanted = wanted(question)
        val asking = wanted.active.filter { it.asks(question) }
        val clauses = clauses(question)
        // When a part is asked about, the quoted sentence must speak of it ("visa", "scams"), or answer
        // a clause asking no part ("will WhatsApp work there?") with two of its words: a parking paragraph
        // sharing "weekend" with a visa question does neither, nor does arriving at night answer "what scams".
        val partless = clauses.filter { c -> asking.none { it.asks(c) } }.map { clauseWords(it, placeWords) }
        val speaks: (String) -> Boolean = { sentence ->
            wanted.isEmpty() || asking.all { it.mode } || asking.any { it.asks(sentence) || it.headings.containsMatchIn(sentence) } || partless.any { cw -> cw.count { shares(it, tokensOf(sentence)) } >= 2 }
        }
        val (headed, others) = own.partition { headedFor(it.evidence, wanted) > 0 }
        val best = if (headed.isEmpty()) bestSentence(words, asked, others, 1, 2, accept = speaks) else {
            // The section headed for the part asked about answers, unless another picked section shares more
            // of the question's words: "do not rely on credit cards" under "Shopping" beats "ATMs accept credit
            // cards" under "Money", but "night cashiers" under "Opening hours" says nothing about cash.
            val part = bestSentence(words, asked, headed, 1, 2, accept = speaks)
            bestSentence(words, asked, others, maxOf(2, (part?.shared ?: 0) + 1), part?.hits ?: 2, accept = speaks) ?: part
        }
        val verdict = if (asksSafetyVerdict(question)) verdictQuote(place, own) else best?.let { chosen ->
            val passage = own.firstOrNull { it.citationId == chosen.lead.citationId } ?: return@let null
            if (!ruleHeading.matches(ownHeading(passage.evidence.title))) return@let null
            val opener = own.firstOrNull { it.evidence.title == passage.evidence.title && isFirstChunk(it.evidence) }
                ?.takeIf { it.citationId != passage.citationId } ?: return@let null
            // The exception cannot stand in for its base rule. Quote the relevant part of the
            // opening chunk as context, retaining the exception's separate citation below it.
            // Days are normally discarded as trip context, but here "Sunday" is the
            // rule's condition, even when the base rule says "shops" rather than
            // the question's "supermarkets". Restore only matched topic terms.
            val ruleWords = (words + asked.flatMap { pattern ->
                pattern.findAll(question).flatMap { tokensOf(it.value).asSequence() }.map(::stem).toList()
            }).distinct()
            bestSentence(ruleWords, asked, listOf(opener), 1, 1, accept = speaks)?.lead?.let { it.citationId to it.text }
        }
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
        val opening = verdict?.second ?: best!!.lead.text
        // One quote per clause asked: a clause no quote answers yet gets the best sentence of any
        // picked passage of the place that shares two of its specific words (one when it has only one).
        for (clause in clauses) {
            val clauseWords = clauseWords(clause, placeWords)
            if (clauseWords.isEmpty()) continue
            val quoted = listOf(opening) + listOfNotNull(best?.lead?.text) + more.map { it.second }
            if (quoted.any { covers(clause, clauseWords, it) }) continue
            if (asksNot.containsMatchIn(clause) && quoted.any { prohibition.containsMatchIn(it) }) continue
            val needsFigure = amount.containsMatchIn(clause)
            val extra = bestSentence(clauseWords, asked, all, if (clauseWords.size == 1) 1 else 2, 0, skip = quoted.joinToString("\n"),
                accept = { !needsFigure || figure.containsMatchIn(it) }) ?: continue
            more += extra.lead.citationId to extra.lead.text
        }
        if (verdict != null) {
            val (citation, text) = verdict
            val rest = listOfNotNull(best?.lead?.takeUnless { it.citationId == citation && (it.text in text || text in it.text) }?.let { it.citationId to it.text }) + more
            return SourceLead.Lead(citation, text, rest)
        }
        return SourceLead.Lead(best!!.lead.citationId, best.lead.text, more)
    }

    private class Quote(val hits: Int, val shared: Int, val lead: SourceLead.Lead)

    /**
     * The passage's sentences, a list item each as the sentence it is, without a piece that opens
     * mid-sentence. A short list item ("- Messaging services: Whatsapp.") is kept when it says one of
     * the question's [words]; otherwise it is a name in a list, not a sentence.
     */
    private fun leadSentences(text: String, words: List<String> = emptyList()): List<String> =
        // A passage may open inside a sentence (passages overlap a little); that piece is no quote.
        text.split(Regex("\\n+")).filterNot { it.startsWith("- ") && it.length < 40 && words.none { w -> shares(w, tokensOf(it)) } }
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ") }
            .flatMap { SourceLead.sentences(it.replace(Regex("\\s+"), " ").trim()) }
            .dropWhile { s -> s.firstOrNull()?.let { !it.isUpperCase() && !it.isDigit() && it !in "\"“'‘(¥€$£" } ?: true }

    private const val MAX_QUOTE = 640

    /**
     * The quote around sentence [i]: it, with the one or two after it, or, when it ends its passage,
     * the [relevant] ones before it. A list item is quoted with the line that leads into its list
     * ("Blocked sites typically include:") and the items before it, since alone it says nothing.
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
        if (back == 0) {
            val leadIn = (i - 1 downTo maxOf(0, i - 4)).firstOrNull { sentences[it].endsWith(":") }
            if (leadIn != null) {
                val withList = sentences.subList(leadIn, i).joinToString(" ") + " " + quote
                if (withList.length <= MAX_QUOTE) quote = withList
            }
        }
        return quote.takeIf { it.last() in TERMINAL && it.length <= MAX_QUOTE }
    }

    /**
     * The best sentence of [sources] scoring above [over] and sharing at least [atLeast] of the
     * question's [words]; not one already in [skip], nor one [accept] turns down.
     */
    private fun bestSentence(words: List<String>, asked: List<Regex>, sources: List<PromptSource>, atLeast: Int, over: Int, skip: String = "",
        accept: (String) -> Boolean = { true }): Quote? {
        var best: Quote? = null
        val hasSpecific = words.any { it !in generic }
        for (source in sources) {
            val sentences = leadSentences(source.evidence.text, words)
            // A section headed with the question's own word ("Stay safe > Traffic") counts double;
            // a subsection does not inherit its parent's heading ("... > Make copies").
            val heading = ownHeading(source.evidence.title)
            val scored = sentences.map { sentence ->
                val tokens = tokensOf(sentence)
                val shared = words.count { w -> shares(w, tokens) }
                // "Safe" alone is no answer to "is the tap water safe": a specific word must be shared when the question has one.
                val specific = if (hasSpecific) words.count { w -> w !in generic && shares(w, tokens) } else shared
                val topical = asked.flatMap { p -> p.findAll(sentence).map { it.value.lowercase() }.toList() }.distinct().size
                Triple(2 * shared + words.count { w -> heading.contains(w) } + topical, shared, specific)
            }
            scored.forEachIndexed { i, (hits, shared, specific) ->
                // "See the Indonesia article for information about visa regulations" sends the reader elsewhere; it is no answer.
                if (specific >= atLeast && hits > (best?.hits ?: over) && sentences[i] !in skip && !defers.containsMatchIn(sentences[i])) {
                    quoteAt(sentences, i) { j -> scored[j].first > 0 }?.takeIf(accept)?.let { best = Quote(hits, shared, SourceLead.Lead(source.citationId, it)) }
                }
            }
        }
        return best
    }

    /** Whether the question asks an amount ("how much for a guide?"), which the passage listing figures answers. */
    internal fun asksAmount(question: String): Boolean = amount.containsMatchIn(question)

    /**
     * Of the titled passages found, those of the named place's guide sections, closest to the
     * question first, one passage per section before a second of any: "Getting tickets" must not
     * crowd out "Rules", where Italy's guide says to validate a ticket. A question asking an amount
     * or several things gets a third slot, for the passage with the figures or the other answer.
     * The names of the [resolved] places say where, not what, and do not count.
     */
    fun pick(question: String, place: String, found: List<Evidence>, limit: Int, resolved: List<String> = listOf(place)): List<Evidence> {
        val placeWords = FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question, resolved, emptyList()) - nameTerms(question)
        val words = FtsQuery.from(question)?.terms.orEmpty().filterNot { it in filler || it in placeWords }.map(::fold).toSet()
        val wanted = wanted(question)
        val verdict = asksSafetyVerdict(question)
        val scores = HashMap<Evidence, Int>()
        fun score(item: Evidence) = scores.getOrPut(item) {
            val tokens = tokensOf(item.title + " " + item.text)
            val title = fold(item.title)
            // The section heading naming the topic ("Traffic", "Beachgoing") counts most, and a
            // heading for the part asked about ("Entry requirements" for a visa) more still;
            // "safe" in "Stay safe" names the topic, not what the section is about.
            words.count { w -> shares(w, tokens) } + 3 * words.count { w -> w !in headingWords && title.contains(w) } +
                headedFor(item, wanted) + if (verdict && isVerdictSection(place, item)) HEADED else 0
        }
        val amounts = asksAmount(question)
        val ranked = found.filter { it.title.startsWith("$place — ") && isSection(it.title) }
            .sortedWith(compareByDescending<Evidence> { score(it) }.thenByDescending { if (amounts) figures(it) else 0 })
        val slots = if (amounts || clauses(question).size > 1) limit + 1 else limit
        return representatives(ranked, ::score, slots, HEADED, amounts) { headedFor(it, wanted) > 0 }
    }

    private fun figures(item: Evidence): Int = figure.findAll(item.text).count()

    /**
     * One passage per section first, then the rest. A section headed for what is asked (scoring
     * at least [headed]) is stood for by its opening passage when that scores within one of its
     * best, since the opening states the rule and later passages its exceptions; a section found
     * by shared words alone is stood for by the passage that shares them. When [amounts] are
     * asked, the headed section's passage with the most figures ("Tour drivers: LE50–100/day")
     * follows its opening.
     */
    internal fun representatives(ranked: List<Evidence>, score: (Evidence) -> Int, limit: Int, headed: Int, amounts: Boolean = false,
        isHeaded: (Evidence) -> Boolean = { score(it) >= headed }): List<Evidence> {
        val sections = ranked.distinctBy { it.title }
        val first = sections.flatMap { item ->
            val opening = ranked.firstOrNull { it.title == item.title && isFirstChunk(it) }
            val rule = ruleHeading.matches(ownHeading(item.title)) && isHeaded(item)
            val stands = if (opening != null && isHeaded(item) && (rule || score(opening) >= score(item) - 1)) opening else item
            // The passage with the figures: the section's best other passage, by shared words then figures.
            val figures = if (amounts && (isHeaded(item) || item == sections.first())) ranked.filter { it.title == item.title && it != stands }
                .sortedWith(compareByDescending<Evidence> { score(it) }.thenByDescending(::figures)).firstOrNull()?.takeIf { figures(it) >= 3 } else null
            // Keep the base rule and the relevant exception together instead of quoting an
            // exception alone (shops closed on Sundays vs station-shop exceptions).
            val exception = item.takeIf { rule && it != stands }
            listOfNotNull(stands, exception, figures)
        }
        return (first + ranked).distinct().take(limit)
    }

    private val defers = Regex("(?i)\\bsee the \\p{L}+(?: \\p{L}+)? article\\b")

    /**
     * Whether the place's own [picked] sections answer the part asked about: one is headed for it,
     * or one says two of the question's words in a sentence that does not send the reader to
     * another guide ("See the Indonesia article for information about visa regulations"). When
     * they do not, the containing country's same sections may: Wikivoyage files visas, money, SIM
     * cards and national rail under the country, and its cities and islands defer to it. For a
     * [journey] read at the country ("Lisbon to Porto by train"), only a section headed for the
     * part counts: the national rail advice, not a sentence sharing "ticket" and "station".
     */
    fun coversPart(question: String, place: String, picked: List<Evidence>, journey: Journey? = journeyOf(question), resolved: List<String> = listOf(place),
        things: List<String> = emptyList()): Boolean {
        val wanted = wanted(question)
        if (wanted.isEmpty() || asksCare(question)) return true
        val own = picked.filter { it.title.startsWith("$place — ") }
        // A regional rail overview or discount pass does not cover ticket validation. Continue
        // through the containing guides until the relevant rule or instruction is actually read.
        if (ticket.containsMatchIn(question) && validation.containsMatchIn(question)) {
            val modes = parts.filter { it.mode && it.asks(question) }
            return own.any { item ->
                (validation.containsMatchIn(item.text) || validationHeading.containsMatchIn(ownHeading(item.title))) &&
                    (modes.isEmpty() || modes.any { it.headings.containsMatchIn(item.title) || it.asks(item.text) })
            }
        }
        if (own.any { headedFor(it, wanted) > 0 }) return true
        if (journey != null && place != journey.destination) return false
        val words = questionWords(question, FtsQuery.from(place)?.terms.orEmpty().toSet() + nameTerms(question, resolved, things))
        // A sentence covers the part only when it speaks of it ("visa"), not when it merely shares
        // two everyday words ("weekend", "apply") with the question, as a parking paragraph did.
        val asking = activeParts(question).filter { it.asks(question) }
        return own.any { item ->
            leadSentences(item.text).any { sentence ->
                val tokens = tokensOf(sentence)
                !defers.containsMatchIn(sentence) && asking.any { it.asks(sentence) } &&
                    words.count { w -> w !in generic && shares(w, tokens) } >= 2
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

    private fun isCare(item: Evidence): Boolean = CARE_TOPICS.any { item.title.substringAfter(" — ").startsWith(it) } ||
        parts.any { it.care && it.headings.containsMatchIn(item.title.substringAfter(" — ")) }
}
