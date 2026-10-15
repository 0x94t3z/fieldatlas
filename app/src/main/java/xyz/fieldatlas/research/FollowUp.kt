package xyz.fieldatlas.research

/**
 * A question asked under an answer builds on it ("which one is closest to the castle?" after
 * "best vegan food in Lisbon"). Research runs on one standalone question, so the follow-up is
 * read together with the question before it. The screen shows that reading before searching,
 * and the person can correct it.
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

    /**
     * The standalone question research runs on. [previous] is the question the shown answer
     * answered, as it was searched; [root] is the first question of the topic, used when the
     * chain of follow-ups has grown too long to carry.
     */
    fun readAs(previous: String, followUp: String, root: String = previous): String {
        val asked = followUp.trim().replace(Regex("\\s+"), " ")
        if (asked.isEmpty()) return previous.trim()
        val context = (if (previous.trim().length <= MAX_CONTEXT) previous else root).trim()
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
}
