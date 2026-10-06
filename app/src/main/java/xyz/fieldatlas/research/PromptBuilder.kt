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
Act as a careful offline research assistant. Answer the question directly, under 150 words unless more detail is requested. Use short paragraphs or bullets for comparisons, periods, or steps. Base the explanation on the relevant excerpts, without citations; add your own knowledge only for basic, widely known facts, and leave out any detail you are not sure of: a shorter correct answer is better than a longer one with a mistake. Never contradict yourself. An excerpt about another country, city or product applies only if it clearly fits the question; never carry over its phone numbers, laws, prices or rules. Preserve relevant conflicts and limitations; never force unrelated findings into an explanation just because they mention the same country or word. A museum description or one narrow study is not a general overview of its topic. Do not guess current or private facts. If the saved excerpts cover only part of the question, state that limitation briefly; do not imply they support the whole explanation.
If an excerpt directly helps answer the question, optionally append "From saved sources" followed by useful exact quotations, one per line: "quotation" [S1]. Use only the matching source number. Keep qualifications and conflicts. Do not cite paraphrases, titles, or index keywords. Ignore unrelated studies. Do not repeat these instructions."""
    private const val VENUE_POLICY = """
For local places, name only places in the evidence. A dietary match must be stated in the evidence, not guessed from cuisine. If asked for the best places but the evidence has no comparative ranking, present them as unranked listings, not verified best choices. Say what date the listing or source shows; do not claim current opening hours, reviews, availability, or a 'best' ranking unless the evidence supports it."""
    private const val MODEL_ONLY_POLICY = """/no_think
Answer from the model's offline knowledge only. Do not invent citations. Answer the question directly, under 150 words unless more detail is requested. Use short paragraphs or bullets for comparisons, periods, or steps. Distinguish background knowledge from uncertain interpretation and state relevant limitations. Do not force unrelated facts into the answer. Do not add a generic disclaimer about missing packs or current data. No matching local evidence is available: acknowledge this when the question requires current, private, location-specific, or source-backed facts, and do not guess them."""
    private const val MODEL_ONLY_VENUE_POLICY = """
For a request seeking specific current local places, explain that you cannot verify or recommend them without an installed relevant local travel pack. Do not invent place names."""
    private const val GUIDE_POLICY = """/no_think
You are an offline first-aid and safety assistant. The evidence is an official guide. Use only the guide excerpts below; never add treatments, medicines, doses, tools or steps that are not in them. Answer for the situation in the question: first the most urgent actions, as short numbered steps close to the guide's wording, then only the "do not" warnings the guide itself gives. Cite each step with the exact source number, such as [S1]. If the guide says to get emergency help, say that first. If the question asks yes/no or when (for example "do I need rabies shots?", "should we move her?", "when should I worry?"), answer that in one sentence from the guide before the steps; if the guide does not say, say so. If the question's situation is not covered (for example a child, pregnancy, another injury or another country), say what the guide does not cover. No introduction or closing remarks; under 150 words."""
    // The person is offline: answers must be things they can do now, without a connection.
    private const val OFFLINE_PERSON = """
The person asking is offline, with no internet and maybe no phone signal. Never suggest searching online, websites, online reviews, apps or social media; give steps they can do now without a connection. They know they are offline: answer directly and never mention internet access."""
    private const val OFFLINE_PERSON_GUIDE = """
The person asking is offline, with no internet and maybe no phone signal. Never suggest searching online, websites, apps or social media, and never mention internet access."""
    private val VENUE_QUESTION = Regex("(?i)\\b(restaurants?|caf[eé]s?|places? to eat|dining|hotels?|hostels?|museums?|attractions?|sights?|bars?|shops?|stores?)\\b")
    private val LOCAL_PLACE_REQUEST = Regex("(?i)\\b(best|recommend|suggest|find|list|which|where|near|around|in)\\b")
    private val SOURCE_ONLY_REQUEST = Regex("(?i)\\b(summari[sz]e|according to|(?:saved|local|provided|attached|these|this|that|my|our) (?:sources?|documents?|files?|notes?|stud(?:y|ies)|papers?|reports?))\\b")

    /**
     * [base] with the [depth] passages of its subject's article after its own sources, whole and in
     * room of their own: the sources [base] chose keep their numbers and excerpts, and none of them
     * is given up for depth.
     */
    fun withDepth(question: String, base: PackedPrompt, depth: List<Evidence>, contextTokenBudget: Int): PackedPrompt {
        val kept = base.sources.map { it.evidence }
        if (kept.isEmpty()) return base
        val extra = depth.filter { item -> kept.none { it.documentId == item.documentId && it.chunkId == item.chunkId } }
        if (extra.isEmpty()) return base
        // Each source also carries a short header ("[S3]", title, source) besides its text.
        val room = extra.sumOf { it.text.length + 200 } * 100 / (CHARS_PER_TOKEN * EVIDENCE_PERCENT) + 1
        return build(question, kept + extra, minOf(MAX_CONTEXT_TOKENS, contextTokenBudget + room), whole = extra.map { it.chunkId }.toSet())
    }

    fun build(question: String, evidence: List<Evidence>, contextTokenBudget: Int, whole: Set<String> = emptySet()): PackedPrompt {
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
            // Depth passages were already chosen and sized for this question; they go in whole.
            val text = if (item.chunkId in whole || EvidenceRelevance.isRequestedOverview(item, question) && passage.length <= 2_400) {
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
        // The text up to here is the same for every prompt of this kind, so the engine reads it once.
        var fixed = 0
        val prompt = buildString {
            append(if (allowsModelExplanation(question)) MIXED_POLICY else POLICY)
            append(OFFLINE_PERSON)
            append("\nEvidence contains selected excerpts, not complete documents. Do not infer that omitted information is absent from the full source.")
            fixed = length
            if (sources.size < evidence.size) append(" Some retrieved passages could not fit; do not claim this is a complete synthesis of all retrieved evidence.")
            if (VENUE_QUESTION.containsMatchIn(question)) append(VENUE_POLICY)
            append("\n\nEVIDENCE:\n")
            append(blocks.joinToString("\n\n"))
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, sources, mixedAnswer = allowsModelExplanation(question), savedPrefixChars = fixed)
    }

    /**
     * An emergency question answered from guide sections only, in strict cited mode: the model's
     * own knowledge is not allowed in, whatever the question's wording.
     */
    fun buildGuide(question: String, sections: List<Evidence>): PackedPrompt {
        require(question.isNotBlank()) { "question must not be blank" }
        val sources = sections.mapIndexed { index, section -> PromptSource("S${index + 1}", section, excerpt = section.text) }
        var fixed = 0
        val prompt = buildString {
            append(GUIDE_POLICY)
            append(OFFLINE_PERSON_GUIDE)
            fixed = length
            append("\n\nEVIDENCE:\n")
            append(sources.joinToString("\n\n") { "[${it.citationId}]\nTitle: ${compactMetadata(it.evidence.title)}\nExcerpt: ${it.evidence.text}" })
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, sources, mixedAnswer = false, savedPrefixChars = fixed)
    }

    fun buildModelOnly(question: String): PackedPrompt {
        require(question.isNotBlank()) { "question must not be blank" }
        var fixed = 0
        val prompt = buildString {
            append(MODEL_ONLY_POLICY)
            append(OFFLINE_PERSON)
            fixed = length
            if (VENUE_QUESTION.containsMatchIn(question) &&
                LOCAL_PLACE_REQUEST.containsMatchIn(question)) append(MODEL_ONLY_VENUE_POLICY)
            append("\n\nQUESTION:\n")
            append(question.trim())
            append("\n\nANSWER:")
        }
        return PackedPrompt(prompt, emptyList(), mixedAnswer = allowsModelExplanation(question), savedPrefixChars = fixed)
    }

    internal fun allowsModelExplanation(question: String): Boolean =
        !asksForPlaces(question) && !SOURCE_ONLY_REQUEST.containsMatchIn(question)

    /**
     * A question about places, answered only from listings. An explanation or comparison of a kind
     * of place that names no place ("how do hostels compare with guesthouses?", "why are museums
     * closed on Mondays?") is explained like any other question.
     */
    private fun asksForPlaces(question: String): Boolean = VENUE_QUESTION.containsMatchIn(question) &&
        (VenueLookup.isPlaceLookup(question) || !EXPLANATORY.containsMatchIn(question) || NAMED_PLACE.containsMatchIn(question))

    private val EXPLANATORY = Regex("(?i)^\\s*(?:how (?:do|does|did|is|are|can|should)|why|explain|describe|compare|" +
        "what(?:'s|’s| is| are) the (?:difference|differences|point)|is it (?:worth|better|cheaper))\\b")
    // "in lisbon", "near the Louvre": a place, not "in terms of" or "in the morning".
    private val NAMED_PLACE = Regex("(?i)\\b(?:in|near|around|at)\\s+(?!(?:a|an|my|your|our|their|his|her|its|this|that|these|those|" +
        "terms|general|particular|advance|total|fact|case|order|summer|winter|spring|autumn|fall|the (?:morning|evening|afternoon|day|night|week|end))\\b)" +
        "(?:the\\s+)?\\p{L}")

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
