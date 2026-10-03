package xyz.fieldatlas.research

object PromptBuilder {
    private const val CHARS_PER_TOKEN = 4
    private const val EVIDENCE_PERCENT = 65
    private const val MIN_CONTEXT_TOKENS = 128
    private const val MAX_CONTEXT_TOKENS = 32_768
    private const val MAX_EXCERPT_CHARS = 700
    private const val POLICY = """/no_think
You are an offline research assistant. Use only the numbered evidence below. Do not use external knowledge. Give a concise answer, compare relevant claims, preserve conflicts and uncertainty, and cite factual claims with the exact source number, such as [S1] or [S2], at the end of each claim. Never output the placeholder [S#] and never cite a number absent from the evidence. Where evidence gives explicit numbers or direct comparisons, prefer them over relative statements. Apply every condition and exception before a decision; an exception or hold decides over the general rule. Keep numbers and negative statements exactly as written; write a converted time beside the original, as in 21:30 (9:30 PM). If the evidence does not contain the answer, say that in one sentence and stop."""
    private const val MIXED_POLICY = """/no_think
Act as a careful offline research assistant. Answer the question directly, under 150 words unless more detail is requested. Use short paragraphs or bullets for comparisons, periods, or steps. Use relevant excerpts and your knowledge to explain the topic, without citations. Distinguish background knowledge from uncertain interpretation. Preserve relevant conflicts and limitations; never force unrelated findings into an explanation just because they mention the same country or word. A museum description or one narrow study is not a general overview of its topic. Do not guess current or private facts. If the saved excerpts cover only part of the question, state that limitation briefly; do not imply they support the whole explanation.
If an excerpt directly helps answer the question, optionally append "From saved sources" followed by useful exact quotations, one per line: "quotation" [S1]. Use only the matching source number. Keep qualifications and conflicts. Do not cite paraphrases, titles, or index keywords. Ignore unrelated studies. Do not repeat these instructions."""
    private const val VENUE_POLICY = """
For local places, name only places in the evidence. A dietary match must be stated in the evidence, not guessed from cuisine. If asked for the best places but the evidence has no comparative ranking, present them as unranked listings, not verified best choices. Say what date the listing or source shows; do not claim current opening hours, reviews, availability, or a 'best' ranking unless the evidence supports it."""
    private const val MODEL_ONLY_POLICY = """/no_think
Answer from the model's offline knowledge only. Do not invent citations. Answer the question directly, under 150 words unless more detail is requested. Use short paragraphs or bullets for comparisons, periods, or steps. Distinguish background knowledge from uncertain interpretation and state relevant limitations. Do not force unrelated facts into the answer. Do not add a generic disclaimer about missing packs or current data. No matching local evidence is available: acknowledge this when the question requires current, private, location-specific, or source-backed facts, and do not guess them."""
    private const val MODEL_ONLY_VENUE_POLICY = """
For a request seeking specific current local places, explain that you cannot verify or recommend them without an installed relevant local travel pack. Do not invent place names."""
    private val VENUE_QUESTION = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat|dining|hotels?|hostels?|museums?|attractions?|sights?|bars?|shops?|stores?)\\b")
    private val LOCAL_PLACE_REQUEST = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where|near|around|in)\\b")
    private val SOURCE_ONLY_REQUEST = Regex("(?i)\\b(summari[sz]e|according to|(?:saved|local|provided|attached|these|this|that|my|our) (?:sources?|documents?|files?|notes?|stud(?:y|ies)|papers?|reports?))\\b")

    fun build(question: String, evidence: List<Evidence>, contextTokenBudget: Int): PackedPrompt {
        require(question.isNotBlank()) { "question must not be blank" }
        require(contextTokenBudget in MIN_CONTEXT_TOKENS..MAX_CONTEXT_TOKENS) {
            "contextTokenBudget must be between $MIN_CONTEXT_TOKENS and $MAX_CONTEXT_TOKENS"
        }
        val scholarly = evidence.isNotEmpty() && evidence.any {
            it.text.contains(Regex("(?im)^\\s*MeSH:"))
        }
        val sourceLimit = if (scholarly) 4 else evidence.size
        val evidenceBudget = (contextTokenBudget.toLong() * CHARS_PER_TOKEN * EVIDENCE_PERCENT / 100)
            .let { if (scholarly) minOf(it, 3_000L) else it }
        var remaining = evidenceBudget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val sources = mutableListOf<PromptSource>()
        val blocks = mutableListOf<String>()
        evidence.forEachIndexed { _, item ->
            if (remaining <= 0 || sources.size >= sourceLimit) return@forEachIndexed
            val citationId = "S${sources.size + 1}"
            // Full metadata and passage remain in PromptSource for the source viewer.
            val prefix = "[$citationId]\nTitle: ${compactMetadata(item.title)}\nSource: ${compactMetadata(item.source)}\nExcerpt: "
            val passage = EvidenceRelevance.passageText(item.text)
            // Bounded reference chunks can contain qualifications in independent
            // paragraphs. Preserve them whole instead of selecting by word overlap.
            // The shared evidence budget below still applies; never truncate to fit.
            val text = if (EvidenceRelevance.isRequestedOverview(item, question) && passage.length <= 2_400) {
                passage
            } else relevantExcerpt(question, passage)
            if (text.isBlank()) return@forEachIndexed
            val block = prefix + text
            // Never split a sentence or separate a claim from its adjacent qualification
            // to meet a quota. Omit an oversized unit rather than distort its meaning.
            if (block.length > remaining) return@forEachIndexed
            blocks += block
            sources += PromptSource(citationId, item, excerpt = text)
            remaining -= block.length
        }
        val prompt = buildString {
            append(if (allowsModelExplanation(question)) MIXED_POLICY else POLICY)
            append("\nEvidence contains selected excerpts, not complete documents. Do not infer that omitted information is absent from the full source.")
            if (sources.size < evidence.size) append(" Some retrieved passages could not fit; do not claim this is a complete synthesis of all retrieved evidence.")
            if (VENUE_QUESTION.containsMatchIn(question)) append(VENUE_POLICY)
            append("\n\nEVIDENCE:\n")
            append(blocks.joinToString("\n\n"))
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, sources, mixedAnswer = allowsModelExplanation(question))
    }

    fun buildModelOnly(question: String): PackedPrompt {
        require(question.isNotBlank()) { "question must not be blank" }
        val prompt = buildString {
            append(MODEL_ONLY_POLICY)
            if (VENUE_QUESTION.containsMatchIn(question) &&
                LOCAL_PLACE_REQUEST.containsMatchIn(question)) append(MODEL_ONLY_VENUE_POLICY)
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, emptyList(), mixedAnswer = allowsModelExplanation(question))
    }

    internal fun allowsModelExplanation(question: String): Boolean =
        !VENUE_QUESTION.containsMatchIn(question) && !SOURCE_ONLY_REQUEST.containsMatchIn(question)

    private fun takeWholeCodePoints(value: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (value.length <= maxChars) return value
        var end = maxChars
        if (end > 0 && value[end - 1].isHighSurrogate()) end--
        return value.substring(0, end)
    }

    private fun compactMetadata(value: String): String =
        if (value.length <= 96) value else takeWholeCodePoints(value, 95) + "…"

    private fun relevantExcerpt(question: String, text: String): String {
        if (text.length <= MAX_EXCERPT_CHARS) return text
        // A sentence window can drop the definition or qualification that makes a
        // matching sentence meaningful. Prefer a complete bounded paragraph when
        // the source supplies paragraph boundaries; retain adjacent caveats too.
        val terms = FtsQuery.from(question)?.terms.orEmpty()
        fun hits(value: String): Int = EvidenceRelevance.termHits(value, terms)
        val paragraphs = text.split(Regex("\\n\\s*\\n")).filter(String::isNotBlank)
        val paragraphAnchor = paragraphs.indices.maxByOrNull { hits(paragraphs[it]) } ?: return ""
        if (paragraphs[paragraphAnchor].length <= 1_200) {
            val continuation = Regex("(?i)^\\s*(however|but|nevertheless|nonetheless|although|in contrast|this|these|those|it|they|such)\\b")
            var start = paragraphAnchor
            var end = paragraphAnchor
            // Anaphoric paragraphs depend on the preceding paragraph. A following
            // limitation can qualify the selected claim across a blank line.
            while (start > 0 && continuation.containsMatchIn(paragraphs[start])) start--
            while (end < paragraphs.lastIndex && continuation.containsMatchIn(paragraphs[end + 1])) end++
            val excerpt = paragraphs.subList(start, end + 1).joinToString("\n\n")
            // Keep the connected unit whole. The caller may omit it if it cannot
            // fit, rather than silently removing the qualification to save space.
            return (if (start > 0) "… " else "") + excerpt +
                (if (end < paragraphs.lastIndex) " …" else "")
        }
        val sentences = text.replace(Regex("[\\r\\n]+"), " ")
            .split(Regex("(?<=[.!?。！？])\\s+"))
            .filter(String::isNotBlank)
        if (sentences.isEmpty()) return ""
        val anchor = sentences.indices.maxByOrNull { hits(sentences[it]) } ?: 0
        // The anchor and immediate context are an indivisible unit. The target length
        // is soft: keeping a qualification matters more than hitting a character cap.
        val start = (anchor - 1).coerceAtLeast(0)
        val end = (anchor + 1).coerceAtMost(sentences.lastIndex)
        val excerpt = sentences.subList(start, end + 1).joinToString(" ")
        return (if (start > 0) "… " else "") + excerpt +
            (if (end < sentences.lastIndex) " …" else "")
    }
}
