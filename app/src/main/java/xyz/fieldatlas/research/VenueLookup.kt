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
    private val veganWord = Regex("(?i)\\bvegan\\b")
    private val vegetarianWord = Regex("(?i)\\bvegetarian\\b")
    private val bestWord = Regex("(?i)\\bbest\\b")
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
    )

    /** A request for places to go, answerable from place listings alone. */
    fun isPlaceLookup(question: String) = isLookup(question)

    // "Pharmacy open now in Berlin" asks for places as plainly as "where is a pharmacy" does.
    private fun isLookup(question: String) = (venueWords.containsMatchIn(question) || essential(question) != null) &&
        (lookupWords.containsMatchIn(question) || openNow.containsMatchIn(question)) && !complexWords.containsMatchIn(question)

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

    /** [now] is this phone's local time, used only when the question asks what is open now. */
    fun answer(question: String, evidence: List<Evidence>, now: LocalDateTime = LocalDateTime.now()): Result? {
        if (!isLookup(question)) return null
        val request = request(question)
        val diet = request.diet
        val listings = evidence.mapNotNull { item ->
            listing(item, question, request, destinationRequired = true)
        }.distinctBy { it.destination.lowercase() to it.name.lowercase() }
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
            val ordered = clock?.let { time -> chosen.sortedBy { openRank(it.hours, time) } } ?: chosen
            return osmAnswer(question, diet, ordered.take(OSM_LISTINGS), request.essential?.noun, clock)
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

    private val nearMePhrase = Regex(
        "(?i)\\b(near me|nearby|near here|around me|around here|close to me|closest|nearest|where i am|" +
            "my (?:current )?location|my (?:current )?city|(?:the )?city i(?:'m| am) (?:currently )?in)\\b",
    )
    private val foodWords = Regex("(?i)\\b(food|eat|eating|meals?|dinner|lunch|breakfast)\\b")
    private val nearbyWords = setOf(
        "near", "nearby", "here", "around", "close", "closest", "nearest", "where", "am", "location",
        "current", "currently", "city", "food", "eat", "eating", "meal", "meals", "dinner", "lunch",
        "breakfast", "can", "get", "good",
    )

    private val selfPhrase = Regex(
        "(?i)\\b(near me|near here|around me|around here|close to me|where i am|" +
            "my (?:current )?location|my (?:current )?city|(?:the )?city i(?:'m| am) (?:currently )?in)\\b",
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

    /**
     * Nearest matching places from the phone's coordinates. Distance bands come first so a
     * fully vegan place 9 km away never outranks one around the corner; within a band, fully
     * vegan places lead when vegan food was asked for.
     */
    fun nearbyAnswer(question: String, places: List<NearbyPlace>, radiusKm: Double, now: LocalDateTime = LocalDateTime.now()): Result {
        val request = request(question)
        val clock = now.takeIf { openNow.containsMatchIn(question) }
        val bearings = places.mapNotNull { place -> place.bearingDegrees?.let { place.evidence.chunkId to it } }.toMap()
        val matched = places.mapNotNull { place ->
            listing(place.evidence, question, request, destinationRequired = false)?.let { it to place.distanceKm }
        }.distinctBy { (listing, _) -> listing.name.lowercase() to listing.location?.lowercase() }
        val meals = matched.filter { (listing, _) -> listing.type in MEAL_TYPES }
        val pool = if (restaurantWords.containsMatchIn(question) && meals.isNotEmpty()) meals else matched
        val chosen = pool.sortedWith(compareBy(
            // "Pharmacy open now near me": a place open by its recorded hours comes before a
            // closer one recorded as closed.
            { (listing, _) -> clock?.let { openRank(listing.hours, it) } ?: 0 },
            { (_, distance) -> DISTANCE_BANDS_KM.indexOfFirst { distance <= it }.let { if (it < 0) DISTANCE_BANDS_KM.size else it } },
            { (listing, _) -> if (request.diet == "vegan" && listing.vegan == "fully vegan") 0 else 1 },
            { (_, distance) -> distance },
        )).take(OSM_LISTINGS)
        if (chosen.isEmpty()) {
            val what = request.essential?.noun ?: listOfNotNull(request.diet, "places").joinToString(" ")
            return Result("I couldn't find saved $what within ${GeoDistance.label(radiusKm)} of your location. " +
                "Saved collections cover places someone mapped; try naming a nearby city.", emptyList())
        }
        val snapshot = chosen.firstNotNullOfOrNull { (listing, _) -> listing.snapshot }
        val answer = buildString {
            clock?.let { appendOpenSummary(chosen.map { (listing, _) -> listing }, it, city = null) }
            if (bestWord.containsMatchIn(question)) {
                append("I can't verify a current “best” ranking offline; nearest places are listed first")
                append(if (request.diet == "vegan") ", fully vegan first within each distance. " else ". ")
            }
            append("Nearest saved places to your current location")
            if (request.diet == "vegan") append(" that OpenStreetMap tags as fully vegan or serving vegan options")
            snapshot?.let { append(" (map data as of ").append(it).append(')') }
            append(":\n\n")
            chosen.forEachIndexed { index, (listing, distance) ->
                append("- **").append(escapeMarkdown(listing.name)).append("** — ")
                append(listOfNotNull(listing.vegan, listing.type).joinToString(" ").ifBlank { "listed place" })
                append(", ").append(GeoDistance.label(distance))
                bearings[listing.evidence.chunkId]?.let { append(' ').append(GeoDistance.compass(it)) }
                append(" away")
                listing.extras().takeIf(String::isNotBlank)?.let { append("; ").append(it) }
                listing.cuisine?.takeIf(String::isNotBlank)?.let { append("; ").append(escapeMarkdown(it.take(60))) }
                append('.')
                listing.location?.takeIf(String::isNotBlank)?.let { append(" Address: ").append(escapeMarkdown(it.take(100))).append('.') }
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

    /** OpenStreetMap wording: tags say what mappers recorded, so each place states its label and date. */
    private fun osmAnswer(question: String, diet: String?, listings: List<Listing>, noun: String?, clock: LocalDateTime?): Result {
        val city = listings.first().destination
        val snapshot = listings.firstNotNullOfOrNull { it.snapshot }
        val answer = buildString {
            clock?.let { appendOpenSummary(listings, it, city) }
            if (bestWord.containsMatchIn(question)) {
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
