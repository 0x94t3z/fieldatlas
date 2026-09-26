package xyz.fieldatlas.research

/** Conservative post-retrieval gate: irrelevant passages are worse than an honest model-only answer. */
object EvidenceRelevance {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val conceptPattern = Regex("^concept match ([0-9.]+)$")

    fun keep(
        evidence: List<Evidence>,
        questionTerms: List<String>,
        expandedTerms: List<String> = emptyList(),
        question: String? = null,
    ): List<Evidence> {
        if (questionTerms.isEmpty()) return evidence
        return evidence.filter { item ->
            isRelevant(item, questionTerms, expandedTerms) &&
                destinationMatches(item, question) && dietaryMatches(item, question)
        }
    }

    private fun dietaryMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !item.documentId.startsWith("wv-eat-")) return true
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
        if (question == null || !item.documentId.startsWith("wv-eat-")) return true
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

    private fun isRelevant(item: Evidence, questionTerms: List<String>, expandedTerms: List<String>): Boolean {
        // FTS prefix matches can lack an exact-term attribution. They still need the same
        // relevance check; otherwise unrelated hits bypass the gate entirely.
        val attribution = item.matchedBy
        val bodyTokens = tokens(item.title + " " + item.text)
        val questionHits = questionTerms.count { term -> bodyTokens.matches(term) }
        val expandedHits = expandedTerms
            .filterNot(questionTerms::contains)
            .count { term -> bodyTokens.matches(term) }
        // Two incidental overlaps are common in broad scientific corpora. A question with
        // four or more distinct content terms needs most of them in the same passage before
        // it can displace an honest model-only answer. Single-topic lookups (including
        // "Indonesian") still need only their one requested term.
        val requiredQuestionHits = when (questionTerms.size) {
            0, 1 -> 1
            2, 3 -> 2
            else -> 3
        }
        val concept = attribution?.let { conceptPattern.matchEntire(it) }
            ?.groupValues?.get(1)?.toDoubleOrNull()

        return questionHits >= requiredQuestionHits ||
            (questionHits >= 1 && expandedHits >= 1) ||
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
