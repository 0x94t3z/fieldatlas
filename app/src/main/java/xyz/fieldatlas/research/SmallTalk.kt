package xyz.fieldatlas.research

/**
 * Greetings, thanks and "who are you", answered by the app at once. "hello" took 13 s on the 4B (a "Key facts" reply
 * from the model) and 29 s on the 2B (a search that cited three passages) on 1 Nov. Only a message made entirely of
 * such phrases counts: "hi, my friend is choking" or "thanks, and what about burns?" is a question and keeps its route.
 */
object SmallTalk {
    private enum class Kind { Greeting, HowAreYou, Thanks, Goodbye, Identity }

    // Longest phrases first, so "thank you so much" is not read as "thank you" with "so much" left over.
    private val phrases: List<Pair<Regex, Kind>> = listOf(
        "what can you do|what do you do|what are you|who are you|who made you|what is this app|what's this app" to Kind.Identity,
        "how are you doing|how are you|how r u|how are u|how's it going|hows it going|how is it going|what's up|whats up|sup" to Kind.HowAreYou,
        "thank you so much|thank you very much|thanks a lot|thanks so much|many thanks|much appreciated|thank you|thank u|thanks|thx|ty|cheers" to Kind.Thanks,
        "good morning|good afternoon|good evening|hello there|hi there|hey there|greetings|hello|hiya|howdy|hey|hi|yo" to Kind.Greeting,
        "see you later|see you|see ya|good night|goodnight|goodbye|bye bye|bye" to Kind.Goodbye,
    ).flatMap { (alternatives, kind) -> alternatives.split('|').map { Regex("^${Regex.escape(it)}(?:\\s+|$)") to kind } }
        .sortedByDescending { it.first.pattern.length }

    /** Words that may come with a greeting or thanks without making it a question: "hi field atlas", "ok thanks again". */
    private val filler = Regex("^(?:field\\s+atlas|atlas|again|there|friend|everyone|all|so|very|much|a\\s+lot|and|ok|okay)(?:\\s+|$)")

    const val GREETING = "Hello. Ask a question and I'll answer from the collections saved on this phone; no signal is needed. " +
        "In an emergency, describe what is happening and I'll open the matching first-aid guide."
    const val HOW_ARE_YOU = "Ready when you are. Ask a question and I'll answer from the collections saved on this phone."
    const val THANKS = "You're welcome. Ask another question any time."
    const val GOODBYE = "Goodbye. Field Atlas works without signal whenever you need it."
    const val IDENTITY = "I'm Field Atlas. I answer questions from the knowledge collections saved on this phone, without an " +
        "internet connection, and show the passages I used. I can also open first-aid guides, give emergency numbers, " +
        "find places near you in a saved map, and work out calculations."

    /** The app's reply when [question] is only small talk, or null when it asks for anything else. */
    fun reply(question: String): String? {
        var rest = question.lowercase()
            .replace('’', '\'')
            .replace(Regex("[^a-z' ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (rest.isEmpty() || rest.length > 60) return null
        val kinds = mutableSetOf<Kind>()
        while (rest.isNotEmpty()) {
            val phrase = phrases.firstOrNull { it.first.containsMatchIn(rest) }
            if (phrase != null) {
                kinds += phrase.second
                rest = phrase.first.replaceFirst(rest, "")
                continue
            }
            val word = filler.find(rest) ?: return null
            rest = rest.substring(word.range.last + 1)
        }
        // Filler alone ("okay", "so much") is not small talk: it needs a greeting, thanks or the like with it.
        return when {
            kinds.isEmpty() -> null
            Kind.Identity in kinds -> IDENTITY
            Kind.Thanks in kinds -> THANKS
            Kind.HowAreYou in kinds -> HOW_ARE_YOU
            Kind.Goodbye in kinds -> GOODBYE
            else -> GREETING
        }
    }
}
