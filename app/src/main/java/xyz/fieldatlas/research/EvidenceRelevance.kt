package xyz.fieldatlas.research

/** Conservative post-retrieval gate: irrelevant passages are worse than an honest model-only answer. */
object EvidenceRelevance {
    /** Short causal questions use the original terms as a strict lexical boundary.
     * Avoid a second generative search turn when that boundary found no evidence;
     * an explicitly unverified model-only answer is preferable to query drift. */
    internal fun isShortCausalQuestion(question: String): Boolean =
        comparisonTerms(question).isEmpty() && PromptBuilder.allowsModelExplanation(question) &&
            FtsQuery.from(question)?.terms.orEmpty().size in 2..3 &&
            Regex("(?i)^\\s*(?:explain\\s+)?(?:why|how)\\b").containsMatchIn(question)

    /** Lexical gap disclosure only. Presence is not proof of adequate coverage. */
    fun missingComparisonSubjects(question: String, evidence: List<Evidence>): List<String> =
        comparisonTerms(question).filter { subject ->
            evidence.none { tokens(topicalText(it.text)).matches(subject) }
        }

    internal fun comparisonTerms(question: String?): List<String> {
        if (question == null) return emptyList()
        // Only unambiguous single-word subjects. Multiword names and trailing clauses
        // retain the normal gate rather than turning a modifier into a subject.
        val comparison = Regex("(?i)^\\s*compare\\s+([\\p{L}\\p{N}-]+)\\s+(?:and|with|versus|vs\\.?)\\s+([\\p{L}\\p{N}-]+)\\s*(?=[.!?]|$)")
            .find(question) ?: Regex(
                "(?i)^\\s*how\\s+(?:does|do|is|are)\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s+(?:differ|different)\\s+from\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s*(?=[.!?]|$|,\\s+and\\s+(?:when|how|why)\\b)",
            ).find(question) ?: return emptyList()
        return FtsQuery.from(comparison.groupValues[1] + " " + comparison.groupValues[2])?.terms.orEmpty()
    }
    // Indexing vocabulary helps discovery, but is not a claim made by a passage.
    internal fun passageText(text: String): String = text.lineSequence()
        .filterNot { it.trimStart().startsWith("MeSH:", ignoreCase = true) }
        .joinToString("\n").trim()

    private fun topicalText(text: String): String {
        val passage = passageText(text)
        // Structured scholarly headers describe provenance, not the subject of the
        // abstract. Keep Evidence itself intact for the source viewer and attribution.
        val abstract = Regex("(?im)^Abstract:\\s*").find(passage)
        return if (passage.startsWith("PMID ") && abstract != null) {
            passage.substring(abstract.range.last + 1)
        } else passage
    }

    internal fun rankByPassage(evidence: List<Evidence>, terms: List<String>): List<Evidence> {
        // Keep structured place ranking intact. Scholarly keyword lists otherwise receive
        // title/BM25 boosts even when the actual passage covers none of the requested topics.
        if (evidence.none { it.text.contains(Regex("(?im)^\\s*MeSH:")) }) return evidence
        return evidence.sortedByDescending { item ->
            val body = tokens(topicalText(item.text))
            terms.filterNot(lookupWrappers::contains).count { body.matches(it) }
        }
    }
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val conceptPattern = Regex("^concept match ([0-9.]+)$")
    private val lookupWrappers = setOf("best", "recommend", "suggest", "find", "list", "listed", "which", "where")

    fun keep(
        evidence: List<Evidence>,
        questionTerms: List<String>,
        expandedTerms: List<String> = emptyList(),
        question: String? = null,
    ): List<Evidence> {
        if (questionTerms.isEmpty()) return evidence
        val subjects = comparisonTerms(question)
        val overview = overviewSubjects(question)
        // Request boilerplate is not evidence of the requested subject. In source-only
        // mode, planner vocabulary must not substitute for that subject either.
        val sourceOnly = question != null && !PromptBuilder.allowsModelExplanation(question)
        val relevanceTerms = if (sourceOnly) questionTerms.filterNot {
            it in setOf("according", "saved", "local", "provided", "attached", "source", "sources",
                "document", "documents", "file", "files", "notes", "summarize", "summarise",
                "today", "currently", "current", "now")
        } else questionTerms
        val namedArticles = namedArticleSubjects(evidence, question)
        val generalExplanation = question != null && PromptBuilder.allowsModelExplanation(question) &&
            Regex("(?i)^\\s*(?:explain|describe|why|how|can|compare|what\\s+(?:is|are|makes)|tell\\s+me\\s+about)\\b").containsMatchIn(question)
        val causalExplanation = subjects.isEmpty() && question != null && PromptBuilder.allowsModelExplanation(question) &&
            Regex("(?i)^\\s*(?:explain\\s+)?(?:why|how)\\b").containsMatchIn(question)
        val overviewAnchors = evidence.filter {
            isRequestedOverview(it, question) && hasOverviewStatement(it, overview)
        }
        return rankByPassage(evidence.filter { item ->
            (if (overview.isNotEmpty()) hasOverviewStatement(item, overview) ||
                overviewAnchors.any { anchor -> isAdjacentOverview(item, anchor) }
             else if (subjects.isEmpty()) isRelevant(item, relevanceTerms, if (sourceOnly) emptyList() else expandedTerms) ||
                 (!sourceOnly && isNamedReferenceDefinition(item, question))
             else subjects.any { tokens(topicalText(item.text)).matches(it) }) &&
                // A place listing cannot explain a physical mechanism merely because its
                // menu contains the same words. For short causal questions, require every
                // original content term in the body unless an independently computed strong
                // concept match bridges the vocabulary gap. Similarity is not fact checking.
                (!causalExplanation || (!isTravelListing(item) &&
                    (questionTerms.size !in 2..3 || isStrongConceptMatch(item) ||
                        questionTerms.all { tokens(topicalText(item.text)).matches(it) }))) &&
                (!generalExplanation || !isTravelListing(item)) &&
                (!generalExplanation || overview.isNotEmpty() || isStrongConceptMatch(item) ||
                    explanationTopicMatches(item, relevanceTerms)) &&
                destinationMatches(item, question) && categoryMatches(item, question) &&
                dietaryMatches(item, question) &&
                (namedArticles.isEmpty() || isTravelListing(item) || namedArticles.any { subject ->
                    containsPhrase(item.title.substringBefore(" — ") + " " + topicalText(item.text), subject)
                })
        }, subjects.ifEmpty { relevanceTerms })
    }

    /** A question can name a precise multiword topic while using different words
     * for its consequences. Require the actual definition, not just a title hit. */
    private fun isNamedReferenceDefinition(item: Evidence, question: String?): Boolean {
        if (question == null || !item.title.endsWith(" — Overview") || isTravelListing(item)) return false
        val parent = item.title.removeSuffix(" — Overview")
        val terms = FtsQuery.from(parent)?.terms.orEmpty()
        val body = tokens(topicalText(item.text))
        return terms.size >= 2 && terms.all { body.matches(it) } &&
            containsPhrase(question, parent) && hasOverviewStatement(item, listOf(parent))
    }

    /** An incidental phrase in a long article is not enough for a general explanation.
     * Prefer a named topic in the title, or a compact explanatory statement in the body.
     * Specific source/study questions retain the normal relevance path. */
    private fun explanationTopicMatches(item: Evidence, terms: List<String>): Boolean {
        val topicTerms = terms.filterNot { it in setOf("both", "same", "need", "needs", "affect", "different", "differ", "useful") }
        if (topicTerms.isEmpty()) return false
        val title = tokens(item.title)
        val titleHits = topicTerms.count { title.matches(it) }
        // Two broad words in a long question (for example room + temperature)
        // must not outweigh its actual subjects (metal + wood).
        if (titleHits >= maxOf(minOf(2, topicTerms.size), (topicTerms.size * 2 + 2) / 3)) return true
        val explanation = Regex("(?i)\\b(?:because|due to|caused by|results? from|results? in|therefore|through|enables?|allows?|gives?|causes?|requires?|converts?|consists? of|defined as|refers? to)\\b")
        val required = if (topicTerms.size <= 3) topicTerms.size else (topicTerms.size * 2 + 2) / 3
        return topicalText(item.text).split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
            .any { sentence ->
                explanation.containsMatchIn(sentence) &&
                    topicTerms.count { tokens(sentence).matches(it) } >= required
            } || hasOverviewStatement(item, listOf(item.title.substringBefore(" — "))).let { overview ->
                // This fallback requires an explicitly named sectioned reference topic.
                overview && " — " in item.title && titleHits > 0
            }
    }

    /** Only explicit reference overview sections for the requested subject qualify.
     * A title alone is not enough to admit a passage: keep() also requires an anchor
     * with explanatory body text in the same section and provenance. */
    internal fun isRequestedOverview(item: Evidence, question: String?): Boolean {
        if (!item.title.endsWith(" — Overview") || isTravelListing(item)) return false
        val parent = item.title.removeSuffix(" — Overview").trim()
        return overviewSubjects(question).any { it.equals(parent, ignoreCase = true) }
    }

    private fun isAdjacentOverview(item: Evidence, anchor: Evidence): Boolean {
        if (item.documentId != anchor.documentId || item.source != anchor.source ||
            item.title != anchor.title) return false
        fun index(value: Evidence): Long? {
            val prefix = value.documentId + ":"
            if (!value.chunkId.startsWith(prefix)) return null
            return value.chunkId.removePrefix(prefix).takeIf { it.matches(Regex("[0-9]{1,9}")) }?.toLongOrNull()
        }
        val current = index(item) ?: return false
        val anchored = index(anchor) ?: return false
        // Do not recursively extend the window through newly admitted passages.
        return kotlin.math.abs(current - anchored) == 1L
    }

    /** Sectioned reference articles carry an explicit parent subject. If the question
     * names one of those parents, incidental overlaps in an unrelated article must
     * not replace that subject. This lexical guard is not an entailment check and
     * deliberately does not infer subjects from planner-generated vocabulary. */
    private fun namedArticleSubjects(evidence: List<Evidence>, question: String?): List<String> {
        if (question == null) return emptyList()
        return evidence.asSequence().filter { !isTravelListing(it) && " — " in it.title }
            .map { it.title.substringBefore(" — ").trim() }
            .filter { it.length >= 4 && FtsQuery.from(it) != null && containsPhrase(question, it) }
            .distinct().toList()
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        val pattern = tokenPattern.findAll(phrase).joinToString("\\s+") { Regex.escape(it.value) }
        return pattern.isNotEmpty() && Regex("(?i)(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])")
            .containsMatchIn(text)
    }

    /** English overview heuristic, not semantic verification. Other question forms keep
     * the ordinary relevance checks; source-specific requests must not lose study findings. */
    private fun overviewSubjects(question: String?): List<String> {
        if (question == null || !PromptBuilder.allowsModelExplanation(question)) return emptyList()
        comparisonTerms(question).takeIf { it.size == 2 }?.let { return it }
        val definition = Regex("(?i)^\\s*(?:what is|what are|explain|describe|tell me about)\\s+([\\p{L}\\p{N} '-]+?)(?=\\s+and\\s+(?:how|why)\\b|[.!?]|$)")
            .find(question.replace('’', '\''))?.groupValues?.get(1)?.trim() ?: return emptyList()
        val words = definition.split(Regex("\\s+"))
        if (words.size !in 1..4 || words.first().lowercase() in setOf("how", "why", "whether", "my", "our", "this", "that", "these", "those")) return emptyList()
        return listOf(definition.replace(Regex("(?i)^the\\s+"), ""))
    }

    private fun hasOverviewStatement(item: Evidence, subjects: List<String>): Boolean {
        fun wordPattern(word: String): String {
            val base = word.lowercase()
            val forms = mutableSetOf(base, base + "s", base + "es")
            when {
                base.endsWith("ies") -> forms += base.dropLast(3) + "y"
                base.endsWith("es") -> forms += base.dropLast(2)
                base.endsWith("s") && !base.endsWith("ss") && !base.endsWith("us") && !base.endsWith("is") -> forms += base.dropLast(1)
            }
            return forms.joinToString("|", "(?:", ")", transform = Regex::escape)
        }
        val subject = subjects.joinToString("|") { phrase -> phrase.split(Regex("\\s+")).joinToString("\\s+", transform = ::wordPattern) }
        // Require the concept to be the subject of an explanatory sentence, rather than
        // appearing as an experimental setting, title, index tag, or trailing keyword.
        val statement = Regex("(?i)^(?:(?:a|an|the)\\s+)?(?:$subject)(?:\\s+(?:and|or)\\s+(?:$subject))?(?:\\s*\\([^)]{1,40}\\)|,\\s*[^,\\n]{1,80},)?\\s+(?:is|are|refers? to|means?|consists? of|involves?|produces?|generates?|preserves?|reduces?|differs?|supports?|stores?|uses?|converts?|causes?|requires?|enables?|prevents?|spans?|begins?|began|includes?)\\b")
        val passage = topicalText(item.text)
        if (passage.split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
            .any { line ->
                // A domain qualifier does not make the following definition incidental.
                // Normalize only for matching; retain the complete original evidence.
                val sentence = line.trim().replace('’', '\'')
                    .replace(Regex("(?i)^abstract:\\s*"), "")
                    .replace(Regex("(?i)^in\\s+[^,.!?\\r\\n]{1,60},\\s*"), "")
                statement.containsMatchIn(sentence)
            }) return true
        // Do not apply English sentence grammar to text we cannot confidently classify
        // as English. Keep the ordinary lexical gate for that evidence instead.
        val englishMarkers = setOf("the", "a", "an", "is", "are", "of", "in", "with", "from", "during", "we", "these", "this")
        if (tokens(passage).count(englishMarkers::contains) < 2) {
            return isRelevant(item, subjects.flatMap { FtsQuery.from(it)?.terms.orEmpty() }, emptyList())
        }
        return false
    }

    private fun categoryMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !item.documentId.startsWith("wv-place-")) return true
        val wanted = buildSet {
            if (Regex("(?i)\\b(restaurants?|places? to eat|dining)\\b").containsMatchIn(question)) add("Eat")
            if (Regex("(?i)\\b(caf[eé]s?)\\b").containsMatchIn(question)) addAll(listOf("Eat", "Drink"))
            if (Regex("(?i)\\b(bars?|nightlife)\\b").containsMatchIn(question)) add("Drink")
            if (Regex("(?i)\\b(hotels?|hostels?|places? to stay)\\b").containsMatchIn(question)) add("Sleep")
            if (Regex("(?i)\\b(museums?|sights?|attractions?|things? to do)\\b").containsMatchIn(question)) {
                addAll(listOf("See", "Do"))
            }
            if (Regex("(?i)\\b(shopping|shops?|stores?)\\b").containsMatchIn(question)) add("Buy")
        }
        if (wanted.isEmpty()) return true
        val category = item.text.lineSequence().firstOrNull { it.startsWith("Category: ") }
            ?.removePrefix("Category: ") ?: return false
        return category in wanted
    }

    private fun dietaryMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !isEatListing(item)) return true
        val veganRequested = Regex("(?i)\\bvegan\\b").containsMatchIn(question)
        val vegetarianRequested = Regex("(?i)\\bvegetarian\\b").containsMatchIn(question)
        if (!veganRequested && !vegetarianRequested) return true
        val text = item.text
        // Vegetarian does not imply vegan. A vegan listing also satisfies a vegetarian query.
        return if (veganRequested) {
            Regex("(?i)\\bvegan\\b").containsMatchIn(text)
        } else {
            Regex("(?i)\\b(?:vegetarian|vegan)\\b").containsMatchIn(text)
        }
    }

    private fun destinationMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !isTravelListing(item)) return true
        if (!Regex("(?i)\\b(in|near|around)\\s+\\p{L}").containsMatchIn(question)) return true
        val firstLine = item.text.lineSequence().firstOrNull() ?: return true
        if (!firstLine.startsWith("Destination: ")) return true
        val destination = firstLine.removePrefix("Destination: ").trim().takeIf(String::isNotEmpty)
            ?: return true
        // A district guide (e.g. London/Camden) is eligible for a city query; a
        // different city mentioned only in an address or directions is not.
        val names = listOf(destination, destination.substringBefore('/')).distinct()
        return names.any { name ->
            Regex("(?i)\\b(?:in|near|around)\\s+${Regex.escape(name)}\\b").containsMatchIn(question)
        }
    }

    private fun isTravelListing(item: Evidence) =
        item.documentId.startsWith("wv-eat-") || item.documentId.startsWith("wv-place-")

    private fun isEatListing(item: Evidence) = item.documentId.startsWith("wv-eat-") ||
        (item.documentId.startsWith("wv-place-") &&
            item.text.lineSequence().any { it == "Category: Eat" })

    private fun isRelevant(item: Evidence, questionTerms: List<String>, expandedTerms: List<String>): Boolean {
        // FTS prefix matches can lack an exact-term attribution. They still need the same
        // relevance check; otherwise unrelated hits bypass the gate entirely.
        val passage = topicalText(item.text)
        if (passage.isBlank()) return false
        val hasIndexTags = passage != item.text.trim()
        val bodyTokens = tokens(if (hasIndexTags) passage else item.title + " " + passage)
        val contentTerms = questionTerms.filterNot(lookupWrappers::contains)
        val questionHits = contentTerms.count { term -> bodyTokens.matches(term) }
        val expandedHits = expandedTerms
            .filterNot(contentTerms::contains)
            .count { term -> bodyTokens.matches(term) }
        // Two incidental overlaps are common in broad scientific corpora. A question with
        // four or more distinct content terms needs most of them in the same passage before
        // it can displace an honest model-only answer. Single-topic lookups (including
        // "Indonesian") still need only their one requested term.
        val requiredQuestionHits = when (contentTerms.size) {
            0, 1 -> 1
            2, 3 -> 2
            else -> 3
        }
        return questionHits >= requiredQuestionHits ||
            (contentTerms.size <= 3 && questionHits >= 1 && expandedHits >= 1) ||
            // A high semantic score can bridge vocabulary mismatch, but the threshold is
            // intentionally stricter than pack import's broad rejection floor.
            isStrongConceptMatch(item)
    }

    private fun isStrongConceptMatch(item: Evidence): Boolean = item.matchedBy
        ?.let { conceptPattern.matchEntire(it) }?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { it.isFinite() && it in HIGH_CONFIDENCE_CONCEPT..1.0 } == true

    private fun tokens(value: String): Set<String> = tokenPattern.findAll(value.lowercase())
        .map { it.value }
        .toSet()

    /** Keep excerpt ranking consistent with the word variants accepted by retrieval. */
    internal fun termHits(text: String, terms: List<String>): Int {
        val words = tokens(text)
        return terms.count { words.matches(it) }
    }

    private fun Set<String>.matches(term: String): Boolean {
        val stem = stem(term.lowercase())
        return any { token -> token.startsWith(stem) }
    }

    private fun stem(term: String): String = when {
        term.length > 4 && term.endsWith("ies") -> term.dropLast(2)
        term.length > 4 && term.endsWith("es") -> term.dropLast(2)
        term.length > 3 && term.endsWith("s") -> term.dropLast(1)
        term.length > 4 && term.endsWith("ing") -> term.dropLast(3)
        term.length > 4 && term.endsWith("ed") -> term.dropLast(2)
        else -> term
    }

    private const val HIGH_CONFIDENCE_CONCEPT = 0.78
}
