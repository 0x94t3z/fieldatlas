package xyz.fieldatlas.research

/**
 * A question as its routing rules expect it spelled. People type "whats the difference" and
 * "hows"; the rules that recognise an explanation, a comparison or a lookup are written for
 * "what's" and "how's". Only routing reads the normalised text; the prompt keeps the question
 * as it was asked.
 */
object QuestionText {
    private val spellings = mapOf(
        "whats" to "what's", "hows" to "how's", "wheres" to "where's", "whos" to "who's", "whens" to "when's",
        "im" to "I'm", "ive" to "I've", "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
        "isnt" to "isn't", "arent" to "aren't", "cant" to "can't", "wont" to "won't", "shouldnt" to "shouldn't",
    )
    private val casual = Regex("(?i)\\b(" + spellings.keys.joinToString("|") + ")\\b")

    /** [question] with casual spellings written as their contractions; the case of the first letter is kept. */
    fun normalised(question: String): String = casual.replace(question) { match ->
        val word = match.value
        val spelled = spellings.getValue(word.lowercase())
        when {
            // "WHATS" stays shouted; "Whats" keeps its capital; "im" becomes "I'm" either way.
            word.length > 1 && word.all(Char::isUpperCase) -> spelled.uppercase()
            word.first().isUpperCase() -> spelled.replaceFirstChar(Char::uppercaseChar)
            else -> spelled
        }
    }
}
