package xyz.fieldatlas.research

/**
 * A question as its routing rules expect it spelled. People type "whats the difference" and
 * "hows"; the rules that recognise an explanation, a comparison or a lookup are written for
 * "what's" and "how's". Only routing reads the normalised text; the prompt keeps the question
 * as it was asked.
 */
object QuestionText {
    /**
     * Text without its accents, for comparing names people type plainly with the names saved with them:
     * "Hạ Long Bay", "Reykjavík" and "Đà Nẵng" read as "Ha Long Bay", "Reykjavik" and "Da Nang". Letters
     * that do not decompose are mapped by hand; other scripts are left alone. Plain ASCII is returned as it is.
     */
    fun foldAccents(text: String): String {
        if (text.all { it.code < 128 }) return text
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) {
            // Only a Latin letter's accents: Japanese voicing marks and other scripts keep theirs.
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt() && out.isNotEmpty() &&
                Character.UnicodeScript.of(out[out.length - 1].code) == Character.UnicodeScript.LATIN) continue
            when (c) {
                'đ' -> out.append('d'); 'Đ' -> out.append('D')
                'ø' -> out.append('o'); 'Ø' -> out.append('O')
                'ł' -> out.append('l'); 'Ł' -> out.append('L')
                'æ' -> out.append("ae"); 'Æ' -> out.append("Ae")
                'œ' -> out.append("oe"); 'Œ' -> out.append("Oe")
                'ß' -> out.append("ss")
                else -> out.append(c)
            }
        }
        return java.text.Normalizer.normalize(out, java.text.Normalizer.Form.NFC)
    }

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
