package xyz.fieldatlas.research

/** Conservative post-retrieval gate: irrelevant passages are worse than an honest model-only answer. */
object EvidenceRelevance {
    internal fun comparisonTerms(question: String?): List<String> {
        if (question == null) return emptyList()
        // Only unambiguous single-word subjects. Multiword names and trailing clauses
        // retain the normal gate rather than turning a modifier into a subject.
        val comparison = Regex("(?i)^\\s*compare\\s+([\\p{L}\\p{N}-]+)\\s+(?:and|with|versus|vs\\.?)\\s+([\\p{L}\\p{N}-]+)\\s*(?=[.!?]|$)")
            .find(question) ?: return emptyList()
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
        return rankByPassage(evidence.filter { item ->
            (if (overview.isNotEmpty()) hasOverviewStatement(item, overview)
             else if (subjects.isEmpty()) isRelevant(item, relevanceTerms, if (sourceOnly) emptyList() else expandedTerms)
             else subjects.any { tokens(topicalText(item.text)).matches(it) }) &&
                destinationMatches(item, question) && categoryMatches(item, question) &&
                dietaryMatches(item, question)
        }, subjects.ifEmpty { relevanceTerms })
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
            .any { line -> statement.containsMatchIn(line.trim().replace('’', '\'').replace(Regex("(?i)^abstract:\\s*"), "")) }) return true
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
        val attribution = item.matchedBy
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
        val concept = attribution?.let { conceptPattern.matchEntire(it) }
            ?.groupValues?.get(1)?.toDoubleOrNull()

        return questionHits >= requiredQuestionHits ||
            (contentTerms.size <= 3 && questionHits >= 1 && expandedHits >= 1) ||
            // A high semantic score can bridge vocabulary mismatch, but the threshold is
            // intentionally stricter than pack import's broad rejection floor.
            (concept != null && concept >= HIGH_CONFIDENCE_CONCEPT)
    }

    private fun tokens(value: String): Set<String> = tokenPattern.findAll(value.lowercase())
        .map { it.value }
        .toSet()

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
