package xyz.fieldatlas.research

/**
 * A question asked under an answer builds on it ("which one is closest to the castle?" after
 * "best vegan food in Lisbon"). Research runs on one standalone question, so the follow-up is
 * read together with the question before it. The screen shows that reading before searching,
 * and the person can correct it. The model is also shown the conversation so far, so "there"
 * and "he" mean what they meant to the person.
 */
object FollowUp {
    /** One answered question of a topic: what was typed, what was searched, what was shown. */
    data class Turn(val asked: String, val readAs: String, val answer: String)

    /** Past this length a chain of follow-ups starts again from the first question of the topic. */
    private const val MAX_CONTEXT = 200

    // "What about South Korea?", "how about in winter?", "and for kids?": the same question about something else.
    // A bare "and cheap ones?" may narrow the answer rather than move it, so it is not a swap.
    private val swap = Regex("(?i)^\\s*(?:(?:and|but|ok|okay|so)\\s*,?\\s+)?(?:(?:what|how)\\s+about\\s+(.+?)|and\\s+((?:in|at|for|on|near|around|during)\\s+.+?))\\s*\\??\\s*$")
    private val clause = Regex("(?i)^(?:what|how|why|when|where|which|who|whose|is|are|was|were|do|does|did|can|could|should|would|will|if|it|its|it's|that|this|they|there)\\b")
    private const val MAX_SUBJECT_WORDS = 5
    private val leadingPreposition = Regex("(?i)^(in|at|for|on|to|from|near|around|during)\\s+")
    // The last "in Japan", "for kids", "during winter" of the earlier question, which the new subject replaces.
    private val trailingPhrase = Regex("(?i)^(.*\\b(in|at|for|on|to|from|near|around|during)\\s+)([^,;:?!]+?)(\\s*[?.!]?\\s*)$")

    // "What is the capital of Japan?", "which planet is closest to the sun", "who wrote War and Peace": the answer
    // is a name the question does not hold, and "how many people live there?" asks about that name.
    private val asksForName = Regex("(?i)^\\s*(?:(?:what|which)(?:'s|’s|s)?\\s+(?:(?:is|was|are|were)\\s+)?(?:the\\s+)?" +
        "(?:capital|currency|tallest|highest|largest|biggest|longest|deepest|oldest|smallest|fastest|closest|nearest|hottest|coldest|first)\\b|" +
        "(?:what|which)\\s+(?:country|countries|city|cities|planet|bird|animal|river|mountain|island|lake|state|composer|painter|writer|author)\\b|" +
        "who\\s+(?:wrote|invented|discovered|painted|composed|built|designed|founded|created|directed|was\\s+the\\s+first|were\\s+the\\s+first)\\b|" +
        "(?:the\\s+)?(?:tallest|highest|largest|biggest|longest|deepest|oldest|smallest|fastest|closest|most\\s+\\w+)\\s+[\\w-]+\\s+(?:of|in|on)\\b)")
    // He, she, it, there: the follow-up points at something said before rather than naming it.
    private val pointsBack = Regex("(?i)\\b(?:it|its|it's|they|them|their|there|he|him|his|she|her|that|those|these|this)\\b")
    private const val SHORT_FOLLOW_UP = 4
    // A run of capitalised words, joined by the small words names keep inside them ("Statue of Liberty").
    private val name = Regex("\\p{Lu}[\\p{L}'’-]*(?:\\s+(?:(?:of|de|del|da|la|le|the|and|von|van)\\s+)?\\p{Lu}[\\p{L}'’-]*)*")
    // The name is given as the answer: "is Tokyo", "has been Wellington", "by Antonio Vivaldi", "by Italian composer Antonio Vivaldi".
    private val beforeName = Regex("(?i)(?:\\b(?:is|was|are|were|has been|have been|had been|became|remains|named|called)|\\bby(?:\\s+[\\p{Ll}-]+){0,2})\\s+$")
    private val afterName = Regex("(?i)^\\s*(?:is|was|are|were|has|have|had|wrote|composed|invented|painted|built|discovered|holds?|became|remains)\\b")
    // What may follow a name that is given as the answer; "Australian commissioners" names nothing.
    private val afterNameAllowed = Regex("^\\s*(?:[,.;:!?)\\]]|$|\\[|(?:which|who|whose|that|in|on|at|of|and|or|with|from|for|as|to|the|a|an|after|since|during|until|located|also|is|was|are|were|has|have|had|but|because|where|when)\\b)")
    private val notNames = setOf("the", "a", "an", "based", "according", "in", "it", "this", "that", "these", "those", "there", "yes", "no",
        "however", "while", "although", "as", "on", "at", "for", "from", "to", "with", "if", "when", "some", "many", "most", "today",
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    // The answer said it did not know; a name in it is mentioned, not given.
    private val refusal = Regex("(?i)\\b(?:do(?:es)? not|don't|doesn't|cannot|can't|unable|no (?:information|evidence|answer|source)|not (?:specify|specified|state|stated|mention|mentioned|contain|identify|identified|found|available|determined)|unclear|unknown)\\b")
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val sayLittle = setOf("roughly", "many", "much", "really", "exactly", "kind", "true", "still", "ever", "right", "now",
        "there", "here", "actually", "about", "like", "long", "big", "far", "old", "hot", "cold", "high", "tall", "deep", "fast", "one",
        "ones", "them", "they", "he", "she", "it", "its", "his", "her", "any", "some", "more", "most", "very", "good", "best", "worth")
    private val citation = Regex("\\[(?:S?\\d+(?:[,–-]\\s*S?\\d+)*)]")

    /**
     * The name an answer gives for what [asked] asked for: "Tokyo" from "The capital of Japan is Tokyo, the biggest
     * city in the country". Only the answer's own first sentence counts, never a quoted excerpt, and only a name it
     * states as the answer ("is Tokyo", "Mercury is", "by Antonio Vivaldi"); an answer that says it does not know
     * names nothing.
     */
    internal fun answerName(asked: String, answer: String): String? {
        if (!asksForName.containsMatchIn(asked)) return null
        // A model's unverified guess is not a fact to search by: "Jupiter" for the planet nearest the Sun would send
        // every follow-up the wrong way. Only an answer checked against the saved passages names the subject.
        if (answer.contains(AnswerText.MODEL_LABEL)) return null
        val sentence = sentences(prose(answer)).firstOrNull() ?: return null
        if (refusal.containsMatchIn(sentence)) return null
        val askedWords = word.findAll(asked.lowercase()).map { it.value }.toSet()
        val stated = name.findAll(sentence).mapNotNull { match ->
            val words = match.value.split(' ').dropWhile { it.lowercase() in notNames }
            if (words.isEmpty() || words.all { it.lowercase() in askedWords || it.lowercase() in notNames }) return@mapNotNull null
            val after = sentence.substring(match.range.last + 1)
            val given = (match.range.first == 0 || beforeName.containsMatchIn(sentence.substring(0, match.range.first)) ||
                afterName.containsMatchIn(after)) && afterNameAllowed.containsMatchIn(after)
            if (given) words else null
        }.toList()
        // "by Italian composer Antonio Vivaldi": a full name says more than the word describing it.
        return (stated.firstOrNull { it.size > 1 } ?: stated.firstOrNull())?.joinToString(" ")
    }

    /**
     * The standalone question research runs on. [previous] is the question the shown answer
     * answered, as it was searched; [root] is the first question of the topic, used when the
     * chain of follow-ups has grown too long to carry. [answer] is the answer shown: when it named
     * what the question asked for, a follow-up pointing back is about that name.
     */
    fun readAs(previous: String, followUp: String, root: String = previous, answer: String = ""): String {
        val asked = followUp.trim().replace(Regex("\\s+"), " ")
        if (asked.isEmpty()) return previous.trim()
        val context = when {
            previous.trim().length > MAX_CONTEXT -> root
            // A chain keeps what it is about, not every follow-up before: after "tell me about fiji: which island
            // should we stay on", "do we need a visa?" asks about Fiji, not about islands.
            previous.trim() != root.trim() && ": " in previous -> previous.substringBefore(": ")
            else -> previous
        }.trim()
        if (pointsBack.containsMatchIn(asked) || asked.split(' ').size <= SHORT_FOLLOW_UP) {
            answerName(previous, answer)?.let { named ->
                // "roughly how many" or "how hot is it there" says little on its own: the earlier question comes
                // along, so the search still knows it is about lakes, or about the planet rather than the metal.
                val content = FtsQuery.from(asked)?.terms.orEmpty().filterNot { it in sayLittle }
                return if (content.size >= 2 || ' ' in named) "$named: $asked" else "$named (${previous.trimEnd('?', '.', '!', ' ')}): $asked"
            }
        }
        swap.matchEntire(asked)?.let { match ->
            val subject = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
            // A new subject, not a new question: "and how much does it cost?" asks something else.
            if (clause.containsMatchIn(subject) || subject.split(' ').size > MAX_SUBJECT_WORDS) return@let
            val preposition = leadingPreposition.find(subject)?.groupValues?.get(1)?.lowercase()
            val bare = subject.replaceFirst(leadingPreposition, "")
            trailingPhrase.matchEntire(context)?.let { earlier ->
                // Keep the earlier preposition unless the follow-up names its own ("and in winter?").
                val head = if (preposition == null) earlier.groupValues[1]
                else earlier.groupValues[1].removeSuffix(earlier.groupValues[2] + " ").trimEnd() + " $preposition "
                return head + bare + "?"
            }
        }
        return context.trimEnd('?', '.', '!', ' ') + ": " + asked
    }

    /**
     * Whether [question] opens with a name an earlier answer gave ("Tokyo: how many people live there") rather than
     * with an earlier question. The passages behind the earlier answer are then about something else (Japan), and
     * the named subject's own article should come first.
     */
    fun namesSubject(question: String, earlier: List<Turn>): Boolean = namedSubject(question, earlier) != null

    /** The name a reading opens with when it is one an earlier answer gave: "Tokyo" for "Tokyo: how many people live there". */
    fun namedSubject(question: String, earlier: List<Turn>): String? {
        val head = question.substringBefore(": ", "").replace(Regex("\\s*\\([^)]*\\)$"), "").trim()
        if (head.isEmpty() || earlier.isEmpty()) return null
        return head.takeIf { earlier.none { it.readAs.startsWith(head, ignoreCase = true) || it.asked.startsWith(head, ignoreCase = true) } }
    }

    /** How many earlier turns the model is shown; the first question of the topic is always among them. */
    private const val SHOWN_TURNS = 2
    private const val SUMMARY_CHARS = 320
    const val NOTHING_FOUND = "(nothing was found)"

    /**
     * The conversation so far, for the model: each earlier question as typed with the gist of its answer,
     * so a follow-up's "there", "he" or "it" is read as the person meant it. Earlier answers are given in
     * one or two sentences of their own prose; quoted excerpts, labels and citation marks are left out.
     */
    fun conversation(earlier: List<Turn>): String {
        if (earlier.isEmpty()) return ""
        val shown = if (earlier.size <= SHOWN_TURNS) earlier else listOf(earlier.first()) + earlier.takeLast(SHOWN_TURNS - 1)
        return "EARLIER IN THIS CONVERSATION:\n" + shown.joinToString("\n") { turn ->
            "Q: " + turn.asked.trim() + "\nA: " + summary(turn.answer)
        }
    }

    /** The gist of an answer: its first two sentences of prose, or a note that it found nothing. */
    internal fun summary(answer: String): String {
        val text = sentences(prose(answer)).take(2).joinToString(" ").trim()
        if (text.isEmpty()) return NOTHING_FOUND
        val first = sentences(text).first()
        if (refusal.containsMatchIn(first) && Regex("(?i)\\b(?:excerpts?|sources?|evidence|passages?|information|text)\\b").containsMatchIn(first)) return NOTHING_FOUND
        return if (text.length <= SUMMARY_CHARS) text else text.take(SUMMARY_CHARS).substringBeforeLast(' ').trimEnd(',', ';', ':') + "…"
    }

    /** An answer's own prose: no headings, labels, quoted excerpts, notes in italics or citation marks. */
    private fun prose(answer: String): String =
        answer.substringBefore("## Saved excerpts").lines().map(String::trim)
            .filterNot { it.isEmpty() || it.startsWith("#") || it.startsWith(">") || it.startsWith("_") || it.startsWith("**") || it.startsWith("- ") || it.startsWith("* ") }
            .joinToString(" ").replace(citation, " ").replace(Regex("\\s+([.,;:])"), "$1").replace(Regex("[,;:]\\s*\\."), ".")
            .replace(Regex("\\s+"), " ").trim()

    private fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+(?=[\\p{Lu}\\p{N}\"“(])")).map(String::trim).filter { it.isNotBlank() }
}
