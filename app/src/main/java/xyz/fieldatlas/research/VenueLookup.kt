package xyz.fieldatlas.research

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** A fast, source-only path for simple local place lookups. No model claim or ranking. */
object VenueLookup {
    /** [openNow] counts listed places open by their recorded hours, when the question asked. */
    data class Result(val answer: String, val sources: List<Evidence>, val openNow: Int? = null)

    /**
     * A kind of place a traveller asks for, mapped to the listing categories that hold it, the
     * words a listing of that kind carries ([type], null when the category alone decides, as for
     * sights), the words of a second place search for it ([query], null when the first search
     * already finds the kind, as for restaurants), and mapped types that carry the kind's word
     * without being one ([excludePattern]: a supermarket called "Maxi Market" is no market).
     */
    private class Kind(val wordPattern: String, val categories: Set<String>, typePattern: String?, val query: String?, excludePattern: String? = null) {
        val words = Regex("(?i)\\b(?:$wordPattern)\\b")
        val type = typePattern?.let { Regex("(?i)\\b(?:$it)\\b") }
        val exclude = excludePattern?.let { Regex("(?im)^Type: (?:$it)$") }
    }

    // Listings of a kind without a type word are found by their category line ("Category: See"),
    // which the full-text index holds like any other words. "See" alone, not "category see": the
    // word every listing shares would flood the search with a city's shortest listings of any kind.
    private const val SEE_QUERY = "see"
    private val KINDS = listOf(
        Kind("museums?|galler(?:y|ies)", setOf("See", "Do"), "museums?|galler(?:y|ies)", "museum"),
        Kind("sights?|attractions?|must[- ]sees?|landmarks?", setOf("See", "Do"), null, SEE_QUERY),
        Kind("things? to do|what (?:can|could) (?:we|i|you) do|what is there to do|anything (?:fun )?to do|activities|indoors?|rainy day",
            setOf("See", "Do"), null, SEE_QUERY),
        Kind("markets?|market halls?|halles", setOf("Buy", "Eat"), "markets?|halles?|mercados?|march[eé]s?|markt|bazaars?|souks?", "market halles",
            excludePattern = "supermarket|convenience|mall|department store"),
        // "Where can I get vegan pastries in Lisbon?" asks for a bakery as plainly as naming one does.
        Kind("baker(?:y|ies)|boulangeries?|p[aâ]tisseries?|pastr(?:y|ies)", setOf("Eat", "Buy"), "baker(?:y|ies)|boulangeries?|p[aâ]tisseries?|pastr(?:y|ies)|bread", "bakery"),
        Kind("street[- ]food|food stalls?|hawker (?:centres?|centers?|stalls?)", setOf("Eat"),
            "street[- ]?food|stalls?|stands?|hawkers?|taquer[ií]as?|puestos?|food trucks?|carts?", "street food"),
        Kind("beach(?:es)?", setOf("See", "Do"), "beach(?:es)?|plages?|playas?|praias?|strand|swim\\w*", "beach"),
        Kind("hotels?", setOf("Sleep"), "hotels?", "hotel"),
        Kind("hostels?", setOf("Sleep"), "hostels?", "hostel"),
        Kind("guest ?houses?|b&bs?|bed and breakfasts?|pensions?", setOf("Sleep"), "guest ?houses?|b&b|bed and breakfast|pensions?", "guest house"),
        Kind("caf[eé]s?|coffee shops?", setOf("Eat", "Drink"), "caf[eé]|coffee", null),
        Kind("bars?|pubs?", setOf("Drink"), "bars?|pubs?", null),
        Kind("shops?|stores?|shopping", setOf("Buy"), null, null),
        Kind("restaurants?|places? to eat|dining", setOf("Eat"), null, null),
    )
    // "Milk bars", "sushi bars": places to eat named after what they serve, not places to drink.
    private val foodBar = Regex("(?i)\\b(milk|snack|salad|sushi|tapas|noodle|juice|soup|burger|sandwich|poke|smoothie|oyster|ramen) bars?\\b")
    private fun foodBarKind(food: String): Kind {
        // Poland's milk bars are listed under their own name too.
        val type = if (food.equals("milk", ignoreCase = true)) "milk bars?|bar mleczny|mleczny" else "${Regex.escape(food)} bars?"
        return Kind("${Regex.escape(food)} bars?", setOf("Eat"), type, "${food.lowercase()} bar")
    }

    /**
     * A property the question wants of the places ("indoors", "with the kids") that no listing
     * states as a category: listings whose text [said] it lead the answer, the rest still follow.
     * [query] joins the kind search so such listings are retrieved at all.
     */
    private class Attribute(val name: String, wordPattern: String, saidPattern: String, val query: String) {
        val words = Regex("(?i)\\b(?:$wordPattern)\\b")
        val said = Regex("(?i)\\b(?:$saidPattern)\\b")
    }
    private val ATTRIBUTES = listOf(
        Attribute("indoor", "indoors?|rain\\w*|wet weather|bad weather", "museums?|aquariums?|planetari(?:um|a)|cinemas?|indoors?|galler(?:y|ies)|librar(?:y|ies)",
            "museum aquarium planetarium cinema indoor"),
        Attribute("children", "kids?|children|child|famil(?:y|ies)|toddlers?", "children|kids?|famil(?:y|ies)|child", "children kids family"),
    )

    private val venueWords = Regex("(?i)\\b(?:" + KINDS.joinToString("|") { it.wordPattern } + "|" + foodBar.pattern.removePrefix("(?i)\\b").removeSuffix("\\b") + ")\\b")
    private val businessWords = Regex("(?i)\\b(?:" + KINDS.filter { k -> k.categories.any { it in setOf("Eat", "Drink", "Sleep", "Buy") } }
        .joinToString("|") { it.wordPattern } + ")\\b")
    private val lookupWords = Regex("(?i)\\b(best|good|recommend|suggest|find|list|which|where|any|need|is there|are there|nearest|closest)\\b")

    /**
     * Everyday essentials a traveller asks for, mapped to the listing category and, where the
     * words name one kind of place, its type. [noun] names them in "none found" answers.
     */
    private data class Essential(val words: Regex, val category: String, val type: Regex?, val noun: String)
    private fun typeLine(vararg labels: String) = Regex("(?im)^Type: (${labels.joinToString("|") { Regex.escape(it) }})$")
    private val ESSENTIALS = listOf(
        Essential(Regex("(?i)\\b(pharmac(?:y|ies)|chemists?|drug ?stores?)\\b"), "Health", typeLine("pharmacy"), "pharmacies"),
        Essential(Regex("(?i)\\b(hospitals?|emergency rooms?|emergency departments?|A&E)\\b"), "Health", typeLine("hospital"), "hospitals"),
        // "GP" is a doctor; "GPS" is not.
        Essential(Regex("(?i)\\b(clinics?|doctors?|(?-i:GPs?))\\b"), "Health", typeLine("clinic"), "clinics"),
        Essential(Regex("(?i)\\b(medical|medicine|health care|healthcare)\\b"), "Health", null, "health services"),
        Essential(Regex("(?i)\\b(atms?|cash ?machines?|cashpoints?|withdraw(?:ing)? (?:cash|money)|get (?:cash|money))\\b"), "Money", typeLine("ATM"), "ATMs"),
        Essential(Regex("(?i)\\b(currency exchange|money exchange|exchange (?:money|currency)|bureau de change|change money)\\b"), "Money", typeLine("currency exchange"), "currency exchanges"),
        Essential(Regex("(?i)\\b(toilets?|restrooms?|bathrooms?|wc|loos?|lavatory|lavatories)\\b"), "Toilets", null, "toilets"),
        Essential(Regex("(?i)\\b(drinking water|water fountains?|refill (?:my )?(?:water|bottle)|tap water|potable water)\\b"), "Water", null, "drinking water points"),
        Essential(Regex("(?i)\\b(police(?: stations?)?)\\b"), "Safety", typeLine("police"), "police stations"),
        Essential(Regex("(?i)\\b(embass(?:y|ies)|consulates?)\\b"), "Safety", typeLine("embassy", "consulate"), "embassies or consulates"),
        Essential(Regex("(?i)\\b(train stations?|railway stations?)\\b"), "Transport", typeLine("train station"), "train stations"),
        Essential(Regex("(?i)\\b(bus stations?|bus terminals?|coach stations?)\\b"), "Transport", typeLine("bus station"), "bus stations"),
        Essential(Regex("(?i)\\b(ferry|ferries|ferry terminals?)\\b"), "Transport", typeLine("ferry terminal"), "ferry terminals"),
        Essential(Regex("(?i)\\b(supermarkets?|grocer(?:y|ies)|groceries)\\b"), "Buy", typeLine("supermarket"), "supermarkets"),
    )
    // "Hotels near the train station", "cafes by the hospital": the essential is a landmark, not what is asked for.
    private val landmarkBefore = Regex("(?i)\\b(?:near|by|close to|next to|opposite|beside|behind|around|across from|in front of|walking distance (?:of|from))\\s+(?:the\\s+|a\\s+|an\\s+|my\\s+|our\\s+)?$")
    private fun essential(question: String) = ESSENTIALS.firstOrNull { e ->
        e.words.findAll(question).any { m -> !landmarkBefore.containsMatchIn(question.substring(0, m.range.first)) }
    }
    private val complexWords = Regex("(?i)\\b(compare|contrast|explain|why|how|reason|evidence|versus)\\b")
    private val veganWord = Regex("(?i)\\bvegans?\\b")
    // "No vegan options at all", "nothing for vegans", "not vegetarian-friendly": a mention that says no.
    private val saysNo = Regex("(?i)\\b(?:no|not|nothing|none|without|lacks?|lacking|zero|never|hardly|barely|isn'?t|aren'?t|won'?t|can'?t|non)\\b[^.;!?]{0,25}$")
    private val noAfter = Regex("(?i)^[^.;!?]{0,20}\\b(?:not|aren'?t|isn'?t|elsewhere|look elsewhere|avoid|struggle)\\b")
    /**
     * The listing says it serves this diet. A map listing's own field ("Vegan: vegan options") says so outright;
     * a travel guide's description must say it without a "no" or "not" around the word.
     */
    private fun serves(text: String, word: Regex): Boolean {
        Regex("(?im)^(Vegan|Vegetarian):\\s*(.+)$").findAll(text).firstOrNull { word.containsMatchIn(it.groupValues[1]) }?.let { field ->
            return !Regex("(?i)^\\s*(?:no|none|false)\\b").containsMatchIn(field.groupValues[2])
        }
        return word.findAll(text).any { m -> !saysNo.containsMatchIn(text.substring(0, m.range.first)) && !noAfter.containsMatchIn(text.substring(m.range.last + 1)) }
    }
    private val vegetarianWord = Regex("(?i)\\bvegetarians?\\b")
    private val bestWord = Regex("(?i)\\bbest\\b")
    // Asking for the places worth going to, not the closest ones.
    private val qualityWords = Regex("(?i)\\b(best|top|recommend\\w*|good|great)\\b")
    private val nearestWords = Regex("(?i)\\b(nearest|closest)\\b")
    // "the closest ATM, ideally a proper bank one, not Euronet": a named brand to leave out. Only a
    // capitalised name counts, so "not too expensive" and "not just snacks" exclude nothing.
    private val exclusion = Regex("\\b(?:[Nn]ot|[Nn]o|[Aa]void(?:ing)?|[Ee]xcept|other than|rather than|instead of)\\s+(?:an?\\s+|the\\s+)?(\\p{Lu}[\\p{L}&'-]{2,})")
    private val bankWords = Regex("(?i)\\b(banks?|banca|banco|banque|bancaire|sparkasse|caixa|postamat|post ?office|poste)\\b")
    private val budgetWords = Regex("(?i)\\b(cheap|cheaper|cheapest|budget|inexpensive|affordable|backpackers?)\\b")
    private val budgetSaid = Regex("(?i)\\b(hostels?|budget|cheap|dorm\\w*|backpackers?)\\b")

    /** Brand or place names the question asks to leave out. */
    private fun excluded(question: String): List<String> = exclusion.findAll(question).map { it.groupValues[1].lowercase() }.toList()

    private fun leftOut(listing: Listing, names: List<String>) = names.any { listing.name.lowercase().contains(it) }
    // "Open now" is this minute; "open tonight" or "today" is a later time, which the current minute does not answer.
    private val openNow = Regex("(?i)\\b(open|closed)\\b.{0,30}\\b(now|currently)\\b|\\b(currently|still) (open|closed)\\b")
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val genericWords = setOf(
        "best", "recommend", "recommendation", "recommendations", "suggest", "suggestions",
        "find", "list", "listed", "which", "where", "restaurants", "restaurant", "cafes", "cafe",
        "cafés", "café", "places", "place", "eat", "food", "dining", "nearby", "some",
        "museums", "museum", "sights", "sight", "attractions", "attraction",
        "hotels", "hotel", "hostels", "hostel", "shops", "shop", "stores", "store",
        "bars", "bar", "markets", "market", "bakeries", "bakery", "beaches", "beach", "street", "stalls",
        "things", "thing", "activities", "activity", "indoors", "indoor",
        // About when, not what: "open right now" is answered with the hours on file and a caveat.
        "open", "right", "now", "today", "tonight", "tomorrow", "currently", "closed", "still", "saved", "listings",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "mondays", "tuesdays", "wednesdays",
        "thursdays", "fridays", "saturdays", "sundays", "morning", "afternoon", "evening", "night", "weekdays",
        // How people phrase a request, not what they want of the place.
        "try", "staying", "stay", "week", "weekend", "weekends", "days", "visit", "visiting", "trip", "eats", "eating", "vegans",
        "vegetarians", "name", "few", "couple", "good", "great", "nice", "options", "option", "spots", "spot", "proper",
        "meals", "meal", "snacks", "snack", "serve", "serves", "serving", "easy", "go", "should", "can", "you", "dine",
        "dinner", "lunch", "breakfast", "somewhere", "anywhere", "any", "anything", "there", "here", "city", "town", "area",
        "heard", "got", "put", "together", "order", "full", "sees", "meant", "all", "walk", "walking", "walkable", "them", "last", "next",
        // How someone feels about the trip or the price, which no listing states in these words:
        // "hungry, any good local places that aren't too expensive?", "museums actually worth it
        // if I only have one day?". Required in every listing, they emptied whole cities.
        "hungry", "starving", "really", "actually", "worth", "one", "two", "day", "only", "just", "first", "time",
        "too", "very", "aren", "isn", "don", "doesn", "expensive", "cheap", "cheaper", "inexpensive", "affordable",
        "budget", "pricey", "local", "locals", "authentic", "typical", "traditional", "famous", "popular", "decent",
        "tasty", "delicious", "quick", "must", "see", "go", "worthwhile", "recommended", "top", "favourite", "favorite",
        "looking", "want", "like", "need", "get", "kids", "kid", "children", "child", "family", "friends", "people",
        "tourists", "tourist", "touristy", "trap", "traps", "rain", "rainy", "raining", "pouring", "storm", "stormy", "wet", "weather",
        // Who is travelling and for how long: "travelling with a 4 and a 7 year old … apart from the
        // beach", "which nights are they on". The gap note once listed "travelling", "old", "apart".
        "travelling", "traveling", "travel", "old", "year", "years", "apart", "besides", "except", "other", "else",
        "going", "nights", "late", "early", "hour", "hours", "weeks", "month", "months", "fun", "do", "doing",
    )

    /**
     * A dish names the kitchen that serves it: "souvlaki" is answered by a Greek taverna whether or
     * not its listing spells the dish out, and by a name written in the local script.
     */
    private val DISHES: Map<String, Set<String>> = mapOf(
        "souvlaki" to setOf("greek", "σουβλάκι", "gyros"), "gyros" to setOf("greek", "souvlaki", "γύρος"), "gyro" to setOf("greek", "souvlaki"),
        "moussaka" to setOf("greek"), "pho" to setOf("vietnamese", "phở"), "banh" to setOf("vietnamese"), "pierogi" to setOf("polish"),
        "tapas" to setOf("spanish"), "paella" to setOf("spanish"), "ramen" to setOf("japanese"), "sushi" to setOf("japanese"),
        "pizza" to setOf("italian", "pizzeria"), "pasta" to setOf("italian"), "curry" to setOf("indian"), "kebab" to setOf("turkish"),
        "tacos" to setOf("mexican", "taqueria", "taquería"), "taco" to setOf("mexican", "taqueria", "taquería"), "bibimbap" to setOf("korean"),
        "schnitzel" to setOf("austrian", "german"), "goulash" to setOf("hungarian"), "dumplings" to setOf("chinese"), "pad" to setOf("thai"),
    )

    /** A request for places to go, answerable from place listings alone. */
    fun isPlaceLookup(question: String) = isLookup(question)

    /**
     * A place lookup for businesses (somewhere to eat, drink, sleep or shop, or an everyday essential such as a
     * pharmacy): which of them exist and open changes, so a model's memory of them is no answer. Sights,
     * museums, beaches and things to do are well known for years, and a model may name them.
     */
    fun asksForBusinesses(question: String) = isLookup(question) &&
        (businessWords.containsMatchIn(question) || foodBar.containsMatchIn(question) || essential(question) != null || dietFood.containsMatchIn(question))

    /** A kind of place to go ("museums", "hostels"), which a city's listings answer rather than a general article. */
    fun isVenueKind(name: String) = venueWords.matches(name.trim())

    // "Pharmacy open now in Berlin" asks for places as plainly as "where is a pharmacy" does.
    // "Where do vegans eat in Montreal?", "Is it easy to eat vegan in Kuala Lumpur? Name a few places."
    private val dietFood = Regex("(?i)\\b(vegans?|vegetarians?)\\b.*\\b(eat|eats|eating|food|places|spots|dine|meals?|dinner|lunch|breakfast)\\b|" +
        "\\b(eat|eats|eating|food|places|spots|dine|meals?|dinner|lunch|breakfast)\\b.*\\b(vegans?|vegetarians?)\\b")
    private val askWords = Regex("(?i)\\b(name|try|should i|where do|where can|options|go to)\\b")
    private val barePlaceRequest = Regex("(?i)^\\s*(?:hotels?|hostels?|restaurants?|caf[eé]s?|museums?|markets?|bakeries)\\s+(?:in|near|around)\\b")
    // "What can we do indoors?", "things to do with the kids": the activity itself is the ask. "What
    // should we do?" is left out: it opens emergencies ("we are lost in a forest, what should we do?").
    private val activityAsk = Regex("(?i)\\b(things? to do|what (?:can|could) (?:we|i|you) do|what is there to do|anything (?:fun )?to do|activities|rainy day)\\b")
    // "Should I bring euros or take out dirhams from an ATM?" weighs two ways to pay; it asks for no ATM.
    private val decision = Regex("(?i)\\bshould i\\b.*\\bor\\b")
    private val seeks = Regex("(?i)\\b(find|where|nearest|closest|near)\\b")
    private val shopHours = Regex("(?i)\\b(?:shops?|stores?|supermarkets?|grocer(?:y|ies))\\b[^.?!]{0,60}\\b(?:open|closed|opening)\\b|" +
        "\\b(?:open|closed|opening)\\b[^.?!]{0,60}\\b(?:shops?|stores?|supermarkets?|grocer(?:y|ies))\\b")
    private val asksSpecificPlaces = Regex("(?i)\\b(?:find|where|nearest|closest|near|which|list|name|any|recommend\\w*)\\b")

    /** General shop-opening rules belong to the destination guide, not arbitrary food listings. */
    private fun generalShopHours(question: String) = shopHours.containsMatchIn(question) && !asksSpecificPlaces.containsMatchIn(question)

    private fun isLookup(raw: String): Boolean {
        val question = withoutCareMentions(raw)
        return (venueWords.containsMatchIn(question) || essential(question) != null || dietFood.containsMatchIn(question)) &&
            (lookupWords.containsMatchIn(question) || askWords.containsMatchIn(question) || openNow.containsMatchIn(question) || barePlaceRequest.containsMatchIn(question) ||
                dayAsk(question) != null ||
                // An activity question is a lookup only for a named place: "what can we do indoors" in
                // Copenhagen, not in a cabin with a leaking gas heater.
                (activityAsk.containsMatchIn(question) && GuideScope.namesPlace(question))) &&
            !complexWords.containsMatchIn(question) && !generalShopHours(question) &&
            // An everyday essential named in a decision between alternatives is a place to find only when asked to find it.
            !(!venueWords.containsMatchIn(question) && !dietFood.containsMatchIn(question) && decision.containsMatchIn(question) && !seeks.containsMatchIn(question))
    }

    private fun isOsmPlace(item: Evidence) = item.documentId.startsWith(OSM_PREFIX)
    private fun isGuidePlace(item: Evidence) = item.documentId.startsWith(WV_PREFIX)

    /**
     * A follow-up keyword query for fully vegan places in the destination the first pass found.
     * A big city can have hundreds of OpenStreetMap places tagged with vegan options; a plain
     * keyword search returns an arbitrary slice of them, which can miss every fully vegan one.
     * "fully" only occurs in the fully-vegan label, so this conjunction is precise.
     */
    fun fullyVeganQuery(question: String, evidence: List<Evidence>): String? {
        if (!veganWord.containsMatchIn(question)) return null
        // Plain lookups, and trip questions ("suggest a vegan food stop and a museum") whose own
        // keywords can miss every place listing ("offline" found a café called Offline instead).
        if (!isLookup(question) && !foodWords.containsMatchIn(question) && !restaurantWords.containsMatchIn(question)) return null
        val destination = destinationIn(question, evidence) ?: return null
        val noun = when {
            Regex("(?i)\\bcaf[eé]s?\\b").containsMatchIn(question) -> " cafe"
            Regex("(?i)\\b(restaurants?|dining)\\b").containsMatchIn(question) -> " restaurant"
            else -> ""
        }
        return "fully vegan $destination$noun"
    }

    /**
     * The question names this place: as written, capitalised ("Nice", "York"), or after a word that
     * places it ("in berlin", typed in lowercase). "Any nice restaurants" is not Nice, and "New York"
     * is not York.
     */
    internal fun names(question: String, place: String): Boolean {
        val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(place)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val placing = Regex("(?i)\\b(?:in|at|near|around|to|from|of|visiting|for)\\s+(?:the\\s+)?$")
        return pattern.findAll(question).any { m ->
            val before = question.substring(0, m.range.first)
            val written = m.value.firstOrNull()?.isUpperCase() == true
            // A capitalised word right before it makes a longer name: "New York", "Little Italy".
            val longer = Regex("\\b\\p{Lu}[\\p{L}'.-]*\\s+$").containsMatchIn(before) && !placing.containsMatchIn(before)
            (written || placing.containsMatchIn(before)) && !longer
        }
    }

    /** The city a question names, as spelled in the place listings already retrieved for it; the longest name wins. */
    private fun destinationIn(question: String, evidence: List<Evidence>): String? = evidence.asSequence()
        .filter { isOsmPlace(it) || isGuidePlace(it) }
        .mapNotNull { field(it.text, "Destination")?.substringBefore('/') }
        .filter { names(question, it) }
        .maxByOrNull { it.length }

    private val museumWord = Regex("(?i)\\b(museums?|galler(?:y|ies))\\b")

    /**
     * A second place search for the kind of place a question asks for in a destination: its
     * [query] runs within the place packs like the first search; its [scope] is passed to the
     * search as the question, so the relevance gate judges the listings against the place alone.
     * Listings of a kind rarely say the question's words: a cathedral is a "must-see" to the asker,
     * "Category: See" to the guide.
     */
    data class KindQuery(val query: String, val scope: String)

    /**
     * A question asking for a kind of place in a city whose listings were retrieved ("suggest a
     * vegan food stop and a museum visit in Berlin", "one day in Seville, which sights", "food
     * markets in lyon") gets one more search for that city's places of the kind: the question's
     * other words otherwise fill every slot, and the answer reaches for whatever the city overview
     * mentions (a zoo) instead. [destinations] are the places the packs confirmed the question
     * names, for a city the first search found nothing in.
     */
    fun kindQuery(question: String, evidence: List<Evidence>, destinations: List<String> = emptyList()): KindQuery? {
        val request = request(question)
        if (request.essential != null) return null
        val words = (request.kinds.mapNotNull { it.query } + request.attributes.map { it.query }).distinct()
        if (words.isEmpty()) return null
        val destination = destinationIn(question, evidence) ?: destinations.firstOrNull() ?: return null
        return KindQuery("$destination " + words.joinToString(" "), destination)
    }

    /**
     * The listings among [candidates] of the kind the question asks for in its destination, in the
     * guide's own order: a Wikivoyage page lists its sights as the guide recommends them (document
     * id wv-place-<page>-<nnnn>, lower first), and listings that state an asked property
     * ("indoors", "with the kids") come before those that do not.
     */
    fun kindListings(question: String, candidates: List<Evidence>, evidence: List<Evidence>, destinations: List<String> = emptyList()): List<Evidence> {
        val request = request(question)
        val destination = destinationIn(question, evidence) ?: destinationIn(question, candidates) ?: destinations.firstOrNull() ?: return emptyList()
        return guideOrder(candidates.filter { item ->
            (isOsmPlace(item) || isGuidePlace(item)) &&
                field(item.text, "Category") in request.categories &&
                field(item.text, "Destination")?.substringBefore('/').equals(destination, ignoreCase = true) &&
                (request.requestedType == null || request.requestedType.containsMatchIn(item.text))
        }, request)
    }

    /** Kept for the museum-only callers: the museum search is [kindQuery] for a museum question. */
    fun museumQuery(question: String, evidence: List<Evidence>): String? {
        if (!museumWord.containsMatchIn(question)) return null
        return destinationIn(question, evidence)?.let { "$it museum" }
    }

    /** Sight listings in that city that are museums or galleries by name or type. */
    fun museumListings(question: String, candidates: List<Evidence>, evidence: List<Evidence>): List<Evidence> {
        val destination = destinationIn(question, evidence) ?: return emptyList()
        return candidates.filter { item ->
            (isOsmPlace(item) || isGuidePlace(item)) &&
                field(item.text, "Category") == "See" &&
                field(item.text, "Destination")?.substringBefore('/').equals(destination, ignoreCase = true) &&
                (museumWord.containsMatchIn(field(item.text, "Place").orEmpty()) || museumWord.containsMatchIn(field(item.text, "Type").orEmpty()))
        }
    }

    private val placeOrdinal = Regex("^wv-place-\\d+-(\\d+)$")

    /** A guide listing's place on its page; map listings and legacy rows have none and keep their order. */
    private fun ordinal(item: Evidence): Int = placeOrdinal.find(item.documentId)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun attributeRank(text: String, request: Request): Int = request.attributes.count { it.said.containsMatchIn(text) }

    private fun guideOrder(items: List<Evidence>, request: Request): List<Evidence> =
        items.sortedWith(compareBy({ -attributeRank(it.text, request) }, { ordinal(it) }))

    private data class Request(
        val categories: Set<String>,
        val requestedType: Regex?,
        val diet: String?,
        val essential: Essential? = null,
        val kinds: List<Kind> = emptyList(),
        val attributes: List<Attribute> = emptyList(),
    )

    /**
     * "The best vegan restaurants in the city I'm in": places to go for, so the answer ranks a
     * whole city's places rather than the closest few. "Nearest" asks for distance.
     */
    fun asksBest(question: String): Boolean = qualityWords.containsMatchIn(question) &&
        !nearestWords.containsMatchIn(question) && essential(question) == null

    /** The listing categories a near-me question asks for, so the lookup can filter early. */
    fun nearbyCategories(question: String): Set<String> = request(withoutOwnPosition(question)).categories

    /**
     * What the question asks for: every kind of place it names ("hostels or cheap hotels" wants
     * both), the categories that hold them, and a type that any of their words satisfies. A kind
     * without a type word (sights) leaves the category to decide for all of them.
     */
    private fun request(question: String): Request {
        essential(question)?.let { need -> return Request(setOf(need.category), need.type, null, need) }
        val foodBars = foodBar.findAll(question).map { foodBarKind(it.groupValues[1]) }.toList()
        // "milk bars" is not also "bars".
        val rest = foodBar.replace(question, " ")
        val kinds = foodBars + KINDS.filter { it.words.containsMatchIn(rest) }
        val categories = kinds.flatMap { it.categories }.toSet().ifEmpty { setOf("Eat") }
        val requestedType = if (kinds.isNotEmpty() && kinds.all { it.type != null }) Regex(kinds.joinToString("|") { it.type!!.pattern }) else null
        val diet = when {
            veganWord.containsMatchIn(question) -> "vegan"
            vegetarianWord.containsMatchIn(question) -> "vegetarian"
            else -> null
        }
        val attributes = if (kinds.isEmpty()) emptyList() else ATTRIBUTES.filter { it.words.containsMatchIn(question) }
        return Request(categories, requestedType, diet, kinds = kinds, attributes = attributes)
    }

    /** Whether a listing's text says [term]: the word itself, a longer form of it ("swimming" for "swim"), or the kitchen a dish names. */
    private fun says(text: String, term: String): Boolean {
        val words = word.findAll(text.lowercase()).map { it.value }.toList()
        if (words.any { it == term || (term.length >= 4 && it.length <= term.length + 4 && it.startsWith(term)) }) return true
        return DISHES[term]?.any { alias -> alias in words } == true
    }

    /**
     * One listing if it satisfies the request's category, type, diet and qualifier words.
     * [destinationRequired] keeps city answers to places in the named city; nearby answers
     * locate by coordinates instead, so their location words are ignored as qualifiers.
     * [relaxed] drops the qualifier words: the kind, type, diet and place still apply.
     */
    private fun listing(item: Evidence, question: String, request: Request, destinationRequired: Boolean, relaxed: Boolean = false): Listing? {
        val legacyEat = item.documentId.startsWith("wv-eat-")
        val newPlace = (isGuidePlace(item) || isOsmPlace(item)) &&
            field(item.text, "Category") in request.categories
        if (!(legacyEat && "Eat" in request.categories) && !newPlace) return null
        val destination = field(item.text, "Destination")?.substringBefore('/')
        if (destinationRequired) {
            // "in Berlin", or the city named anywhere ("the saved Berlin vegan restaurants"), but not an
            // ordinary word that is also a city ("any nice restaurants in Berlin" is not Nice).
            if (destination == null || !names(question, destination)) return null
        }
        val name = field(item.text, if (legacyEat) "Place to eat" else "Place")?.takeIf(String::isNotBlank)
            ?: return null
        if (request.requestedType != null && !request.requestedType.containsMatchIn(item.text)) return null
        if (request.kinds.any { kind -> kind.exclude?.containsMatchIn(item.text) == true }) return null
        val qualifiers = if (relaxed) emptyList() else freeWords(question, request, destinationRequired, destination)
        if (!qualifiers.all { qualifier -> says(item.text, qualifier) }) return null
        if (request.diet == "vegan" && !serves(item.text, veganWord)) return null
        if (request.diet == "vegetarian" && !serves(item.text, veganWord) && !serves(item.text, vegetarianWord)) return null
        return Listing(
            evidence = item,
            name = name,
            destination = destination.orEmpty(),
            description = field(item.text, "Description"),
            location = field(item.text, "Address") ?: field(item.text, "Directions"),
            checked = field(item.text, "Listing last checked"),
            pageRevision = field(item.text, "Source page revision"),
            vegan = field(item.text, "Vegan"),
            type = field(item.text, "Type"),
            cuisine = field(item.text, "Cuisine"),
            hours = field(item.text, "Hours in source"),
            snapshot = field(item.text, "Map data snapshot")?.substringBefore(' '),
            phone = field(item.text, "Phone"),
            fee = field(item.text, "Fee"),
            emergency = field(item.text, "Emergency department"),
            wheelchair = field(item.text, "Wheelchair access"),
            latitude = field(item.text, "Latitude")?.toDoubleOrNull(),
            longitude = field(item.text, "Longitude")?.toDoubleOrNull(),
        )
    }

    /**
     * The question's words that must appear in a listing: not how the request is phrased, the
     * kind of place (the type regex judges that), the diet, or the place's own name.
     */
    private fun freeWords(question: String, request: Request, destinationRequired: Boolean, destination: String?): List<String> {
        if (request.essential != null) return emptyList()
        val ignored = if (destinationRequired) word.findAll(destination.orEmpty().lowercase()).map { it.value }.toSet() else nearbyWords
        return FtsQuery.from(question)?.terms.orEmpty().filterNot { term ->
            // "11pm", "4": a time or a number is not a word a listing is found by.
            term in genericWords || term in ignored || term == request.diet || term.any(Char::isDigit)
        }
    }

    /**
     * A map pack files a place under its nearest city: cafés in Malmö, across the water in Sweden,
     * came back as Copenhagen. Within one city's listings, a place far from where most of them are
     * (the median) is in another town and is left out.
     */
    private fun withinCity(listings: List<Listing>): List<Listing> = listings.groupBy { it.destination.lowercase() }.values.flatMap { city ->
        val located = city.filter { it.latitude != null && it.longitude != null }
        if (located.size < 3) return@flatMap city
        val lat = located.map { it.latitude!! }.sorted()[located.size / 2]
        val lon = located.map { it.longitude!! }.sorted()[located.size / 2]
        city.filter { it.latitude == null || it.longitude == null || kilometres(lat, lon, it.latitude, it.longitude) <= CITY_RADIUS_KM }
    }

    private fun kilometres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    // Words that say what kind of place a name is, in the languages of the saved map data; what is
    // left names the business: "Minh Chay Vegan Restaurant" and "Nhà Hàng Minh Chay" are one.
    private val kindWords = Regex("(?i)\\b(?:restaurants?|restaurante|ristorante|restoran|nhà hàng|quán|cafe|café|caffè|vegan|vegans|" +
        "vegetarian|vegetariano|vegetariana|vegetarisch|végétarien|chay|chay quán|the|and)\\b")

    /** A place's name without its kind, to treat a business's branches and duplicate map entries as one. */
    internal fun brand(name: String): String =
        kindWords.replace(name.lowercase(), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().ifEmpty { name.lowercase() }

    // Several branches or duplicate entries of one restaurant show once, at the first (nearest or
    // best-ranked) of them, so the answer offers different places. A pharmacy or ATM chain's
    // branches are real alternatives when the nearest is closed, so essentials keep them all.
    private fun <T> List<T>.onePerBusiness(request: Request, name: (T) -> String): List<T> =
        if (request.essential != null) this else distinctBy { brand(name(it)).takeIf { b -> b.length >= 4 } ?: name(it).lowercase() }

    /**
     * The city's listings that fit the question. Words of the question that no listing carries
     * ("polish", "picnic") must not empty the whole city, as they did not empty the area around the
     * phone: the places that fit the kind, type, diet and city are kept, and the answer says which
     * words none of them mention.
     */
    private fun cityMatches(question: String, request: Request, evidence: List<Evidence>): Pair<List<Listing>, List<String>> {
        fun matches(relaxed: Boolean) = withinCity(evidence.mapNotNull { item ->
            listing(item, question, request, destinationRequired = true, relaxed = relaxed)
        }).distinctBy { it.destination.lowercase() to it.name.lowercase() }
        val strict = matches(relaxed = false)
        if (strict.isNotEmpty() || request.essential != null) return strict to emptyList()
        val loose = matches(relaxed = true)
        if (loose.isEmpty()) return loose to emptyList()
        val words = freeWords(question, request, destinationRequired = true, destination = loose.first().destination)
        if (words.isEmpty()) return loose to emptyList()
        // Never relax a named cuisine/dish into unrelated restaurants.
        if (words.any { it in DISHES && loose.none { listing -> says(listing.evidence.text, it) } }) return emptyList<Listing>() to words
        return loose to words.filter { term -> loose.none { says(it.evidence.text, term) } }
    }

    /** [now] is this phone's local time, used only when the question asks what is open now. */
    fun answer(question: String, evidence: List<Evidence>, now: LocalDateTime = LocalDateTime.now()): Result? {
        if (!isLookup(question)) return null
        val request = request(question)
        val diet = request.diet
        val day = dayAsk(question)
        val (matched, unmatched) = cityMatches(question, request, evidence)
        val listings = matched
            // Stable sort: retrieval order is kept within each group.
            .sortedBy { if (diet == "vegan" && it.vegan == "fully vegan") 0 else 1 }
        if (listings.isEmpty()) return null
        // OpenStreetMap places carry explicit dietary tags and their own wording; when both packs
        // match, prefer them over free-text Wikivoyage mentions instead of mixing two sources'
        // claims under one description.
        listings.filter { isOsmPlace(it.evidence) }.takeIf { it.isNotEmpty() }?.let { osm ->
            // OpenStreetMap's Eat group includes ice cream shops and cafes; a restaurant
            // question keeps meal places whenever there are any.
            val meals = osm.filter { it.type in MEAL_TYPES }
            val chosen = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else osm
            val clock = now.takeIf { openNow.containsMatchIn(question) }
            val picks = guidePicks(evidence, chosen.first().destination)
            val ordered = clock?.let { time -> chosen.sortedBy { openRank(it.hours, time) } }
                ?: day?.let { ask -> chosen.sortedBy { dayRank(it.hours, ask) } }
                ?: if (asksBest(question)) {
                    val chains = chainNames(evidence)
                    // A named city has no position to measure from: every place sits in the same band.
                    chosen.sortedWith { a, b -> compareRanks(bestRanks(a, 0.0, request, picks, chains), bestRanks(b, 0.0, request, picks, chains)) }
                } else chosen
            val shown = ordered.onePerBusiness(request) { it.name }.take(OSM_LISTINGS)
            return osmAnswer(question, diet, shown, request.essential?.noun, clock, guided = shown.any { brand(it.name) in picks },
                unmatched = unmatched, day = day)
        }
        // The guide's own order (a page lists its sights as it recommends them), with the places
        // that state an asked property first; a day question puts the places open that day first.
        val guideOrdered = listings.sortedWith(compareBy(
            { if (diet == "vegan" && it.vegan == "fully vegan") 0 else 1 },
            { day?.let { ask -> dayRank(it.hours, ask) } ?: 0 },
            { -attributeRank(it.evidence.text, request) },
            { ordinal(it.evidence) },
        ))
        val shown = guideOrdered.take(WV_LISTINGS)
        val ordered = shown.count { ordinal(it.evidence) != Int.MAX_VALUE } >= 2

        val city = shown.first().destination
        val answer = buildString {
            if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline. ")
            }
            day?.let { appendDaySummary(shown, it) }
            if (ordered) {
                append("These Wikivoyage listings for ").append(city)
                appendDiet(diet)
                append(", in the guide's order")
                request.attributes.filter { attribute -> shown.any { attribute.said.containsMatchIn(it.evidence.text) } }
                    .takeIf { it.isNotEmpty() }?.let { attributes ->
                        append(", those described as ").append(attributes.joinToString(" and ") { it.name }).append(" first")
                    }
            } else {
                append("These unranked Wikivoyage listings for ").append(city)
                appendDiet(diet)
            }
            append(":\n\n")
            appendUnmatched(unmatched, "in $city")
            shown.forEachIndexed { index, listing ->
                append("- **")
                append(escapeMarkdown(listing.name))
                append("**")
                listing.description?.takeIf(String::isNotBlank)?.let { description ->
                    append(" — ")
                    val excerpt = descriptionExcerpt(description)
                    append(escapeMarkdown(excerpt))
                    if (excerpt.lastOrNull() !in listOf('.', '!', '?', '…')) append('.')
                }
                listing.location?.takeIf(String::isNotBlank)?.let { location ->
                    append(" Address: ")
                    append(escapeMarkdown(location.take(100)))
                    append('.')
                }
                day?.let { appendDayStatus(listing, it) }
                append(' ')
                append(listing.checked?.let { "Listing last checked: $it." }
                    ?: "Listing check date not recorded.")
                if (listing.checked == null) {
                    listing.pageRevision?.let { append(" Source page revised: $it.") }
                }
                append(" [S${index + 1}]\n")
            }
            append("\nListings can change; confirm details before visiting.")
        }
        return Result(answer, shown.map(Listing::evidence))
    }

    private fun StringBuilder.appendDiet(diet: String?) {
        if (diet == null) return
        append(" mention ").append(diet)
        append(" food. Some may offer dietary options rather than being entirely ").append(diet)
    }

    // Ways people point at where they are standing: by distance ("nearest", "close to me"), by
    // place ("around here", "where I am") or by their phone's position ("my location").
    /**
     * The phone's position in walking words: "a pharmacy I can walk to from here", "any museum within
     * walking distance of me". Walking distance of somewhere named ("of the Louvre") is that place.
     */
    private const val WALKABLE = "(?:walk(?:ing)?|get) (?:to )?from here|" +
        "(?:within )?walking distance(?! (?:of|from) (?!me\\b|here\\b|us\\b))(?: (?:of|from) (?:me|here|us))?"
    // A bare "from here" is the phone's position only when no other place is named:
    // "a day trip from here" to Kyoto, or "the train from here to Kyoto", is about Kyoto.
    private const val FROM_HERE = "from here(?! to\\b(?! (?:visit|see|eat|walk|go|get|do|try|explore|stay|drink|shop)\\b))"

    private val nearMePhrase = Regex(
        "(?i)\\b(nearest|closest|nearby|(?:near|close to|around) (?:me|here)|where i am|" +
            "(?:the )?city i(?:'m| am) (?:currently )?in|my (?:current )?(?:location|city)|$WALKABLE|$FROM_HERE)\\b",
    )
    private val foodWords = Regex("(?i)\\b(food|eat|eating|meals?|dinner|lunch|breakfast)\\b")
    private val nearbyWords = setOf(
        "near", "nearby", "here", "around", "close", "closest", "nearest", "where", "am", "location",
        "current", "currently", "city", "food", "eat", "eating", "meal", "meals", "dinner", "lunch",
        "breakfast", "can", "get", "good",
    )

    private val selfPhrase = Regex(
        "(?i)\\b((?:near|close to|around) (?:me|here)|where i am|" +
            "(?:the )?city i(?:'m| am) (?:currently )?in|my (?:current )?(?:location|city)|$WALKABLE)\\b",
    )
    private val namedPlace = Regex("(?i)\\b(?:in|at|around|near)\\s+(?!me\\b|here\\b|my\\b|the city i)\\p{L}")
    // A time after "at" or "in" is not a place: "open at night", "in the evening", "in under ten minutes".
    private val timeNotPlace = Regex("(?i)\\b(?:in|at|around)\\s+(?:night|midnight|noon|dawn|dusk|sunset|sunrise|lunch\\w*|breakfast\\w*|" +
        "dinner\\w*|brunch|supper|happy hour|christmas|easter|new year\\w*|summer|winter|spring|autumn|fall|holidays?|tonight|today|" +
        "tomorrow|case|an? emergency|walking (?:range|distance)|the (?:(?:early|late) )?(?:morning|afternoon|evening|night|moment|weekend|rain|snow|" +
        "heat|cold|summer|winter|holidays?)|under|less than|about|around|over|a few|an? (?:hour|minute|few|bit|while|moment)|one|two|" +
        "three|four|five|ten|twenty|thirty|this (?:time|hour)|time|weekends?)\\b(?![\\p{L}-])")
    private val fromHere = Regex("(?i)\\b$FROM_HERE\\b")
    // A capitalised name after "to", "near" or "towards" is a place: "nearest pharmacy to the Eiffel
    // Tower", "restaurants within walking distance near the Colosseum", "from here towards Shibuya".
    private val namedLandmark = Regex("\\b(?:to|towards|near|nearby|close to|by)\\s+(?:the\\s+)?\\p{Lu}\\p{L}+|" +
        // Two capitalised words after "in" or "at" are a name: "at Sunset Boulevard", not "at sunset".
        "\\b(?:in|at)\\s+\\p{Lu}\\p{L}+\\s+\\p{Lu}\\p{L}+")

    // Where the person is standing now: "I'm at the Hilton", "we're staying at the campsite", "I'm in the
    // lobby of the Novotel". That place is the phone's position, not somewhere to search; "in Lyon" names a
    // city and stays a city lookup. A plan ("we're at a conference in Berlin next week") is not a position.
    private val ownPosition = Regex("(?i)\\b(?:i'?m|i am|we'?re|we are)\\s+(?:(?:currently|now|right now|still|staying|sitting|waiting|stuck)\\s+)*" +
        "(?:at|inside|outside|in the (?:lobby|car park|parking lot|foyer|bar|restaurant) of)\\s+[^,.;?!\\n]*|\\bstaying at\\s+[^,.;?!\\n]*")
    private val notNow = Regex("(?i)\\b(?:next|tomorrow|later|soon|tonight'?s|will|we'?ll|i'?ll|planning|plan to|going to be|arriv\\w*)\\b")

    /**
     * "We're 90 minutes from the nearest hospital" says how far help is, and "take her to a doctor" or "see a GP" asks
     * whether to, not where: neither is a request to find a place. The phrase is taken out before the question is
     * read as a lookup, so "where is the nearest pharmacy" still is one.
     */
    internal fun withoutCareMentions(question: String): String =
        careVisit.replace(distanceStatement.replace(question, " "), " ")
    private val distanceStatement = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:minutes?|mins?|hours?|hrs?|km|kilomet(?:re|er)s?|miles?|mi)\\s+" +
        "(?:(?:drive|walk|ride|away)\\s+)?(?:from|to|out of)\\s+(?:the\\s+)?(?:nearest|closest)\\s+\\w+")
    private val careVisit = Regex("(?i)\\b(?:(?:take|bring|get|rush|drive|carry)\\s+(?:him|her|them|me|us|myself|(?:my|the|our)\\s+\\w+)\\s+(?:in\\s+)?to\\s+(?:a|an|the)\\s+" +
        "(?:doctor|gp|hospital|a&e|er|emergency room|clinic|vet|dentist|pharmacist|nurse)|" +
        "(?:see|call|phone|ring|visit|consult|ask)\\s+(?:a|an|the|my|your|our)\\s+(?:doctor|gp|dentist|vet|pharmacist|nurse)|" +
        "go\\s+to\\s+(?:a|an|the|my)\\s+(?:doctor|gp|hospital|a&e|er|emergency room|clinic|vet|dentist))\\b")

    /** The question without the person's own position, which says where they are, not what they want. */
    internal fun withoutOwnPosition(question: String): String =
        if (notNow.containsMatchIn(question)) question else ownPosition.replace(question, " ")

    /** A place question answered from the phone's own location rather than a named city.
     * "Nearest supermarket in Jakarta" names its city, so it is a city lookup, not a GPS one. */
    fun isNearMe(raw: String): Boolean {
        val question = withoutCareMentions(raw)
        val rest = withoutOwnPosition(question)
        val placed = rest != question
        return (nearMePhrase.containsMatchIn(rest) || placed) && !namedLandmark.containsMatchIn(rest) &&
            // The rest still names a city or place to search ("…where should I eat in Barcelona?"): that place is asked.
            (selfPhrase.containsMatchIn(rest) || !namedPlace.containsMatchIn(rest.replace(timeNotPlace, " "))) &&
            (venueWords.containsMatchIn(rest) || foodWords.containsMatchIn(rest) || essential(rest) != null) &&
            !complexWords.containsMatchIn(rest)
    }

    const val LOCATION_UNAVAILABLE = "I couldn't get this phone's location. Turn on Location (GPS works " +
        "offline) and allow Field Atlas to use it, or name the city in your question."
    const val LOCATION_NO_FIX = "Location is on, but this phone has no fix yet: GPS needs open sky and a minute to warm up " +
        "(it works offline). Ask again shortly, step outside if you can, or name the city in your question."
    const val LOCATION_NO_PERMISSION = "Field Atlas isn't allowed to use this phone's location. Allow Location for " +
        "Field Atlas in the phone's settings, or name the city in your question."

    /** What to do about a missing position, by why it was missing. */
    fun locationUnavailable(problem: LocationProblem?): String = when (problem) {
        LocationProblem.NO_FIX -> LOCATION_NO_FIX
        LocationProblem.NO_PERMISSION -> LOCATION_NO_PERMISSION
        else -> LOCATION_UNAVAILABLE
    }

    // "Where am I?", "What city am I in?", "What's my location?": the phone's own position, named.
    private val whereAmI = Regex(
        "(?i)^\\s*(?:where am i|where are we|" +
            "(?:what|which) (?:city|town|village|country|place|area|neighbou?rhood|district|street|state|province|region|part of town) " +
            "(?:am i|are we) in|what(?:'s|’s| is) (?:my|our) (?:current |exact )?(?:location|position|address)|" +
            "what(?:'s|’s| is) this (?:place|town|city|village|area|neighbou?rhood))(?: now| right now| exactly)?\\s*[?.!]*\\s*$",
    )

    private val ownCoordinates = Regex("(?i)^\\s*(?:what(?:'s|’s| is| are)|show(?: me)?)\\s+(?:my|our)\\s+" +
        "(?:(?:exact|current|gps)\\s+)*(?:coordinates|latitude and longitude)(?:\\s+(?:right now|now|currently))?\\s*[?.!]*\\s*$")
    fun isWhereAmI(question: String): Boolean = whereAmI.containsMatchIn(question) || ownCoordinates.containsMatchIn(question)

    private val digit = Regex("\\p{N}")

    /**
     * The town in a mapped address, which the map data writes as "street number, postcode town":
     * "Verlengde Voorstraat 56, 1949CN Wijk aan Zee" is in Wijk aan Zee.
     */
    internal fun townOf(address: String): String? {
        if (',' !in address) return null
        val town = address.substringAfterLast(',').trim().split(Regex("\\s+"))
            .filterNot { digit.containsMatchIn(it) }.joinToString(" ").trim()
        return town.takeIf { it.length >= 2 && it.any(Char::isLetter) && it.length <= 40 }
    }

    private fun coordinates(point: GeoPoint): String = String.format(Locale.US, "%.5f° %s, %.5f° %s",
        kotlin.math.abs(point.lat), if (point.lat >= 0) "N" else "S", kotlin.math.abs(point.lon), if (point.lon >= 0) "E" else "W")

    /**
     * Where this phone is, worked out on the phone: the town the nearest mapped addresses name,
     * the travel guide's destination for the area, the coordinates, and the closest mapped places.
     */
    fun whereAnswer(point: GeoPoint, places: List<NearbyPlace>): Result {
        val nearest = places.sortedBy { it.distanceKm }
        val town = nearest.take(WHERE_ADDRESSES).mapNotNull { place -> field(place.evidence.text, "Address")?.let(::townOf) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val city = cityOf(nearest)
        val landmarks = nearest.filter { place -> field(place.evidence.text, "Place")?.isNotBlank() == true }
            .distinctBy { field(it.evidence.text, "Place")!!.lowercase() }.take(WHERE_LANDMARKS)
        val answer = buildString {
            appendFixAge(point.ageMinutes)
            when {
                town != null -> {
                    append("You're in **").append(escapeMarkdown(town)).append("**, going by the addresses of the places mapped nearest you.")
                    if (city != null && !city.equals(town, ignoreCase = true)) {
                        append(" The travel guides cover this area under **").append(escapeMarkdown(city)).append("**.")
                    }
                }
                city != null -> append("You're in or near **").append(escapeMarkdown(city)).append("**, where the map files the places nearest you.")
                else -> append("No saved map collection has places near you, so I can't name the town.")
            }
            append("\n\nYour position: ").append(coordinates(point)).append(when (point.fromGps) {
                true -> ", from this phone's GPS, which works without a connection."
                false -> ", from the phone's location service (GPS had no fix, so Android used Wi-Fi or mobile networks)."
                null -> ", from this phone's location."
            })
            if (landmarks.isNotEmpty()) {
                append("\n\nNearest mapped places:\n\n")
                landmarks.forEachIndexed { index, place ->
                    val text = place.evidence.text
                    append("- **").append(escapeMarkdown(field(text, "Place")!!)).append("**")
                    field(text, "Type")?.takeIf(String::isNotBlank)?.let { append(" (").append(escapeMarkdown(it)).append(')') }
                    append(", ").append(GeoDistance.label(place.distanceKm))
                    place.bearingDegrees?.let { append(" to the ").append(GeoDistance.compass(it)) } ?: append(" away")
                    append(". [S").append(index + 1).append("]\n")
                }
                append("\nMap data © OpenStreetMap contributors (ODbL).")
            }
        }
        return Result(answer, landmarks.map { it.evidence })
    }

    /** A position from an earlier fix may be from somewhere else: the answer says how old it is. */
    private fun StringBuilder.appendFixAge(minutes: Long) {
        if (minutes < OLD_FIX_MINUTES) return
        append("Using this phone's last known location, from ").append(GeoDistance.ageLabel(minutes))
        append(" ago; no newer fix was available. ")
    }

    private const val OLD_FIX_MINUTES = 10L
    private const val WHERE_ADDRESSES = 20
    private const val WHERE_LANDMARKS = 3

    /**
     * Nearest matching places from the phone's coordinates. Distance bands come first so a
     * fully vegan place 9 km away never outranks one around the corner; within a band, fully
     * vegan places lead when vegan food was asked for.
     */
    fun nearbyAnswer(asked: String, places: List<NearbyPlace>, radiusKm: Double, now: LocalDateTime = LocalDateTime.now(),
                     fixAgeMinutes: Long = 0): Result {
        // "I'm at the campsite, nearest shop": the campsite is where the phone is, not a second thing to find.
        val question = withoutOwnPosition(asked)
        val request = request(question)
        val clock = now.takeIf { openNow.containsMatchIn(question) }
        val day = dayAsk(question)
        if (clock == null && day == null && asksBest(question)) bestNearby(question, request, places, fixAgeMinutes)?.let { return it }
        val bearings = places.mapNotNull { place -> place.bearingDegrees?.let { place.evidence.chunkId to it } }.toMap()
        val (matched, unmatchedWords) = nearbyMatches(question, request, places)
        val meals = matched.filter { (listing, _) -> listing.type in MEAL_TYPES || isGuidePlace(listing.evidence) }
        val pool = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else matched
        val wantsBank = request.essential?.category == "Money" && bankWords.containsMatchIn(question)
        // "Hostels or cheap hotels I can walk to": a hostel a kilometre off answers before a grand
        // hotel on the square; within the circle asked for, the budget places lead.
        val wantsBudget = request.essential == null && budgetWords.containsMatchIn(question)
        val chosen = pool.sortedWith(compareBy(
            // "Pharmacy open now near me": a place open by its recorded hours comes before a
            // closer one recorded as closed.
            { (listing, _) -> clock?.let { openRank(listing.hours, it) } ?: day?.let { dayRank(listing.hours, it) } ?: 0 },
            { (listing, _) -> if (wantsBudget && !budgetSaid.containsMatchIn(listing.evidence.text)) 1 else 0 },
            { (_, distance) -> DISTANCE_BANDS_KM.indexOfFirst { distance <= it }.let { if (it < 0) DISTANCE_BANDS_KM.size else it } },
            // "Ideally a proper bank one": an ATM a bank or post office runs, by its mapped name, first.
            { (listing, _) -> if (wantsBank && !bankWords.containsMatchIn(listing.name)) 1 else 0 },
            { (listing, _) -> if (request.diet == "vegan" && listing.vegan == "fully vegan") 0 else 1 },
            { (_, distance) -> distance },
        )).onePerBusiness(request) { (listing, _) -> listing.name }.take(OSM_LISTINGS)
        if (chosen.isEmpty()) {
            val what = request.essential?.noun ?: listOfNotNull(request.diet, "places").joinToString(" ")
            return Result("I couldn't find saved $what within ${GeoDistance.label(radiusKm)} of your location. " +
                "Saved collections cover places someone mapped; try naming a nearby city.", emptyList())
        }
        val snapshot = chosen.firstNotNullOfOrNull { (listing, _) -> listing.snapshot }
        val answer = buildString {
            appendFixAge(fixAgeMinutes)
            clock?.let { appendOpenSummary(chosen.map { (listing, _) -> listing }, it, city = null) }
            day?.let { appendDaySummary(chosen.map { (listing, _) -> listing }, it) }
            if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline; nearest places are listed first")
                append(if (request.diet == "vegan") ", fully vegan first within each distance. " else ". ")
            }
            append("Nearest saved places to your current location")
            if (request.diet == "vegan") append(if (chosen.all { (listing, _) -> isGuidePlace(listing.evidence) })
                " that the travel guide describes as vegan or serving vegan food"
                else if (chosen.any { (listing, _) -> isGuidePlace(listing.evidence) })
                    " that OpenStreetMap tags, or the travel guide describes, as vegan or serving vegan options"
                else " that OpenStreetMap tags as fully vegan or serving vegan options")
            snapshot?.let { append(" (map data as of ").append(it).append(')') }
            append(":\n\n")
            appendUnmatched(unmatchedWords)
            appendVeganOnlyNote(request, chosen.map { (listing, _) -> listing })
            chosen.forEachIndexed { index, (listing, distance) ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(kindLabel(listing))
                append(", ").append(GeoDistance.label(distance))
                bearings[listing.evidence.chunkId]?.let { append(' ').append(GeoDistance.compass(it)) }
                append(" away")
                listing.extras().takeIf(String::isNotBlank)?.let { append("; ").append(it) }
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                append('.')
                appendGuideDescription(listing)
                appendWhere(listing)
                appendHours(listing, clock)
                day?.let { appendDayStatus(listing, it) }
                listing.phone?.takeIf(String::isNotBlank)?.let { append(" Phone: ").append(escapeMarkdown(it.take(40))).append('.') }
                append(' ')
                append(listing.checked?.let { "Listing last checked: $it." } ?: "No check date recorded.")
                append(" [S${index + 1}]\n")
            }
            if (request.essential?.category in setOf("Health", "Safety")) {
                append("\nIn an emergency, call the local emergency number first.")
            }
            append("\nDistances are straight-line from this phone's location, which stayed on the phone; directions are compass bearings. ")
            append("Tags and hours can be out of date; confirm before visiting. ")
            appendSourceCredit(chosen.map { (listing, _) -> listing })
        }
        return Result(answer, chosen.map { (listing, _) -> listing.evidence },
            clock?.let { time -> chosen.count { (listing, _) -> status(listing.hours, time) is OpeningHours.Status.Open } })
    }

    /** "fully vegan restaurant" from the map's tags; a guide listing has none and says where it comes from. */
    private fun kindLabel(listing: Listing): String =
        listOfNotNull(listing.vegan, listing.type).joinToString(" ").ifBlank { if (isGuidePlace(listing.evidence)) "listed in the Wikivoyage guide" else "listed place" }

    /** A guide listing's own words: the map has tags, the guide has a description. */
    private fun StringBuilder.appendGuideDescription(listing: Listing) {
        if (!isGuidePlace(listing.evidence)) return
        listing.description?.takeIf(String::isNotBlank)?.let { append(" “").append(escapeMarkdown(descriptionExcerpt(it))).append("”") }
    }

    private fun StringBuilder.appendSourceCredit(listings: List<Listing>) {
        if (listings.any { isOsmPlace(it.evidence) }) append("Map data © OpenStreetMap contributors (ODbL).")
        if (listings.any { isGuidePlace(it.evidence) }) {
            if (listings.any { isOsmPlace(it.evidence) }) append(' ')
            append("Guide listings are from the saved Wikivoyage guide.")
        }
    }

    /**
     * The saved map collections hold every pharmacy and station, but of places to eat only those
     * tagged for vegan food. A plain food question answered from that collection alone is told so,
     * rather than passing a vegan shortlist off as the places nearby.
     */
    private fun StringBuilder.appendVeganOnlyNote(request: Request, listings: List<Listing>) {
        if (request.diet != null || "Eat" !in request.categories || request.essential != null) return
        if (listings.isEmpty() || !listings.all { isOsmPlace(it.evidence) && it.vegan != null }) return
        append("Of places to eat, the saved map collection holds only those tagged for vegan food, so these are the ")
        append("vegan-friendly places nearby rather than everything around you.\n\n")
    }

    /** The city the map files the nearest places under: where this phone is. */
    /**
     * Museums, sights and things to do are travel-guide listings, which carry no map index: a
     * "museum within walking distance of me" finds nothing by position even in Stockholm. Those
     * kinds are answered from the city the phone is in instead ([inCity]).
     */
    fun asksGuideKind(question: String): Boolean =
        KINDS.any { kind -> kind.categories.any { it == "See" || it == "Do" } && kind.words.containsMatchIn(question) }

    /** The question with its "near me" words replaced by the city the phone is in. */
    // The near-me words with what leads into them: "in the city I'm in", "I can walk to from here".
    private val nearMeWithLead = Regex("(?i)\\s*(?:\\b(?:in|at|to|of|from|near|around)\\s+)?(?:\\b(?:i|we|you)\\s+(?:can|could)\\s+)?(?:${nearMePhrase.pattern.removePrefix("(?i)")})")
    fun inCity(question: String, city: String): String =
        question.replace(nearMeWithLead, " ").replace(Regex("\\s+(?:of|to)\\s+(?=[?.!,]|$)"), " ")
            .replace(Regex("\\s+([?.!,])"), "$1").trim().trimEnd('?', '.', '!') + " in $city?"

    /** Said before a city-wide answer stands in for a near-me one, so the dropped distance is not hidden. */
    fun cityWideNote(question: String, city: String): String {
        val asked = if (Regex("(?i)walk").containsMatchIn(question)) "within walking distance" else "near you"
        return "_None of the saved map places $asked matches this, so here is what the travel guide lists for " +
            "$city as a whole. Check distances before you set off._\n\n"
    }

    internal fun cityOf(places: List<NearbyPlace>): String? = places.sortedBy { it.distanceKm }.take(15)
        .mapNotNull { field(it.evidence.text, "Destination")?.substringBefore('/')?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Eat listings in a city's Wikivoyage guide, by business name: places the guide itself recommends. */
    private fun guidePicks(items: List<Evidence>, city: String?): Map<String, Evidence> = items
        .filter { isGuidePlace(it) && field(it.text, "Category") == "Eat" }
        .filter { city == null || field(it.text, "Destination")?.substringBefore('/').equals(city, ignoreCase = true) }
        .mapNotNull { item -> field(item.text, "Place")?.let { brand(it) to item } }
        .filter { (key, _) -> key.length >= 3 }
        .toMap()

    /** Names on three or more of the listings nearby: a chain's branches. */
    private fun chainNames(items: List<Evidence>): Set<String> = items
        .mapNotNull { field(it.text, "Place")?.let(::brand) }
        .groupingBy { it }.eachCount().filter { (key, count) -> count >= 3 && key.length >= 3 }.keys

    /**
     * Order for "best" with no ratings offline, lower first at each step: fully vegan when vegan
     * food was asked for, recommended by the city's Wikivoyage guide, a sit-down restaurant before a
     * café or fast food, an independent place before a chain, then the nearer distance band, then the
     * more completely mapped. A missing opening-hours tag says nothing about the food, so it must not
     * send the place round the corner below one across town.
     */
    private fun bestRanks(listing: Listing, distanceKm: Double, request: Request, picks: Map<String, Evidence>, chains: Set<String>): List<Int> {
        val key = brand(listing.name)
        return listOf(
            if (request.diet == "vegan" && listing.vegan != "fully vegan") 1 else 0,
            if (key in picks || isGuidePlace(listing.evidence)) 0 else 1,
            when (listing.type) { "restaurant" -> 0; null, "cafe" -> 1; else -> 2 },
            if (key in chains) 1 else 0,
            DISTANCE_BANDS_KM.indexOfFirst { distanceKm <= it }.let { if (it < 0) DISTANCE_BANDS_KM.size else it },
            3 - listOfNotNull(listing.hours, listing.cuisine, listing.checked).size,
        )
    }

    private fun compareRanks(a: List<Int>, b: List<Int>): Int =
        a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: 0

    private const val BEST_PREAMBLE = "There are no ratings offline, so these are ordered by what the map and the travel " +
        "guide record: "

    /** [guide]: only when a listed place is also in the city's Wikivoyage guide is that order claimed. */
    private fun StringBuilder.appendBestOrder(diet: String?, guide: Boolean) {
        append(BEST_PREAMBLE)
        if (diet == "vegan") append("fully vegan places first, then ")
        if (guide) append("places the city's Wikivoyage guide also recommends, ")
        append("sit-down restaurants before cafés and fast food, ")
        append("and independent places before chains.")
    }

    /**
     * Listings near the phone that fit the request. Words of the question that no listing nearby
     * carries ("hungry", "cheap") must not empty the whole area: the places that fit everything else
     * are kept, and [appendUnmatched] says which words none of them mention.
     */
    private fun nearbyMatches(question: String, request: Request, places: List<NearbyPlace>): Pair<List<Pair<Listing, Double>>, List<String>> {
        val leftOut = excluded(question)
        fun matches(relaxed: Boolean) = places.mapNotNull { place ->
            listing(place.evidence, question, request, destinationRequired = false, relaxed = relaxed)?.takeUnless { leftOut(it, leftOut) }?.let { it to place.distanceKm }
        }.distinctBy { (listing, _) -> listing.name.lowercase() to listing.location?.lowercase() }
        val strict = matches(relaxed = false)
        if (strict.isNotEmpty() || request.essential != null) return strict to emptyList()
        val words = freeWords(question, request, destinationRequired = false, destination = null)
        if (words.isEmpty()) return strict to emptyList()
        // Kind, diet and type still apply; only the free words are relaxed.
        val loose = matches(relaxed = true)
        return loose to words.filter { term -> loose.none { (listing, _) -> says(listing.evidence.text, term) } }
    }

    /** [where]: "nearby" for the phone's surroundings, "in Lyon" for a city. */
    private fun StringBuilder.appendUnmatched(words: List<String>, where: String = "nearby") {
        if (words.isEmpty()) return
        append("None of these saved listings mentions ").append(words.joinToString(", ") { "“$it”" })
        append("; they are the places ").append(where).append(" that fit the rest of the question.\n\n")
    }

    /** An address when mapped; otherwise coordinates, which any offline map app can find. */
    private fun StringBuilder.appendWhere(listing: Listing) {
        val address = listing.location?.takeIf(String::isNotBlank)
        address?.let { append(" Address: ").append(escapeMarkdown(it.take(100))).append('.') }
        if (address == null && listing.latitude != null && listing.longitude != null) {
            append(" Coordinates: ").append("%.5f, %.5f".format(Locale.ROOT, listing.latitude, listing.longitude)).append('.')
        }
    }

    /**
     * The best places around this phone: the city it is in, named, and that city's places ranked as
     * [bestRanks] says, distance last. Null when nothing matched, so the caller says so as before.
     */
    private fun bestNearby(question: String, request: Request, places: List<NearbyPlace>, fixAgeMinutes: Long): Result? {
        val bearings = places.mapNotNull { place -> place.bearingDegrees?.let { place.evidence.chunkId to it } }.toMap()
        val (matched, unmatchedWords) = nearbyMatches(question, request, places)
        val meals = matched.filter { (listing, _) -> listing.type in MEAL_TYPES || isGuidePlace(listing.evidence) }
        val pool = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else matched
        if (pool.isEmpty()) return null
        val city = cityOf(places)
        val local = city?.let { name -> pool.filter { (listing, _) -> listing.destination.equals(name, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() } ?: pool
        val picks = guidePicks(places.map { it.evidence }, city)
        val chains = chainNames(places.map { it.evidence })
        val chosen = local.sortedWith { a, b ->
            compareRanks(bestRanks(a.first, a.second, request, picks, chains), bestRanks(b.first, b.second, request, picks, chains))
                .takeIf { it != 0 } ?: a.second.compareTo(b.second)
        }.onePerBusiness(request) { (listing, _) -> listing.name }.take(OSM_LISTINGS)
        val guides = chosen.mapNotNull { (listing, _) ->
            picks[brand(listing.name)]?.takeIf { it.chunkId != listing.evidence.chunkId }
        }.distinctBy { it.chunkId }
        val snapshot = chosen.firstNotNullOfOrNull { (listing, _) -> listing.snapshot }
        val answer = buildString {
            appendFixAge(fixAgeMinutes)
            append(city?.let { "Near you in **${escapeMarkdown(it)}**. " } ?: "Near you. ")
            appendBestOrder(request.diet, guide = guides.isNotEmpty())
            snapshot?.let { append(" Map data as of ").append(it).append('.') }
            append("\n\n")
            appendUnmatched(unmatchedWords)
            appendVeganOnlyNote(request, chosen.map { (listing, _) -> listing })
            chosen.forEachIndexed { index, (listing, distance) ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(kindLabel(listing))
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                append("; ").append(GeoDistance.label(distance))
                bearings[listing.evidence.chunkId]?.let { append(" to the ").append(GeoDistance.compass(it)) } ?: append(" away")
                append('.')
                appendGuideDescription(listing)
                appendWhere(listing)
                appendHours(listing, null)
                append(" [S${index + 1}]")
                val guide = picks[brand(listing.name)]?.takeIf { it.chunkId != listing.evidence.chunkId }
                guide?.let { item ->
                    field(item.text, "Description")?.takeIf(String::isNotBlank)?.let { description ->
                        append(" The Wikivoyage guide: “").append(escapeMarkdown(descriptionExcerpt(description))).append("”")
                    } ?: append(" Also listed in the Wikivoyage guide.")
                    append(" [S${chosen.size + guides.indexOfFirst { it.chunkId == item.chunkId } + 1}]")
                }
                append('\n')
            }
            append("\nDistances are straight-line from this phone's location, which stayed on the phone. ")
            append("Tags and hours can be out of date; confirm before visiting. ")
            appendSourceCredit(chosen.map { (listing, _) -> listing })
        }
        return Result(answer, chosen.map { (listing, _) -> listing.evidence } + guides)
    }

    /** OpenStreetMap wording: tags say what mappers recorded, so each place states its label and date. */
    private fun osmAnswer(question: String, diet: String?, listings: List<Listing>, noun: String?, clock: LocalDateTime?,
                          guided: Boolean = false, unmatched: List<String> = emptyList(), day: DayAsk? = null): Result {
        val city = listings.first().destination
        val snapshot = listings.firstNotNullOfOrNull { it.snapshot }
        val answer = buildString {
            clock?.let { appendOpenSummary(listings, it, city) }
            day?.let { appendDaySummary(listings, it) }
            if (clock == null && day == null && asksBest(question)) {
                appendBestOrder(diet, guide = guided)
                append(' ')
            } else if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline")
                append(if (diet == "vegan") "; fully vegan places are listed first. " else ". ")
            }
            append("OpenStreetMap lists these ")
            append(noun ?: "places")
            append(" in ")
            append(city)
            when (diet) {
                "vegan" -> append(" as fully vegan or serving vegan options")
                "vegetarian" -> append(" as serving vegan food, which is also vegetarian")
            }
            snapshot?.let { append(" (map data as of ").append(it).append(')') }
            append(":\n\n")
            appendUnmatched(unmatched, "in $city")
            listings.forEachIndexed { index, listing ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(listOfNotNull(listing.vegan, listing.type).joinToString(" "))
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                listing.extras().takeIf(String::isNotBlank)?.let { append("; ").append(it) }
                append('.')
                val address = listing.location?.takeIf(String::isNotBlank)
                address?.let { append(" Address: ").append(escapeMarkdown(it.take(100))).append('.') }
                // Many mapped places (most of Tokyo) carry no address; coordinates still find
                // them in any offline map app.
                if (address == null && listing.latitude != null && listing.longitude != null) {
                    append(" Coordinates: ").append("%.5f, %.5f".format(Locale.ROOT, listing.latitude, listing.longitude)).append('.')
                }
                appendHours(listing, clock)
                day?.let { appendDayStatus(listing, it) }
                listing.phone?.takeIf(String::isNotBlank)?.let { append(" Phone: ").append(escapeMarkdown(it.take(40))).append('.') }
                append(' ')
                append(listing.checked?.let { "Listing last checked: $it." } ?: "No check date recorded.")
                append(" [S${index + 1}]\n")
            }
            append("\nTags and hours can be out of date; confirm before visiting. Map data © OpenStreetMap contributors (ODbL).")
        }
        return Result(answer, listings.map(Listing::evidence))
    }

    /**
     * Opening status from hours as the guides and maps write them. OpenStreetMap values are read
     * as they are; a Wikivoyage listing's hours are first put into the same form (see [guideHours]).
     */
    private fun status(hours: String?, now: LocalDateTime): OpeningHours.Status = OpeningHours.status(hours?.let(::guideHours), now)

    private fun openRank(hours: String?, now: LocalDateTime) = when (status(hours, now)) {
        is OpeningHours.Status.Open -> 0
        OpeningHours.Status.Unknown -> 1
        OpeningHours.Status.Closed -> 2
    }

    private val ampm = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b")
    private val longDays = Regex("(?i)\\b(mon|tues?|wed(?:nes)?|thu(?:rs?)?|fri|sat(?:ur)?|sun)(?:day)?s?\\b")
    // "M-F", "W" and "F" are how Wikivoyage abbreviates weekdays whose two letters would clash.
    private val letterDays = Regex("\\b(M|W|F)\\b(?=[\\s,;-]|$)")
    private val adjacentDays = Regex("\\b(Mo|Tu|We|Th|Fr|Sa|Su)\\s+(?=(?:Mo|Tu|We|Th|Fr|Sa|Su)\\b)")
    private val daily = Regex("(?i)\\b(?:open\\s+)?(?:daily|every\\s*day)\\b[,:]?\\s*")

    /**
     * A Wikivoyage listing's hours in the OpenStreetMap form [OpeningHours] reads: "Daily 11:00-22:00"
     * is every day, "M-F 07:00-18:00, Sa Su 07:00-15:00" is "Mo-Fr 07:00-18:00, Sa,Su 07:00-15:00",
     * "Tu-Sa 07:00-12:00 and 15:00-19:00" lists two ranges, "7AM-3:30PM" is a 24-hour range. What is
     * still not in that form ("lunch Th-Sa") stays unreadable and is reported as such, not as closed.
     */
    internal fun guideHours(hours: String): String = hours
        .replace(daily, "")
        .replace(Regex("(?i)\\s+and\\s+"), ", ")
        .replace(Regex("(?i)\\bnoon\\b"), "12:00").replace(Regex("(?i)\\bmidnight\\b"), "24:00")
        .replace(ampm) { m ->
            if (m.groupValues[1].toInt() !in 1..12 || m.groupValues[2].ifEmpty { "0" }.toInt() !in 0..59) return@replace m.value
            val hour = m.groupValues[1].toInt().let { h -> if (m.groupValues[3].equals("pm", true) && h < 12) h + 12 else if (m.groupValues[3].equals("am", true) && h == 12) 0 else h }
            "%02d:%s".format(Locale.ROOT, hour, m.groupValues[2].ifEmpty { "00" })
        }
        .replace(longDays) { m -> m.groupValues[1].take(2).let { it[0].uppercaseChar() + it.substring(1).lowercase() } }
        .replace(letterDays) { m -> when (m.groupValues[1]) { "M" -> "Mo"; "W" -> "We"; else -> "Fr" } }
        .replace(adjacentDays) { m -> m.groupValues[1] + "," }
        .trim()

    /** "Open on Sunday morning": the weekday asked about, and the part of it if one was named. */
    private data class DayAsk(val day: DayOfWeek, val part: String?) {
        val label: String get() = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + (part?.let { " $it" } ?: "")
    }

    private val dayWord = Regex("(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?(?:\\s+(morning|afternoon|evening|night))?\\b")
    private val openWord = Regex("(?i)\\b(open|opens|opening|closed|close|closes)\\b")

    /** A question about a named weekday's opening ("is anything open on sunday morning?"); "open now" is answered by the clock instead. */
    private fun dayAsk(question: String): DayAsk? {
        if (!openWord.containsMatchIn(question) || openNow.containsMatchIn(question)) return null
        val match = dayWord.find(question) ?: return null
        return DayAsk(DayOfWeek.valueOf(match.groupValues[1].uppercase(Locale.ROOT)), match.groupValues[2].lowercase(Locale.ROOT).ifEmpty { null })
    }

    // These part-of-day boundaries are explicit approximations, not business-specific rules.
    private val PART_WINDOWS = mapOf(
        "morning" to (6 * 60 to 12 * 60),
        "afternoon" to (12 * 60 to 18 * 60),
        "evening" to (18 * 60 to 22 * 60),
        "night" to (22 * 60 to 24 * 60),
    )
    private val REFERENCE_MONDAY: LocalDate = LocalDate.of(2026, 1, 5)

    /** Whether recorded hours have the place open on the day (and part of it) asked about. */
    private fun dayStatus(hours: String?, ask: DayAsk): OpeningHours.Status {
        val date = REFERENCE_MONDAY.plusDays(ask.day.ordinal.toLong())
        val window = ask.part?.let { PART_WINDOWS.getValue(it) } ?: (0 to 1440)
        return OpeningHours.during(hours?.let(::guideHours), date, window.first, window.second)
    }

    private fun dayRank(hours: String?, ask: DayAsk) = when (dayStatus(hours, ask)) {
        is OpeningHours.Status.Open -> 0
        OpeningHours.Status.Unknown -> 1
        OpeningHours.Status.Closed -> 2
    }

    private fun StringBuilder.appendDaySummary(listings: List<Listing>, ask: DayAsk) {
        val statuses = listings.map { dayStatus(it.hours, ask) }
        val open = statuses.count { it is OpeningHours.Status.Open }
        val unknown = statuses.count { it == OpeningHours.Status.Unknown }
        when {
            open == listings.size -> append(if (open == 1) "This place is open on ${ask.label} by its recorded hours" else "All $open places below are open on ${ask.label} by their recorded hours")
            open > 0 -> append("$open of the ${listings.size} places below ${if (open == 1) "is" else "are"} open on ${ask.label} by their recorded hours; those come first")
            unknown == listings.size -> append("I can't tell which of these places are open on ${ask.label}: their hours aren't recorded in a form I can check")
            unknown > 0 -> append("None of the places below can be confirmed open on ${ask.label} by its recorded hours")
            else -> append("None of the places below is open on ${ask.label} by its recorded hours")
        }
        if (unknown in 1 until listings.size) {
            append(". ").append(if (unknown == 1) "1 has" else "$unknown have").append(" no hours I can check")
        }
        append(". Recorded hours can be out of date and don't account for public holidays. ")
    }

    private fun StringBuilder.appendDayStatus(listing: Listing, ask: DayAsk) {
        val hours = listing.hours?.takeIf(String::isNotBlank)
        when (dayStatus(hours, ask)) {
            is OpeningHours.Status.Open -> append(" **Open ${ask.label}**.")
            OpeningHours.Status.Closed -> append(" **Closed ${ask.label}**.")
            OpeningHours.Status.Unknown -> if (hours == null) append(" No opening hours recorded.")
        }
        hours?.let { append(" Hours in source: ").append(escapeMarkdown(it.take(80))).append('.') }
    }

    private fun clockLabel(now: LocalDateTime) =
        "%s %02d:%02d".format(Locale.ROOT, now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), now.hour, now.minute)

    /**
     * Opening state from mapped hours and the phone's clock. Offline there is no live status and
     * no time-zone database for a named city, so the answer says what it assumed.
     */
    private fun StringBuilder.appendOpenSummary(listings: List<Listing>, now: LocalDateTime, city: String?) {
        val statuses = listings.map { status(it.hours, now) }
        val open = statuses.count { it is OpeningHours.Status.Open }
        val unknown = statuses.count { it == OpeningHours.Status.Unknown }
        val checked = "by the opening hours mappers recorded, checked against this phone's clock (${clockLabel(now)})"
        when {
            open == listings.size -> append(if (open == 1) "This place is open now $checked" else "All $open places below are open now $checked")
            open > 0 -> append("$open of the ${listings.size} places below ${if (open == 1) "is" else "are"} open now $checked; open places are listed first")
            unknown == listings.size -> append(if (unknown == 1) "I can't tell whether this place is open now: its hours aren't recorded in a form I can check"
                else "I can't tell which of these places are open now: their hours aren't recorded in a form I can check")
            unknown > 0 -> append("None of the places below can be confirmed open now $checked")
            else -> append("None of the places below is open now $checked")
        }
        if (unknown in 1 until listings.size) {
            append(". ").append(if (unknown == 1) "1 has" else "$unknown have").append(" no hours I can check")
        }
        append(". ")
        if (unknown < listings.size) {
            city?.let { append("That assumes the phone is set to ").append(it).append("'s local time. ") }
            append("Recorded hours can be out of date and don't account for public holidays. ")
        }
    }

    private fun StringBuilder.appendHours(listing: Listing, clock: LocalDateTime?) {
        val hours = listing.hours?.takeIf(String::isNotBlank)
        if (clock != null) {
            when (val state = status(hours, clock)) {
                is OpeningHours.Status.Open -> append(" **Open now**").append(state.until?.let { " until %02d:%02d".format(Locale.ROOT, it.hour, it.minute) } ?: "").append('.')
                OpeningHours.Status.Closed -> append(" **Closed now**.")
                OpeningHours.Status.Unknown -> if (hours == null) append(" No opening hours recorded.")
            }
        }
        hours?.let { append(" Hours in source: ").append(escapeMarkdown(it.take(80))).append('.') }
    }

    private data class Listing(
        val evidence: Evidence,
        val name: String,
        val destination: String,
        val description: String?,
        val location: String?,
        val checked: String?,
        val pageRevision: String?,
        val vegan: String? = null,
        val type: String? = null,
        val cuisine: String? = null,
        val hours: String? = null,
        val snapshot: String? = null,
        val phone: String? = null,
        val fee: String? = null,
        val emergency: String? = null,
        val wheelchair: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) {
        /** Practical details for essentials, as short clauses. */
        fun extras(): String = listOfNotNull(
            emergency?.let { if (it == "yes") "emergency department" else "no emergency department" },
            fee?.let { if (it == "no") "free" else "fee charged" },
            wheelchair?.let { "wheelchair access: $it" },
        ).joinToString("; ")
    }

    private const val OSM_PREFIX = "osm-place-"
    private const val WV_PREFIX = "wv-place-"
    private const val OSM_LISTINGS = 6
    /** Guide listings shown for a city: enough for a day's sights or a market round. */
    private const val WV_LISTINGS = 6
    private val DISTANCE_BANDS_KM = listOf(1.0, 3.0, 10.0)
    private val MEAL_TYPES = setOf("restaurant", "fast food", "food court")
    private const val CITY_RADIUS_KM = 25.0
    private val restaurantWords = Regex("(?i)\\b(restaurants?|dining|places? to eat)\\b")

    private fun field(text: String, key: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith("$key: ") }
        ?.removePrefix("$key: ")
        ?.trim()

    private fun descriptionExcerpt(description: String): String {
        val clean = description.trim()
        if (clean.length <= 160) return clean
        return clean.take(160).substringBeforeLast(' ', clean.take(160)).trimEnd() + "…"
    }

    private fun escapeMarkdown(value: String): String = value.replace(Regex("[\\\\*_`\\[\\]]")) { match ->
        "\\${match.value}"
    }
}
