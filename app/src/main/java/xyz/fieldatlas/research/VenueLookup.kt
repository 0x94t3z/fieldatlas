package xyz.fieldatlas.research

/** A fast, source-only path for simple local eating-place lookups. No model claim or ranking. */
object VenueLookup {
    data class Result(val answer: String, val sources: List<Evidence>)

    private val venueWords = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat)\\b")
    private val lookupWords = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where)\\b")
    private val complexWords = Regex("(?i)\\b(compare|contrast|explain|why|how|reason|evidence|versus)\\b")
    private val veganWord = Regex("(?i)\\bvegan\\b")
    private val vegetarianWord = Regex("(?i)\\bvegetarian\\b")

    fun answer(question: String, evidence: List<Evidence>): Result? {
        if (!venueWords.containsMatchIn(question) || !lookupWords.containsMatchIn(question) ||
            complexWords.containsMatchIn(question)
        ) return null
        val diet = when {
            veganWord.containsMatchIn(question) -> "vegan"
            vegetarianWord.containsMatchIn(question) -> "vegetarian"
            else -> return null
        }
        val listings = evidence.mapNotNull { item ->
            if (!item.documentId.startsWith("wv-eat-")) return@mapNotNull null
            val destination = field(item.text, "Destination")?.substringBefore('/') ?: return@mapNotNull null
            if (!Regex("(?i)\\b(?:in|near|around)\\s+${Regex.escape(destination)}\\b")
                    .containsMatchIn(question)) return@mapNotNull null
            val name = field(item.text, "Place to eat")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val dietMatch = if (diet == "vegan") veganWord.containsMatchIn(item.text)
                else veganWord.containsMatchIn(item.text) || vegetarianWord.containsMatchIn(item.text)
            if (!dietMatch) return@mapNotNull null
            Listing(item, name, destination, field(item.text, "Address") ?: field(item.text, "Directions"),
                field(item.text, "Listing last checked"))
        }.distinctBy { it.destination.lowercase() to it.name.lowercase() }.take(4)
        if (listings.isEmpty()) return null

        val city = listings.first().destination
        val answer = buildString {
            append("I can't verify a current “best” ranking offline. These ")
            append(diet)
            append(" places are listed in ")
            append(city)
            append(" in the local Wikivoyage pack:\n\n")
            listings.forEachIndexed { index, listing ->
                append("- **")
                append(escapeMarkdown(listing.name))
                append("**")
                listing.location?.takeIf(String::isNotBlank)?.let { location ->
                    append(" — ")
                    append(escapeMarkdown(location.take(100)))
                }
                append(". ")
                append(listing.checked?.let { "Listing last checked: $it." } ?: "Listing check date not recorded.")
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
        val location: String?,
        val checked: String?,
    )

    private fun field(text: String, key: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith("$key: ") }
        ?.removePrefix("$key: ")
        ?.trim()

    private fun escapeMarkdown(value: String): String = value.replace(Regex("[\\\\*_`\\[\\]]")) { match ->
        "\\${match.value}"
    }
}
