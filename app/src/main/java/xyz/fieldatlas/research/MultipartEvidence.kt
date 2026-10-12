package xyz.fieldatlas.research

/** A bounded recall aid for explicit clauses, not evidence entailment or completeness.
 * Each clause still passes the ordinary gate. An elliptical/pronominal later clause
 * may only use the first clause's named reference topic or its already accepted document. */
internal object MultipartEvidence {
    private val independent = Regex("(?i)^\\s*(?:what\\s+(?:is|are)|(?:how|why)\\s+(?:does|do))\\s+")
    private val dependent = Regex("(?i)\\b(?:it|its|they|their|them|this|that|these|those|former|latter)\\b")

    fun clauses(question: String): List<String> = if (VenueLookup.isPlaceLookup(question) ||
        TechnicalRuleFocus.from(question) != null || TravelSections.inTravelContext(question) ||
        Phrasebook.ask(question) != null) emptyList() else QuestionParts.clauses(question)

    fun groups(evidence: List<Evidence>, question: String,
        ordinary: (List<Evidence>, String) -> List<Evidence>): List<List<Evidence>> {
        val clauses = clauses(question)
        if (clauses.isEmpty()) return emptyList()
        val sourceOnly = !PromptBuilder.allowsModelExplanation(question)
        fun keep(clause: String): List<Evidence> {
            // Carry the ORIGINAL policy, never turn a source-only directive into a
            // general model-knowledge question merely because discovery split it.
            val scoped = if (sourceOnly) "According to the saved sources, $clause" else clause
            val found = ordinary(evidence, scoped)
            return if (EvidenceRelevance.missingRequestedIdentifier(scoped, found) ||
                EvidenceRelevance.missingNamedMechanism(scoped, found)) emptyList() else found
        }
        val first = keep(clauses.first())
        val documents = first.map { it.documentId to it.source }.toSet()
        val names = first.filter { EvidenceRelevance.hasQuestionTopic(it, clauses.first()) }
            .map { it.title.substringBefore(" — ") }.distinct()
        val firstScope = if (sourceOnly) "According to the saved sources, ${clauses.first()}" else clauses.first()
        return listOf(first) + clauses.drop(1).map { clause ->
            val namesOwnTopic = independent.containsMatchIn(clause) && !dependent.containsMatchIn(clause)
            keep(clause).filter { item ->
                (item.documentId to item.source) in documents ||
                    (EvidenceRelevance.hasQuestionTopic(item, clauses.first()) &&
                        !EvidenceRelevance.missingRequestedIdentifier(firstScope, listOf(item))) ||
                    (names.any { EvidenceRelevance.mentionsNamedTopic(item, it) } &&
                        !EvidenceRelevance.missingRequestedIdentifier(firstScope, listOf(item))) ||
                    namesOwnTopic && EvidenceRelevance.hasQuestionTopic(item, clause)
            }
        }
    }

    /** Reserve one already eligible passage per clause before a bounded merge. */
    fun anchors(groups: List<List<Evidence>>): List<Evidence> {
        // Prefer a row eligible for several clauses over several redundant rows.
        // Stable max preserves retrieval order on ties; this remains lexical recall.
        val membership = groups.flatten().distinct().associateWith { row -> groups.count { row in it } }
        return groups.mapNotNull { rows -> rows.maxByOrNull { membership.getValue(it) } }
            .distinctBy { Triple(it.documentId, it.chunkId, it.source) }
    }

    /** At most one extra search per missing clause (two or three clauses total).
     * Original question scopes every query, and the usual relevance gate is reapplied.
     * No inferred synonym, new model turn, change of question, or article-wide bypass. */
    suspend fun recover(question: String, initial: List<Evidence>, retriever: Retriever,
        candidateLimit: Int, resultLimit: Int): List<Evidence> {
        val clauses = clauses(question)
        if (clauses.isEmpty()) return initial
        var found = initial
        val terms = FtsQuery.from(question)?.terms.orEmpty()
        for ((index, clause) in clauses.withIndex()) {
            if (EvidenceRelevance.clauseGroups(found, question).getOrNull(index).orEmpty().isNotEmpty()) continue
            val rows = retriever.searchForQuestion(clause, question, minOf(24, candidateLimit)) { }
            found = EvidenceRelevance.keep((found + rows)
                .distinctBy { Triple(it.documentId, it.chunkId, it.source) }, terms, question = question)
        }
        val anchors = anchors(EvidenceRelevance.clauseGroups(found, question))
        return (anchors + found).distinctBy { Triple(it.documentId, it.chunkId, it.source) }.take(resultLimit)
    }
}
