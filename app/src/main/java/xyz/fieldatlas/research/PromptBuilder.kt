package xyz.fieldatlas.research

object PromptBuilder {
    private const val CHARS_PER_TOKEN = 4
    private const val EVIDENCE_PERCENT = 65
    private const val MIN_CONTEXT_TOKENS = 128
    private const val MAX_CONTEXT_TOKENS = 32_768
    private const val POLICY = """/no_think
You are an offline research assistant. Use only the numbered evidence below. Do not use external knowledge. Give a concise answer, compare relevant claims, preserve conflicts and uncertainty, and cite factual claims with the exact source number, such as [S1] or [S2], at the end of each claim. Never output the placeholder [S#] and never cite a number absent from the evidence. Where evidence gives explicit numbers or direct comparisons, prefer them over relative statements. If the evidence does not contain the answer, say that in one sentence and stop."""
    private const val VENUE_POLICY = """
For local places, name only places in the evidence. A dietary match must be stated in the evidence, not guessed from cuisine. If asked for the best places but the evidence has no comparative ranking, present them as unranked listings, not verified best choices. Say what date the listing or source shows; do not claim current opening hours, reviews, availability, or a 'best' ranking unless the evidence supports it."""
    private const val MODEL_ONLY_POLICY = """/no_think
You are an offline assistant running entirely on this phone. No matching local sources were found. Answer from the model's offline knowledge only. Do not invent citations. For a stable conceptual question, give the direct explanation in one or two short paragraphs, ideally under 150 words. Do not add a generic disclaimer about missing packs or current data. Mention missing local evidence only when the question requires current, private, location-specific, or source-backed facts. State meaningful uncertainty without repeating yourself."""
    private const val MODEL_ONLY_VENUE_POLICY = """
For a request seeking specific current local places, explain that you cannot verify or recommend them without an installed relevant local travel pack. Do not invent place names."""
    private val VENUE_QUESTION = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat|dining|hotels?|hostels?|museums?|attractions?|sights?)\\b")
    private val LOCAL_PLACE_REQUEST = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where|near|around|in)\\b")

    fun build(question: String, evidence: List<Evidence>, contextTokenBudget: Int): PackedPrompt {
        require(question.isNotBlank()) { "question must not be blank" }
        require(contextTokenBudget in MIN_CONTEXT_TOKENS..MAX_CONTEXT_TOKENS) {
            "contextTokenBudget must be between $MIN_CONTEXT_TOKENS and $MAX_CONTEXT_TOKENS"
        }
        val evidenceBudget = contextTokenBudget.toLong() * CHARS_PER_TOKEN * EVIDENCE_PERCENT / 100
        var remaining = evidenceBudget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val sources = mutableListOf<PromptSource>()
        val blocks = mutableListOf<String>()
        evidence.forEach { item ->
            if (remaining <= 0) return@forEach
            val citationId = "S${sources.size + 1}"
            val prefix = "[$citationId]\nTitle: ${item.title}\nSource: ${item.source}\nChunk: ${item.chunkId}\nText: "
            if (prefix.length >= remaining) return@forEach
            val text = takeWholeCodePoints(item.text, remaining - prefix.length)
            if (text.isBlank()) return@forEach
            val block = prefix + text
            blocks += block
            sources += PromptSource(citationId, item)
            remaining -= block.length
        }
        val prompt = buildString {
            append(POLICY)
            if (VENUE_QUESTION.containsMatchIn(question)) append(VENUE_POLICY)
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nEVIDENCE:\n")
            append(blocks.joinToString("\n\n"))
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, sources)
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
        return PackedPrompt(prompt, emptyList())
    }

    private fun takeWholeCodePoints(value: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (value.length <= maxChars) return value
        var end = maxChars
        if (end > 0 && value[end - 1].isHighSurrogate()) end--
        return value.substring(0, end)
    }
}
