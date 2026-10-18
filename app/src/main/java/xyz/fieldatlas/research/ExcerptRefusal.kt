package xyz.fieldatlas.research

/**
 * A labelled model explanation that only says the excerpts do not cover the question ("No excerpt supports
 * information about swallowed batteries…", "The provided excerpts do not confirm that muscle loss occurs with
 * age…"). The mixed prompt keeps the model to the excerpts, so when retrieval found nothing about the question
 * the reply is a refusal, followed by unrelated saved excerpts: 7 of 32 turns of set 33 (19 October 2026).
 *
 * Only the opening sentence decides, plus a short reply that ends by declining. A reply that answers first and
 * then names what the excerpts leave out ("…, but they do not give the opening hours") is an answer.
 */
internal object ExcerptRefusal {
    private const val EXCERPTS = "(?:evidence|excerpts?|sources?|passages?|text|context|documents?|information)"
    private const val QUALIFIERS = "(?:(?:the|these|this|those|provided|selected|given|saved|supplied|retrieved|available|above|cited)\\s+){0,3}"

    private val opening = Regex(
        "(?i)^(?:(?:unfortunately|however|sadly),?\\s+)?(?:" +
            // "The provided excerpts do not confirm …", "These sources, which cover X, do not mention …".
            "$QUALIFIERS$EXCERPTS\\b[^.!?]{0,80}?\\b(?:do|does|did)\\s*(?:not|n't)\\b|" +
            // "No excerpt supports …", "No provided evidence covers …".
            "no\\s+$QUALIFIERS$EXCERPTS\\b|" +
            // "None of the excerpts mention …".
            "none\\s+of\\s+$QUALIFIERS$EXCERPTS\\b|" +
            // "There is no information in the excerpts about …".
            "there\\s+(?:is|are)\\s+no\\b[^.!?]{0,60}\\b$EXCERPTS\\b)",
    )

    private val declines = Regex(
        "(?i)\\b(?:cannot|can't|can not|unable to)\\s+(?:be\\s+)?answer(?:ed)?\\b[^.!?]{0,60}?\\b" +
            "(?:evidence|excerpts?|sources?|passages?|text|supplied|provided|given)\\b",
    )

    /** Replies longer than this that decline somewhere also answer something; only short ones are refusals. */
    private const val SHORT_REPLY = 700

    fun opensWith(reply: String): Boolean {
        val body = reply.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString(" ")
            .trim()
        if (body.isEmpty()) return false
        val first = Regex("^.*?[.!?](?=\\s|$)").find(body)?.value ?: body
        return opening.containsMatchIn(first) || body.length < SHORT_REPLY && declines.containsMatchIn(body)
    }
}
