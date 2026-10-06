package xyz.fieldatlas.research

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** A fast, source-only path for simple local place lookups. No model claim or ranking. */
object VenueLookup {
    /** [openNow] counts listed places open by their recorded hours, when the question asked. */
    data class Result(val answer: String, val sources: List<Evidence>, val openNow: Int? = null)

    private val venueWords = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat|museums?|sights?|attractions?|hotels?|hostels?|shops?|stores?|bars?)\\b")
    private val lookupWords = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where|any|need|is there|are there)\\b")

    /**
     * Everyday essentials a traveller asks for, mapped to the listing category and, where the
     * words name one kind of place, its type. [noun] names them in "none found" answers.
     */
    private data class Essential(val words: Regex, val category: String, val type: Regex?, val noun: String)
    private fun typeLine(vararg labels: String) = Regex("(?im)^Type: (${labels.joinToString("|") { Regex.escape(it) }})$")
    private val ESSENTIALS = listOf(
        Essential(Regex("(?i)\\b(pharmac(?:y|ies)|chemists?|drug ?stores?)\\b"), "Health", typeLine("pharmacy"), "pharmacies"),
        Essential(Regex("(?i)\\b(hospitals?|emergency rooms?|emergency departments?|A&E)\\b"), "Health", typeLine("hospital"), "hospitals"),
        Essential(Regex("(?i)\\b(clinics?|doctors?|GPs?)\\b"), "Health", typeLine("clinic"), "clinics"),
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
    private fun essential(question: String) = ESSENTIALS.firstOrNull { it.words.containsMatchIn(question) }
    private val complexWords = Regex("(?i)\\b(compare|contrast|explain|why|how|reason|evidence|versus)\\b")
    private val veganWord = Regex("(?i)\\bvegans?\\b")
    private val vegetarianWord = Regex("(?i)\\bvegetarians?\\b")
    private val bestWord = Regex("(?i)\\bbest\\b")
    // Asking for the places worth going to, not the closest ones.
    private val qualityWords = Regex("(?i)\\b(best|top|recommend\\w*|good|great)\\b")
    private val nearestWords = Regex("(?i)\\b(nearest|closest)\\b")
    // "the closest ATM, ideally a proper bank one, not Euronet": a named brand to leave out. Only a
    // capitalised name counts, so "not too expensive" and "not just snacks" exclude nothing.
    private val exclusion = Regex("\\b(?:[Nn]ot|[Nn]o|[Aa]void(?:ing)?|[Ee]xcept|other than|rather than|instead of)\\s+(?:an?\\s+|the\\s+)?(\\p{Lu}[\\p{L}&'-]{2,})")
    private val bankWords = Regex("(?i)\\b(banks?|banca|banco|banque|bancaire|sparkasse|caixa|postamat|post ?office|poste)\\b")

    /** Brand or place names the question asks to leave out. */
    private fun excluded(question: String): List<String> = exclusion.findAll(question).map { it.groupValues[1].lowercase() }.toList()

    private fun leftOut(listing: Listing, names: List<String>) = names.any { listing.name.lowercase().contains(it) }
    private val openNow = Regex("(?i)\\b(open|closed)\\b.{0,30}\\b(now|today|tonight|currently)\\b|\\b(currently|still) (open|closed)\\b")
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val genericWords = setOf(
        "best", "recommend", "recommendation", "recommendations", "suggest", "suggestions",
        "find", "list", "listed", "which", "where", "restaurants", "restaurant", "cafes", "cafe",
        "cafés", "café", "places", "place", "eat", "food", "dining", "nearby", "some",
        "museums", "museum", "sights", "sight", "attractions", "attraction",
        "hotels", "hotel", "hostels", "hostel", "shops", "shop", "stores", "store",
        "bars", "bar",
        // About when, not what: "open right now" is answered with the hours on file and a caveat.
        "open", "right", "now", "today", "tonight", "currently", "closed", "still", "saved", "listings",
        // How people phrase a request, not what they want of the place.
        "try", "staying", "stay", "week", "weekend", "days", "visit", "visiting", "trip", "eats", "eating", "vegans",
        "vegetarians", "name", "few", "couple", "good", "great", "nice", "options", "option", "spots", "spot", "proper",
        "meals", "meal", "snacks", "snack", "serve", "serves", "serving", "easy", "go", "should", "can", "you", "dine",
        "dinner", "lunch", "breakfast", "somewhere", "anywhere", "any", "there", "here", "city", "town", "area",
        // How someone feels about the trip or the price, which no listing states in these words:
        // "hungry, any good local places that aren't too expensive?", "museums actually worth it
        // if I only have one day?". Required in every listing, they emptied whole cities.
        "hungry", "starving", "really", "actually", "worth", "one", "two", "day", "only", "just", "first", "time",
        "too", "very", "aren", "isn", "don", "doesn", "expensive", "cheap", "cheaper", "inexpensive", "affordable",
        "budget", "pricey", "local", "locals", "authentic", "typical", "traditional", "famous", "popular", "decent",
        "tasty", "delicious", "quick", "must", "see", "go", "worthwhile", "recommended", "top", "favourite", "favorite",
        "looking", "want", "like", "need", "get", "kids", "family", "friends", "people", "tourists", "tourist",
    )

    /** A request for places to go, answerable from place listings alone. */
    fun isPlaceLookup(question: String) = isLookup(question)

    /** A kind of place to go ("museums", "hostels"), which a city's listings answer rather than a general article. */
    fun isVenueKind(name: String) = venueWords.matches(name.trim())

    // "Pharmacy open now in Berlin" asks for places as plainly as "where is a pharmacy" does.
    // "Where do vegans eat in Montreal?", "Is it easy to eat vegan in Kuala Lumpur? Name a few places."
    private val dietFood = Regex("(?i)\\b(vegans?|vegetarians?)\\b.*\\b(eat|eats|eating|food|places|spots|dine|meals?)\\b|" +
        "\\b(eat|eats|eating|food|places|spots|dine|meals?)\\b.*\\b(vegans?|vegetarians?)\\b")
    private val askWords = Regex("(?i)\\b(name|try|should i|where do|where can|options|go to)\\b")
    // "Should I bring euros or take out dirhams from an ATM?" weighs two ways to pay; it asks for no ATM.
    private val decision = Regex("(?i)\\bshould i\\b.*\\bor\\b")
    private val seeks = Regex("(?i)\\b(find|where|nearest|closest|near)\\b")

    private fun isLookup(question: String) =
        (venueWords.containsMatchIn(question) || essential(question) != null || dietFood.containsMatchIn(question)) &&
            (lookupWords.containsMatchIn(question) || askWords.containsMatchIn(question) || openNow.containsMatchIn(question)) &&
            !complexWords.containsMatchIn(question) &&
            // An everyday essential named in a decision between alternatives is a place to find only when asked to find it.
            !(!venueWords.containsMatchIn(question) && !dietFood.containsMatchIn(question) && decision.containsMatchIn(question) && !seeks.containsMatchIn(question))

    private fun isOsmPlace(item: Evidence) = item.documentId.startsWith(OSM_PREFIX)

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

    /** The city a question names, as spelled in the place listings already retrieved for it. */
    private fun destinationIn(question: String, evidence: List<Evidence>): String? = evidence.asSequence()
        .filter { isOsmPlace(it) || it.documentId.startsWith("wv-place-") }
        .mapNotNull { field(it.text, "Destination")?.substringBefore('/') }
        .firstOrNull { Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])").containsMatchIn(question) }

    private val museumWord = Regex("(?i)\\b(museums?|galler(?:y|ies))\\b")

    /**
     * A question asking for a museum in a city whose listings were retrieved ("suggest a vegan
     * food stop and a museum visit in Berlin") gets one more search for that city's museums: the
     * question's food words otherwise fill every slot, and the answer reaches for whatever the
     * city overview mentions (a zoo) instead.
     */
    fun museumQuery(question: String, evidence: List<Evidence>): String? {
        if (!museumWord.containsMatchIn(question)) return null
        return destinationIn(question, evidence)?.let { "$it museum" }
    }

    /** Sight listings in that city that are museums or galleries by name or type. */
    fun museumListings(question: String, candidates: List<Evidence>, evidence: List<Evidence>): List<Evidence> {
        val destination = destinationIn(question, evidence) ?: return emptyList()
        return candidates.filter { item ->
            (isOsmPlace(item) || item.documentId.startsWith("wv-place-")) &&
                field(item.text, "Category") == "See" &&
                field(item.text, "Destination")?.substringBefore('/').equals(destination, ignoreCase = true) &&
                (museumWord.containsMatchIn(field(item.text, "Place").orEmpty()) || museumWord.containsMatchIn(field(item.text, "Type").orEmpty()))
        }
    }

    private data class Request(
        val categories: Set<String>,
        val requestedType: Regex?,
        val diet: String?,
        val essential: Essential? = null,
    )

    /**
     * "The best vegan restaurants in the city I'm in": places to go for, so the answer ranks a
     * whole city's places rather than the closest few. "Nearest" asks for distance.
     */
    fun asksBest(question: String): Boolean = qualityWords.containsMatchIn(question) &&
        !nearestWords.containsMatchIn(question) && essential(question) == null

    /** The listing categories a near-me question asks for, so the lookup can filter early. */
    fun nearbyCategories(question: String): Set<String> = request(question).categories

    private fun request(question: String): Request {
        essential(question)?.let { need -> return Request(setOf(need.category), need.type, null, need) }
        val categories = when {
            Regex("(?i)\\b(museums?|sights?|attractions?)\\b").containsMatchIn(question) -> setOf("See", "Do")
            Regex("(?i)\\b(hotels?|hostels?)\\b").containsMatchIn(question) -> setOf("Sleep")
            Regex("(?i)\\b(shops?|stores?)\\b").containsMatchIn(question) -> setOf("Buy")
            Regex("(?i)\\b(bars?)\\b").containsMatchIn(question) -> setOf("Drink")
            Regex("(?i)\\b(caf[eé]s?)\\b").containsMatchIn(question) -> setOf("Eat", "Drink")
            else -> setOf("Eat")
        }
        val requestedType = when {
            Regex("(?i)\\bmuseums?\\b").containsMatchIn(question) -> Regex("(?i)\\bmuseums?\\b")
            Regex("(?i)\\bhotels?\\b").containsMatchIn(question) -> Regex("(?i)\\bhotels?\\b")
            Regex("(?i)\\bhostels?\\b").containsMatchIn(question) -> Regex("(?i)\\bhostels?\\b")
            Regex("(?i)\\bcaf[eé]s?\\b").containsMatchIn(question) -> Regex("(?i)\\b(caf[eé]|coffee)\\b")
            Regex("(?i)\\bbars?\\b").containsMatchIn(question) -> Regex("(?i)\\bbars?\\b")
            else -> null
        }
        val diet = when {
            veganWord.containsMatchIn(question) -> "vegan"
            vegetarianWord.containsMatchIn(question) -> "vegetarian"
            else -> null
        }
        return Request(categories, requestedType, diet)
    }

    /**
     * One listing if it satisfies the request's category, type, diet and qualifier words.
     * [destinationRequired] keeps city answers to places in the named city; nearby answers
     * locate by coordinates instead, so their location words are ignored as qualifiers.
     */
    private fun listing(item: Evidence, question: String, request: Request, destinationRequired: Boolean): Listing? {
        val legacyEat = item.documentId.startsWith("wv-eat-")
        val newPlace = (item.documentId.startsWith("wv-place-") || isOsmPlace(item)) &&
            field(item.text, "Category") in request.categories
        if (!(legacyEat && "Eat" in request.categories) && !newPlace) return null
        val destination = field(item.text, "Destination")?.substringBefore('/')
        if (destinationRequired) {
            // "in Berlin", or the city named anywhere ("the saved Berlin vegan restaurants").
            if (destination == null || !Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(destination)}(?![\\p{L}\\p{N}])")
                    .containsMatchIn(question)) return null
        }
        val name = field(item.text, if (legacyEat) "Place to eat" else "Place")?.takeIf(String::isNotBlank)
            ?: return null
        if (request.requestedType != null && !request.requestedType.containsMatchIn(item.text)) return null
        val ignored = if (destinationRequired) {
            word.findAll(destination.orEmpty().lowercase()).map { it.value }.toSet()
        } else nearbyWords
        val qualifiers = if (request.essential != null) emptyList() else FtsQuery.from(question)?.terms.orEmpty().filterNot { term ->
            term in genericWords || term in ignored || term == request.diet
        }
        if (!qualifiers.all { qualifier ->
                word.findAll(item.text.lowercase()).any { it.value == qualifier }
            }) return null
        if (request.diet == "vegan" && !veganWord.containsMatchIn(item.text)) return null
        if (request.diet == "vegetarian" && !veganWord.containsMatchIn(item.text) &&
            !vegetarianWord.containsMatchIn(item.text)) return null
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

    /** [now] is this phone's local time, used only when the question asks what is open now. */
    fun answer(question: String, evidence: List<Evidence>, now: LocalDateTime = LocalDateTime.now()): Result? {
        if (!isLookup(question)) return null
        val request = request(question)
        val diet = request.diet
        val listings = withinCity(evidence.mapNotNull { item ->
            listing(item, question, request, destinationRequired = true)
        }).distinctBy { it.destination.lowercase() to it.name.lowercase() }
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
                ?: if (asksBest(question)) {
                    val chains = chainNames(evidence)
                    chosen.sortedWith { a, b -> compareRanks(bestRanks(a, request, picks, chains), bestRanks(b, request, picks, chains)) }
                } else chosen
            val shown = ordered.onePerBusiness(request) { it.name }.take(OSM_LISTINGS)
            return osmAnswer(question, diet, shown, request.essential?.noun, clock, guided = shown.any { brand(it.name) in picks })
        }
        val shown = listings.take(4)

        val city = shown.first().destination
        val answer = buildString {
            if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline. ")
            }
            append("These unranked Wikivoyage listings for ")
            append(city)
            if (diet != null) {
                append(" mention ")
                append(diet)
                append(" food. Some may offer dietary options rather than being entirely ")
                append(diet)
            }
            append(":\n\n")
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

    // Ways people point at where they are standing: by distance ("nearest", "close to me"), by
    // place ("around here", "where I am") or by their phone's position ("my location").
    private val nearMePhrase = Regex(
        "(?i)\\b(nearest|closest|nearby|(?:near|close to|around) (?:me|here)|where i am|" +
            "(?:the )?city i(?:'m| am) (?:currently )?in|my (?:current )?(?:location|city))\\b",
    )
    private val foodWords = Regex("(?i)\\b(food|eat|eating|meals?|dinner|lunch|breakfast)\\b")
    private val nearbyWords = setOf(
        "near", "nearby", "here", "around", "close", "closest", "nearest", "where", "am", "location",
        "current", "currently", "city", "food", "eat", "eating", "meal", "meals", "dinner", "lunch",
        "breakfast", "can", "get", "good",
    )

    private val selfPhrase = Regex(
        "(?i)\\b((?:near|close to|around) (?:me|here)|where i am|" +
            "(?:the )?city i(?:'m| am) (?:currently )?in|my (?:current )?(?:location|city))\\b",
    )
    private val namedPlace = Regex("(?i)\\b(?:in|at|around|near)\\s+(?!me\\b|here\\b|my\\b|the city i)\\p{L}")

    /** A place question answered from the phone's own location rather than a named city.
     * "Nearest supermarket in Jakarta" names its city, so it is a city lookup, not a GPS one. */
    fun isNearMe(question: String): Boolean = nearMePhrase.containsMatchIn(question) &&
        (selfPhrase.containsMatchIn(question) || !namedPlace.containsMatchIn(question)) &&
        (venueWords.containsMatchIn(question) || foodWords.containsMatchIn(question) || essential(question) != null) &&
        !complexWords.containsMatchIn(question)

    const val LOCATION_UNAVAILABLE = "I couldn't get this phone's location. Turn on Location (GPS works " +
        "offline) and allow Field Atlas to use it, or name the city in your question."

    // "Where am I?", "What city am I in?", "What's my location?": the phone's own position, named.
    private val whereAmI = Regex(
        "(?i)^\\s*(?:where am i|where are we|" +
            "(?:what|which) (?:city|town|village|country|place|area|neighbou?rhood|district|street|state|province|region|part of town) " +
            "(?:am i|are we) in|what(?:'s|’s| is) (?:my|our) (?:current |exact )?(?:location|position|address)|" +
            "what(?:'s|’s| is) this (?:place|town|city|village|area|neighbou?rhood))(?: now| right now| exactly)?\\s*[?.!]*\\s*$",
    )

    fun isWhereAmI(question: String): Boolean = whereAmI.containsMatchIn(question)

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
            append("\n\nYour position: ").append(coordinates(point)).append(", from this phone's GPS, which works without a connection.")
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
    fun nearbyAnswer(question: String, places: List<NearbyPlace>, radiusKm: Double, now: LocalDateTime = LocalDateTime.now(),
                     fixAgeMinutes: Long = 0): Result {
        val request = request(question)
        val clock = now.takeIf { openNow.containsMatchIn(question) }
        if (clock == null && asksBest(question)) bestNearby(question, request, places, fixAgeMinutes)?.let { return it }
        val bearings = places.mapNotNull { place -> place.bearingDegrees?.let { place.evidence.chunkId to it } }.toMap()
        val (matched, unmatchedWords) = nearbyMatches(question, request, places)
        val meals = matched.filter { (listing, _) -> listing.type in MEAL_TYPES }
        val pool = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else matched
        val wantsBank = request.essential?.category == "Money" && bankWords.containsMatchIn(question)
        val chosen = pool.sortedWith(compareBy(
            // "Pharmacy open now near me": a place open by its recorded hours comes before a
            // closer one recorded as closed.
            { (listing, _) -> clock?.let { openRank(listing.hours, it) } ?: 0 },
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
            if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline; nearest places are listed first")
                append(if (request.diet == "vegan") ", fully vegan first within each distance. " else ". ")
            }
            append("Nearest saved places to your current location")
            if (request.diet == "vegan") append(" that OpenStreetMap tags as fully vegan or serving vegan options")
            snapshot?.let { append(" (map data as of ").append(it).append(')') }
            append(":\n\n")
            appendUnmatched(unmatchedWords)
            chosen.forEachIndexed { index, (listing, distance) ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(listOfNotNull(listing.vegan, listing.type).joinToString(" ").ifBlank { "listed place" })
                append(", ").append(GeoDistance.label(distance))
                bearings[listing.evidence.chunkId]?.let { append(' ').append(GeoDistance.compass(it)) }
                append(" away")
                listing.extras().takeIf(String::isNotBlank)?.let { append("; ").append(it) }
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                append('.')
                appendWhere(listing)
                appendHours(listing, clock)
                listing.phone?.takeIf(String::isNotBlank)?.let { append(" Phone: ").append(escapeMarkdown(it.take(40))).append('.') }
                append(' ')
                append(listing.checked?.let { "Listing last checked: $it." } ?: "No check date recorded.")
                append(" [S${index + 1}]\n")
            }
            if (request.essential?.category in setOf("Health", "Safety")) {
                append("\nIn an emergency, call the local emergency number first.")
            }
            append("\nDistances are straight-line from this phone's location, which stayed on the phone; directions are compass bearings. ")
            append("Tags and hours can be out of date; confirm before visiting. Map data © OpenStreetMap contributors (ODbL).")
        }
        return Result(answer, chosen.map { (listing, _) -> listing.evidence },
            clock?.let { time -> chosen.count { (listing, _) -> OpeningHours.status(listing.hours, time) is OpeningHours.Status.Open } })
    }

    /** The city the map files the nearest places under: where this phone is. */
    private fun cityOf(places: List<NearbyPlace>): String? = places.sortedBy { it.distanceKm }.take(15)
        .mapNotNull { field(it.evidence.text, "Destination")?.substringBefore('/')?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Eat listings in a city's Wikivoyage guide, by business name: places the guide itself recommends. */
    private fun guidePicks(items: List<Evidence>, city: String?): Map<String, Evidence> = items
        .filter { it.documentId.startsWith("wv-place-") && field(it.text, "Category") == "Eat" }
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
     * café or fast food, an independent place before a chain, then the more completely mapped.
     */
    private fun bestRanks(listing: Listing, request: Request, picks: Map<String, Evidence>, chains: Set<String>): List<Int> {
        val key = brand(listing.name)
        return listOf(
            if (request.diet == "vegan" && listing.vegan != "fully vegan") 1 else 0,
            if (key in picks || listing.evidence.documentId.startsWith("wv-place-")) 0 else 1,
            when (listing.type) { "restaurant" -> 0; null, "cafe" -> 1; else -> 2 },
            if (key in chains) 1 else 0,
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
        fun matches(words: String) = places.mapNotNull { place ->
            listing(place.evidence, words, request, destinationRequired = false)?.takeUnless { leftOut(it, leftOut) }?.let { it to place.distanceKm }
        }.distinctBy { (listing, _) -> listing.name.lowercase() to listing.location?.lowercase() }
        val strict = matches(question)
        if (strict.isNotEmpty() || request.essential != null) return strict to emptyList()
        val words = FtsQuery.from(question)?.terms.orEmpty().filterNot { it in genericWords || it in nearbyWords || it == request.diet }
        if (words.isEmpty()) return strict to emptyList()
        // Kind, diet and type still apply; only the free words are relaxed.
        val loose = matches(listOfNotNull(request.diet, "places near me").joinToString(" "))
        return loose to words.filter { term -> loose.none { (listing, _) -> word.findAll(listing.evidence.text.lowercase()).any { it.value == term } } }
    }

    private fun StringBuilder.appendUnmatched(words: List<String>) {
        if (words.isEmpty()) return
        append("None of these saved listings mentions ").append(words.joinToString(", ") { "“$it”" })
        append("; they are the places nearby that fit the rest of the question.\n\n")
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
        val meals = matched.filter { (listing, _) -> listing.type in MEAL_TYPES }
        val pool = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else matched
        if (pool.isEmpty()) return null
        val city = cityOf(places)
        val local = city?.let { name -> pool.filter { (listing, _) -> listing.destination.equals(name, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() } ?: pool
        val picks = guidePicks(places.map { it.evidence }, city)
        val chains = chainNames(places.map { it.evidence })
        val chosen = local.sortedWith { a, b ->
            compareRanks(bestRanks(a.first, request, picks, chains), bestRanks(b.first, request, picks, chains))
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
            chosen.forEachIndexed { index, (listing, distance) ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(listOfNotNull(listing.vegan, listing.type).joinToString(" ").ifBlank { "listed place" })
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                append("; ").append(GeoDistance.label(distance))
                bearings[listing.evidence.chunkId]?.let { append(" to the ").append(GeoDistance.compass(it)) } ?: append(" away")
                append('.')
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
            append("Tags and hours can be out of date; confirm before visiting. Map data © OpenStreetMap contributors (ODbL).")
        }
        return Result(answer, chosen.map { (listing, _) -> listing.evidence } + guides)
    }

    /** OpenStreetMap wording: tags say what mappers recorded, so each place states its label and date. */
    private fun osmAnswer(question: String, diet: String?, listings: List<Listing>, noun: String?, clock: LocalDateTime?,
                          guided: Boolean = false): Result {
        val city = listings.first().destination
        val snapshot = listings.firstNotNullOfOrNull { it.snapshot }
        val answer = buildString {
            clock?.let { appendOpenSummary(listings, it, city) }
            if (clock == null && asksBest(question)) {
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
                listing.phone?.takeIf(String::isNotBlank)?.let { append(" Phone: ").append(escapeMarkdown(it.take(40))).append('.') }
                append(' ')
                append(listing.checked?.let { "Listing last checked: $it." } ?: "No check date recorded.")
                append(" [S${index + 1}]\n")
            }
            append("\nTags and hours can be out of date; confirm before visiting. Map data © OpenStreetMap contributors (ODbL).")
        }
        return Result(answer, listings.map(Listing::evidence))
    }

    private fun openRank(hours: String?, now: LocalDateTime) = when (OpeningHours.status(hours, now)) {
        is OpeningHours.Status.Open -> 0
        OpeningHours.Status.Unknown -> 1
        OpeningHours.Status.Closed -> 2
    }

    private fun clockLabel(now: LocalDateTime) =
        "%s %02d:%02d".format(Locale.ROOT, now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), now.hour, now.minute)

    /**
     * Opening state from mapped hours and the phone's clock. Offline there is no live status and
     * no time-zone database for a named city, so the answer says what it assumed.
     */
    private fun StringBuilder.appendOpenSummary(listings: List<Listing>, now: LocalDateTime, city: String?) {
        val statuses = listings.map { OpeningHours.status(it.hours, now) }
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
            when (val status = OpeningHours.status(hours, clock)) {
                is OpeningHours.Status.Open -> append(" **Open now**").append(status.until?.let { " until %02d:%02d".format(Locale.ROOT, it.hour, it.minute) } ?: "").append('.')
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
    private const val OSM_LISTINGS = 6
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
