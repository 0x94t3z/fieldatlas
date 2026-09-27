package xyz.fieldatlas.research

/** A fast, source-only path for simple local place lookups. No model claim or ranking. */
object VenueLookup {
    data class Result(val answer: String, val sources: List<Evidence>)

    private val venueWords = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat|museums?|sights?|attractions?|hotels?|hostels?|shops?|stores?|bars?)\\b")
    private val lookupWords = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where)\\b")
    private val complexWords = Regex("(?i)\\b(compare|contrast|explain|why|how|reason|evidence|versus)\\b")
    private val veganWord = Regex("(?i)\\bvegan\\b")
    private val vegetarianWord = Regex("(?i)\\bvegetarian\\b")
    private val bestWord = Regex("(?i)\\bbest\\b")
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val genericWords = setOf(
        "best", "recommend", "recommendation", "recommendations", "suggest", "suggestions",
        "find", "list", "listed", "which", "where", "restaurants", "restaurant", "cafes", "cafe",
        "cafés", "café", "places", "place", "eat", "food", "dining", "nearby", "some",
        "museums", "museum", "sights", "sight", "attractions", "attraction",
        "hotels", "hotel", "hostels", "hostel", "shops", "shop", "stores", "store",
        "bars", "bar",
    )

    fun answer(question: String, evidence: List<Evidence>): Result? {
        if (!venueWords.containsMatchIn(question) || !lookupWords.containsMatchIn(question) ||
            complexWords.containsMatchIn(question)
        ) return null
        val wantedCategories = when {
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
        val listings = evidence.mapNotNull { item ->
            val legacyEat = item.documentId.startsWith("wv-eat-")
            val newPlace = item.documentId.startsWith("wv-place-") &&
                field(item.text, "Category") in wantedCategories
            if (!(legacyEat && "Eat" in wantedCategories) && !newPlace) return@mapNotNull null
            val destination = field(item.text, "Destination")?.substringBefore('/') ?: return@mapNotNull null
            if (!Regex("(?i)\\b(?:in|near|around)\\s+${Regex.escape(destination)}\\b")
                    .containsMatchIn(question)) return@mapNotNull null
            val name = field(item.text, if (legacyEat) "Place to eat" else "Place")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            if (requestedType != null && !requestedType.containsMatchIn(item.text)) return@mapNotNull null
            val destinationWords = word.findAll(destination.lowercase()).map { it.value }.toSet()
            val qualifiers = FtsQuery.from(question)?.terms.orEmpty().filterNot { term ->
                term in genericWords || term in destinationWords || term == diet
            }
            if (!qualifiers.all { qualifier ->
                    word.findAll(item.text.lowercase()).any { it.value == qualifier }
                }) return@mapNotNull null
            if (diet == "vegan" && !veganWord.containsMatchIn(item.text)) return@mapNotNull null
            if (diet == "vegetarian" && !veganWord.containsMatchIn(item.text) &&
                !vegetarianWord.containsMatchIn(item.text)) return@mapNotNull null
            Listing(
                evidence = item,
                name = name,
                destination = destination,
                description = field(item.text, "Description"),
                location = field(item.text, "Address") ?: field(item.text, "Directions"),
                checked = field(item.text, "Listing last checked"),
                pageRevision = field(item.text, "Source page revision"),
            )
        }.distinctBy { it.destination.lowercase() to it.name.lowercase() }.take(4)
        if (listings.isEmpty()) return null

        val city = listings.first().destination
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
            listings.forEachIndexed { index, listing ->
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
        return Result(answer, listings.map(Listing::evidence))
    }

    private data class Listing(
        val evidence: Evidence,
        val name: String,
        val destination: String,
        val description: String?,
        val location: String?,
        val checked: String?,
        val pageRevision: String?,
    )

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
