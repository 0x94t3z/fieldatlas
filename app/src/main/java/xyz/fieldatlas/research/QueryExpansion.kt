package xyz.fieldatlas.research

import java.util.Locale

/**
 * Turns a question into a bag of retrieval keywords using the already-loaded on-device model.
 *
 * FTS5 matching is lexical: "heart attack" will not meet a chunk that says "myocardial
 * infarction". The model can bridge that gap — it is loaded anyway, so a short constrained
 * generation costs a few seconds and turns semantic misses into keyword hits. The expansion is
 * deliberately advisory: terms are merged with the question's own terms and the AND-relaxation
 * in [FtsRetriever] decides what actually matters, and any failure here falls back to the raw
 * question.
 */
internal object QueryExpansion {
    /** Hard token cap for the keyword turn — it must never eat into the answer budget. */
    const val GENERATION_BUDGET = 96
    const val MAX_TERMS = 6
    private const val MAX_TERM_CHARS = 48

    private val LEADING_NOISE = Regex("^[\\d.+)\\-*•\"'’‘`\\s]+")
    // These generic planner additions match almost every broad corpus and can displace the
    // entities the user actually asked about. They are filtered only from model-generated
    // expansion; the user's original terms are merged separately and always remain eligible.
    private val PLANNER_NOISE = setOf(
        "answer",
        "analysis",
        "information",
        "method",
        "overview",
        "process",
        "procedure",
        "question",
        "quality",
        "technique",
        "topic",
    )

    /**
     * Persona for the keyword turn only. The research system prompt forbids external knowledge,
     * but choosing search synonyms *is* a knowledge task — models (especially literal ones) were
     * refusing to generate keywords under it. This persona grants word knowledge while keeping
     * the answer itself off-limits; the gateway swaps personas via resetConversation, so the
     * answer turn still runs under the strict research prompt.
     */
    const val SYSTEM_PROMPT =
        "You are a search planner for an offline document retrieval system. You may use general " +
        "knowledge of words, synonyms and likely document vocabulary, but never answer a question " +
        "and never explain anything. Your only output is the requested keyword line."
    private val TRAILING_NOISE = Regex("[.,;:!?)\"'\\s]+$")

    fun prompt(question: String): String = "/no_think\n" +
        "You are the search planner for an offline document search engine. Do not answer.\n" +
        "Question: ${question.trim()}\n" +
        "Write search keywords: use only the essential content words of the question, dropping " +
        "words like 'tell', 'me', 'about'; then close synonyms and terms that documents " +
        "answering it would actually use, in any morphological form; then, if you know from " +
        "general knowledge which named articles, species, records or lists documents would cite, " +
        "add their exact names as keywords too (for a tallest-animal question add the names of the " +
        "candidate species). Return 4 to $MAX_TERMS specific terms. Never pad the list with generic " +
        "words such as process, method, analysis, quality, information, overview, or technique.\n" +
        "Reply with exactly one comma-separated line. No sentences, no numbering, no quotes."

    /**
     * Parses the model's keyword line into clean lowercase terms: splits on commas/semicolons/
     * newlines, strips numbering, bullets and stray quotes, enforces length limits, and caps at
     * [MAX_TERMS]. Garbage in, bounded keyword set out — the caller merges the result into the
     * FTS query where relaxation keeps noise from dominating.
     */
    fun parse(raw: String): List<String> {
        // Reasoning models sometimes emit their raw think-tag open/close control markers even
        // in no-thinking turns; neither the tags nor the hidden reasoning may become keywords.
        val cleaned = AnswerText.visible(raw)
            .replace(Regex("</?think>?", RegexOption.IGNORE_CASE), " ")
        val split = cleaned
            .split(',', ';', '\n')
            .asSequence()
            .map { piece -> piece.trim().replace(LEADING_NOISE, "").replace(TRAILING_NOISE, "") }
            .map { term -> term.lowercase(Locale.ROOT) }
            .filter { term ->
                term.isNotBlank() &&
                    term !in PLANNER_NOISE &&
                    term.codePointCount(0, term.length) <= MAX_TERM_CHARS &&
                    term.count { it == ' ' } <= 2
            }
            .distinct()
            .take(MAX_TERMS)
            .toList()
        if (split.isNotEmpty()) return split
        // Models that ignore the comma instruction (e.g. replying "tiger lion weight") would
        // otherwise be discarded wholesale by the phrase-length filter; fall back to their
        // individual words, deduplicated, which is exactly what a keyword list is anyway.
        return cleaned.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter {
                it !in PLANNER_NOISE &&
                    it.codePointCount(0, it.length) in 2..MAX_TERM_CHARS
            }
            .distinct()
            .take(MAX_TERMS)
    }
}
